#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$ROOT_DIR/scripts/smoke-server-jar.sh"

SERVER_JARS=()
while IFS= read -r jar; do
  SERVER_JARS+=("$jar")
done < <(find "$ROOT_DIR" -maxdepth 1 -type f \( -name 'paper-*.jar' -o -name 'folia-*.jar' \) | sort)

if [ "${#SERVER_JARS[@]}" -eq 0 ]; then
  echo "No paper-*.jar or folia-*.jar files found in $ROOT_DIR" >&2
  exit 2
fi

FAILURES=0

for jar in "${SERVER_JARS[@]}"; do
  echo "==> Smoke testing $(basename "$jar")"
  if ! bash "$SCRIPT" "$jar"; then
    FAILURES=$((FAILURES + 1))
  fi
done

if [ "$FAILURES" -ne 0 ]; then
  echo "Smoke tests finished with $FAILURES failure(s)." >&2
  exit 1
fi

echo "All smoke tests passed."
