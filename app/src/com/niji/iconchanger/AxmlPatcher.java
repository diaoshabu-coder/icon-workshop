package com.niji.iconchanger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal editor for Android binary XML (AXML) string pools.
 * Used to rewrite the overlay manifest's package / targetPackage values without
 * needing aapt2 on the device. Only string values are changed, so every other
 * chunk (resource map, elements, attributes) stays byte-identical.
 */
public class AxmlPatcher {

    public static byte[] replaceStrings(byte[] data, Map<String, String> replacements) throws IOException {
        if (data.length < 8 || u16(data, 0) != 0x0003) {
            throw new IOException("not a binary XML document");
        }
        int total = u32(data, 4);
        int pos = 8;
        while (pos + 8 <= total) {
            int ctype = u16(data, pos);
            int csize = u32(data, pos + 4);
            if (csize < 8) throw new IOException("corrupt chunk");
            if (ctype == 0x0001) {
                byte[] pool = rebuildPool(data, pos, replacements);
                ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 256);
                out.write(data, 0, pos);
                out.write(pool);
                out.write(data, pos + csize, total - (pos + csize));
                byte[] res = out.toByteArray();
                putU32(res, 4, res.length);
                return res;
            }
            pos += csize;
        }
        throw new IOException("string pool not found");
    }

    private static byte[] rebuildPool(byte[] d, int pos, Map<String, String> rep) throws IOException {
        int hsize = u16(d, pos + 2);
        int stringCount = u32(d, pos + 8);
        int styleCount = u32(d, pos + 12);
        int flags = u32(d, pos + 16);
        int stringsStart = u32(d, pos + 20);
        if (styleCount != 0) throw new IOException("styled string pools unsupported");
        boolean utf8 = (flags & 0x100) != 0;

        List<String> strings = new ArrayList<>(stringCount);
        for (int i = 0; i < stringCount; i++) {
            int off = u32(d, pos + hsize + 4 * i);
            strings.add(readString(d, pos + stringsStart + off, utf8));
        }
        Map<String, String> applied = new HashMap<>();
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            if (rep.containsKey(s)) {
                strings.set(i, rep.get(s));
                applied.put(s, rep.get(s));
            }
        }
        if (applied.size() != rep.size()) {
            throw new IOException("manifest string not found: " + rep.keySet() + " applied=" + applied.keySet());
        }

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            offsets[i] = data.size();
            writeString(data, strings.get(i), utf8);
        }
        while (data.size() % 4 != 0) data.write(0);

        int newStringsStart = 28 + 4 * stringCount + 4 * styleCount;
        int newSize = newStringsStart + data.size();
        ByteArrayOutputStream chunk = new ByteArrayOutputStream(newSize);
        writeU16(chunk, 0x0001);
        writeU16(chunk, 28);
        writeU32(chunk, newSize);
        writeU32(chunk, stringCount);
        writeU32(chunk, 0);
        writeU32(chunk, flags);
        writeU32(chunk, newStringsStart);
        writeU32(chunk, 0);
        for (int o : offsets) writeU32(chunk, o);
        byte[] body = data.toByteArray();
        chunk.write(body, 0, body.length);
        return chunk.toByteArray();
    }

    private static String readString(byte[] d, int p, boolean utf8) {
        if (utf8) {
            int[] a = readVarint(d, p);
            int[] b = readVarint(d, a[1]);
            int len = b[0];
            return new String(d, b[1], len, StandardCharsets.UTF_8);
        } else {
            int len = u16(d, p);
            return new String(d, p + 2, len * 2, StandardCharsets.UTF_16LE);
        }
    }

    private static void writeString(ByteArrayOutputStream out, String s, boolean utf8) {
        if (utf8) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            int utf16Len = s.length();
            writeVarint(out, utf16Len);
            writeVarint(out, b.length);
            out.write(b, 0, b.length);
            out.write(0);
        } else {
            byte[] b = s.getBytes(StandardCharsets.UTF_16LE);
            writeU16(out, b.length / 2);
            out.write(b, 0, b.length);
            writeU16(out, 0);
        }
    }

    private static int[] readVarint(byte[] d, int p) {
        int v = 0, shift = 0;
        while (true) {
            int x = d[p++] & 0xff;
            v |= (x & 0x7f) << shift;
            if ((x & 0x80) == 0) break;
            shift += 7;
        }
        return new int[]{v, p};
    }

    private static void writeVarint(ByteArrayOutputStream out, int v) {
        while (true) {
            int x = v & 0x7f;
            v >>>= 7;
            if (v != 0) out.write(x | 0x80); else { out.write(x); break; }
        }
    }

    private static int u16(byte[] d, int p) { return (d[p] & 0xff) | ((d[p + 1] & 0xff) << 8); }
    private static int u32(byte[] d, int p) { return (d[p] & 0xff) | ((d[p + 1] & 0xff) << 8) | ((d[p + 2] & 0xff) << 16) | ((d[p + 3] & 0xff) << 24); }
    private static void putU32(byte[] d, int p, int v) { d[p] = (byte) v; d[p + 1] = (byte) (v >> 8); d[p + 2] = (byte) (v >> 16); d[p + 3] = (byte) (v >> 24); }
    private static void writeU16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >> 8) & 0xff); }
    private static void writeU32(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >> 8) & 0xff); o.write((v >> 16) & 0xff); o.write((v >> 24) & 0xff); }
}
