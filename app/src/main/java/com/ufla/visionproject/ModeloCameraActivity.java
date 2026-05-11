package com.ufla.visionproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

public class ModeloCameraActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 103;

    private PreviewView previewView;
    private ImageView originalView;
    private ImageView correctedView;
    private TextView status;
    private ExecutorService cameraExecutor;
    private volatile Bitmap lastBitmap;
    private Bitmap capturedBitmap;
    private Bitmap correctedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        cameraExecutor = Executors.newSingleThreadExecutor();

        LinearLayout root = Ui.vertical(this);
        root.setPadding(16, 32, 16, 16);

        status = Ui.text(this,
                "Atividade 3: valores K aproximados, undistort fixo e linhas epipolares ilustrativas.");

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        originalView = new ImageView(this);
        originalView.setAdjustViewBounds(true);
        originalView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        originalView.setBackgroundColor(0xFF000000);

        correctedView = new ImageView(this);
        correctedView.setAdjustViewBounds(true);
        correctedView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        correctedView.setBackgroundColor(0xFF000000);

        Button capture = Ui.button(this, "Capturar folha A4/grid");
        Button undistort = Ui.button(this, "Aplicar Calib3d.undistort() com K/dist fixos");
        Button save = Ui.button(this, "Salvar original e corrigida");

        EpipolarOverlayView epipolar = new EpipolarOverlayView(this);

        capture.setOnClickListener(v -> captureGrid());
        undistort.setOnClickListener(v -> applyUndistort());
        save.setOnClickListener(v -> savePair());

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.addView(originalView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        pair.addView(correctedView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        root.addView(Ui.title(this, "Atividade 3 — Modelo de Câmera"));
        root.addView(status);
        root.addView(previewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.75f));
        root.addView(capture);
        root.addView(undistort);
        root.addView(save);
        root.addView(pair, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.75f));
        root.addView(Ui.text(this, "Toque até 5 pontos na área abaixo para desenhar linhas epipolares."));
        root.addView(epipolar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300));

        setContentView(root);

        if (hasCameraPermission()) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
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
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                status.setText("Erro ao iniciar câmera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureGrid() {
        Bitmap bitmap = lastBitmap;
        if (bitmap == null) {
            Toast.makeText(this, "Aguarde um frame.", Toast.LENGTH_SHORT).show();
            return;
        }

        capturedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        originalView.setImageBitmap(capturedBitmap);

        double fx = Math.max(capturedBitmap.getWidth(), capturedBitmap.getHeight()) * 1.25;
        double cx = capturedBitmap.getWidth() / 2.0;
        double cy = capturedBitmap.getHeight() / 2.0;

        status.setText("K placeholder:\nfx=" + String.format("%.1f", fx)
                + " fy=" + String.format("%.1f", fx)
                + " cx=" + String.format("%.1f", cx)
                + " cy=" + String.format("%.1f", cy)
                + "\ndist=[-0.25, 0.10, 0.0005, 0.0005, 0.0]");
    }

    private void applyUndistort() {
        if (capturedBitmap == null) {
            Toast.makeText(this, "Capture primeiro a imagem do grid.", Toast.LENGTH_SHORT).show();
            return;
        }

        correctedBitmap = OpenCvHelper.undistortWithDefault(capturedBitmap);
        correctedView.setImageBitmap(correctedBitmap);
        Toast.makeText(this, "Correção aplicada. Compare linhas do grid.", Toast.LENGTH_LONG).show();
    }

    private void savePair() {
        if (capturedBitmap == null || correctedBitmap == null) {
            Toast.makeText(this, "Capture e aplique undistort primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageUtils.saveBitmapToGallery(this, capturedBitmap, "atividade3_original_grid");
        ImageUtils.saveBitmapToGallery(this, correctedBitmap, "atividade3_corrigida_grid");
        Toast.makeText(this, "Par de imagens salvo.", Toast.LENGTH_LONG).show();
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
