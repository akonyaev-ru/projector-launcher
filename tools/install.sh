#!/usr/bin/env bash
# Собрать, прогнать тесты и поставить «Витрину» обычным приложением. Родной экран не трогает.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ROOT"
./gradlew -q :app:testDebugUnitTest :app:assembleRelease
connect
adbp install -r "$APK"
adbp shell am start -n "$PKG/.MainActivity"
echo "Поставлено и запущено на $PROJECTOR"
