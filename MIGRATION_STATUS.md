# GitHub migration status — 2026-11-21

| Workstream | Status |
| --- | --- |
| Open-source project name/package/licensing | Prepared in source |
| IP presets: restore, save, choose default | Prepared in source / local core tests passed |
| Public GitHub repository `fantominsider/dhaka-flex-plus` | **BLOCKED — repository does not exist under connected account** |
| GitHub App repository installation/access | **BLOCKED — no installations reported** |
| Android APK compilation | **BLOCKED — local Android SDK/Gradle/LibVLC unavailable and no repository exists for CI** |
| APK download in GitHub Release | **BLOCKED — depends on successful CI build and repository authorization** |
| GitHub-only CI/release workflow | Prepared, not yet exercised |

**User action:** create the public repository at https://github.com/new using `dhaka-flex-plus`, then authorize the connected ChatGPT GitHub app for that repository. This action is required because the active GitHub connector exposes repository file operations but no `create_repository` capability. Do not mistake this status document or a downloaded ZIP for a compiled APK.
