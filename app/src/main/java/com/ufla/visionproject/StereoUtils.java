package com.ufla.visionproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utilitários do pipeline de visão estéreo:
 * leitura do par, undistort, ORB, matching, F/RANSAC,
 * retificação, StereoSGBM, profundidade e exportação PLY.
 */
public final class StereoUtils {
    private static final String PREFS = "stereo_prefs";
    private static final String KEY_BASELINE_CM = "baseline_cm";
    private static final String KEY_OBJECT_DISTANCE_CM = "object_distance_cm";
    private static final String KEY_SGBM_PRESET = "sgbm_preset";
    private static final int MAX_PROCESS_WIDTH = 960;

    private StereoUtils() {}

    public static File stereoDir(Context context) {
        File dir = new File(context.getFilesDir(), "stereo");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File leftFile(Context context) {
        return new File(stereoDir(context), "I_L.jpg");
    }

    public static File rightFile(Context context) {
        return new File(stereoDir(context), "I_R.jpg");
    }

    public static File matchesFile(Context context) {
        return new File(stereoDir(context), "matches_inliers.jpg");
    }

    public static File epipolarFile(Context context) {
        return new File(stereoDir(context), "epipolar_lines.jpg");
    }

    public static File rectifiedFile(Context context) {
        return new File(stereoDir(context), "rectified_pair.jpg");
    }

    public static File disparityFile(Context context) {
        return new File(stereoDir(context), "disparity.jpg");
    }

    public static File disparityPresetFile(Context context, int presetId) {
        return new File(stereoDir(context), "disparity_preset_" + presetId + ".jpg");
    }

    public static File depthFile(Context context) {
        return new File(stereoDir(context), "depth_visual.jpg");
    }

    public static File plyFile(Context context) {
        return new File(stereoDir(context), "cloud.ply");
    }

    public static File reportFile(Context context) {
        return new File(stereoDir(context), "stereo_report.txt");
    }

    public static Bitmap loadBitmap(File file) {
        if (file == null || !file.exists()) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    public static boolean saveBitmapToFile(Bitmap bitmap, File file) {
        if (bitmap == null || file == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String readText(File file) {
        try {
            if (file == null || !file.exists()) return null;
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static StereoResult runPipeline(Context context) throws Exception {
        Bitmap leftBitmap = loadBitmap(leftFile(context));
        Bitmap rightBitmap = loadBitmap(rightFile(context));

        if (leftBitmap == null || rightBitmap == null) {
            throw new IllegalStateException("Par estéreo não encontrado. Capture I_L e I_R na tela de captura.");
        }

        Mat leftBgr = resizeForProcessing(OpenCvHelper.bitmapToBgr(leftBitmap));
        Mat rightBgr = resizeForProcessing(OpenCvHelper.bitmapToBgr(rightBitmap));

        if (leftBgr.cols() != rightBgr.cols() || leftBgr.rows() != rightBgr.rows()) {
            Imgproc.resize(rightBgr, rightBgr, new Size(leftBgr.cols(), leftBgr.rows()));
        }

        CalibrationData calibration = CalibrationStore.load(context);
        Mat K = scaledCameraMatrix(calibration, leftBgr.cols(), leftBgr.rows());
        Mat dist = calibration != null ? calibration.distCoeffs() : Mat.zeros(1, 5, CvType.CV_64F);

        Mat leftUndist = new Mat();
        Mat rightUndist = new Mat();
        Calib3d.undistort(leftBgr, leftUndist, K, dist);
        Calib3d.undistort(rightBgr, rightUndist, K, dist);

        Mat grayL = new Mat();
        Mat grayR = new Mat();
        Imgproc.cvtColor(leftUndist, grayL, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(rightUndist, grayR, Imgproc.COLOR_BGR2GRAY);

        FeatureResult featureResult = estimateFundamental(grayL, grayR, leftUndist, rightUndist);

        Mat rectifiedL = new Mat();
        Mat rectifiedR = new Mat();
        Mat rectifiedPair = new Mat();

        // Par métrico: usado para disparidade/profundidade.
        // Como o suporte físico faz translação horizontal real e a orientação é a mesma da calibração,
        // a profundidade deve ser calculada no sistema de pixels compatível com K.
        // A retificação não calibrada é mantida para validação visual da geometria epipolar,
        // mas não é usada para o cálculo métrico de Z, pois a homografia pode alterar a escala horizontal.
        Mat metricL = leftUndist.clone();
        Mat metricR = rightUndist.clone();

        boolean rectified = rectifyPair(
                leftUndist,
                rightUndist,
                featureResult.inlierPointsL,
                featureResult.inlierPointsR,
                featureResult.F,
                rectifiedL,
                rectifiedR
        );

        if (!rectified) {
            rectifiedL = leftUndist.clone();
            rectifiedR = rightUndist.clone();
        }

        rectifiedPair = drawRectifiedPair(rectifiedL, rectifiedR);
        Mat epipolarDraw = drawEpipolarValidation(leftUndist, rightUndist, featureResult.inlierPointsL, featureResult.inlierPointsR, featureResult.F);

        double baselineCm = loadBaselineCm(context);
        int selectedPreset = loadDisparityPreset(context);
        DisparityResult disparityResult = computeDisparity(context, metricL, metricR, K, baselineCm, selectedPreset, true);
        List<DisparityResult> presetResults = computeAndSavePresetDisparities(context, metricL, metricR, K, baselineCm, selectedPreset, disparityResult);

        Bitmap matchesBitmap = OpenCvHelper.matToBitmap(featureResult.matchesDraw);
        Bitmap epipolarBitmap = OpenCvHelper.matToBitmap(epipolarDraw);
        Bitmap rectifiedBitmap = OpenCvHelper.matToBitmap(rectifiedPair);
        Bitmap disparityBitmap = OpenCvHelper.matToBitmap(disparityResult.disparityColor);
        Bitmap depthBitmap = OpenCvHelper.matToBitmap(disparityResult.depthColor);

        saveBitmapToFile(matchesBitmap, matchesFile(context));
        saveBitmapToFile(epipolarBitmap, epipolarFile(context));
        saveBitmapToFile(rectifiedBitmap, rectifiedFile(context));
        saveBitmapToFile(disparityBitmap, disparityFile(context));
        saveBitmapToFile(depthBitmap, depthFile(context));

        ImageUtils.saveBitmapToGallery(context, matchesBitmap, "stereo_matches_inliers");
        ImageUtils.saveBitmapToGallery(context, epipolarBitmap, "stereo_epipolar_lines");
        ImageUtils.saveBitmapToGallery(context, rectifiedBitmap, "stereo_rectified_pair");
        ImageUtils.saveBitmapToGallery(context, disparityBitmap, "stereo_disparity");
        ImageUtils.saveBitmapToGallery(context, depthBitmap, "stereo_depth_visual");

        String report = buildReport(
                calibration,
                leftUndist.cols(),
                leftUndist.rows(),
                featureResult,
                rectified,
                disparityResult,
                presetResults,
                baselineCm,
                loadObjectDistanceCm(context),
                K
        );

        Files.write(reportFile(context).toPath(), report.getBytes(StandardCharsets.UTF_8));

        StereoResult result = new StereoResult();
        result.matchesBitmap = matchesBitmap;
        result.epipolarBitmap = epipolarBitmap;
        result.rectifiedPairBitmap = rectifiedBitmap;
        result.disparityBitmap = disparityBitmap;
        result.depthBitmap = depthBitmap;
        result.reportText = report;
        result.plyPath = plyFile(context).getAbsolutePath();
        return result;
    }

    private static Mat resizeForProcessing(Mat bgr) {
        int width = bgr.cols();
        int height = bgr.rows();

        if (width <= MAX_PROCESS_WIDTH) return bgr;

        double scale = MAX_PROCESS_WIDTH / (double) width;
        Mat resized = new Mat();
        Imgproc.resize(bgr, resized, new Size(MAX_PROCESS_WIDTH, Math.round(height * scale)));
        bgr.release();
        return resized;
    }

    private static Mat scaledCameraMatrix(CalibrationData data, int width, int height) {
        if (data == null || data.fx <= 0.0 || data.fy <= 0.0 || data.width <= 0 || data.height <= 0) {
            Mat k = Mat.eye(3, 3, CvType.CV_64F);
            double f = Math.max(width, height) * 1.25;
            k.put(0, 0, f);
            k.put(1, 1, f);
            k.put(0, 2, width / 2.0);
            k.put(1, 2, height / 2.0);
            return k;
        }

        double sx = width / (double) data.width;
        double sy = height / (double) data.height;

        Mat k = Mat.eye(3, 3, CvType.CV_64F);
        k.put(0, 0, data.fx * sx);
        k.put(1, 1, data.fy * sy);
        k.put(0, 2, data.cx * sx);
        k.put(1, 2, data.cy * sy);
        return k;
    }

    private static FeatureResult estimateFundamental(Mat grayL, Mat grayR, Mat leftBgr, Mat rightBgr) throws Exception {
        ORB orb = ORB.create(1800);

        MatOfKeyPoint kpL = new MatOfKeyPoint();
        MatOfKeyPoint kpR = new MatOfKeyPoint();
        Mat descL = new Mat();
        Mat descR = new Mat();

        orb.detectAndCompute(grayL, new Mat(), kpL, descL);
        orb.detectAndCompute(grayR, new Mat(), kpR, descR);

        if (descL.empty() || descR.empty()) {
            throw new IllegalStateException("ORB não encontrou descritores suficientes. Use uma cena com mais textura.");
        }

        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

        // Matching recomendado para ORB: KNN + ratio test de Lowe.
        // O método anterior pegava apenas as menores distâncias globais, o que favorecia
        // correspondências repetidas em barras/linhas paralelas e podia deformar F.
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descL, descR, knnMatches, 2);

        List<DMatch> selected = new ArrayList<>();
        List<DMatch> fallbackBest = new ArrayList<>();

        for (MatOfDMatch m : knnMatches) {
            DMatch[] arr = m.toArray();
            if (arr.length >= 1) fallbackBest.add(arr[0]);
            if (arr.length >= 2 && arr[0].distance < 0.75f * arr[1].distance) {
                selected.add(arr[0]);
            }
        }

        // Fallback controlado: em cenas muito simples, o ratio test pode ser restritivo.
        // Ainda assim, mantemos limite máximo para evitar matches ruins dominando a matriz F.
        if (selected.size() < 30) {
            Collections.sort(fallbackBest, Comparator.comparingDouble(m -> m.distance));
            selected.clear();
            int keep = Math.min(fallbackBest.size(), 220);
            for (int i = 0; i < keep; i++) selected.add(fallbackBest.get(i));
        }

        if (selected.size() < 8) {
            throw new IllegalStateException("Menos de 8 correspondências. Não é possível estimar F.");
        }

        KeyPoint[] keypointsL = kpL.toArray();
        KeyPoint[] keypointsR = kpR.toArray();

        List<Point> ptsLList = new ArrayList<>();
        List<Point> ptsRList = new ArrayList<>();

        for (DMatch match : selected) {
            ptsLList.add(keypointsL[match.queryIdx].pt);
            ptsRList.add(keypointsR[match.trainIdx].pt);
        }

        MatOfPoint2f ptsL = new MatOfPoint2f();
        MatOfPoint2f ptsR = new MatOfPoint2f();
        ptsL.fromList(ptsLList);
        ptsR.fromList(ptsRList);

        Mat mask = new Mat();
        Mat F = Calib3d.findFundamentalMat(ptsL, ptsR, Calib3d.FM_RANSAC, 3.0, 0.99, mask);

        if (F.empty() || F.rows() != 3 || F.cols() != 3) {
            throw new IllegalStateException("A matriz fundamental F não pôde ser estimada.");
        }

        List<DMatch> inlierMatches = new ArrayList<>();
        List<Point> inlierPtsL = new ArrayList<>();
        List<Point> inlierPtsR = new ArrayList<>();

        for (int i = 0; i < selected.size(); i++) {
            if (isInlier(mask, i)) {
                inlierMatches.add(selected.get(i));
                inlierPtsL.add(ptsLList.get(i));
                inlierPtsR.add(ptsRList.get(i));
            }
        }

        if (inlierMatches.size() < 8) {
            throw new IllegalStateException("Poucos inliers RANSAC (" + inlierMatches.size()
                    + "). Refaça a captura com translação mais pura e cena mais texturizada.");
        }

        List<DMatch> drawMatches = inlierMatches.subList(0, Math.min(inlierMatches.size(), 80));
        MatOfDMatch drawMat = new MatOfDMatch();
        drawMat.fromList(drawMatches);

        Mat matchesDraw = new Mat();
        Features2d.drawMatches(
                leftBgr,
                kpL,
                rightBgr,
                kpR,
                drawMat,
                matchesDraw,
                Scalar.all(-1),
                Scalar.all(-1),
                new MatOfByte(),
                Features2d.DrawMatchesFlags_NOT_DRAW_SINGLE_POINTS
        );

        MatOfPoint2f inPtsL = new MatOfPoint2f();
        MatOfPoint2f inPtsR = new MatOfPoint2f();
        inPtsL.fromList(inlierPtsL);
        inPtsR.fromList(inlierPtsR);

        double[] verticalStats = verticalErrorStats(inlierPtsL, inlierPtsR);

        FeatureResult result = new FeatureResult();
        result.keypointsLeft = (int) kpL.size().height;
        result.keypointsRight = (int) kpR.size().height;
        result.rawMatches = knnMatches.size();
        result.selectedMatches = selected.size();
        result.inliers = inlierMatches.size();
        result.medianVerticalErrorPx = verticalStats[0];
        result.maxVerticalErrorPx = verticalStats[1];
        result.F = F;
        result.matchesDraw = matchesDraw;
        result.inlierPointsL = inPtsL;
        result.inlierPointsR = inPtsR;
        return result;
    }

    private static boolean isInlier(Mat mask, int index) {
        if (mask == null || mask.empty()) return false;

        double[] value;
        if (mask.rows() == 1) value = mask.get(0, index);
        else value = mask.get(index, 0);

        return value != null && value.length > 0 && value[0] != 0.0;
    }

    private static double[] verticalErrorStats(List<Point> ptsL, List<Point> ptsR) {
        List<Double> absDy = new ArrayList<>();
        int n = Math.min(ptsL == null ? 0 : ptsL.size(), ptsR == null ? 0 : ptsR.size());
        for (int i = 0; i < n; i++) {
            absDy.add(Math.abs(ptsL.get(i).y - ptsR.get(i).y));
        }
        if (absDy.isEmpty()) return new double[]{0.0, 0.0};
        Collections.sort(absDy);
        return new double[]{absDy.get(absDy.size() / 2), absDy.get(absDy.size() - 1)};
    }

    private static Mat drawEpipolarValidation(Mat left, Mat right, MatOfPoint2f inlierL, MatOfPoint2f inlierR, Mat F) {
        Mat leftDraw = left.clone();
        Mat rightDraw = right.clone();

        try {
            Point[] ptsL = inlierL.toArray();
            Point[] ptsR = inlierR.toArray();
            int n = Math.min(10, Math.min(ptsL.length, ptsR.length));
            if (n <= 0) {
                List<Mat> fallback = new ArrayList<>();
                fallback.add(leftDraw);
                fallback.add(rightDraw);
                Mat pair = new Mat();
                Core.hconcat(fallback, pair);
                return pair;
            }

            List<Point> subL = new ArrayList<>();
            List<Point> subR = new ArrayList<>();
            int stride = Math.max(1, ptsL.length / n);
            for (int i = 0; i < ptsL.length && subL.size() < n; i += stride) {
                subL.add(ptsL[i]);
                subR.add(ptsR[i]);
            }

            MatOfPoint2f pL = new MatOfPoint2f();
            MatOfPoint2f pR = new MatOfPoint2f();
            pL.fromList(subL);
            pR.fromList(subR);

            Mat linesR = new Mat();
            Mat linesL = new Mat();
            Calib3d.computeCorrespondEpilines(pL, 1, F, linesR);
            Calib3d.computeCorrespondEpilines(pR, 2, F, linesL);

            for (int i = 0; i < subL.size(); i++) {
                Scalar color = colorForIndex(i);
                drawLineFromABC(rightDraw, linesR.get(i, 0), color);
                drawLineFromABC(leftDraw, linesL.get(i, 0), color);
                Imgproc.circle(leftDraw, subL.get(i), 6, color, 2);
                Imgproc.circle(rightDraw, subR.get(i), 6, color, 2);
            }
        } catch (Exception ignored) {
        }

        List<Mat> images = new ArrayList<>();
        images.add(leftDraw);
        images.add(rightDraw);
        Mat pair = new Mat();
        Core.hconcat(images, pair);
        Imgproc.line(pair, new Point(left.cols(), 0), new Point(left.cols(), pair.rows()), new Scalar(255, 255, 255), 2);
        return pair;
    }

    private static void drawLineFromABC(Mat img, double[] abc, Scalar color) {
        if (abc == null || abc.length < 3) return;
        double a = abc[0], b = abc[1], c = abc[2];
        int w = img.cols();
        int h = img.rows();
        if (Math.abs(b) > 1e-9) {
            Point p1 = new Point(0, -c / b);
            Point p2 = new Point(w, -(c + a * w) / b);
            Imgproc.line(img, p1, p2, color, 2);
        } else if (Math.abs(a) > 1e-9) {
            double x = -c / a;
            Imgproc.line(img, new Point(x, 0), new Point(x, h), color, 2);
        }
    }

    private static Scalar colorForIndex(int i) {
        Scalar[] colors = new Scalar[]{
                new Scalar(0, 0, 255),
                new Scalar(0, 255, 0),
                new Scalar(255, 0, 0),
                new Scalar(0, 255, 255),
                new Scalar(255, 0, 255),
                new Scalar(255, 255, 0),
                new Scalar(0, 128, 255),
                new Scalar(128, 0, 255),
                new Scalar(255, 128, 0),
                new Scalar(0, 255, 128)
        };
        return colors[Math.abs(i) % colors.length];
    }

    private static boolean rectifyPair(
            Mat left,
            Mat right,
            MatOfPoint2f inlierL,
            MatOfPoint2f inlierR,
            Mat F,
            Mat rectifiedL,
            Mat rectifiedR
    ) {
        try {
            Mat H1 = new Mat();
            Mat H2 = new Mat();
            Size imageSize = new Size(left.cols(), left.rows());

            boolean ok = Calib3d.stereoRectifyUncalibrated(
                    inlierL,
                    inlierR,
                    F,
                    imageSize,
                    H1,
                    H2,
                    3.0
            );

            if (!ok || H1.empty() || H2.empty()) return false;

            Imgproc.warpPerspective(left, rectifiedL, H1, imageSize);
            Imgproc.warpPerspective(right, rectifiedR, H2, imageSize);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Mat drawRectifiedPair(Mat rectifiedL, Mat rectifiedR) {
        List<Mat> images = new ArrayList<>();
        images.add(rectifiedL);
        images.add(rectifiedR);

        Mat pair = new Mat();
        Core.hconcat(images, pair);

        int w = pair.cols();
        int h = pair.rows();
        int step = Math.max(30, h / 10);

        for (int y = step; y < h; y += step) {
            Imgproc.line(pair, new Point(0, y), new Point(w, y), new Scalar(0, 255, 255), 2);
        }

        Imgproc.line(pair, new Point(rectifiedL.cols(), 0), new Point(rectifiedL.cols(), h), new Scalar(255, 255, 255), 2);
        return pair;
    }

    private static DisparityResult computeDisparity(
            Context context,
            Mat rectifiedL,
            Mat rectifiedR,
            Mat K,
            double baselineCm,
            int presetId,
            boolean exportDepth
    ) throws Exception {
        Mat grayL = new Mat();
        Mat grayR = new Mat();

        Imgproc.cvtColor(rectifiedL, grayL, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(rectifiedR, grayR, Imgproc.COLOR_BGR2GRAY);

        Imgproc.equalizeHist(grayL, grayL);
        Imgproc.equalizeHist(grayR, grayR);

        SgbmPreset preset = presetFor(presetId);
        int blockSize = preset.blockSize;
        int maxByWidth = Math.max(16, ((grayL.cols() / 2) / 16) * 16);
        int numDisparities = Math.min(preset.numDisparities, maxByWidth);
        if (numDisparities < 16) numDisparities = 16;
        numDisparities = (numDisparities / 16) * 16;
        if (numDisparities < 16) numDisparities = 16;

        StereoSGBM sgbm = StereoSGBM.create(0, numDisparities, blockSize);
        sgbm.setP1(8 * blockSize * blockSize);
        sgbm.setP2(32 * blockSize * blockSize);
        sgbm.setPreFilterCap(31);
        sgbm.setMode(StereoSGBM.MODE_SGBM);
        sgbm.setUniquenessRatio(preset.uniquenessRatio);
        sgbm.setSpeckleWindowSize(preset.speckleWindowSize);
        sgbm.setSpeckleRange(preset.speckleRange);
        sgbm.setDisp12MaxDiff(1);

        Mat disp16 = new Mat();
        sgbm.compute(grayL, grayR, disp16);

        Mat disp32 = new Mat();
        disp16.convertTo(disp32, CvType.CV_32F, 1.0 / 16.0);

        // Visualização com escala fixa pela faixa de busca.
        // Isso evita que poucos pixels extremos dominem a normalização do colormap.
        Mat disp8 = new Mat();
        disp32.convertTo(disp8, CvType.CV_8U, 255.0 / Math.max(16.0, numDisparities));

        Mat disp8Filtered = new Mat();
        Imgproc.medianBlur(disp8, disp8Filtered, 5);

        Mat dispColor = new Mat();
        Imgproc.applyColorMap(disp8Filtered, dispColor, Imgproc.COLORMAP_JET);

        DisparityResult result = new DisparityResult();
        result.presetId = preset.id;
        result.presetLabel = preset.label;
        result.numDisparities = numDisparities;
        result.blockSize = blockSize;
        result.p1 = 8 * blockSize * blockSize;
        result.p2 = 32 * blockSize * blockSize;
        result.uniquenessRatio = preset.uniquenessRatio;
        result.speckleWindowSize = preset.speckleWindowSize;
        result.speckleRange = preset.speckleRange;
        result.disparity16 = disp16;
        result.disparityColor = dispColor;
        result.depthColor = dispColor.clone();

        if (exportDepth) {
            DepthStats stats = computeDepthStatsAndExportPly(context, rectifiedL, disp16, K, baselineCm);

            // A comparação com a distância real do objeto deve usar uma região de interesse (ROI),
            // não a mediana global da cena inteira. Por padrão, usamos a ROI central, pois na
            // prática o objeto de referência deve ser colocado no centro da imagem. Se a ROI não
            // tiver pixels válidos suficientes, fazemos fallback para a estatística global.
            result.validDepthPixels = stats.roiValidPixels > 0 ? stats.roiValidPixels : stats.globalValidPixels;
            result.medianZcm = stats.roiValidPixels > 0 ? stats.roiMedianZcm : stats.globalMedianZcm;
            result.minZcm = stats.roiValidPixels > 0 ? stats.roiMinZcm : stats.globalMinZcm;
            result.maxZcm = stats.roiValidPixels > 0 ? stats.roiMaxZcm : stats.globalMaxZcm;

            result.globalValidDepthPixels = stats.globalValidPixels;
            result.globalMedianZcm = stats.globalMedianZcm;
            result.globalMinZcm = stats.globalMinZcm;
            result.globalMaxZcm = stats.globalMaxZcm;
            result.roiValidDepthPixels = stats.roiValidPixels;
            result.roiMedianZcm = stats.roiMedianZcm;
            result.roiMinZcm = stats.roiMinZcm;
            result.roiMaxZcm = stats.roiMaxZcm;
            result.roiX = stats.roiX;
            result.roiY = stats.roiY;
            result.roiWidth = stats.roiWidth;
            result.roiHeight = stats.roiHeight;
            result.exportedPoints = stats.exportedPoints;

            // Desenha a ROI central no mapa visual de profundidade para ficar claro, no relatório
            // e no vídeo, de onde saiu o Z usado no erro relativo.
            if (stats.roiWidth > 0 && stats.roiHeight > 0) {
                Imgproc.rectangle(
                        result.depthColor,
                        new Point(stats.roiX, stats.roiY),
                        new Point(stats.roiX + stats.roiWidth, stats.roiY + stats.roiHeight),
                        new Scalar(0, 255, 0),
                        3
                );
            }
        }

        return result;
    }

    private static List<DisparityResult> computeAndSavePresetDisparities(
            Context context,
            Mat rectifiedL,
            Mat rectifiedR,
            Mat K,
            double baselineCm,
            int selectedPreset,
            DisparityResult selectedResult
    ) throws Exception {
        List<DisparityResult> results = new ArrayList<>();
        for (int presetId = 1; presetId <= 3; presetId++) {
            DisparityResult r;
            if (presetId == selectedPreset) {
                r = selectedResult;
            } else {
                r = computeDisparity(context, rectifiedL, rectifiedR, K, baselineCm, presetId, false);
            }
            Bitmap bmp = OpenCvHelper.matToBitmap(r.disparityColor);
            saveBitmapToFile(bmp, disparityPresetFile(context, presetId));
            ImageUtils.saveBitmapToGallery(context, bmp, "stereo_disparity_preset_" + presetId);
            results.add(r);
        }
        return results;
    }

    private static SgbmPreset presetFor(int id) {
        if (id == 1) return new SgbmPreset(1, "Preset 1: 64 disparidades | bloco 5", 64, 5, 8, 80, 2);
        if (id == 2) return new SgbmPreset(2, "Preset 2: 128 disparidades | bloco 7", 128, 7, 10, 100, 16);
        return new SgbmPreset(3, "Preset 3: 192 disparidades | bloco 9", 192, 9, 12, 120, 32);
    }

    private static DepthStats computeDepthStatsAndExportPly(Context context, Mat rectifiedL, Mat disp16, Mat K, double baselineCm) throws Exception {
        double fx = K.get(0, 0)[0];
        double fy = K.get(1, 1)[0];
        double cx = K.get(0, 2)[0];
        double cy = K.get(1, 2)[0];

        int cols = disp16.cols();
        int rows = disp16.rows();

        // ROI central menor e mais conservadora: a comparação métrica deve usar
        // apenas a região onde o objeto de referência foi centralizado.
        // A ROI anterior era grande demais e podia incluir chão, barras laterais e fundo.
        int roiW = Math.max(40, (int) Math.round(cols * 0.30));
        int roiH = Math.max(40, (int) Math.round(rows * 0.30));
        int roiX = Math.max(0, (cols - roiW) / 2);
        int roiY = Math.max(0, (rows - roiH) / 2);
        Rect roi = new Rect(roiX, roiY, Math.min(roiW, cols - roiX), Math.min(roiH, rows - roiY));

        List<Double> globalZ = new ArrayList<>();
        List<Double> roiZ = new ArrayList<>();
        StringBuilder points = new StringBuilder();

        int exported = 0;
        int plyStep = 5;

        // Estatísticas de Z em todos os pixels válidos; exportação PLY subamostrada.
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double[] rawArr = disp16.get(y, x);
                if (rawArr == null || rawArr.length == 0) continue;

                double disparity = rawArr[0] / 16.0;
                if (disparity <= 1.5) continue;

                double z = (fx * baselineCm) / disparity;
                if (z <= 5.0 || z > 1000.0) continue;

                globalZ.add(z);
                if (roi.contains(new Point(x, y))) {
                    roiZ.add(z);
                }

                if (exported < 50000 && x % plyStep == 0 && y % plyStep == 0) {
                    double X = (x - cx) * z / fx;
                    double Y = (y - cy) * z / fy;

                    double[] bgr = rectifiedL.get(y, x);
                    int b = bgr != null && bgr.length > 0 ? clampToByte(bgr[0]) : 255;
                    int g = bgr != null && bgr.length > 1 ? clampToByte(bgr[1]) : 255;
                    int r = bgr != null && bgr.length > 2 ? clampToByte(bgr[2]) : 255;

                    points.append(String.format(java.util.Locale.US,
                            "%.4f %.4f %.4f %d %d %d%n", X, Y, z, r, g, b));
                    exported++;
                }
            }
        }

        Collections.sort(globalZ);
        Collections.sort(roiZ);

        DepthStats stats = new DepthStats();
        stats.exportedPoints = exported;
        stats.roiX = roi.x;
        stats.roiY = roi.y;
        stats.roiWidth = roi.width;
        stats.roiHeight = roi.height;

        fillStats(globalZ, true, stats);
        fillStats(roiZ, false, stats);

        File ply = plyFile(context);
        writePly(ply, exported, points.toString());

        return stats;
    }

    private static void fillStats(List<Double> values, boolean global, DepthStats stats) {
        int n = values == null ? 0 : values.size();
        if (global) stats.globalValidPixels = n;
        else stats.roiValidPixels = n;

        if (n == 0) return;

        double min = values.get(0);
        double median = values.get(n / 2);
        double max = values.get(n - 1);

        if (global) {
            stats.globalMinZcm = min;
            stats.globalMedianZcm = median;
            stats.globalMaxZcm = max;
        } else {
            stats.roiMinZcm = min;
            stats.roiMedianZcm = median;
            stats.roiMaxZcm = max;
        }
    }

    private static void writePly(File file, int count, String body) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("ply\n");
            writer.write("format ascii 1.0\n");
            writer.write("comment VisionProject stereo smartphone reconstruction\n");
            writer.write("element vertex " + count + "\n");
            writer.write("property float x\n");
            writer.write("property float y\n");
            writer.write("property float z\n");
            writer.write("property uchar red\n");
            writer.write("property uchar green\n");
            writer.write("property uchar blue\n");
            writer.write("end_header\n");
            writer.write(body);
        }
    }

    private static int clampToByte(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static double loadBaselineCm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(KEY_BASELINE_CM, Double.doubleToLongBits(8.0)));
    }

    public static double loadObjectDistanceCm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(prefs.getLong(KEY_OBJECT_DISTANCE_CM, Double.doubleToLongBits(100.0)));
    }

    public static int loadDisparityPreset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int value = prefs.getInt(KEY_SGBM_PRESET, 1);
        if (value < 1 || value > 3) return 1;
        return value;
    }

    public static void saveDisparityPreset(Context context, int presetId) {
        int safe = presetId < 1 || presetId > 3 ? 1 : presetId;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SGBM_PRESET, safe)
                .apply();
    }

    private static String buildReport(
            CalibrationData calibration,
            int width,
            int height,
            FeatureResult feature,
            boolean rectified,
            DisparityResult disparity,
            List<DisparityResult> presetResults,
            double baselineCm,
            double objectDistanceCm,
            Mat K
    ) {
        double fx = K.get(0, 0)[0];
        double fy = K.get(1, 1)[0];

        StringBuilder sb = new StringBuilder();
        sb.append("RELATÓRIO DO PIPELINE ESTÉREO\n");
        sb.append("Imagem processada: ").append(width).append(" x ").append(height).append(" px\n");
        sb.append("Baseline usada: ")
                .append(String.format(java.util.Locale.US, "%.2f", baselineCm))
                .append(" cm (deslocamento lateral POS1→POS2)\n");
        sb.append("Distância real do objeto de referência: ")
                .append(String.format(java.util.Locale.US, "%.2f", objectDistanceCm))
                .append(" cm\n");
        if (baselineCm > 20.0) {
            sb.append("ALERTA: baseline acima de 20 cm. Verifique se não foi informada a distância até o objeto no lugar do deslocamento lateral.\n");
        }
        sb.append("Calibração: ")
                .append(calibration == null ? "não encontrada; K aproximada e distorção zero." : "calibration.json carregado.")
                .append("\n");
        if (calibration != null) {
            sb.append("RMS calibração: ").append(String.format(java.util.Locale.US, "%.4f", calibration.rms)).append("\n");
        }
        sb.append("fx usado: ").append(String.format(java.util.Locale.US, "%.2f", fx)).append(" px | fy usado: ")
                .append(String.format(java.util.Locale.US, "%.2f", fy)).append(" px\n\n");

        sb.append("T3 — ORB e correspondências\n");
        sb.append("Keypoints esquerda: ").append(feature.keypointsLeft).append("\n");
        sb.append("Keypoints direita: ").append(feature.keypointsRight).append("\n");
        sb.append("Matches brutos: ").append(feature.rawMatches).append("\n");
        sb.append("Matches selecionados: ").append(feature.selectedMatches).append("\n\n");

        sb.append("T4 — Matriz fundamental com RANSAC\n");
        sb.append("Inliers RANSAC: ").append(feature.inliers).append("\n");
        sb.append("Erro vertical mediano antes da retificação: ")
                .append(String.format(java.util.Locale.US, "%.2f", feature.medianVerticalErrorPx))
                .append(" px\n");
        sb.append("Erro vertical máximo antes da retificação: ")
                .append(String.format(java.util.Locale.US, "%.2f", feature.maxVerticalErrorPx))
                .append(" px\n");
        sb.append("Matriz F estimada:\n").append(matrixToString(feature.F)).append("\n");
        sb.append(feature.inliers < 30
                ? "Aviso: inliers < 30. O enunciado recomenda refazer a aquisição quando isso ocorrer.\n\n"
                : "Inliers suficientes para prosseguir, desde que as linhas epipolares estejam coerentes.\n\n");

        sb.append("T5 — Retificação\n");
        sb.append("Retificação não calibrada: ").append(rectified ? "executada." : "falhou; usando par não retificado como fallback.").append("\n\n");

        sb.append("T6/T7 — Disparidade\n");
        sb.append("Preset selecionado para T8: ").append(disparity.presetId)
                .append(" — ").append(disparity.presetLabel).append("\n");
        sb.append("StereoSGBM numDisparities: ").append(disparity.numDisparities).append("\n");
        sb.append("StereoSGBM blockSize: ").append(disparity.blockSize).append("\n");
        sb.append("P1: ").append(disparity.p1).append(" | P2: ").append(disparity.p2).append("\n");
        sb.append("Correção aplicada: a disparidade métrica é calculada no par undistorted original, sem usar a homografia da retificação não calibrada.\n");
        sb.append("Motivo: a retificação não calibrada pode alterar a escala horizontal e distorcer Z = f*B/d.\n");
        sb.append("Filtro T7 aplicado: medianBlur 5x5 sobre o mapa normalizado.\n");
        sb.append("Tabela comparativa dos presets testados:\n");
        sb.append("Preset | numDisparities | blockSize | P1 | P2 | uniqueness | speckleWindow | speckleRange\n");
        if (presetResults != null) {
            for (DisparityResult r : presetResults) {
                sb.append(r.presetId).append(" | ")
                        .append(r.numDisparities).append(" | ")
                        .append(r.blockSize).append(" | ")
                        .append(r.p1).append(" | ")
                        .append(r.p2).append(" | ")
                        .append(r.uniquenessRatio).append(" | ")
                        .append(r.speckleWindowSize).append(" | ")
                        .append(r.speckleRange).append("\n");
            }
        }
        sb.append("Mapas salvos: disparity_preset_1.jpg, disparity_preset_2.jpg e disparity_preset_3.jpg em files/stereo e na galeria.\n\n");

        sb.append("T8 — Profundidade aproximada\n");
        double expectedD = objectDistanceCm > 0.0 ? (fx * baselineCm) / objectDistanceCm : 0.0;
        sb.append("Disparidade esperada para o objeto: ")
                .append(String.format(java.util.Locale.US, "%.2f", expectedD))
                .append(" px, considerando Z real e baseline informados.\n");
        if (expectedD > disparity.numDisparities) {
            sb.append("ALERTA: a disparidade esperada é maior que numDisparities. Use um preset com maior faixa ou reduza a baseline.\n");
        }

        sb.append("ROI central usada para comparar com o objeto: x=").append(disparity.roiX)
                .append(", y=").append(disparity.roiY)
                .append(", largura=").append(disparity.roiWidth)
                .append(", altura=").append(disparity.roiHeight)
                .append(" px. Mantenha o objeto de referência dentro dessa região.\n");

        sb.append("Pixels válidos globais: ").append(disparity.globalValidDepthPixels).append("\n");
        if (disparity.globalValidDepthPixels > 0) {
            sb.append("Z mediano global: ").append(String.format(java.util.Locale.US, "%.2f", disparity.globalMedianZcm)).append(" cm\n");
            sb.append("Z mínimo global: ").append(String.format(java.util.Locale.US, "%.2f", disparity.globalMinZcm)).append(" cm\n");
            sb.append("Z máximo global: ").append(String.format(java.util.Locale.US, "%.2f", disparity.globalMaxZcm)).append(" cm\n");
        }

        sb.append("Pixels válidos na ROI central: ").append(disparity.roiValidDepthPixels).append("\n");
        if (disparity.validDepthPixels > 0) {
            sb.append("Z mediano usado no erro: ").append(String.format(java.util.Locale.US, "%.2f", disparity.medianZcm)).append(" cm")
                    .append(disparity.roiValidDepthPixels > 0 ? " (ROI central)\n" : " (fallback global)\n");
            sb.append("Z mínimo usado: ").append(String.format(java.util.Locale.US, "%.2f", disparity.minZcm)).append(" cm\n");
            sb.append("Z máximo usado: ").append(String.format(java.util.Locale.US, "%.2f", disparity.maxZcm)).append(" cm\n");
            double absError = Math.abs(disparity.medianZcm - objectDistanceCm);
            double relError = objectDistanceCm > 0.0 ? (absError / objectDistanceCm) * 100.0 : 0.0;
            sb.append("Erro absoluto vs. objeto: ").append(String.format(java.util.Locale.US, "%.2f", absError)).append(" cm\n");
            sb.append("Erro relativo vs. objeto: ").append(String.format(java.util.Locale.US, "%.2f", relError)).append("%\n");
        } else {
            sb.append("Não houve disparidades positivas suficientes para estimar Z.\n");
        }
        sb.append("Pontos exportados no PLY: ").append(disparity.exportedPoints).append("\n");
        sb.append("cloud.ply salvo em: files/stereo/cloud.ply\n\n");

        sb.append("Interpretação crítica obrigatória: discuta rotação entre capturas, foco automático, pouca textura, baseline inadequada, oclusões, reflexos e alterações de iluminação. ");
        sb.append("Para a comparação métrica, posicione o objeto medido dentro da ROI central marcada no mapa de profundidade.");
        return sb.toString();
    }

    private static String matrixToString(Mat m) {
        if (m == null || m.empty()) return "[matriz vazia]\n";
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < m.rows(); r++) {
            out.append("[");
            for (int c = 0; c < m.cols(); c++) {
                double[] value = m.get(r, c);
                double v = value != null && value.length > 0 ? value[0] : Double.NaN;
                out.append(String.format(java.util.Locale.US, "% .6e", v));
                if (c < m.cols() - 1) out.append(", ");
            }
            out.append("]\n");
        }
        return out.toString();
    }

