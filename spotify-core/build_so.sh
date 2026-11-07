#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -z "$ANDROID_NDK_HOME" ]; then
    if [ -n "$NDK_HOME" ]; then
        ANDROID_NDK_HOME="$NDK_HOME"
    elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/ndk" ]; then
        ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | tail -n 1)"
    elif [ -d "$HOME/Android/Sdk/ndk" ]; then
        ANDROID_NDK_HOME="$(ls -d "$HOME/Android/Sdk/ndk/"* 2>/dev/null | tail -n 1)"
    fi
fi
export ANDROID_NDK_HOME
export NDK_HOME="$ANDROID_NDK_HOME"

cd "$SCRIPT_DIR"
cargo ndk -t arm64-v8a -P 26 -o "$SCRIPT_DIR/../android-app/app/src/main/jniLibs" build --release --no-default-features --features capi

STRIP_TOOL="$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -name "llvm-strip" 2>/dev/null | head -n 1)"
if [ -n "$STRIP_TOOL" ] && [ -x "$STRIP_TOOL" ]; then
    "$STRIP_TOOL" --strip-unneeded "$SCRIPT_DIR/../android-app/app/src/main/jniLibs/arm64-v8a/libspotify_core.so" 2>/dev/null || true
fi
