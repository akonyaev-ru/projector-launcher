#!/usr/bin/env bash
# Вернуть родной домашний экран, «Витрина» остаётся обычным приложением. Полный откат с удалением — rollback.sh.
set -uo pipefail
source "$(dirname "$0")/env.sh"
connect || { echo "Нет связи с $PROJECTOR"; exit 1; }
adbp shell pm enable "$STOCK_PKG" >/dev/null 2>&1
adbp shell pm disable "$HOME_ALIAS" >/dev/null 2>&1
adbp shell cmd package set-home-activity "$STOCK_HOME"
adbp shell input keyevent KEYCODE_HOME
echo "Домашний экран: $(adbp shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
