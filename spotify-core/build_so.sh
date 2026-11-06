#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${NDK_HOME:-/home/heyily/Android/Sdk/ndk/26.3.11579264}}"
export NDK_HOME="$ANDROID_NDK_HOME"
cd "$SCRIPT_DIR"
cargo ndk -t arm64-v8a -P 26 -o "$SCRIPT_DIR/../android-app/app/src/main/jniLibs" build --release --no-default-features --features capi
