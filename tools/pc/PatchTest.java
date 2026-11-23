import com.niji.iconchanger.AxmlPatcher;
import com.niji.iconchanger.ZipPatcher;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PatchTest {
    public static void main(String[] a) throws Exception {
        byte[] apk = Files.readAllBytes(Paths.get(a[0]));
        byte[] manifest = null;
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new ByteArrayInputStream(apk))) {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals("AndroidManifest.xml")) {
                    manifest = ZipPatcher.readAll(zin);
                    break;
                }
            }
        }
        Map<String,String> rep = new HashMap<>();
        rep.put(a[2], a[3]);
        rep.put(a[4], a[5]);
        if (a.length >= 8) rep.put(a[6], a[7]);
        byte[] patched = AxmlPatcher.replaceStrings(manifest, rep);
        Map<String,byte[]> repl = new HashMap<>();
        repl.put("AndroidManifest.xml", patched);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipPatcher.rewrite(apk, repl, out);
        Files.write(Paths.get(a[1]), out.toByteArray());
        System.out.println("patched -> " + a[1] + " manifest " + manifest.length + " -> " + patched.length);
    }
}
