#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0"

cd "$ROOT"
npx cap sync android

cat > "$ROOT/android/local.properties" <<EOF
sdk.dir=$ANDROID_HOME
EOF

cd "$ROOT/android"
chmod +x ./gradlew
./gradlew assembleDebug --no-daemon

OUT="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
mkdir -p "$ROOT/dist"
cp -f "$OUT" "$ROOT/dist/ApplKlava-debug.apk"
echo "APK ready: $ROOT/dist/ApplKlava-debug.apk"
ls -lh "$ROOT/dist/ApplKlava-debug.apk"
