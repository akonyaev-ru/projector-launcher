#!/usr/bin/env bash
# Сделать «Витрину» домашним экраном. Родной лаунчер не удаляется. Откат — tools/rollback.sh.
set -euo pipefail
source "$(dirname "$0")/env.sh"
connect
adbp shell pm enable "$HOME_ALIAS"
adbp shell cmd package set-home-activity "$HOME_ALIAS"
# Кнопку «Домой» в этой прошивке ловит прокладка com.jm.jmlauncher (категория HOME.JM) и открывает
# записанный у себя лаунчер. Родной отключаем (не удаляем) — прокладка находит «Витрину». Откат — home-off.sh.
adbp shell pm disable-user --user 0 "$STOCK_PKG"
adbp shell input keyevent KEYCODE_HOME
echo "Домашний экран: $(adbp shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
