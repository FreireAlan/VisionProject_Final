package com.ufla.visionproject;

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
