package com.example.identify.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.example.identify.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageUtil {
    private ImageUtil() {}

    /** Private, upright JPEG copy (longest side at most Config.MODEL_IMAGE_MAX_DIM) in filesDir/images. */
    public static File prepareForModel(Context ctx, Uri uri) throws IOException {
        ContentResolver cr = ctx.getContentResolver();

        // 1. Two-pass decode with a power-of-two inSampleSize.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("cannot open " + uri);
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        if (longest <= 0) throw new IOException("cannot read image size: " + uri);
        int sampleSize = 1;
        while (longest / sampleSize > Config.MODEL_IMAGE_MAX_DIM * 2) sampleSize *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        Bitmap decoded;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("cannot open " + uri);
            decoded = BitmapFactory.decodeStream(in, null, opts);
        }
        if (decoded == null) throw new IOException("cannot decode " + uri);

        // 2. EXIF rotation.
        int degrees = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            }
        } catch (IOException | RuntimeException e) {
            Log.w(Config.LOG_TAG, "could not read EXIF, continuing without rotation", e);
        }
        Bitmap rotated = decoded;
        if (degrees != 0) {
            Matrix m = new Matrix();
            m.postRotate(degrees);
            rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), m, true);
        }
        if (rotated != decoded) decoded.recycle();

        // 3. Scale so the longest side equals the limit exactly.
        Bitmap scaled = rotated;
        int w = rotated.getWidth();
        int h = rotated.getHeight();
        int maxSide = Math.max(w, h);
        if (maxSide > Config.MODEL_IMAGE_MAX_DIM) {
            float scale = (float) Config.MODEL_IMAGE_MAX_DIM / maxSide;
            int nw;
            int nh;
            if (w >= h) {
                nw = Config.MODEL_IMAGE_MAX_DIM;
                nh = Math.max(1, Math.round(h * scale));
            } else {
                nh = Config.MODEL_IMAGE_MAX_DIM;
                nw = Math.max(1, Math.round(w * scale));
            }
            scaled = Bitmap.createScaledBitmap(rotated, nw, nh, true);
        }
        if (scaled != rotated) rotated.recycle();

        // 4. Save JPEG quality 85.
        File dir = new File(ctx.getFilesDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)) throw new IOException("JPEG encode failed");
        } finally {
            scaled.recycle();
        }
        return out;
    }

    /** Center-cropped square, Config.EMBED_IMAGE_DIM px, written to cacheDir/embed_input.jpg (overwritten). */
    public static File prepareForEmbedding(Context ctx, String imagePath) throws IOException {
        Bitmap src = BitmapFactory.decodeFile(imagePath);
        if (src == null) throw new IOException("cannot decode " + imagePath);

        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, side, side);
        if (cropped != src) src.recycle();

        Bitmap scaled = Bitmap.createScaledBitmap(cropped, Config.EMBED_IMAGE_DIM, Config.EMBED_IMAGE_DIM, true);
        if (scaled != cropped) cropped.recycle();

        File out = new File(ctx.getCacheDir(), "embed_input.jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, fos)) throw new IOException("JPEG encode failed");
        } finally {
            scaled.recycle();
        }
        return out;
    }
}
