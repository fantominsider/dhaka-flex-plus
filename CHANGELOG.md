# Changelog

## 0.6.1 — GitHub-only delivery preparation

- Added automated debug APK and SHA256 artifact build on pushes, pull requests and manual runs.
- Added tag-triggered prerelease publication of debug-only APKs through GitHub Releases.
- Added GitHub-only project workflow and first-device validation checklist.
- Preserved v0.6 preset IP defaults and editable server controls.
- Source only: Android build and installation are not yet verified.


## 0.6.0 — editable factory IP presets (2026-11-21)

- Added four user-requested original private-network host presets (7, 10, 12, 14) and preserved their `/DHAKA-FLIX-*` content-root paths.
- Added in-place **Restore Default IP**, **Save IP** and **Make Default** controls per preset; restore requires Save to persist.
- Added one-time migration for existing installs without overwriting edits, plus explicit action to re-add deliberately removed factory entries.
- Added default-server shortcut and prioritized tile on the home screen.
- Included selected default-server ID in portable backup format 2 without breaking older backups.
- Added standalone preset validation and URL/path-retention tests; adapted GitHub Actions to allow expected catalog IPs only.
- **No Android APK built or on-device tested.**

## 0.5.0 — open-source preparation (2026-11-21)

- Rebranded to **Dhaka Flex Plus** with unique Android namespace `org.dhakaflexplus.player`.
- Removed hardcoded private ISP addresses from default first-run settings.
- Added Apache-2.0 licensing, third-party notices, README, contribution/security documents and CI build workflow.
- Preserved import of prior JSON backup formats via migration alias.
- Updated original generic TV banner to a project-branded banner.
- Updated GitHub Actions to run standalone playlist parsing tests before compiling the debug APK.
- **No new APK was built or hardware-tested.**

## 0.4.0 — original source feature base

- Embedded LibVLC dependency, IPTV hub, M3U imports, stream playback UI, version checks, prior XR and server library features.

## 0.3.0, 0.2.0, 0.1.0 — preceding prototypes

- History/bookmarks/backup, WebXR, server manager and HTTP media browsing, respectively.
