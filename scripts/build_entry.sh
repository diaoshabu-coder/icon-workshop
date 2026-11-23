#!/usr/bin/env bash
# Builds the tiny launcher-entry template and copies it into app/assets/.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/env.sh

rm -rf build/entry
mkdir -p build/entry/classes build/entry/dex app/assets

"$BT/aapt2" compile --dir entry/res -o build/entry/compiled.zip
"$BT/aapt2" link -o build/entry/base.apk \
  --manifest entry/AndroidManifest.xml \
  -I "$AJ" --min-sdk-version 26 --target-sdk-version 35 \
  build/entry/compiled.zip

"$JAVA_HOME/bin/javac" --release 11 -cp "$AJ" \
  -d build/entry/classes entry/src/com/niji/entry/LaunchActivity.java

"$D8" --lib "$AJ" --min-api 26 --output build/entry/dex \
  build/entry/classes/com/niji/entry/LaunchActivity.class

"$PYTHON" - <<'PY'
import os, shutil, zipfile
shutil.copy("build/entry/base.apk", "build/entry/template.apk")
with zipfile.ZipFile("build/entry/template.apk", "a", zipfile.ZIP_DEFLATED) as z:
    z.write("build/entry/dex/classes.dex", "classes.dex")
os.makedirs("app/assets", exist_ok=True)
shutil.copy("build/entry/template.apk", "app/assets/entry-template.apk")
print("entry template -> app/assets/entry-template.apk")
PY
