#!/usr/bin/env bash
# Собирает ядро APEX CLIENT (sing-box libbox + парсер подписки apexcore) в app/libs/libbox.aar
#
# Требуется: Go 1.25+, JDK 17, Android SDK (ANDROID_HOME) и NDK 28 (ANDROID_NDK_HOME).
# Запуск из корня проекта:  ./core/build-core.sh
set -euo pipefail

SING_BOX_VERSION="${SING_BOX_VERSION:-v1.13.21}"
GOMOBILE_VERSION="v0.1.12"
BUILD_TAGS="with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,badlinkname,tfogo_checklinkname0"
# NaiveProxy (Chromium cronet) — как в официальном клиенте sing-box. NAIVE=0 — собрать без него
NAIVE="${NAIVE:-1}"
TARGETS="${TARGETS:-android/arm64,android/arm}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/core/.build"
OUT="$ROOT/app/libs/libbox.aar"

: "${ANDROID_HOME:?Укажите ANDROID_HOME (путь к Android SDK)}"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/28.* 2>/dev/null | sort -V | tail -1 || true)"
fi
: "${ANDROID_NDK_HOME:?NDK 28 не найден: установите 'ndk;28.0.13004108' через sdkmanager}"
export ANDROID_HOME ANDROID_NDK_HOME

echo "==> sing-box $SING_BOX_VERSION"
rm -rf "$WORK"
git clone --quiet --depth 1 --branch "$SING_BOX_VERSION" https://github.com/SagerNet/sing-box "$WORK"
cp -r "$ROOT/core/apexcore" "$WORK/experimental/apexcore"

echo "==> gomobile $GOMOBILE_VERSION"
go install "github.com/sagernet/gomobile/cmd/gomobile@$GOMOBILE_VERSION"
go install "github.com/sagernet/gomobile/cmd/gobind@$GOMOBILE_VERSION"
export PATH="$(go env GOPATH)/bin:$PATH"

cd "$WORK"
echo "==> тесты парсера подписки"
APEX_RULES_DIR="$ROOT/app/src/main/assets/rules" go test -tags "$BUILD_TAGS" -ldflags "-checklinkname=0" ./experimental/apexcore/

# Конфиги Xray, которые собирает apexcore, проверяем настоящим Xray (если он скачан)
if [[ -x "$ROOT/core/.xray-host/xray" ]]; then
  echo "==> проверка конфигов Xray"
  DUMP="$(mktemp -d)"
  APEX_XRAY_DUMP="$DUMP" APEX_RULES_DIR="$ROOT/app/src/main/assets/rules" go test -tags "$BUILD_TAGS" -ldflags "-checklinkname=0" -run XrayDump ./experimental/apexcore/
  BAD=0
  for f in "$DUMP"/*.json; do
    if ! XRAY_OUT=$("$ROOT/core/.xray-host/xray" run -test -c "$f" 2>&1); then
      BAD=1
      echo "::error title=Xray $(basename "$f")::$(echo "$XRAY_OUT" | tail -3 | tr '\n' ' ' | cut -c1-600)"
    fi
  done
  [[ "$BAD" == 0 ]] || { echo "Xray отклонил конфиги"; exit 1; }
  echo "==> все конфиги Xray приняты"
fi

bind() {
  gomobile bind -v \
    -target "$TARGETS" \
    -androidapi 23 \
    -javapkg=io.nekohasekai \
    -libname=box \
    -trimpath -buildvcs=false \
    -ldflags "-X github.com/sagernet/sing-box/constant.Version=${SING_BOX_VERSION#v} -X internal/godebug.defaultGODEBUG=multipathtcp=0 -s -w -buildid= -checklinkname=0" \
    -tags "$1" \
    -o libbox.aar \
    ./experimental/libbox ./experimental/apexcore
}

if [[ "$NAIVE" == "1" ]]; then
  echo "==> проверка конфига NaiveProxy"
  APEX_RULES_DIR="$ROOT/app/src/main/assets/rules" go test -tags "$BUILD_TAGS,with_naive_outbound" -ldflags "-checklinkname=0" -run Naive ./experimental/apexcore/ \
    || echo "::warning title=NaiveProxy::тест конфига NaiveProxy не прошёл на сборочной машине"
  echo "==> gomobile bind ($TARGETS) с NaiveProxy"
  if ! bind "$BUILD_TAGS,with_naive_outbound" 2> >(tee "$WORK/bind.err" >&2); then
    echo "::warning title=NaiveProxy bind::$(grep -iE 'error|undefined|cannot' "$WORK/bind.err" | head -5 | tr '\n' ' ' | cut -c1-800)"
    echo "::warning title=NaiveProxy::ядро с NaiveProxy не собралось — собираю без него"
    bind "$BUILD_TAGS"
  fi
else
  echo "==> gomobile bind ($TARGETS)"
  bind "$BUILD_TAGS"
fi

mkdir -p "$(dirname "$OUT")"
cp libbox.aar "$OUT"
echo "==> готово: $OUT ($(du -h "$OUT" | cut -f1))"
echo "::notice title=Ядро::libbox.aar $(du -h "$OUT" | cut -f1), $(unzip -l "$OUT" | grep -c 'libbox.so') ABI"
