import os
import zipfile
from pathlib import Path

PROJECT = "VisionProject"

files = {
"settings.gradle": r'''pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "VisionProject"
include ":app"
''',

"build.gradle": r'''plugins {
    id "com.android.application" version "8.7.3" apply false
}
''',

"gradle.properties": r'''android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
''',

"app/build.gradle": r'''plugins {
    id "com.android.application"
}

android {
    namespace "com.ufla.visionproject"
    compileSdk 35

    defaultConfig {
        applicationId "com.ufla.visionproject"
        minSdk 24
        targetSdk 35
        versionCode 1
        versionName "1.0"
    }

    buildFeatures {
        buildConfig true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.core:core:1.13.1"

    implementation "androidx.camera:camera-core:1.4.0"
    implementation "androidx.camera:camera-camera2:1.4.0"
    implementation "androidx.camera:camera-lifecycle:1.4.0"
    implementation "androidx.camera:camera-view:1.4.0"

    implementation "org.opencv:opencv:4.10.0"
}
''',

"app/src/main/AndroidManifest.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.camera"
        android:required="true" />

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <application
        android:allowBackup="true"
        android:theme="@style/Theme.VisionProject"
        android:label="@string/app_name"
        android:supportsRtl="true">

        <activity android:name=".CalibrationActivity" android:screenOrientation="portrait" />
        <activity android:name=".ModeloCameraActivity" android:screenOrientation="portrait" />
        <activity android:name=".ProcessActivity" android:screenOrientation="portrait" />
        <activity android:name=".CameraActivity" android:screenOrientation="portrait" />

        <activity
            android:name=".MainActivity"
            android:screenOrientation="portrait"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
''',

"app/src/main/res/values/strings.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">VisionProject</string>
</resources>
''',

"app/src/main/res/values/colors.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#263238</color>
    <color name="primary_dark">#102027</color>
    <color name="accent">#7E57C2</color>
    <color name="white">#FFFFFF</color>
    <color name="soft_bg">#F4F1FA</color>
</resources>
''',

"app/src/main/res/values/styles.xml": r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.VisionProject" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:statusBarColor">@color/primary_dark</item>
        <item name="android:navigationBarColor">@color/primary_dark</item>
        <item name="colorAccent">@color/accent</item>
    </style>
</resources>
''',

"app/src/main/java/com/ufla/visionproject/MainActivity.java": r'''package com.ufla.visionproject;

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
''',

"app/src/main/java/com/ufla/visionproject/Ui.java": r'''package com.ufla.visionproject;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(244, 241, 250));
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return layout;
    }

    public static TextView title(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(26f);
        view.setTextColor(Color.rgb(38, 50, 56));
        view.setPadding(0, 0, 0, 12);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    public static TextView text(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(15f);
        view.setTextColor(Color.rgb(55, 65, 70));
        return view;
    }

    public static Button button(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        button.setLayoutParams(params);
        return button;
    }
}
''',

"app/src/main/java/com/ufla/visionproject/OpenCvHelper.java": r'''package com.ufla.visionproject;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public final class OpenCvHelper {
    private static final String TAG = "OpenCvHelper";
    private static boolean initialized = false;

    private OpenCvHelper() {}

    public static synchronized boolean initOpenCV(Context context) {
        if (initialized) return true;

        boolean ok = OpenCVLoader.initDebug();
        initialized = ok;

        if (ok) {
            Log.i(TAG, "OpenCV inicializado.");
        } else {
            Toast.makeText(context, "Erro ao inicializar OpenCV.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Falha ao inicializar OpenCV.");
        }
        return ok;
    }

    public static Mat defaultCameraMatrix(int width, int height) {
        double fx = Math.max(width, height) * 1.25;
        double fy = fx;
        double cx = width / 2.0;
        double cy = height / 2.0;

        Mat k = Mat.eye(3, 3, CvType.CV_64F);
        k.put(0, 0, fx);
        k.put(1, 1, fy);
        k.put(0, 2, cx);
        k.put(1, 2, cy);
        return k;
    }

    public static Mat defaultDistCoeffs() {
        Mat dist = Mat.zeros(1, 5, CvType.CV_64F);
        dist.put(0, 0, -0.25);
        dist.put(0, 1, 0.10);
        dist.put(0, 2, 0.0005);
        dist.put(0, 3, 0.0005);
        dist.put(0, 4, 0.0);
        return dist;
    }

    public static Mat bitmapToBgr(Bitmap bitmap) {
        Mat rgba = new Mat();
        Utils.bitmapToMat(bitmap, rgba);
        Mat bgr = new Mat();
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR);
        rgba.release();
        return bgr;
    }

    public static Bitmap matToBitmap(Mat mat) {
        Mat output = new Mat();
        if (mat.channels() == 1) {
            Imgproc.cvtColor(mat, output, Imgproc.COLOR_GRAY2RGBA);
        } else {
            Imgproc.cvtColor(mat, output, Imgproc.COLOR_BGR2RGBA);
        }

        Bitmap bitmap = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, bitmap);
        output.release();
        return bitmap;
    }

    public static ProcessResult processCanny(Bitmap bitmap, int low, int high) {
        Mat bgr = bitmapToBgr(bitmap);

        Mat gray = new Mat();
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);

        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 1.0);

        Mat edges = new Mat();
        Imgproc.Canny(blurred, edges, low, high);

        ProcessResult result = new ProcessResult();
        result.original = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        result.gray = matToBitmap(gray);
        result.blurred = matToBitmap(blurred);
        result.edges = matToBitmap(edges);

        bgr.release();
        gray.release();
        blurred.release();
        edges.release();

        return result;
    }

    public static Bitmap undistortWithDefault(Bitmap bitmap) {
        Mat bgr = bitmapToBgr(bitmap);
        Mat undistorted = new Mat();
        Mat k = defaultCameraMatrix(bitmap.getWidth(), bitmap.getHeight());
        Mat dist = defaultDistCoeffs();

        Calib3d.undistort(bgr, undistorted, k, dist);

        Bitmap output = matToBitmap(undistorted);

        bgr.release();
        undistorted.release();
        k.release();
        dist.release();

        return output;
    }

    public static Bitmap undistortWithCalibration(Bitmap bitmap, CalibrationData data) {
        if (data == null) {
            return undistortWithDefault(bitmap);
        }

        Mat bgr = bitmapToBgr(bitmap);
        Mat undistorted = new Mat();
        Mat k = data.cameraMatrix();
        Mat dist = data.distCoeffs();

        Calib3d.undistort(bgr, undistorted, k, dist);

        Bitmap output = matToBitmap(undistorted);

        bgr.release();
        undistorted.release();
        k.release();
        dist.release();

        return output;
    }

    public static final class ProcessResult {
        public Bitmap original;
        public Bitmap gray;
        public Bitmap blurred;
        public Bitmap edges;
    }
}
''',

"app/src/main/java/com/ufla/visionproject/ImageUtils.java": r'''package com.ufla.visionproject;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class ImageUtils {
    private static final String TAG = "ImageUtils";

    private ImageUtils() {}

    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        byte[] nv21 = yuv420ToNv21(image);
        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null
        );

        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                92,
                jpeg
        );

        byte[] bytes = jpeg.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        if (rotation != 0 && bitmap != null) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        return bitmap;
    }

    private static byte[] yuv420ToNv21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 2;

        byte[] nv21 = new byte[ySize + uvSize];

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();

        int pos = 0;
        for (int row = 0; row < height; row++) {
            int rowStart = row * yRowStride;
            yBuffer.position(rowStart);
            yBuffer.get(nv21, pos, width);
            pos += width;
        }

        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int uvHeight = height / 2;
        int uvWidth = width / 2;

        for (int row = 0; row < uvHeight; row++) {
            for (int col = 0; col < uvWidth; col++) {
                int uvIndex = row * uvRowStride + col * uvPixelStride;
                int nv21Index = ySize + row * width + col * 2;

                nv21[nv21Index] = vBuffer.get(uvIndex);
                nv21[nv21Index + 1] = uBuffer.get(uvIndex);
            }
        }

        return nv21;
    }

    public static Uri saveBitmapToGallery(Context context, Bitmap bitmap, String prefix) {
        if (bitmap == null) return null;

        String fileName = prefix + "_" + System.currentTimeMillis() + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VisionProject");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            Log.e(TAG, "Falha ao criar Uri para salvar imagem.");
            return null;
        }

        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) return null;
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar imagem.", e);
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }

        return uri;
    }
}
''',

"app/src/main/java/com/ufla/visionproject/CameraActivity.java": r'''package com.ufla.visionproject;

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
''',

"app/src/main/java/com/ufla/visionproject/ProcessActivity.java": r'''package com.ufla.visionproject;

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
''',

"app/src/main/java/com/ufla/visionproject/ModeloCameraActivity.java": r'''package com.ufla.visionproject;

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
''',

"app/src/main/java/com/ufla/visionproject/EpipolarOverlayView.java": r'''package com.ufla.visionproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class EpipolarOverlayView extends View {
    private final List<PointF> points = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EpipolarOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(18, 18, 22));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        float half = getWidth() / 2f;
        float x = Math.min(event.getX(), half - 8);
        float y = event.getY();

        if (points.size() >= 5) points.clear();
        points.add(new PointF(x, y));
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float half = w / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(30f);
        paint.setColor(Color.WHITE);
        canvas.drawText("Imagem 1: pontos", 18, 38, paint);
        canvas.drawText("Imagem 2: linhas epipolares", half + 18, 38, paint);

        paint.setColor(Color.GRAY);
        paint.setStrokeWidth(3f);
        canvas.drawLine(half, 0, half, h, paint);

        paint.setTextSize(22f);
        paint.setColor(Color.LTGRAY);
        canvas.drawText("Modelo retificado: l' = Fp → linha quase horizontal", 18, h - 18, paint);

        int[] colors = {
                Color.rgb(255, 82, 82),
                Color.rgb(255, 193, 7),
                Color.rgb(76, 175, 80),
                Color.rgb(3, 169, 244),
                Color.rgb(186, 104, 200)
        };

        for (int i = 0; i < points.size(); i++) {
            PointF p = points.get(i);
            paint.setColor(colors[i % colors.length]);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(p.x, p.y, 10f, paint);
            canvas.drawText("p" + (i + 1), p.x + 12, p.y - 10, paint);

            float yRight = p.y;
            float slope = (i - 2) * 0.035f;
            float x1 = half + 10;
            float x2 = w - 10;
            float y1 = yRight - slope * 120;
            float y2 = yRight + slope * 120;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }
}
''',

"app/src/main/java/com/ufla/visionproject/CalibrationData.java": r'''package com.ufla.visionproject;

import org.json.JSONObject;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class CalibrationData {
    public int width;
    public int height;
    public double fx;
    public double fy;
    public double cx;
    public double cy;
    public double k1;
    public double k2;
    public double p1;
    public double p2;
    public double k3;
    public double rms;
    public int imageCount;
    public long timestamp;

    public Mat cameraMatrix() {
        Mat k = Mat.eye(3, 3, CvType.CV_64F);
        k.put(0, 0, fx);
        k.put(1, 1, fy);
        k.put(0, 2, cx);
        k.put(1, 2, cy);
        return k;
    }

    public Mat distCoeffs() {
        Mat dist = Mat.zeros(1, 5, CvType.CV_64F);
        dist.put(0, 0, k1);
        dist.put(0, 1, k2);
        dist.put(0, 2, p1);
        dist.put(0, 3, p2);
        dist.put(0, 4, k3);
        return dist;
    }

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("width", width);
        json.put("height", height);
        json.put("fx", fx);
        json.put("fy", fy);
        json.put("cx", cx);
        json.put("cy", cy);
        json.put("k1", k1);
        json.put("k2", k2);
        json.put("p1", p1);
        json.put("p2", p2);
        json.put("k3", k3);
        json.put("rms", rms);
        json.put("imageCount", imageCount);
        json.put("timestamp", timestamp);
        return json;
    }

    public static CalibrationData fromJson(JSONObject json) throws Exception {
        CalibrationData data = new CalibrationData();
        data.width = json.optInt("width", 0);
        data.height = json.optInt("height", 0);
        data.fx = json.getDouble("fx");
        data.fy = json.getDouble("fy");
        data.cx = json.getDouble("cx");
        data.cy = json.getDouble("cy");
        data.k1 = json.optDouble("k1", 0.0);
        data.k2 = json.optDouble("k2", 0.0);
        data.p1 = json.optDouble("p1", 0.0);
        data.p2 = json.optDouble("p2", 0.0);
        data.k3 = json.optDouble("k3", 0.0);
        data.rms = json.optDouble("rms", -1.0);
        data.imageCount = json.optInt("imageCount", 0);
        data.timestamp = json.optLong("timestamp", 0L);
        return data;
    }

    public String pretty() {
        return "RMS=" + String.format("%.4f", rms)
                + "\nfx=" + String.format("%.2f", fx)
                + " fy=" + String.format("%.2f", fy)
                + "\ncx=" + String.format("%.2f", cx)
                + " cy=" + String.format("%.2f", cy)
                + "\nk1=" + String.format("%.6f", k1)
                + " k2=" + String.format("%.6f", k2)
                + "\np1=" + String.format("%.6f", p1)
                + " p2=" + String.format("%.6f", p2)
                + " k3=" + String.format("%.6f", k3)
                + "\nImagens usadas=" + imageCount;
    }
}
''',

"app/src/main/java/com/ufla/visionproject/CalibrationStore.java": r'''package com.ufla.visionproject;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class CalibrationStore {
    private static final String TAG = "CalibrationStore";
    public static final String FILE_NAME = "calibration.json";

    private CalibrationStore() {}

    public static boolean save(Context context, CalibrationData data) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            Files.write(file.toPath(), data.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar calibration.json", e);
            return false;
        }
    }

    public static CalibrationData load(Context context) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) return null;

            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return CalibrationData.fromJson(new JSONObject(content));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar calibration.json", e);
            return null;
        }
    }

    public static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}
''',

"app/src/main/java/com/ufla/visionproject/CalibrationActivity.java": r'''package com.ufla.visionproject;

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
''',

"README.md": r'''# VisionProject — Atividades 1 a 4

Projeto Android Java + CameraX + OpenCV.

## Implementado

### Atividade 1
- Projeto Android em Java.
- CameraX com câmera traseira.
- Preview ao vivo via PreviewView.
- Botão para salvar frame JPEG em `Pictures/VisionProject`.

### Atividade 2
- Pipeline OpenCV: RGB → cinza → GaussianBlur → Canny.
- SeekBars para limiares Tlow/Thigh.
- Salvamento das 4 imagens: original, cinza, suavizada e bordas.

### Atividade 3
- Activity `ModeloCameraActivity`.
- Exibição de parâmetros K aproximados.
- Correção `Calib3d.undistort()` com `K` e `distCoeffs` fixos.
- Comparação original/corrigida.
- Canvas com seleção manual de até 5 pontos e linhas epipolares ilustrativas.

### Atividade 4
- Activity `CalibrationActivity`.
- Detecção de tabuleiro 9x6 cantos internos.
- Quadrado padrão: 25 mm.
- Captura automática quando a pose fica estável.
- Calibração com OpenCV `Calib3d.calibrateCamera`.
- Log/exibição de RMS, fx, fy, cx, cy, k1, k2, p1, p2, k3.
- Salvamento/carregamento de `calibration.json`.
- Before/after `undistort`.

## Como abrir

1. Abra o Android Studio.
2. Escolha `Open`.
3. Selecione a pasta `VisionProject`.
4. Aguarde o Gradle Sync.
5. Rode em dispositivo físico com câmera traseira.

## Observação

Para a Atividade 4, imprima um tabuleiro de calibração com 9x6 cantos internos e quadrados de 25 mm.
Cole o papel em uma superfície rígida para evitar ondulação.
''',
}

def write_project():
    root = Path(PROJECT)
    if root.exists():
        print(f"Removendo projeto anterior: {root}")
        for path in sorted(root.rglob("*"), reverse=True):
            if path.is_file():
                path.unlink()
            else:
                try:
                    path.rmdir()
                except OSError:
                    pass
        try:
            root.rmdir()
        except OSError:
            pass

    root.mkdir(parents=True, exist_ok=True)

    for rel, content in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    zip_path = Path(f"{PROJECT}.zip")
    if zip_path.exists():
        zip_path.unlink()

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for path in root.rglob("*"):
            if path.is_file():
                z.write(path, path.as_posix())

    print("Projeto gerado com sucesso.")
    print(f"Pasta: {root.resolve()}")
    print(f"ZIP:   {zip_path.resolve()}")
    print()
    print("Abra a pasta VisionProject no Android Studio.")

if __name__ == "__main__":
    write_project()