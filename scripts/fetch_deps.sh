#!/usr/bin/env bash
# Downloads the Shizuku client libraries from Maven Central and extracts
# their classes.jar into libs/. Requires curl and unzip.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=13.1.5
BASE="https://repo1.maven.org/maven2/dev/rikka/shizuku"
mkdir -p libs .cache

fetch() {
  local name="$1"
  local file=".cache/$name-$VERSION.aar"
  if [ ! -f "$file" ]; then
    echo "downloading $name $VERSION"
    curl -fsSL "$BASE/$name/$VERSION/$name-$VERSION.aar" -o "$file"
  fi
  rm -rf ".cache/$name"
  mkdir -p ".cache/$name"
  unzip -q -o "$file" classes.jar -d ".cache/$name"
  cp ".cache/$name/classes.jar" "libs/shizuku-$name.jar"
}

fetch api
fetch provider
fetch aidl
fetch shared

echo "Shizuku libraries are in libs/."
echo "apksig is taken from \$ANDROID_HOME/build-tools/35.0.0/lib/apksigner.jar at build time."
