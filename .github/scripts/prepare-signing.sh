#!/usr/bin/env bash
# Prepares a keystore for signing the release APK and exports the env vars read by app/build.gradle.kts.
# Uses the KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD secrets when present,
# otherwise generates a throw-away keystore (APK is installable, but updates from a different
# keystore cannot be installed over it).
set -euo pipefail

KS="${RUNNER_TEMP:-/tmp}/release.keystore"

if [[ -n "${KEYSTORE_BASE64:-}" && -n "${KEYSTORE_PASSWORD:-}" && -n "${KEY_ALIAS:-}" && -n "${KEY_PASSWORD:-}" ]]; then
  echo "Using keystore from repository secrets"
  echo "$KEYSTORE_BASE64" | base64 -d > "$KS"
  STORE_PASS="$KEYSTORE_PASSWORD"; ALIAS="$KEY_ALIAS"; KEY_PASS="$KEY_PASSWORD"
else
  echo "::warning::Signing secrets not set - generating a temporary keystore. Set KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD so updates install over previous versions."
  STORE_PASS="$(openssl rand -hex 16)"; ALIAS="fx991"; KEY_PASS="$STORE_PASS"
  rm -f "$KS"
  keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=FX-991 Calc, O=FX991, C=US" >/dev/null
fi

{
  echo "KEYSTORE_FILE=$KS"
  echo "KEYSTORE_PASSWORD=$STORE_PASS"
  echo "KEY_ALIAS=$ALIAS"
  echo "KEY_PASSWORD=$KEY_PASS"
} >> "$GITHUB_ENV"
