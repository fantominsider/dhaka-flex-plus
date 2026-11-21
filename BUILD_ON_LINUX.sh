#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Missing Gradle 8.11.1. Use GitHub Actions or install Gradle 8.11.1 + Android SDK 36.' >&2
  exit 2
fi
gradle --no-daemon :app:assembleDebug
echo "Debug APK: $PWD/app/build/outputs/apk/debug/app-debug.apk"
