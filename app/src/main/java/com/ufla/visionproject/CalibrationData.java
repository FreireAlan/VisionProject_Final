package com.ufla.visionproject;

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
