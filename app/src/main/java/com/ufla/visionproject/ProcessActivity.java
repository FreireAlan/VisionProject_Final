package com.ufla.visionproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 102;

    private PreviewView previewView;
    private ImageView resultView;
    private TextView status;
    private SeekBar lowSeek;
    private SeekBar highSeek;
    private ExecutorService cameraExecutor;

    private volatile Bitmap lastBitmap;
    private OpenCvHelper.ProcessResult lastResult;

    private int low = 50;
    private int high = 150;
    private long lastAutoProcessMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        cameraExecutor = Executors.newSingleThreadExecutor();

        LinearLayout root = Ui.vertical(this);
        root.setPadding(16, 32, 16, 16);

        status = Ui.text(this, label());

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        resultView = new ImageView(this);
        resultView.setAdjustViewBounds(true);
        resultView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        resultView.setBackgroundColor(0xFF000000);

        lowSeek = new SeekBar(this);
        lowSeek.setMax(255);
        lowSeek.setProgress(low);

        highSeek = new SeekBar(this);
        highSeek.setMax(255);
        highSeek.setProgress(high);

        Button process = Ui.button(this, "Processar frame atual");
        Button save = Ui.button(this, "Salvar original, cinza, suavizada e bordas");

        lowSeek.setOnSeekBarChangeListener(new ThresholdListener(true));
        highSeek.setOnSeekBarChangeListener(new ThresholdListener(false));

        process.setOnClickListener(v -> processCurrentFrame(true));
        save.setOnClickListener(v -> saveAll());

        root.addView(Ui.title(this, "Atividade 2 — Filtros e Canny"));
        root.addView(status);
        root.addView(previewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.8f));
        root.addView(resultView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.8f));
        root.addView(Ui.text(this, "Threshold fraco / Tlow"));
        root.addView(lowSeek);
        root.addView(Ui.text(this, "Threshold forte / Thigh"));
        root.addView(highSeek);
        root.addView(process);
        root.addView(save);

        setContentView(root);

        if (hasCameraPermission()) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    private String label() {
        return "Pipeline: RGB → cinza → Gaussiano 5x5 → Canny | Tlow=" + low + " Thigh=" + high;
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
                        if (bitmap != null) {
                            lastBitmap = bitmap;
                            long now = System.currentTimeMillis();
                            if (now - lastAutoProcessMs > 700) {
                                lastAutoProcessMs = now;
                                runOnUiThread(() -> processCurrentFrame(false));
                            }
                        }
                    } finally {
                        image.close();
                    }
                });

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                status.setText("Erro ao iniciar câmera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processCurrentFrame(boolean showToast) {
        Bitmap bitmap = lastBitmap;
        if (bitmap == null) {
            if (showToast) Toast.makeText(this, "Aguarde um frame.", Toast.LENGTH_SHORT).show();
            return;
        }

        int safeHigh = Math.max(high, low + 1);
        lastResult = OpenCvHelper.processCanny(bitmap, low, safeHigh);
        resultView.setImageBitmap(lastResult.edges);
        status.setText(label());

        if (showToast) {
            Toast.makeText(this, "Processamento concluído.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAll() {
        if (lastResult == null) {
            Toast.makeText(this, "Clique em Processar primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageUtils.saveBitmapToGallery(this, lastResult.original, "atividade2_original");
        ImageUtils.saveBitmapToGallery(this, lastResult.gray, "atividade2_cinza");
        ImageUtils.saveBitmapToGallery(this, lastResult.blurred, "atividade2_gaussiano");
        ImageUtils.saveBitmapToGallery(this, lastResult.edges, "atividade2_canny");

        Toast.makeText(this, "4 imagens salvas em Pictures/VisionProject.", Toast.LENGTH_LONG).show();
    }

    private class ThresholdListener implements SeekBar.OnSeekBarChangeListener {
        private final boolean controlsLow;

        ThresholdListener(boolean controlsLow) {
            this.controlsLow = controlsLow;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (controlsLow) low = progress;
            else high = progress;

            status.setText(label());
            if (fromUser) processCurrentFrame(false);
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {
            processCurrentFrame(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
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
            finish();
        }
    }
}
