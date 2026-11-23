# Icon Workshop

**Icon Workshop** gives any installed Android app a second launcher entry with a
custom icon — without root, and without modifying the original app or its data.
The entry is a tiny APK generated on the device, signed locally, and installed
silently through [Shizuku](https://shizuku.rikka.app/) (the ADB shell identity).

The project is a personal, self-contained tool: no analytics, no network access,
no server component.

---

## What it does

- Lists all launchable apps on the device.
- Lets you pick any image (or a preset you add yourself) as the icon.
- Builds a minimal "launcher entry" APK on the device:
  - one activity that resolves the target package and starts it,
  - the chosen icon (legacy bitmaps + adaptive icon),
  - the target app's label,
  - a hidden, no-window activity (no flash, no animation),
  - excluded from Recents and marked `noHistory`.
- Signs the entry with a locally generated key.
- Installs it silently with `pm install` executed through Shizuku.
- Creates icons that open the real app; entries can be deleted from the app.

## Why not replace the existing icon?

Android does not allow a third-party app to change another app's icon in place.
The three possible routes are all blocked on modern Android:

1. **Runtime Resource Overlay (RRO)** — since Android 12, an overlay that is not
   signed with the target's certificate must name an `<overlayable>` declared by
   the target app. Most third-party apps declare none, so the overlay installs but
   stays `STATE_NO_IDMAP` and cannot be enabled.
2. **`pm hide`** — requires `android.permission.MANAGE_USERS`, which the ADB shell
   identity does not have.
3. **Editing the installed APK** — any modification invalidates the signature,
   so the original must be uninstalled and replaced. That loses app data, forces
   a re-login, and breaks apps that verify their signing certificate (many Google
   apps in particular).

A second launcher entry is the only non-destructive option. The original icon can
be removed from the home screen manually; the app itself stays in the drawer.

## Requirements

Runtime:

- Android 8.0+ (API 26)
- Shizuku 13.x running (wireless debugging or root), permission granted to Icon
  Workshop

Build:

- JDK 17 or newer
- Android SDK with **platform `android-35`** and **build-tools `35.0.0`**
- Python 3
- Bash (Git Bash works on Windows)

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk

./scripts/fetch_deps.sh      # Shizuku client jars from Maven Central
./scripts/make_keystore.sh   # local demo signing key -> app/assets/niji.p12
./scripts/build_entry.sh     # entry template -> app/assets/entry-template.apk
./scripts/build_app.sh       # -> out/IconWorkshop.apk
```

Optional preset icons: put images at `app/assets/presets/<name>.png` and map a
package name to that file in `PRESETS` inside
`app/src/com/niji/iconchanger/MainActivity.java`. Presets are not bundled with the
repository.

`scripts/make_keystore.sh` uses the demo password `iconworkshop`. It is not a
secret — the keystore is generated locally and never committed. If you change the
password, update `KS_PASS` in `EntryBuilder.java` as well.

## Usage

1. Install `out/IconWorkshop.apk`.
2. Start Shizuku and grant the permission when Icon Workshop asks for it.
3. Tap an app, then choose **Pick an image** or **Use built-in avatar**.
4. Wait for the toast; the new icon appears in the launcher drawer. Drag it to the
   home screen.
5. To remove an entry: tap the app in Icon Workshop → **Delete custom icon**, or
   uninstall the entry like a normal app.

## How it works

```
Icon Workshop (app)
 ├─ reads app/assets/entry-template.apk
 ├─ patches AndroidManifest.xml string pool (binary XML):
 │    template package -> com.niji.entry.n<sha1(target)[0..8]>
 │    placeholder target -> the selected package
 │    PLACEHOLDER_LABEL -> the app's label
 ├─ regenerates mipmap PNGs from the selected bitmap (per density,
 │    plus an adaptive-icon foreground layer)
 ├─ rewrites the APK zip (resources.arsc and PNGs stay uncompressed)
 ├─ signs it with apksig (v2 + v3)
 └─ Shizuku shell:
      cat > /data/local/tmp/niji_tmp.apk
      pm install -r /data/local/tmp/niji_tmp.apk
```

The entry activity is `com.niji.entry.LaunchActivity`. It reads the target package
from its own `niji_target_pkg` meta-data, calls
`getLaunchIntentForPackage()`, starts it with `NEW_TASK | NO_ANIMATION`, and
finishes immediately.

`AxmlPatcher` is a small binary-XML editor that rewrites string-pool entries only;
all other chunks stay byte-identical, so no aapt2 is needed on the device.
`ZipPatcher` rebuilds the APK while preserving per-entry compression.

## Project layout

```
app/          Icon Workshop itself (Java, no AndroidX)
  src/        MainActivity, ShizukuShell, EntryBuilder, IconGen,
              AxmlPatcher, ZipPatcher
  res/        layout + placeholder icons
entry/        launcher-entry template source (single activity)
scripts/      dependency fetch, keystore, entry build, app build, packaging
tools/pc/     optional desktop helper (binary manifest patch test)
libs/         Shizuku client jars (downloaded, git-ignored)
```

No AndroidX or Gradle is used; the build is plain `aapt2` + `javac` + `d8` +
`zipalign` + `apksigner`.

## Privacy

- No network permission, no telemetry, no analytics.
- No data leaves the device.
- The signing key is generated locally by `make_keystore.sh` and is never committed.
- Icon Workshop only calls Shizuku to run `pm install` / `pm uninstall` and to read
  package information.

## Limitations

- Entries are separate packages (`com.niji.entry.*`); they do not receive the
  original app's notifications, badges, or long-press shortcuts.
- The original app and its icon remain installed. Remove the original icon from the
  home screen if you want only the custom one.
- Entries are labeled with the target app's name so that the launcher looks right;
  only use this for apps you own or are allowed to customize.
- Tested on Android 16 (HyperOS 3). Behavior of the launcher drawer and of Shizuku
  may differ on other ROMs.

## License

MIT — see [LICENSE](LICENSE).

Third-party components:

- [Shizuku API](https://github.com/RikkaApps/Shizuku-API) — Apache License 2.0
- [apksig](https://android.googlesource.com/platform/tools/apksig/) — Apache
  License 2.0
