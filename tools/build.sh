#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v gradle >/dev/null; then
  echo 'Falta Gradle 8.9. Instálalo o utiliza el workflow de GitHub Actions incluido.' >&2
  exit 2
fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" && ! -f local.properties ]]; then
  echo 'Configura ANDROID_HOME o sdk.dir en local.properties (Android SDK 35).' >&2
  exit 2
fi
gradle :core:test :app:lintDebug :app:assembleDebug --stacktrace
