#!/usr/bin/env bash
set -euo pipefail
# Private key/password stay outside the source repository and CI.
if [[ $# -ne 4 ]]; then
  echo 'Uso: sign-review.sh apksigner.jar entrada.apk salida.apk directorio-de-firma' >&2
  exit 2
fi
java -jar "$1" sign --ks "$4/review.p12" --ks-key-alias workflow-review --ks-pass "file:$4/review-password.txt" --key-pass "file:$4/review-password.txt" --out "$3" "$2"
java -jar "$1" verify --verbose --print-certs "$3"
