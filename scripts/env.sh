#!/usr/bin/env bash
# Common build environment. Source this file from the repository root.
set -euo pipefail

: "${ANDROID_HOME:?Please export ANDROID_HOME to your Android SDK directory}"
: "${JAVA_HOME:?Please export JAVA_HOME to a JDK 17+ installation}"

BT="$ANDROID_HOME/build-tools/35.0.0"
AJ="$ANDROID_HOME/platforms/android-35/android.jar"
LIBS="$(pwd)/libs"

if [ ! -x "$BT/aapt2" ] && [ ! -f "$BT/aapt2.exe" ]; then
  echo "aapt2 not found in $BT" >&2
  exit 1
fi
if [ ! -f "$AJ" ]; then
  echo "android.jar not found at $AJ" >&2
  exit 1
fi

# Windows (Git Bash) uses ';' in the javac classpath, everything else ':'
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac

# Prefer a real Python 3 (the Windows Store "python3" stub is not usable)
find_python() {
  for c in python3 python; do
    if command -v "$c" >/dev/null 2>&1 &&        "$c" -c 'import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)' >/dev/null 2>&1; then
      echo "$c"; return 0
    fi
  done
  return 1
}
PYTHON=$(find_python) || { echo "Python 3 is required" >&2; exit 1; }
export PYTHON

# d8 / apksigner launchers
D8="$BT/d8"
[ -f "$D8.bat" ] && D8="$D8.bat"
APKSIGNER="$BT/apksigner"
[ -f "$APKSIGNER.bat" ] && APKSIGNER="$APKSIGNER.bat"
ZIPALIGN="$BT/zipalign"

export ANDROID_HOME JAVA_HOME BT AJ LIBS SEP D8 APKSIGNER ZIPALIGN PYTHON
