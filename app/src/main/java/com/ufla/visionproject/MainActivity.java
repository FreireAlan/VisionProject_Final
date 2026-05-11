package com.ufla.visionproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
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
                "Atividades 1 a 4\nCameraX + OpenCV + Canny + Modelo de Câmera + Calibração");
        subtitle.setPadding(0, 0, 0, 24);

        Button camera = Ui.button(this, "Atividade 1 — Capturar Frame JPEG");
        Button process = Ui.button(this, "Atividade 2 — Processar: Cinza → Gaussiano → Canny");
        Button model = Ui.button(this, "Atividade 3 — Modelo de Câmera e Distorção");
        Button calibration = Ui.button(this, "Atividade 4 — Calibração de Câmera");
        Button loadCalibration = Ui.button(this, "Carregar calibration.json");

        camera.setOnClickListener(v -> startActivity(new Intent(this, CameraActivity.class)));
        process.setOnClickListener(v -> startActivity(new Intent(this, ProcessActivity.class)));
        model.setOnClickListener(v -> startActivity(new Intent(this, ModeloCameraActivity.class)));
        calibration.setOnClickListener(v -> startActivity(new Intent(this, CalibrationActivity.class)));
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
        root.addView(camera);
        root.addView(process);
        root.addView(model);
        root.addView(calibration);
        root.addView(loadCalibration);

        setContentView(root);
    }
}
