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

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.TermCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalibrationActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 104;

    private static final int BOARD_COLS = 9;
    private static final int BOARD_ROWS = 6;
    private static final float SQUARE_SIZE_MM = 25.0f;
    private static final int MIN_IMAGES = 20;

    private PreviewView previewView;
    private TextView status;
    private ImageView beforeView;
    private ImageView afterView;
    private ExecutorService cameraExecutor;

    private final List<Mat> objectPoints = new ArrayList<>();
    private final List<Mat> imagePoints = new ArrayList<>();

    private volatile Bitmap lastBitmap;
    private volatile boolean autoCapture = false;
    private volatile boolean detecting = false;

    private MatOfPoint2f previousCorners;
    private int stableCount = 0;
    private long lastAcceptedMs = 0L;
    private Size imageSize;

    private CalibrationData calibrationData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenCvHelper.initOpenCV(this);

        cameraExecutor = Executors.newSingleThreadExecutor();

        LinearLayout root = Ui.vertical(this);
        root.setPadding(16, 32, 16, 16);

        status = Ui.text(this, "Atividade 4: imprima tabuleiro 9x6 cantos internos, quadrado 25 mm.");

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        beforeView = new ImageView(this);
        beforeView.setAdjustViewBounds(true);
        beforeView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        beforeView.setBackgroundColor(0xFF000000);

        afterView = new ImageView(this);
        afterView.setAdjustViewBounds(true);
        afterView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        afterView.setBackgroundColor(0xFF000000);

        Button toggleAuto = Ui.button(this, "Iniciar captura automática");
        Button calibrate = Ui.button(this, "Rodar calibração");
        Button beforeAfter = Ui.button(this, "Mostrar before/after undistort");
        Button load = Ui.button(this, "Carregar calibration.json");

        toggleAuto.setOnClickListener(v -> {
            autoCapture = !autoCapture;
            toggleAuto.setText(autoCapture ? "Pausar captura automática" : "Iniciar captura automática");
            status.setText(statusLine("Captura automática: " + (autoCapture ? "ativa" : "pausada")));
        });

        calibrate.setOnClickListener(v -> runCalibration());
        beforeAfter.setOnClickListener(v -> showBeforeAfter());
        load.setOnClickListener(v -> {
            calibrationData = CalibrationStore.load(this);
            if (calibrationData == null) {
                Toast.makeText(this, "Nenhum calibration.json encontrado.", Toast.LENGTH_LONG).show();
            } else {
                status.setText("Calibração carregada:\n" + calibrationData.pretty());
            }
        });

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.addView(beforeView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        pair.addView(afterView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        root.addView(Ui.title(this, "Atividade 4 — Calibração"));
        root.addView(status);
        root.addView(previewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(toggleAuto);
        root.addView(calibrate);
        root.addView(beforeAfter);
        root.addView(load);
        root.addView(pair, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.75f));

        setContentView(root);

        if (hasCameraPermission()) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String statusLine(String extra) {
        return extra
                + "\nImagens aceitas: " + imagePoints.size() + "/" + MIN_IMAGES
                + "\nCritério: tabuleiro detectado + pose estável + sem blur perceptível.";
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
                        if (bitmap == null) return;

                        lastBitmap = bitmap;
                        imageSize = new Size(bitmap.getWidth(), bitmap.getHeight());

                        if (autoCapture && !detecting) {
                            detecting = true;
                            detectAndMaybeAccept(bitmap);
                            detecting = false;
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

    private void detectAndMaybeAccept(Bitmap bitmap) {
        Mat bgr = null;
        Mat gray = null;
        MatOfPoint2f corners = new MatOfPoint2f();

        try {
            bgr = OpenCvHelper.bitmapToBgr(bitmap);
            gray = new Mat();
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);

            Size pattern = new Size(BOARD_COLS, BOARD_ROWS);
            boolean found = Calib3d.findChessboardCorners(
                    gray,
                    pattern,
                    corners,
                    Calib3d.CALIB_CB_ADAPTIVE_THRESH + Calib3d.CALIB_CB_NORMALIZE_IMAGE
            );

            if (!found) {
                stableCount = 0;
                runOnUiThread(() -> status.setText(statusLine("Tabuleiro não detectado.")));
                return;
            }

            TermCriteria criteria = new TermCriteria(
                    TermCriteria.EPS + TermCriteria.COUNT,
                    30,
                    0.001
            );

            Imgproc.cornerSubPix(
                    gray,
                    corners,
                    new Size(11, 11),
                    new Size(-1, -1),
                    criteria
            );

            double movement = movementFromPrevious(corners);
            previousCorners = new MatOfPoint2f(corners.toArray());

            if (movement < 2.0) stableCount++;
            else stableCount = 0;

            long now = System.currentTimeMillis();
            boolean cooldown = now - lastAcceptedMs > 1300L;

            if (stableCount >= 4 && cooldown) {
                imagePoints.add(new MatOfPoint2f(corners.toArray()));
                objectPoints.add(createObjectPoints());
                lastAcceptedMs = now;
                stableCount = 0;

                runOnUiThread(() -> {
                    Toast.makeText(this, "Imagem de calibração aceita.", Toast.LENGTH_SHORT).show();
                    status.setText(statusLine("Tabuleiro detectado e estável. Imagem aceita."));
                });
            } else {
                runOnUiThread(() -> status.setText(statusLine(
                        "Tabuleiro detectado. Movimento médio="
                                + String.format("%.2f", movement)
                                + " px | estável=" + stableCount + "/4"
                )));
            }
        } catch (Exception e) {
            runOnUiThread(() -> status.setText("Erro na detecção: " + e.getMessage()));
        } finally {
            if (bgr != null) bgr.release();
            if (gray != null) gray.release();
            corners.release();
        }
    }

    private double movementFromPrevious(MatOfPoint2f current) {
        if (previousCorners == null || previousCorners.empty()) return 999.0;

        Point[] a = previousCorners.toArray();
        Point[] b = current.toArray();

        if (a.length != b.length || a.length == 0) return 999.0;

        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double dx = a[i].x - b[i].x;
            double dy = a[i].y - b[i].y;
            sum += Math.sqrt(dx * dx + dy * dy);
        }
        return sum / a.length;
    }

    private Mat createObjectPoints() {
        List<Point3> points = new ArrayList<>();
        for (int r = 0; r < BOARD_ROWS; r++) {
            for (int c = 0; c < BOARD_COLS; c++) {
                points.add(new Point3(c * SQUARE_SIZE_MM, r * SQUARE_SIZE_MM, 0.0));
            }
        }
        MatOfPoint3f mat = new MatOfPoint3f();
        mat.fromList(points);
        return mat;
    }

    private void runCalibration() {
        if (imagePoints.size() < MIN_IMAGES) {
            Toast.makeText(this,
                    "Colete pelo menos " + MIN_IMAGES + " imagens. Atual: " + imagePoints.size(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (imageSize == null) {
            Toast.makeText(this, "Tamanho da imagem indisponível.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
            Mat distCoeffs = Mat.zeros(1, 5, CvType.CV_64F);
            List<Mat> rvecs = new ArrayList<>();
            List<Mat> tvecs = new ArrayList<>();

            double rms = Calib3d.calibrateCamera(
                    objectPoints,
                    imagePoints,
                    imageSize,
                    cameraMatrix,
                    distCoeffs,
                    rvecs,
                    tvecs
            );

            CalibrationData data = new CalibrationData();
            data.width = (int) imageSize.width;
            data.height = (int) imageSize.height;
            data.fx = cameraMatrix.get(0, 0)[0];
            data.fy = cameraMatrix.get(1, 1)[0];
            data.cx = cameraMatrix.get(0, 2)[0];
            data.cy = cameraMatrix.get(1, 2)[0];
            data.k1 = getDist(distCoeffs, 0);
            data.k2 = getDist(distCoeffs, 1);
            data.p1 = getDist(distCoeffs, 2);
            data.p2 = getDist(distCoeffs, 3);
            data.k3 = getDist(distCoeffs, 4);
            data.rms = rms;
            data.imageCount = imagePoints.size();
            data.timestamp = System.currentTimeMillis();

            calibrationData = data;
            boolean saved = CalibrationStore.save(this, data);

            status.setText("Calibração concluída. JSON salvo=" + saved
                    + "\nArquivo interno: " + CalibrationStore.file(this).getAbsolutePath()
                    + "\n\n" + data.pretty());

            cameraMatrix.release();
            distCoeffs.release();
        } catch (Exception e) {
            status.setText("Erro na calibração: " + e.getMessage());
        }
    }

    private double getDist(Mat dist, int index) {
        if (dist.rows() == 1) {
            return dist.cols() > index ? dist.get(0, index)[0] : 0.0;
        }
        return dist.rows() > index ? dist.get(index, 0)[0] : 0.0;
    }

    private void showBeforeAfter() {
        Bitmap bitmap = lastBitmap;
        if (bitmap == null) {
            Toast.makeText(this, "Aguarde um frame.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (calibrationData == null) {
            calibrationData = CalibrationStore.load(this);
        }

        if (calibrationData == null) {
            Toast.makeText(this, "Rode ou carregue a calibração primeiro.", Toast.LENGTH_LONG).show();
            return;
        }

        Bitmap before = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        Bitmap after = OpenCvHelper.undistortWithCalibration(before, calibrationData);

        beforeView.setImageBitmap(before);
        afterView.setImageBitmap(after);

        ImageUtils.saveBitmapToGallery(this, before, "atividade4_before");
        ImageUtils.saveBitmapToGallery(this, after, "atividade4_after");

        Toast.makeText(this, "Before/after salvo na galeria.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (previousCorners != null) previousCorners.release();

        for (Mat mat : objectPoints) mat.release();
        for (Mat mat : imagePoints) mat.release();
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
