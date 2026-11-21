# Dhaka Flex Plus v0.6.0 — source QA and release status

Date: 2026-11-21. Source release only.

## Verified in local development environment

- Confirmed original reference APK static-inspection record of four IPs and 17 content roots (one prefix per host, with 4 + 4 + 1 + 8 subcategories).
- Added four factory presets, with host values from the prior static report and original per-server base paths.
- **32 / 32** standalone preset-catalog and address-editing assertions passed (`tests/TestServerPresets.java`). Test cases include factory URL lookup, unique IDs, host-only changes preserving paths, full URL overrides, port/scheme changes, and rejecting unsafe input.
- Existing IPTV parser tests: **13 / 13** assertions passed.
- **16 Java files** successfully syntax-parsed by JDK 17 (`JavacTask.parse()`), with **zero syntax errors**.
- **9 core Java classes** (plus Android/JSON stubs) type-compiled successfully. This does **not** prove real Android SDK or LibVLC compilation.
- Android XML parsed without errors.
- Public-source check: factory host IPs appear only in `DefaultServerCatalog.java` under `app/src/main`. The original third-party APK is not included.
- App `versionCode=6`, `versionName=0.6.0`; CI artifact name updated to v0.6.

## Implemented behaviors (code reviewed; Android UI not executed)

- First launch seeds the four factory entries; one-time migration to v0.6 adds missing entries without overwriting any edited existing server by ID.
- The Restore Default IP button fills a field but does not save until Save IP or Make Default is pressed.
- Save IP persists the validated HTTP(S) address. A host/IP without a path keeps the previous folder path.
- Make Default saves the field and chooses the selected server for the home screen shortcut and first tile.
- A server deleted after initial migration does not silently reappear; explicit Re-add missing factory presets restores deleted factory entries.
- The selected default server ID is included in compatible JSON backup format 2; old format 1/2 backups without the field remain accepted.
- Factory presets are data only: the app does not probe/auto-connect until the user chooses a browse/test action.

## Not verified

- **No Android APK was compiled here.** Android Gradle, SDK and embedded LibVLC dependencies remain unavailable in the local authoring environment.
- No phone, TV, PICO, Quest or XREAL hardware tests; no live private-network connection verification.
- Android widgets, migrations, and backup UI need on-device/instrumented tests. Original codebase v0.5 was also source-only.
- No public GitHub repository was found under the connected GitHub account at the time of checking. This version is archived in Google Drive, not yet published to GitHub.

## Release gate

1. Create repository `fantominsider/dhaka-flex-plus` (Public) under the connected GitHub account.
2. Upload the **extracted** source tree, not only the ZIP file. Run GitHub Actions debug build and resolve any Gradle compile issues.
3. Validate all controls and backups on Android; test server paths only on an authorized ISP/network.
4. Publish a separately verified, signed release APK, respecting LibVLC license obligations.
