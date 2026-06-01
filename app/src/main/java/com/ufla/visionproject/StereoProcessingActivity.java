package com.ufla.visionproject;

import android.content.Intent;
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

public class StereoProcessingActivity extends AppCompatActivity {
    private TextView status;
    private ImageView matchesView;
    private ImageView epipolarView;
    private ImageView rectifiedView;
    private ImageView disparityView;

    private ExecutorService executor;
    private StereoUtils.StereoResult lastResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        executor = Executors.newSingleThreadExecutor();

        LinearLayout content = Ui.vertical(this);
        content.setPadding(16, 32, 16, 16);

        status = Ui.text(this, "Carregue o par I_L/I_R e execute o pipeline.");

        matchesView = previewImage();
        epipolarView = previewImage();
        rectifiedView = previewImage();
        disparityView = previewImage();

        Button run = Ui.button(this, "Processar T3–T7 com preset selecionado");
        Button preset1 = Ui.button(this, "Preset 1 — 64 disparidades | bloco 5");
        Button preset2 = Ui.button(this, "Preset 2 — 128 disparidades | bloco 7");
        Button preset3 = Ui.button(this, "Preset 3 — 192 disparidades | bloco 9");
        Button openDepth = Ui.button(this, "Abrir T8 — Profundidade e nuvem PLY");
        Button reloadSaved = Ui.button(this, "Carregar imagens de resultado salvas");

        run.setOnClickListener(v -> runPipeline());
        preset1.setOnClickListener(v -> selectPresetAndRun(1));
        preset2.setOnClickListener(v -> selectPresetAndRun(2));
        preset3.setOnClickListener(v -> selectPresetAndRun(3));
        openDepth.setOnClickListener(v -> startActivity(new Intent(this, StereoDepthActivity.class)));
        reloadSaved.setOnClickListener(v -> loadSavedImages());

        content.addView(Ui.title(this, "Estéreo — T3 a T7"));
        content.addView(Ui.text(this,
                "Esta tela usa o par capturado, corrige distorção, estima correspondências, calcula F com RANSAC, retifica e gera o mapa de disparidade."));
        content.addView(Ui.text(this,
                "Escolha um preset de StereoSGBM. O relatório salva uma tabela com os três presets, como solicitado na atividade."));
        content.addView(status);
        content.addView(run);
        content.addView(preset1);
        content.addView(preset2);
        content.addView(preset3);
        content.addView(openDepth);
        content.addView(reloadSaved);

        content.addView(Ui.text(this, "Matches ORB / inliers RANSAC"));
        content.addView(matchesView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 520));

        content.addView(Ui.text(this, "Linhas epipolares — validação visual da matriz F"));
        content.addView(epipolarView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 520));

        content.addView(Ui.text(this, "Par retificado com linhas horizontais de conferência"));
        content.addView(rectifiedView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 520));

        content.addView(Ui.text(this, "Mapa de disparidade"));
        content.addView(disparityView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 520));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);

        loadSavedImages();
    }

    private ImageView previewImage() {
        ImageView view = new ImageView(this);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setBackgroundColor(0xFF000000);
        return view;
    }

    private void selectPresetAndRun(int presetId) {
        StereoUtils.saveDisparityPreset(this, presetId);
        Toast.makeText(this, "Preset " + presetId + " selecionado.", Toast.LENGTH_SHORT).show();
        runPipeline();
    }

    private void runPipeline() {
        if (!StereoUtils.leftFile(this).exists() || !StereoUtils.rightFile(this).exists()) {
            Toast.makeText(this, "Capture I_L e I_R primeiro.", Toast.LENGTH_LONG).show();
            return;
        }

        status.setText("Processando com preset " + StereoUtils.loadDisparityPreset(this) + ". Aguarde alguns segundos...");
        executor.execute(() -> {
            try {
                StereoUtils.StereoResult result = StereoUtils.runPipeline(this);
                lastResult = result;

                runOnUiThread(() -> {
                    status.setText(result.reportText);
                    matchesView.setImageBitmap(result.matchesBitmap);
                    epipolarView.setImageBitmap(result.epipolarBitmap);
                    rectifiedView.setImageBitmap(result.rectifiedPairBitmap);
                    disparityView.setImageBitmap(result.disparityBitmap);
                    Toast.makeText(this, "Pipeline concluído. Resultados salvos em Pictures/VisionProject e files/stereo.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Erro no pipeline estéreo:\n" + e.getMessage()
                            + "\n\nRefaça o par se houver rotação, tremor, pouca textura ou baseline incorreta.");
                    Toast.makeText(this, "Falha no processamento estéreo.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadSavedImages() {
        Bitmap matches = StereoUtils.loadBitmap(StereoUtils.matchesFile(this));
        Bitmap epipolar = StereoUtils.loadBitmap(StereoUtils.epipolarFile(this));
        Bitmap rectified = StereoUtils.loadBitmap(StereoUtils.rectifiedFile(this));
        Bitmap disparity = StereoUtils.loadBitmap(StereoUtils.disparityFile(this));

        if (matches != null) matchesView.setImageBitmap(matches);
        if (epipolar != null) epipolarView.setImageBitmap(epipolar);
        if (rectified != null) rectifiedView.setImageBitmap(rectified);
        if (disparity != null) disparityView.setImageBitmap(disparity);

        String report = StereoUtils.readText(StereoUtils.reportFile(this));
        if (report != null && report.trim().length() > 0) {
            status.setText(report);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
