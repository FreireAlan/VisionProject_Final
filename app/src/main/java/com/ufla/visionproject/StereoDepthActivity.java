package com.ufla.visionproject;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StereoDepthActivity extends AppCompatActivity {
    private TextView status;
    private ImageView depthView;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        executor = Executors.newSingleThreadExecutor();

        LinearLayout content = Ui.vertical(this);
        content.setPadding(16, 32, 16, 16);

        status = Ui.text(this, "Carregue ou recalcule o mapa de profundidade.");
        depthView = new ImageView(this);
        depthView.setAdjustViewBounds(true);
        depthView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        depthView.setBackgroundColor(0xFF000000);

        Button load = Ui.button(this, "Carregar profundidade e relatório salvos");
        Button recalc = Ui.button(this, "Recalcular T8 e exportar cloud.ply");

        load.setOnClickListener(v -> loadSaved());
        recalc.setOnClickListener(v -> recalculate());

        content.addView(Ui.title(this, "Estéreo — T8 Profundidade"));
        content.addView(Ui.text(this,
                "A profundidade é estimada por Z = f·B/d, usando f em pixels, baseline em cm e disparidade em pixels. A distância real informada na T2 é usada para erro absoluto e relativo."));
        content.addView(Ui.text(this,
                "Nesta versão, o erro é calculado preferencialmente na ROI central marcada no mapa de profundidade. Posicione o objeto medido no centro da imagem."));
        content.addView(status);
        content.addView(load);
        content.addView(recalc);
        content.addView(depthView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 650));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);

        loadSaved();
    }

    private void loadSaved() {
        Bitmap depth = StereoUtils.loadBitmap(StereoUtils.depthFile(this));
        if (depth == null) depth = StereoUtils.loadBitmap(StereoUtils.disparityFile(this));
        if (depth != null) depthView.setImageBitmap(depth);

        String report = StereoUtils.readText(StereoUtils.reportFile(this));
        if (report != null) {
            status.setText(report + "\n\nPLY: " + StereoUtils.plyFile(this).getAbsolutePath());
        } else {
            status.setText("Nenhum resultado salvo. Execute o processamento primeiro.");
        }
    }

    private void recalculate() {
        if (!StereoUtils.leftFile(this).exists() || !StereoUtils.rightFile(this).exists()) {
            Toast.makeText(this, "Capture I_L e I_R primeiro.", Toast.LENGTH_LONG).show();
            return;
        }

        status.setText("Recalculando profundidade e PLY...");
        executor.execute(() -> {
            try {
                StereoUtils.StereoResult result = StereoUtils.runPipeline(this);
                runOnUiThread(() -> {
                    depthView.setImageBitmap(result.depthBitmap);
                    status.setText(result.reportText + "\n\nPLY: " + StereoUtils.plyFile(this).getAbsolutePath());
                    Toast.makeText(this, "Profundidade e PLY exportados.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erro ao recalcular T8:\n" + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
