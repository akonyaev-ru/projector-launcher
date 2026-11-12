#!/usr/bin/env bash
# Поиск, что грузит видеокарту: для каждого набора выключателей — перезапуск и одинаковый прогон пультом.
# Пример: tools/bisect.sh "" b s g e bsge
set -uo pipefail
source "$(dirname "$0")/env.sh"
connect
for off in "$@"; do
  adbp shell am start -S -n "$PKG/.MainActivity" --es off "x$off" >/dev/null
  sleep 4
  adbp shell input keyevent DPAD_RIGHT; adbp shell input keyevent DPAD_LEFT   # прогрев
  adbp shell dumpsys gfxinfo "$PKG" reset >/dev/null
  for k in DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_LEFT DPAD_LEFT DPAD_DOWN DPAD_LEFT DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_UP DPAD_UP DPAD_UP DPAD_UP; do
    adbp shell input keyevent "$k"
  done
  sleep 1
  echo "=== выключено: ${off:-ничего}"
  adbp shell dumpsys gfxinfo "$PKG" framestats | python3 -I "$ROOT/tools/framestats.py" | grep -E "кадров|GPU до|кадр целиком"
done
