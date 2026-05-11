package com.ufla.visionproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
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

public class CameraActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 101;

    private PreviewView previewView;
    private TextView status;
    private ExecutorService cameraExecutor;
    private volatile Bitmap lastBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        cameraExecutor = Executors.newSingleThreadExecutor();

        LinearLayout root = Ui.vertical(this);
        root.setPadding(16, 32, 16, 16);

        status = Ui.text(this, "Atividade 1: preview CameraX + captura JPEG");
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        Button capture = Ui.button(this, "Capturar frame atual em JPEG");
        capture.setOnClickListener(v -> captureFrame());

        root.addView(Ui.title(this, "Atividade 1 — Câmera"));
        root.addView(status);
        root.addView(previewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(capture);

        setContentView(root);

        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
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
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                );

                status.setText("Câmera traseira ativa. Toque em Capturar.");
            } catch (Exception e) {
                status.setText("Erro ao iniciar câmera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureFrame() {
        Bitmap bitmap = lastBitmap;
        if (bitmap == null) {
            Toast.makeText(this, "Aguarde o primeiro frame da câmera.", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageUtils.saveBitmapToGallery(this, bitmap, "atividade1_frame");
        Toast.makeText(this, "JPEG salvo em Pictures/VisionProject.", Toast.LENGTH_LONG).show();
        status.setText("Frame salvo com timestamp.");
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
            Toast.makeText(this, "Permissão de câmera é obrigatória.", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
