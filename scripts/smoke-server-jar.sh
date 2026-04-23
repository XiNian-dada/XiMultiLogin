#!/usr/bin/env bash

set -euo pipefail

if [ "${1:-}" = "" ]; then
  echo "Usage: $0 <server-jar> [plugin-jar]" >&2
  exit 2
fi

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_JAR_INPUT="$1"
PLUGIN_JAR_INPUT="${2:-}"

SERVER_JAR="$(cd "$(dirname "$SERVER_JAR_INPUT")" && pwd)/$(basename "$SERVER_JAR_INPUT")"

if [ ! -f "$SERVER_JAR" ]; then
  echo "Server jar not found: $SERVER_JAR" >&2
  exit 2
fi

if [ "$PLUGIN_JAR_INPUT" = "" ]; then
  PLUGIN_JAR_INPUT="$(find "$ROOT_DIR/target" -maxdepth 1 -name 'XiMultiLogin-*.jar' ! -name 'original-*' | sort | tail -n 1)"
fi

if [ "$PLUGIN_JAR_INPUT" = "" ]; then
  echo "Plugin jar not found. Build the project first." >&2
  exit 2
fi

PLUGIN_JAR="$(cd "$(dirname "$PLUGIN_JAR_INPUT")" && pwd)/$(basename "$PLUGIN_JAR_INPUT")"
SERVER_NAME="$(basename "$SERVER_JAR" .jar)"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="$ROOT_DIR/.smoke/$SERVER_NAME-$STAMP"
LOG_FILE="$RUN_DIR/server.log"
FIFO_FILE="$RUN_DIR/commands.fifo"
PORT_OFFSET="$(printf '%s' "$SERVER_NAME" | cksum | awk '{print $1 % 1000}')"
SERVER_PORT="$((23000 + PORT_OFFSET))"
START_TIMEOUT_SECONDS="${START_TIMEOUT_SECONDS:-120}"

mkdir -p "$RUN_DIR/plugins"
cp "$PLUGIN_JAR" "$RUN_DIR/plugins/"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<EOF
online-mode=true
server-port=$SERVER_PORT
motd=XiMultiLogin Smoke Test
max-players=5
spawn-protection=0
view-distance=4
simulation-distance=4
enable-status=false
enable-query=false
EOF

mkfifo "$FIFO_FILE"

(
  cd "$RUN_DIR"
  cat "$FIFO_FILE" | java -Xms512M -Xmx1024M -jar "$SERVER_JAR" nogui > "$LOG_FILE" 2>&1
  echo $? > "$RUN_DIR/exit.code"
) &
SERVER_PID=$!

cleanup() {
  if kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    {
      printf 'stop\n' > "$FIFO_FILE"
    } >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
  rm -f "$FIFO_FILE"
}

trap cleanup EXIT

started=0
plugin_enabled=0
injection_succeeded=0
reflection_replacement_succeeded=0
failure_detected=0

for _ in $(seq 1 "$START_TIMEOUT_SECONDS"); do
  if [ -f "$LOG_FILE" ]; then
    if grep -q "XiMultiLogin: Plugin enabled successfully" "$LOG_FILE"; then
      plugin_enabled=1
    fi
    if grep -q "XiMultiLogin: Session service injected successfully" "$LOG_FILE"; then
      injection_succeeded=1
    fi
    if grep -q "XiReflection: Session service replacement succeeded" "$LOG_FILE"; then
      reflection_replacement_succeeded=1
    fi
    if grep -Eq "Done \\(|For help, type \"help\"" "$LOG_FILE"; then
      started=1
    fi
    if grep -Eq "Failed to inject session service|Exception during injection|XiReflection: Reconstruction failed|XiReflection: Failed to locate record component for session service replacement|XiReflection: Failed to adapt replacement session service|You need to agree to the EULA|UnsupportedClassVersionError|Could not load 'plugins/.*/XiMultiLogin|Failed to download|UnknownHostException|Error occurred while disabling XiMultiLogin|Error occurred \\(in the plugin loader\\) while disabling XiMultiLogin|paperclip" "$LOG_FILE"; then
      failure_detected=1
      break
    fi
    if [ "$started" -eq 1 ] && [ "$plugin_enabled" -eq 1 ] && [ "$injection_succeeded" -eq 1 ] && [ "$reflection_replacement_succeeded" -eq 1 ]; then
      break
    fi
  fi

  if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

RESULT="FAIL"
cleanup

if [ -f "$LOG_FILE" ] && grep -Eq "Error occurred while disabling XiMultiLogin|Error occurred \\(in the plugin loader\\) while disabling XiMultiLogin" "$LOG_FILE"; then
  failure_detected=1
fi

if [ "$started" -eq 1 ] && [ "$plugin_enabled" -eq 1 ] && [ "$injection_succeeded" -eq 1 ] && [ "$reflection_replacement_succeeded" -eq 1 ] && [ "$failure_detected" -eq 0 ]; then
  RESULT="PASS"
fi

echo "[$RESULT] $SERVER_NAME"
echo "  server jar: $SERVER_JAR"
echo "  plugin jar: $PLUGIN_JAR"
echo "  log file:   $LOG_FILE"
if [ "$injection_succeeded" -eq 0 ]; then
  echo "  injection:  missing success log"
fi
if [ "$reflection_replacement_succeeded" -eq 0 ]; then
  echo "  reflection: missing replacement success log"
fi

if [ "$RESULT" != "PASS" ]; then
  if [ -f "$LOG_FILE" ]; then
    echo "  tail:"
    tail -n 60 "$LOG_FILE" | sed 's/^/    /'
  fi
  exit 1
fi
