# Общие настройки скриптов. Адрес проектора можно переопределить: PROJECTOR=ip:5555 tools/...
PKG="pro.icover.vitrina"
HOME_ALIAS="$PKG/.Home"
STOCK_HOME="com.master.t972/.MainActivity"
STOCK_PKG="com.master.t972"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
if [[ -z "${PROJECTOR:-}" && -f "$ROOT/.projector-address" ]]; then
    PROJECTOR="$(< "$ROOT/.projector-address")"
fi
: "${PROJECTOR:?Укажите PROJECTOR=адрес:порт или добавьте адрес в .projector-address}"
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
adbp() { adb -s "$PROJECTOR" "$@"; }
connect() { adb connect "$PROJECTOR" >/dev/null && adbp get-state >/dev/null; }
