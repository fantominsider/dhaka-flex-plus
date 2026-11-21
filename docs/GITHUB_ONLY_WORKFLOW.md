# GitHub-only development and APK distribution

GitHub is the source of truth. **Google Drive is not needed** for source files or APK releases.

## Ownership and onboarding

1. Create the **public** repository `fantominsider/dhaka-flex-plus` with an initial README (or empty repository).
2. In ChatGPT > Plugins > GitHub, authorize repository access for the repository. A linked GitHub user profile alone does not grant repository-content access. Verify that the repository is visible to the connected GitHub app.
3. Publish all project files from this ZIP as **individual repository files** (including hidden `.github/workflows/android-debug.yml`, `.gitignore`), not merely as an uploaded ZIP. If the repository already contains a README, overwrite/replace its contents with the repository's `README.md`.
4. Push to `main`. **Actions > Build Android APK** should run automatically. An Actions **success** result is required before claiming a working build.

## Obtaining an APK

- Test builds: open the successful **Actions** run, download `dhaka-flex-plus-debug-apk`, extract `DhakaFlexPlus-debug.apk` and `SHA256SUMS.txt`.
- Long-lived test release: create a tag, e.g. `v0.6.1-rc.1`, for the passing `main` commit. A tag push triggers the build; after a successful build, the workflow publishes a **GitHub prerelease** with a directly downloadable **debug/test APK** and checksum.
- Do not advertise a debug APK as a production-signed app. For production updates, configure one persistent signing key, verify licenses and perform on-device tests. Do not commit keystores or credentials.

## Subsequent development

- Changes: commit to feature branches; open a pull request; require successful Android build and core tests.
- Planned work: GitHub Issues (reproducible steps) / Discussions for feature proposals.
- Release versions: use `vMAJOR.MINOR.PATCH` tags; attach signed production APK in GitHub Releases once actually built and verified.
- Backups: Git remote history is versioned, but keep separate copies of signing keys. For sensitive saved user server configurations, never upload backups publicly.

## Known gates

The source has been locally syntax checked and tested without Android dependencies. It **has not been fully built** here, because the authoring environment has no Android SDK / Gradle / LibVLC download access. The GitHub CI workflow is designed to perform that first real build and may expose defects requiring corrections.