    public static final class StereoResult {
        public Bitmap matchesBitmap;
        public Bitmap epipolarBitmap;
        public Bitmap rectifiedPairBitmap;
        public Bitmap disparityBitmap;
        public Bitmap depthBitmap;
        public String reportText;
        public String plyPath;
    }

    private static final class FeatureResult {
        int keypointsLeft;
        int keypointsRight;
        int rawMatches;
        int selectedMatches;
        int inliers;
        double medianVerticalErrorPx;
        double maxVerticalErrorPx;
        Mat F;
        Mat matchesDraw;
        MatOfPoint2f inlierPointsL;
        MatOfPoint2f inlierPointsR;
    }

    private static final class DisparityResult {
        int presetId;
        String presetLabel;
        int numDisparities;
        int blockSize;
        int p1;
        int p2;
        int uniquenessRatio;
        int speckleWindowSize;
        int speckleRange;
        Mat disparity16;
        Mat disparityColor;
        Mat depthColor;
        int validDepthPixels;
        double medianZcm;
        double minZcm;
        double maxZcm;
        int globalValidDepthPixels;
        double globalMedianZcm;
        double globalMinZcm;
        double globalMaxZcm;
        int roiValidDepthPixels;
        double roiMedianZcm;
        double roiMinZcm;
        double roiMaxZcm;
        int roiX;
        int roiY;
        int roiWidth;
        int roiHeight;
        int exportedPoints;
    }

    private static final class SgbmPreset {
        final int id;
        final String label;
        final int numDisparities;
        final int blockSize;
        final int uniquenessRatio;
        final int speckleWindowSize;
        final int speckleRange;

        SgbmPreset(int id, String label, int numDisparities, int blockSize,
                   int uniquenessRatio, int speckleWindowSize, int speckleRange) {
            this.id = id;
            this.label = label;
            this.numDisparities = numDisparities;
            this.blockSize = blockSize;
            this.uniquenessRatio = uniquenessRatio;
            this.speckleWindowSize = speckleWindowSize;
            this.speckleRange = speckleRange;
        }
    }

    private static final class DepthStats {
        int exportedPoints;
        int globalValidPixels;
        double globalMedianZcm;
        double globalMinZcm;
        double globalMaxZcm;
        int roiValidPixels;
        double roiMedianZcm;
        double roiMinZcm;
        double roiMaxZcm;
        int roiX;
        int roiY;
        int roiWidth;
        int roiHeight;
    }

}
