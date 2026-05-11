package com.ufla.visionproject;

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
