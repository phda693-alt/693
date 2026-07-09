package com.pdv.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ImageUtils {

    public static String uriToBase64(Context context, Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) return null;

            // Resize to max 400x400 to save space
            int maxSize = 400;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float scale = Math.min((float) maxSize / width, (float) maxSize / height);
            if (scale < 1) {
                width = (int) (width * scale);
                height = (int) (height * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap base64ToBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    public static void loadBase64IntoImageView(ImageView imageView, String base64) {
        Bitmap bitmap = base64ToBitmap(base64);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
    }
}
