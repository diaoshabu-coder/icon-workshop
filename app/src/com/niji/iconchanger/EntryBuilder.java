package com.niji.iconchanger;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.android.apksig.ApkSigner;

/** Builds and signs a small "launcher entry" APK with the user's icon. */
public class EntryBuilder {

    public static final String TEMPLATE_ASSET = "entry-template.apk";
    public static final String KEYSTORE_ASSET = "niji.p12";
    /**
     * Password of the demo signing key bundled at assets/niji.p12.
     * It is NOT a secret: the keystore is generated locally by
     * scripts/make_keystore.sh. Change both this constant and KS_PASS in
     * that script if you want a different password.
     */
    public static final String KS_PASS = "iconworkshop";
    public static final String KS_ALIAS = "niji";

    public static String entryPackageFor(String targetPkg) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(targetPkg.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(String.format(Locale.US, "%02x", h[i]));
            return "com.niji.entry.n" + sb;
        } catch (Exception e) {
            return "com.niji.entry.n" + Math.abs(targetPkg.hashCode());
        }
    }

    public static byte[] build(Context ctx, String targetPkg, String label, Bitmap icon) throws Exception {
        byte[] template = readAsset(ctx, TEMPLATE_ASSET);
        byte[] manifest = extract(template, "AndroidManifest.xml");
        List<String> pngs = listPngEntries(template);

        Map<String, String> strings = new HashMap<>();
        strings.put("com.niji.entry.tpl", entryPackageFor(targetPkg));
        strings.put("com.placeholder.target", targetPkg);
        strings.put("PLACEHOLDER_LABEL", label);
        byte[] patchedManifest = AxmlPatcher.replaceStrings(manifest, strings);

        Map<String, byte[]> replacements = new HashMap<>();
        replacements.put("AndroidManifest.xml", patchedManifest);
        replacements.putAll(IconGen.buildIcons(icon, pngs));

        ByteArrayOutputStream unsigned = new ByteArrayOutputStream();
        ZipPatcher.rewrite(template, replacements, unsigned);

        File cache = new File(ctx.getCacheDir(), "entries");
        if (!cache.exists()) cache.mkdirs();
        File in = new File(cache, "unsigned.apk");
        File out = new File(cache, entryPackageFor(targetPkg) + ".apk");
        try (FileOutputStream fos = new FileOutputStream(in)) {
            fos.write(unsigned.toByteArray());
        }
        sign(ctx, in, out);
        return readFile(out);
    }

    private static void sign(Context ctx, File in, File out) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = ctx.getAssets().open(KEYSTORE_ASSET)) {
            ks.load(is, KS_PASS.toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(KS_ALIAS, KS_PASS.toCharArray());
        Certificate[] chain = ks.getCertificateChain(KS_ALIAS);
        List<X509Certificate> certs = new ArrayList<>();
        for (Certificate c : chain) certs.add((X509Certificate) c);

        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(KS_ALIAS, key, certs).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(in)
                .setOutputApk(out)
                .setMinSdkVersion(26)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign();
    }

    private static byte[] extract(byte[] zip, String name) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals(name)) return ZipPatcher.readAll(zin);
            }
        }
        throw new Exception("模板缺少 " + name);
    }

    private static List<String> listPngEntries(byte[] zip) throws Exception {
        List<String> list = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String n = e.getName();
                if (n.startsWith("res/mipmap-") && n.endsWith(".png")) list.add(n);
            }
        }
        return list;
    }

    private static byte[] readAsset(Context ctx, String name) throws Exception {
        try (InputStream is = ctx.getAssets().open(name)) {
            return ZipPatcher.readAll(is);
        }
    }

    private static byte[] readFile(File f) throws Exception {
        try (InputStream is = new java.io.FileInputStream(f)) {
            return ZipPatcher.readAll(is);
        }
    }
}
