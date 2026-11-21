# Third-party notices

## LibVLC / VideoLAN

This Android project declares `org.videolan.android:libvlc-all:3.7.7` in `app/build.gradle`. Gradle fetches this dependency when building; the source repository does not include the LibVLC native AAR or VLC app. VideoLAN provides LibVLC under LGPL terms. Check the exact notices, bundled components, license texts and distribution/relinking/source obligations of the specific version before shipping an APK.

- Library: https://www.videolan.org/vlc/libvlc.html
- Upstream source: https://code.videolan.org/videolan/vlc-android
- Maven: https://central.sonatype.com/artifact/org.videolan.android/libvlc-all/versions

The project is independent and not endorsed by VideoLAN.

## Android / Gradle build tooling

Android Gradle Plugin, the Android SDK, and other toolchains are downloaded by a builder and retain their respective licenses. No SDK or VLC binary is redistributed in the source archive.

## Media and server URLs

No movies, IPTV subscriptions, login credentials, or original third-party APK are distributed in public source. Four non-secret RFC1918 factory IP address templates are included at the project owner's request and can be edited before use. Users are responsible for obtaining permission to access the media sources.
