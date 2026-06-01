package com.ufla.visionproject;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Executa a captura do par estéreo monocular:
 * I_L.jpg = imagem esquerda; I_R.jpg = imagem direita.
 *
 * Fluxo físico:
 * 1) coloque o Redmi 14C no batente esquerdo do suporte;
 * 2) toque em Capturar I_L;
 * 3) deslize o celular horizontalmente até o batente direito, sem girar;
 * 4) toque em Capturar I_R.
 */
public class StereoCaptureActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 201;
    private static final String PREFS = "stereo_prefs";
    private static final String KEY_BASELINE_CM = "baseline_cm";
    private static final String KEY_OBJECT_DISTANCE_CM = "object_distance_cm";

    private PreviewView previewView;
    private ImageView leftView;
    private ImageView rightView;
    private TextView status;
    private EditText baselineInput;
    private EditText objectDistanceInput;

    private ExecutorService cameraExecutor;
    private volatile Bitmap lastBitmap;
    private Camera boundCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        cameraExecutor = Executors.newSingleThreadExecutor();

        LinearLayout root = Ui.vertical(this);
        root.setPadding(16, 32, 16, 16);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        status = Ui.text(this, "Aguardando câmera. Use o suporte com translação horizontal pura.");

        baselineInput = new EditText(this);
        baselineInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        baselineInput.setHint("Baseline em cm = deslocamento lateral. Ex.: 8.0");
        baselineInput.setText(String.valueOf(loadBaselineCm()));
        baselineInput.setSingleLine(true);

        objectDistanceInput = new EditText(this);
        objectDistanceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        objectDistanceInput.setHint("Distância real do objeto à câmera em cm. Ex.: 100");
        objectDistanceInput.setText(String.valueOf(loadObjectDistanceCm()));
        objectDistanceInput.setSingleLine(true);

        leftView = previewImage();
        rightView = previewImage();

        Button saveBaseline = Ui.button(this, "Salvar baseline e distância real");
        Button lock = Ui.button(this, "Travar foco/exposição no centro");
        Button captureLeft = Ui.button(this, "1) Capturar I_L — posição esquerda");
        Button captureRight = Ui.button(this, "2) Capturar I_R — posição direita");
        Button openProcessing = Ui.button(this, "Abrir processamento estéreo");
        Button clear = Ui.button(this, "Limpar par estéreo salvo");

        saveBaseline.setOnClickListener(v -> {
            double b = readBaselineFromInput();
            double obj = readObjectDistanceFromInput();
            saveBaselineCm(b);
            saveObjectDistanceCm(obj);
            String warning = b > 20.0
                    ? "\nATENÇÃO: baseline é o deslocamento lateral do celular, não a distância até o objeto."
                    : "";
            Toast.makeText(this,
                    "Baseline salva: " + b + " cm. Distância real do objeto: " + obj + " cm." + warning,
                    Toast.LENGTH_LONG).show();
        });

        lock.setOnClickListener(v -> lockCenterFocusMetering());
        captureLeft.setOnClickListener(v -> captureStereoImage(true));
        captureRight.setOnClickListener(v -> captureStereoImage(false));
        openProcessing.setOnClickListener(v -> startActivity(new Intent(this, StereoProcessingActivity.class)));
        clear.setOnClickListener(v -> clearStereoPair());

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.addView(leftView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        pair.addView(rightView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        root.addView(Ui.title(this, "Estéreo — T2 Captura do par"));
        root.addView(Ui.text(this, "Antes de capturar: mantenha a cena parada, não altere zoom, não gire o celular e use batentes físicos."));
        root.addView(Ui.text(this, "Use a mesma orientação da calibração. Recomendado neste projeto: celular na vertical/retrato."));
        root.addView(Ui.text(this, "Baseline = distância lateral entre POS 1 e POS 2. Não é a distância até o objeto. Recomendado: 4 a 10 cm."));
        root.addView(status);
        root.addView(baselineInput);
        root.addView(Ui.text(this, "Distância real = distância frontal da câmera até o objeto de referência. Use fita métrica; serve para calcular o erro da profundidade."));
        root.addView(objectDistanceInput);
        root.addView(saveBaseline);
        root.addView(previewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.95f));
        root.addView(lock);
        root.addView(captureLeft);
        root.addView(captureRight);
        root.addView(pair, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.70f));
        root.addView(openProcessing);
        root.addView(clear);

        setContentView(root);

        refreshThumbnails();

        if (hasCameraPermission()) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    private ImageView previewImage() {
        ImageView view = new ImageView(this);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setBackgroundColor(0xFF000000);
        return view;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, image -> {
                    try {
                        Bitmap bitmap = ImageUtils.imageProxyToBitmap(image);
                        if (bitmap != null) lastBitmap = bitmap;
                    } finally {
                        image.close();
                    }
                });

                provider.unbindAll();
                boundCamera = provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                );

                status.setText("Câmera ativa. Salve a baseline e capture I_L / I_R.");
            } catch (Exception e) {
                status.setText("Erro ao iniciar câmera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void lockCenterFocusMetering() {
        if (boundCamera == null || previewView.getWidth() == 0 || previewView.getHeight() == 0) {
            Toast.makeText(this, "Aguarde o preview estabilizar.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float cx = previewView.getWidth() / 2f;
            float cy = previewView.getHeight() / 2f;

            FocusMeteringAction action = new FocusMeteringAction.Builder(
                    previewView.getMeteringPointFactory().createPoint(cx, cy),
                    FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB
            ).disableAutoCancel().build();

            boundCamera.getCameraControl().startFocusAndMetering(action);
            status.setText("Foco, exposição e balanço de branco solicitados no ponto central. Aguarde 2 s antes de capturar.");
            Toast.makeText(this, "Medição central solicitada.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            status.setText("Não foi possível travar medição: " + e.getMessage());
        }
    }

    private void captureStereoImage(boolean left) {
        Bitmap bitmap = lastBitmap;
        if (bitmap == null) {
            Toast.makeText(this, "Aguarde o primeiro frame.", Toast.LENGTH_SHORT).show();
            return;
        }

        double baseline = readBaselineFromInput();
        double objectDistance = readObjectDistanceFromInput();
        saveBaselineCm(baseline);
        saveObjectDistanceCm(objectDistance);

        File out = left ? StereoUtils.leftFile(this) : StereoUtils.rightFile(this);
        boolean ok = StereoUtils.saveBitmapToFile(bitmap, out);

        if (!ok) {
            Toast.makeText(this, "Falha ao salvar imagem interna.", Toast.LENGTH_LONG).show();
            return;
        }

        ImageUtils.saveBitmapToGallery(this, bitmap, left ? "stereo_I_L" : "stereo_I_R");

        if (left) {
            leftView.setImageBitmap(bitmap);
            status.setText("I_L salvo. Agora deslize o celular para a direita, sem girar, até o segundo batente.");
        } else {
            rightView.setImageBitmap(bitmap);
            status.setText("I_R salvo. Par estéreo pronto. Baseline=" + baseline
                    + " cm; distância real do objeto=" + objectDistance + " cm. Abra o processamento.");
        }

        Toast.makeText(this, (left ? "I_L" : "I_R") + " salvo em files/stereo.", Toast.LENGTH_LONG).show();
    }

    private void refreshThumbnails() {
        Bitmap left = StereoUtils.loadBitmap(StereoUtils.leftFile(this));
        Bitmap right = StereoUtils.loadBitmap(StereoUtils.rightFile(this));

        if (left != null) leftView.setImageBitmap(left);
        if (right != null) rightView.setImageBitmap(right);
    }

    private void clearStereoPair() {
        StereoUtils.leftFile(this).delete();
        StereoUtils.rightFile(this).delete();
        StereoUtils.matchesFile(this).delete();
        StereoUtils.epipolarFile(this).delete();
        StereoUtils.rectifiedFile(this).delete();
        StereoUtils.disparityFile(this).delete();
        StereoUtils.disparityPresetFile(this, 1).delete();
        StereoUtils.disparityPresetFile(this, 2).delete();
        StereoUtils.disparityPresetFile(this, 3).delete();
        StereoUtils.depthFile(this).delete();
        StereoUtils.reportFile(this).delete();
        StereoUtils.plyFile(this).delete();

        leftView.setImageDrawable(null);
        rightView.setImageDrawable(null);
        status.setText("Par estéreo e resultados removidos. Capture novamente I_L e I_R.");
        Toast.makeText(this, "Arquivos estéreo limpos.", Toast.LENGTH_SHORT).show();
    }

    private double readBaselineFromInput() {
        try {
            String s = baselineInput.getText().toString().trim().replace(",", ".");
            double value = Double.parseDouble(s);
            if (value <= 0.0) return 8.0;
            return value;
        } catch (Exception e) {
            return 8.0;
        }
    }

    private double loadBaselineCm() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(KEY_BASELINE_CM, Double.doubleToLongBits(8.0)));
    }

    private double readObjectDistanceFromInput() {
        try {
            String s = objectDistanceInput.getText().toString().trim().replace(",", ".");
            double value = Double.parseDouble(s);
            if (value <= 0.0) return 100.0;
            return value;
        } catch (Exception e) {
            return 100.0;
        }
    }

    private double loadObjectDistanceCm() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(KEY_OBJECT_DISTANCE_CM, Double.doubleToLongBits(100.0)));
    }

    private void saveBaselineCm(double value) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_BASELINE_CM, Double.doubleToLongBits(value))
                .apply();
    }

    private void saveObjectDistanceCm(double value) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_OBJECT_DISTANCE_CM, Double.doubleToLongBits(value))
                .apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            try {
                cameraExecutor.awaitTermination(300, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Permissão de câmera é obrigatória.", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
