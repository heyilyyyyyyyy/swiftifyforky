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
cargo ndk -t arm64-v8a check --no-default-features --features capi
