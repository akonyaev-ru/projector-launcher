#!/usr/bin/env bash
# Замер на проекторе: прогон фокуса пультом, затем кадры (gfxinfo) и память (meminfo).
set -euo pipefail
source "$(dirname "$0")/env.sh"
connect
adbp shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 3
# прогрев: один проход до замера, чтобы JIT успел скомпилировать горячий код
adbp shell input keyevent DPAD_DOWN; adbp shell input keyevent DPAD_DOWN; adbp shell input keyevent BACK; adbp shell input keyevent DPAD_UP
sleep 1
adbp shell dumpsys gfxinfo "$PKG" reset >/dev/null
keys() { for k in "$@"; do adbp shell input keyevent "$k"; done; }
# «Ряд кругов»: док вправо-влево, вниз на круг, по ряду ярлыков, лист: открыть, вниз по рядам, назад к кругу и в док.
keys DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_LEFT DPAD_LEFT DPAD_DOWN DPAD_LEFT DPAD_LEFT \
     DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP
sleep 1
adbp shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th"
adbp shell dumpsys meminfo "$PKG" | grep -E "TOTAL PSS|TOTAL:" | head -2
