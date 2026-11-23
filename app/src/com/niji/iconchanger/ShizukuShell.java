package com.niji.iconchanger;

import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

/** Runs shell commands (and silent package installs) through Shizuku's ADB identity. */
public class ShizukuShell {

    public static class Result {
        public final int code;
        public final String output;
        Result(int code, String output) { this.code = code; this.output = output; }
        public boolean ok() { return code == 0; }
    }

    public static boolean available() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public static boolean granted() {
        try { return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }

    public static void requestPermission() { Shizuku.requestPermission(0); }

    public static Result run(String script, byte[] stdin) throws Exception {
        IShizukuService service = IShizukuService.Stub.asInterface(
                new ShizukuBinderWrapper(Shizuku.getBinder()));
        IRemoteProcess rp = service.newProcess(new String[]{"sh", "-c", script}, null, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread t1 = drain(new ParcelFileDescriptor.AutoCloseInputStream(rp.getInputStream()), out);
        Thread t2 = drain(new ParcelFileDescriptor.AutoCloseInputStream(rp.getErrorStream()), out);
        t1.start(); t2.start();

        OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(rp.getOutputStream());
        if (stdin != null) os.write(stdin);
        os.close();

        int code = rp.waitFor();
        t1.join(5000); t2.join(5000);
        return new Result(code, out.toString("UTF-8"));
    }

    private static Thread drain(final java.io.InputStream in, final ByteArrayOutputStream out) {
        return new Thread(() -> {
            try {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    synchronized (out) { out.write(buf, 0, n); }
                }
            } catch (Exception ignored) { }
        });
    }

    /** Silent install from stdin (the shell copies it to /data/local/tmp, which pm can read). */
    public static Result install(byte[] apk) throws Exception {
        return run("cat > /data/local/tmp/niji_tmp.apk && pm install -r /data/local/tmp/niji_tmp.apk; "
                + "rc=$?; rm -f /data/local/tmp/niji_tmp.apk; exit $rc", apk);
    }

    public static Result uninstall(String pkg) throws Exception {
        return run("pm uninstall " + pkg, null);
    }
}
