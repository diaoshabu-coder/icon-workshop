#!/usr/bin/env bash
# Creates the local signing key bundled into the app (app/assets/niji.p12).
# The password below is a demo value, not a secret. If you change it, update
# KS_PASS in app/src/com/niji/iconchanger/EntryBuilder.java as well.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Please export JAVA_HOME}"

KS_PASS="${KS_PASS:-iconworkshop}"
mkdir -p app/assets
if [ -f app/assets/niji.p12 ]; then
  echo "app/assets/niji.p12 already exists, leaving it untouched."
  exit 0
fi
"$JAVA_HOME/bin/keytool" -genkeypair \
  -keystore app/assets/niji.p12 \
  -storetype PKCS12 \
  -alias niji \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$KS_PASS" \
  -keypass "$KS_PASS" \
  -dname "CN=Icon Workshop, O=Icon Workshop, C=US"
echo "Created app/assets/niji.p12 (password: $KS_PASS)."
