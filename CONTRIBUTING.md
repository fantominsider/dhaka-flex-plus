# Contributing to Dhaka Flex Plus

Thanks for helping! Please open an issue before major architectural changes. Submit a pull request with a concise purpose, test steps and affected devices/Android API levels.

## Build and test

- Java 17, Android SDK platform 36 (compileSdk), Android Gradle Plugin 8.10.1, Gradle 8.11.1. The app retains minSdk 23 and targetSdk 32.
- Build: `gradle --no-daemon :app:assembleDebug` (or Android Studio Build APK).
- Pure-Java IPTV parser tests: `javac -d /tmp/dfplus-tests app/src/main/java/org/dhakaflexplus/player/IptvParser.java tests/TestIptv.java tests/TestIptvExtra.java && java -cp /tmp/dfplus-tests TestIptv && java -cp /tmp/dfplus-tests TestIptvExtra`.
- Report real-device results for Android phone/tablet, TV, headset or XREAL host when relevant.

Do **not** submit third-party copyrighted video, original DhakaFlix APK/decompiled code, unlicensed logos, any additional/undocumented private ISP server addresses, passwords, IPTV subscription URLs or signed keystores.

All new original contributions should be compatible with the project's Apache-2.0 license; note third-party component licenses separately.
