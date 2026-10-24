#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd "$(dirname "$0")/../.." && pwd)
sdk_dir=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/srbenjamin/Android/Sdk}}
smoke_dir=${1:-/tmp/squareds-visual-smoke}
mkdir -p "$smoke_dir/classes" "$smoke_dir/dex"
platform_jar="$sdk_dir/platforms/android-37.0/android.jar"
build_tools="$sdk_dir/build-tools/36.0.0"
javac -source 8 -target 8 -classpath "$platform_jar" -d "$smoke_dir/classes" "$repo_dir"/tools/local-visuals-smoke/*.java
"$build_tools/d8" --lib "$platform_jar" --min-api 26 --output "$smoke_dir/dex" "$smoke_dir"/classes/dev/lelonio/square/visualsmoke/*.class
"$build_tools/aapt2" link -I "$platform_jar" --manifest "$repo_dir/tools/local-visuals-smoke/AndroidManifest.xml" -o "$smoke_dir/unsigned.apk"
python3 - "$smoke_dir" <<'PY'
from pathlib import Path
import sys, zipfile
folder = Path(sys.argv[1])
with zipfile.ZipFile(folder / 'unsigned.apk', 'a') as apk:
    apk.write(folder / 'dex/classes.dex', 'classes.dex')
PY
# Standard debug key, used only for this disposable test APK.
"$build_tools/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$smoke_dir/smoke.apk" "$smoke_dir/unsigned.apk"
printf '%s\n' "$smoke_dir/smoke.apk"
