#!/usr/bin/env bash
set -euo pipefail
# Private key/password stay outside the source repository and CI.
if [[ $# -ne 4 ]]; then
  echo 'Uso: sign-review.sh apksigner.jar entrada.apk salida.apk directorio-de-firma' >&2
  exit 2
fi
# PKCS12 uses the store password for its private key. Reusing one password file for
# two arguments makes apksigner read a second line and fail with end-of-file.
java -jar "$1" sign --ks "$4/review.p12" --ks-key-alias workflow-review --ks-pass "file:$4/review-password.txt" --out "$3" "$2"
java -jar "$1" verify --verbose --print-certs "$3"
