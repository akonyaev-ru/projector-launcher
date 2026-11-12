#!/usr/bin/env bash
# Вернуть родной домашний экран и удалить «Витрину». Безопасно запускать повторно.
set -uo pipefail
source "$(dirname "$0")/env.sh"
connect || { echo "Нет связи с $PROJECTOR"; exit 1; }
adbp shell pm enable "$STOCK_PKG" >/dev/null 2>&1
adbp shell cmd package set-home-activity "$STOCK_HOME"
adbp uninstall "$PKG" >/dev/null 2>&1 || true
adbp shell input keyevent KEYCODE_HOME
echo "Домашний экран: $(adbp shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
