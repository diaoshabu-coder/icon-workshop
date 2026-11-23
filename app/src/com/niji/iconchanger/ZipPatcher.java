package com.niji.iconchanger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Rewrites an APK zip, replacing selected entries and keeping the original
 * compression method for everything else. resources.arsc must stay STORED.
 */
public class ZipPatcher {

    public static void rewrite(byte[] apk, Map<String, byte[]> replacements, ByteArrayOutputStream out) throws IOException {
        Map<String, byte[]> rep = new HashMap<>(replacements);
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(apk))) {
            ZipOutputStream zos = new ZipOutputStream(out);
            ZipEntry in;
            byte[] buf = new byte[64 * 1024];
            while ((in = zin.getNextEntry()) != null) {
                String name = in.getName();
                byte[] content;
                if (rep.containsKey(name)) {
                    content = rep.remove(name);
                } else {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    int n;
                    while ((n = zin.read(buf)) > 0) b.write(buf, 0, n);
                    content = b.toByteArray();
                }
                ZipEntry e = new ZipEntry(name);
                e.setTime(0x21980L); // fixed timestamp, harmless
                boolean store = in.getMethod() == ZipEntry.STORED || name.equals("resources.arsc");
                if (store) {
                    e.setMethod(ZipEntry.STORED);
                    e.setSize(content.length);
                    CRC32 crc = new CRC32();
                    crc.update(content);
                    e.setCrc(crc.getValue());
                } else {
                    e.setMethod(ZipEntry.DEFLATED);
                }
                zos.putNextEntry(e);
                zos.write(content);
                zos.closeEntry();
            }
            if (!rep.isEmpty()) throw new IOException("entries not found: " + rep.keySet());
            zos.finish();
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
