package com.ufla.visionproject;

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
