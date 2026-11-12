#!/usr/bin/env bash
# Создаёт ключ подписи APEX CLIENT. Делается ОДИН раз — все обновления
# должны быть подписаны этим же ключом, иначе Android не установит их поверх.
# Храните apex-release.jks и пароли в надёжном месте!
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JKS="$ROOT/apex-release.jks"
ALIAS="apex"

if [[ -f "$JKS" ]]; then
  echo "Ключ уже существует: $JKS"; exit 1
fi

read -rsp "Придумайте пароль ключа (мин. 6 символов): " PASS; echo
keytool -genkeypair -v -keystore "$JKS" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=APEX VPN, O=APEX VPN"

cat > "$ROOT/keystore.properties" <<EOF
storeFile=$JKS
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
EOF

echo
echo "Готово. Для GitHub Actions добавьте секреты (Settings → Secrets → Actions):"
echo "  APEX_KEYSTORE_BASE64  = вывод команды: base64 -w0 apex-release.jks"
echo "  APEX_KEYSTORE_PASSWORD = ваш пароль"
echo "  APEX_KEY_ALIAS         = $ALIAS"
echo "  APEX_KEY_PASSWORD      = ваш пароль"
