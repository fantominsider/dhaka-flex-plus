# Dhaka Flex Plus v0.6.1 debug build verification

Date: 2026-11-22 (Asia/Dhaka). Debug APKs compiled locally; device qualification remains pending.

## Results

- Universal `:app:assembleDebug :app:lintDebug`: passed.
- ARM64 `-PdhakaAbi=arm64-v8a :app:assembleDebug`: passed.
- Existing standalone suites: 45 assertions passed, 0 failed.
- Android lint: 0 errors, 15 warnings. Warnings cover existing backup metadata, icons, localization, layout direction and redundant API checks.
- APK signatures: verified with Android build-tools; v1 and v2 present.
- APK ZIP integrity, 16 KiB ZIP native-library alignment, launcher manifest and packaged VLC libraries: verified. ZIP alignment alone does not establish native ELF or device compatibility.
- Android minimum: API 23 / Android 6.0. Existing targetSdk 32 retained for sideloaded testing; the Google Play targeting check is configured as warning-only. This does not establish Play eligibility.

## Toolchain

JDK 17.0.21.1+2, Gradle 8.11.1, Android Gradle Plugin 8.10.1, compileSdk 36 and Android build-tools 35.0.0. Embedded dependency: `org.videolan.android:libvlc-all:3.7.7`.

## Compatibility fix and build options

The XR relay used `URLConnection.getContentLengthLong()`, introduced in API 24, despite supporting API 23. It now parses the HTTP Content-Length header as a long, preserving large video lengths and unknown-length transfers on Android 6.

A universal APK remains the default. Pass `-PdhakaAbi=arm64-v8a` for an ARM64-only APK; supported overrides are arm64-v8a, armeabi-v7a, x86 and x86_64.

## APK records

| APK | Bytes | ABIs | SHA-256 |
| --- | ---: | --- | --- |
| DhakaFlexPlus_v0.6.1-arm64-debug.apk | 57185190 | arm64-v8a | `437baf909116284244a6b564de5f61b13514771391e6a5a71e62547e86749722` |
| DhakaFlexPlus_v0.6.1-universal-debug.apk | 212125739 | arm64-v8a, armeabi-v7a, x86, x86_64 | `2494c7de3102a15c645a60f84f91c790401ad9894281cd8b744522e6dbd3c4f7` |

Both APKs use the same debug test certificate. The test keystore is retained privately for future test updates and is not included in the source repository.

## Source provenance

Base: `39240adef5fe9639d4018370518ca431c7c11ec4`, plus the compatibility and build configuration changes in this branch.

## Remaining verification

No Android device or emulator was available for execution. Installation, playback, phone/TV controls, PICO/Quest/XREAL behavior, backup UI and authorized ISP access require real-device testing. The release qualification checklist remains open.
