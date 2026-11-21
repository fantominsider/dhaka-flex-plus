# Dhaka Flex Plus

**Open-source Android media browser and LibVLC player** for phones, tablets, Android TV, and experimental XR viewing. Independent project; not affiliated with VideoLAN, Meta, PICO, XREAL, any ISP, or the third-party DhakaFlix application.

> **Release status: v0.6.1 source-only / GitHub migration release candidate. NOT built or device tested.** Do not distribute as a verified working APK until GitHub Actions/Android Studio builds and real-device playback checks pass.

## Features in source

- Four prefilled, editable factory RFC1918 server entries, plus additional user-added HTTP(S) servers for HTML directory browsing. No credentials or media files are bundled.
- Embedded LibVLC 3.7.7 for Android video/audio/streams (once successfully built); no separately installed VLC app required.
- VLC/Live TV hub, M3U IPTV imports, direct network stream URLs, local files with Android document picker.
- Favorites, Continue Watching, sorting, sleep timer, 2 MB JSON library backup/restore, quick server checks.
- Responsive layouts with phone/tablet, Android TV and best-effort VR headset profile detection, plus manual override.
- XREAL-host side-by-side output and experimental WebXR theater, VR180 and VR360 viewer. WebXR uses headset browser decoding, not native LibVLC.

## Build APK (GitHub Actions)

1. Push **the extracted contents** of this repository ZIP to the root of a **public** GitHub repository called `dhaka-flex-plus`. Do not commit the ZIP itself as the repository's only file.
2. Open **Actions** → **Android APK (debug)** → **Run workflow** (or push to `main`).
3. If the workflow passes, download `dhaka-flex-plus-debug-apk` from that run's **Artifacts**. The ZIP artifact contains `app-debug.apk`; install only for testing.
4. On a networked Android Studio machine, open the project root and build with Android SDK platform 36, JDK 17, Gradle 8.11.1 and Android Gradle Plugin 8.10.1. Internet is required for first Gradle/LibVLC dependency resolution.

**The build has not been executed successfully in the authoring environment.** CI may expose Android compile errors requiring fixes. A release-signed version requires a secure, persistent signing key, license verification, device testing and an appropriate delivery/update process.

## Factory server presets and editing (v0.6)

On first launch, four optional factory entries appear in the Server Manager:

| ID | Default address / content root |
|---|---|
| df7 | `http://172.16.50.7/DHAKA-FLIX-7/` |
| df10 | `http://172.16.50.10/DHAKA-FLIX-10/` |
| df12 | `http://172.16.50.12/DHAKA-FLIX-12/` |
| df14 | `http://172.16.50.14/DHAKA-FLIX-14/` |

These are **private-network addresses**, not publicly accessible Internet endpoints. The app **does not auto-connect** to the factory addresses. Access depends on the ISP/network and your authorization to view the content. Addresses are included in the public source at the user's explicit request; they are not secret credentials.

Each factory server card contains an editable **IP or base URL** field with **Restore Default IP** beside it, and below it **Save IP** and **Make Default**. Restore only fills the editor; Save IP persists the address; Make Default saves any pending edit and marks that server as the home-screen default. Entering just a host/IP optionally with a port preserves the existing folder prefix (`/DHAKA-FLIX-*/`), whereas entering a URL with a path replaces the full folder path. Choosing a default affects only the app's home-screen shortcut and tile ordering; it does not alter the factory IP that Restore Default IP uses.

Upgrading v0.5 installs the missing presets once **without overwriting saved custom addresses**. Manually deleted presets remain deleted until you tap **Re-add missing factory presets**. The default server selection is exported in and restored from the JSON library backup where possible. All 17 original category shortcuts use your saved editable URLs.

## Configure a media server

Use **Servers → Add** and enter your own authorized HTTP(S) directory listing URL (example `http://192.168.1.50:8080/Movies/`). Your network must be able to route to the host. This client does **not** universally browse SMB, Plex, Jellyfin or arbitrary web pages.

**Migration:** Existing `UniversalMediaBrowserXR` JSON backups (formats 1 and 2) can be imported into this application; import is explicit and includes sensitive personal stream URLs. For copyright and security reasons, the original third-party APK, its media and its audit report are **not** part of this repository. Four user-requested RFC1918 factory host templates are included as editable settings; no media is bundled.

## Known limitations

- Not all VLC Android features have custom UI equivalents; no Chromecast, EPG/DVR, SMB auto-discovery, PIP, full audio DSP, or automatic engine update. A newer embedded engine requires a new signed app build.
- WebXR is browser-based and unverified on PICO 4 Ultra and Quest; XREAL 3D output and Android TV D-pad behavior require hardware tests.
- HTTP streams are unencrypted; use servers and IPTV channels you are permitted to access.
- Playback codecs, network access, DRM and stream reliability depend on the device/server.

## Project and license

The application's original source in this repository is offered under the **Apache License, Version 2.0** (see [LICENSE](LICENSE)). It depends at build time on VideoLAN's separate **LibVLC** libraries, under their own LGPL and other dependency licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Check license/source/relinking obligations before distributing a combined APK.

Open issues and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## GitHub-only development

Source commits, build logs, CI artifacts and test APK prereleases are all managed in GitHub. Google Drive is optional and is not required for further development. See [GitHub-only workflow](docs/GITHUB_ONLY_WORKFLOW.md) and [first-device test plan](docs/RELEASE_TEST_PLAN.md). **This release is source-only until Actions succeeds.**

## Build a test APK without GitHub Actions

If GitHub Actions is disabled by a billing lock, you can compile in a free Google Colab CPU notebook instead:

[**Open Dhaka Flex Plus APK Builder in Google Colab**](https://colab.research.google.com/github/fantominsider/dhaka-flex-plus/blob/main/tools/DhakaFlexPlus_Colab_APK_Builder_v2.ipynb)

Sign into Colab, select **Runtime → Run all**, and use the browser download for `app-debug.apk`. The notebook clones `main`, checks source compatibility and installs Java 17, Android SDK API 36 and Gradle 8.11.1, attempts `:app:assembleDebug`, and downloads a diagnostic log if compilation fails. **This is an unverified test build, not a signed production release.** Colab free-tier capacity is not guaranteed.

### LibVLC 3.7.7 build compatibility (November 2026)

A Colab build log revealed that LibVLC 3.7.7 requires `compileSdk >= 36`, while the initial project used SDK 35. The build is now configured for `compileSdk 36`, Android Gradle Plugin 8.10.1, and Gradle 8.11.1. **The full Android build remains unverified until Colab completes successfully.** Reopen the latest Colab notebook (not an already-loaded old tab) and rerun all cells. If it fails again, share the newly downloaded diagnostic log.

> **Colab stale-notebook note:** If the build log says Gradle **8.7**, `compileSdk 35`, or Android Gradle Plugin **8.6.1**, the *old* builder ran. Use the **v2** Colab link above in a new tab and select **Runtime → Restart session**, followed by **Runtime → Run all**. The new failure log is named `DhakaFlexPlus-Colab-Build-v2.log`.
