#!/usr/bin/env bash
# Builds and signs out/IconWorkshop.apk.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/env.sh

for f in libs/shizuku-api.jar libs/shizuku-provider.jar libs/shizuku-aidl.jar libs/shizuku-shared.jar; do
  [ -f "$f" ] || { echo "Missing $f — run scripts/fetch_deps.sh first." >&2; exit 1; }
done
[ -f app/assets/entry-template.apk ] || { echo "Run scripts/build_entry.sh first." >&2; exit 1; }
[ -f app/assets/niji.p12 ] || { echo "Run scripts/make_keystore.sh first." >&2; exit 1; }

rm -rf build/gen build/classes build/dex
mkdir -p build/gen build/classes build/dex out

"$BT/aapt2" compile --dir app/res -o build/compiled.zip
"$BT/aapt2" link -o build/base.apk \
  --manifest app/AndroidManifest.xml \
  -I "$AJ" --java build/gen \
  --min-sdk-version 26 --target-sdk-version 35 \
  build/compiled.zip

CP="$AJ${SEP}libs/shizuku-api.jar${SEP}libs/shizuku-provider.jar${SEP}libs/shizuku-aidl.jar${SEP}libs/shizuku-shared.jar${SEP}$BT/lib/apksigner.jar"
"$JAVA_HOME/bin/javac" --release 11 -encoding UTF-8 -cp "$CP" -d build/classes \
  $(find app/src build/gen -name "*.java")

"$PYTHON" scripts/app_pack.py jar
"$D8" --lib "$AJ" --min-api 26 --output build/dex \
  build/classes.jar \
  libs/shizuku-api.jar libs/shizuku-provider.jar \
  libs/shizuku-aidl.jar libs/shizuku-shared.jar \
  "$BT/lib/apksigner.jar"

"$PYTHON" scripts/app_pack.py pack
"$ZIPALIGN" -p -f 4 build/app-unsigned.apk build/app-aligned.apk

KS_PASS="${KS_PASS:-iconworkshop}"
"$APKSIGNER" sign --ks app/assets/niji.p12 --ks-type PKCS12 \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out out/IconWorkshop.apk build/app-aligned.apk

echo
echo "built: out/IconWorkshop.apk"
"$BT/aapt2" dump badging out/IconWorkshop.apk 2>/dev/null | grep -E "^package:" || true
