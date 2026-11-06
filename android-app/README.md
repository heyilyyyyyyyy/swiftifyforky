# Swiftify Android

Android client for Swiftify, utilizing Material 3 Expressive UI and the native Rust `spotify-core` engine.

## Prerequisites
- Android SDK (API 34+)
- Android NDK (r26d+)
- Rust toolchain (`cargo ndk`)
- Java 17

## Building Native Core
To build `libspotify_core.so`:
```bash
cd ../spotify-core
./build_so.sh
```

## Building Android App
```bash
./gradlew assembleDebug
```
