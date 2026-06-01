package com.ufla.visionproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private CalibrationData calibrationData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        LinearLayout root = Ui.vertical(this);
        root.setPadding(32, 48, 32, 32);

        TextView title = Ui.title(this, "VisionProject");
        TextView subtitle = Ui.text(this,
                "Atividades 1 a 4 + Estéreo Visão com smartphone\nCameraX + OpenCV + Canny + Calibração + ORB/RANSAC + Disparidade");
        subtitle.setPadding(0, 0, 0, 24);

        Button camera = Ui.button(this, "Atividade 1 — Capturar Frame JPEG");
        Button process = Ui.button(this, "Atividade 2 — Processar: Cinza → Gaussiano → Canny");
        Button model = Ui.button(this, "Atividade 3 — Modelo de Câmera e Distorção");
        Button calibration = Ui.button(this, "Atividade 4 — Calibração de Câmera");
        Button loadCalibration = Ui.button(this, "Carregar calibration.json");

        Button stereoCapture = Ui.button(this, "Estéreo T2 — Capturar I_L e I_R");
        Button stereoProcessing = Ui.button(this, "Estéreo T3–T7 — ORB, F, retificação e disparidade");
        Button stereoDepth = Ui.button(this, "Estéreo T8 — Profundidade Z e nuvem PLY");

        camera.setOnClickListener(v -> startActivity(new Intent(this, CameraActivity.class)));
        process.setOnClickListener(v -> startActivity(new Intent(this, ProcessActivity.class)));
        model.setOnClickListener(v -> startActivity(new Intent(this, ModeloCameraActivity.class)));
        calibration.setOnClickListener(v -> startActivity(new Intent(this, CalibrationActivity.class)));

        stereoCapture.setOnClickListener(v -> startActivity(new Intent(this, StereoCaptureActivity.class)));
        stereoProcessing.setOnClickListener(v -> startActivity(new Intent(this, StereoProcessingActivity.class)));
        stereoDepth.setOnClickListener(v -> startActivity(new Intent(this, StereoDepthActivity.class)));

        loadCalibration.setOnClickListener(v -> {
            calibrationData = CalibrationStore.load(this);
            if (calibrationData == null) {
                Toast.makeText(this, "Nenhum calibration.json encontrado ainda.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        "Calibração carregada: RMS=" + String.format("%.4f", calibrationData.rms),
                        Toast.LENGTH_LONG).show();
            }
        });

        root.addView(title);
        root.addView(subtitle);

        root.addView(Ui.text(this, "Módulos já implementados"));
        root.addView(camera);
        root.addView(process);
        root.addView(model);
        root.addView(calibration);
        root.addView(loadCalibration);

        root.addView(Ui.text(this, "\nNova atividade — visão estéreo monocular"));
        root.addView(stereoCapture);
        root.addView(stereoProcessing);
        root.addView(stereoDepth);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }
}
