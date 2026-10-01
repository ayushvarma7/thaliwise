package com.example.identify.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.example.identify.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageUtil {
    private ImageUtil() {}

    /** The private photo copy plus what was done to produce it (for the experiment log). */
    public static final class PreparedImage {
        public final File file;
        public final int sourceWidth;
        public final int sourceHeight;
        public final int sampleSize;
        public final int rotationDegrees;
        public final int width;
        public final int height;
        public final long bytes;
        public final long elapsedMs;

        PreparedImage(File file, int sourceWidth, int sourceHeight, int sampleSize, int rotationDegrees,
                      int width, int height, long bytes, long elapsedMs) {
            this.file = file;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.sampleSize = sampleSize;
            this.rotationDegrees = rotationDegrees;
            this.width = width;
            this.height = height;
            this.bytes = bytes;
            this.elapsedMs = elapsedMs;
        }
    }

    /** Private, upright JPEG copy (longest side at most Config.MODEL_IMAGE_MAX_DIM) in filesDir/images. */
    public static PreparedImage prepareForModel(Context ctx, Uri uri) throws IOException {
        long t0 = SystemClock.elapsedRealtime();
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
        Bitmap scaled = scaleToLongestSide(rotated, Config.MODEL_IMAGE_MAX_DIM);
        if (scaled != rotated) rotated.recycle();

        // 4. Save JPEG quality 85.
        File dir = new File(ctx.getFilesDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)) throw new IOException("JPEG encode failed");
        } finally {
            scaled.recycle();
        }
        PreparedImage p = new PreparedImage(out, bounds.outWidth, bounds.outHeight, sampleSize, degrees,
                w, h, out.length(), SystemClock.elapsedRealtime() - t0);
        Log.i(Config.LOG_TAG, "image prepared file=" + out.getName() + " source=" + bounds.outWidth + "x" + bounds.outHeight
                + " sample=" + sampleSize + " rotation=" + degrees + " out=" + w + "x" + h
                + " bytes=" + p.bytes + " ms=" + p.elapsedMs);
        return p;
    }

    /**
     * The image handed to the model: longest side at most Config.INFER_IMAGE_MAX_DIM, so LFM2-VL encodes it
     * as one tile. Named after the source photo, so a cached native encoding can never belong to another
     * photo. Reused if it already exists; older inference images are deleted.
     */
    public static File prepareForInference(Context ctx, String imagePath) throws IOException {
        File src = new File(imagePath);
        File dir = new File(ctx.getCacheDir(), "infer");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, "infer_" + src.getName());
        if (out.exists() && out.length() > 0) return out;

        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (!f.delete()) Log.w(Config.LOG_TAG, "could not delete " + f);
            }
        }
        long t0 = SystemClock.elapsedRealtime();
        Bitmap decoded = BitmapFactory.decodeFile(imagePath);
        if (decoded == null) throw new IOException("cannot decode " + imagePath);
        Bitmap scaled = scaleToLongestSide(decoded, Config.INFER_IMAGE_MAX_DIM);
        if (scaled != decoded) decoded.recycle();
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, fos)) throw new IOException("JPEG encode failed");
        } finally {
            scaled.recycle();
        }
        Log.i(Config.LOG_TAG, "inference image file=" + out.getName() + " size=" + w + "x" + h
                + " bytes=" + out.length() + " ms=" + (SystemClock.elapsedRealtime() - t0));
        return out;
    }

    /** Returns the same bitmap when it already fits. */
    private static Bitmap scaleToLongestSide(Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int maxSide = Math.max(w, h);
        if (maxSide <= maxDim) return src;
        float scale = (float) maxDim / maxSide;
        int nw;
        int nh;
        if (w >= h) {
            nw = maxDim;
            nh = Math.max(1, Math.round(h * scale));
        } else {
            nh = maxDim;
            nw = Math.max(1, Math.round(w * scale));
        }
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }
}
