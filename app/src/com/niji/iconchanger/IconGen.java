package com.niji.iconchanger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a user image into the bitmap set the entry template expects. */
public class IconGen {

    private static final Map<String, Integer> LEGACY = new HashMap<>();
    private static final Map<String, Integer> FG = new HashMap<>();
    static {
        LEGACY.put("mdpi", 48); LEGACY.put("hdpi", 72); LEGACY.put("xhdpi", 96);
        LEGACY.put("xxhdpi", 144); LEGACY.put("xxxhdpi", 192);
        FG.put("mdpi", 108); FG.put("hdpi", 162); FG.put("xhdpi", 216);
        FG.put("xxhdpi", 324); FG.put("xxxhdpi", 432);
    }
    private static final Pattern ENTRY = Pattern.compile("res/mipmap-(\\w+?)-v4/(\\w+)\\.png");

    public static Bitmap loadFromUri(Context ctx, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > 1024) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new Exception("无法读取图片");
        return centerCrop(bmp, 512);
    }

    public static Bitmap centerCrop(Bitmap src, int size) {
        int w = src.getWidth(), h = src.getHeight();
        int side = Math.min(w, h);
        Bitmap square = Bitmap.createBitmap(src, (w - side) / 2, (h - side) / 2, side, side);
        if (side == size) return square;
        Bitmap scaled = Bitmap.createScaledBitmap(square, size, size, true);
        if (scaled != square) square.recycle();
        return scaled;
    }

    public static Map<String, byte[]> buildIcons(Bitmap master, Iterable<String> pngEntries) {
        Map<String, byte[]> out = new HashMap<>();
        for (String entry : pngEntries) {
            Matcher m = ENTRY.matcher(entry);
            if (!m.matches()) continue;
            String dpi = m.group(1);
            String name = m.group(2);
            if (name.equals("niji_fg")) {
                Integer size = FG.get(dpi);
                if (size == null) continue;
                Bitmap canvas = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(canvas);
                c.drawColor(Color.WHITE);
                int art = (int) (size * 0.80);
                Bitmap scaled = Bitmap.createScaledBitmap(master, art, art, true);
                c.drawBitmap(scaled, (size - art) / 2f, (size - art) / 2f, null);
                scaled.recycle();
                out.put(entry, png(canvas));
                canvas.recycle();
            } else {
                Integer size = LEGACY.get(dpi);
                if (size == null) continue;
                Bitmap scaled = Bitmap.createScaledBitmap(master, size, size, true);
                out.put(entry, png(scaled));
                scaled.recycle();
            }
        }
        return out;
    }

    public static byte[] png(Bitmap bmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }
}
