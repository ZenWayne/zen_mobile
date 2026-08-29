#!/usr/bin/env bash
# run-appium-test.sh — parse a Make target like "smoke-bc72" and run the suite.
#
# Usage: run-appium-test.sh <target>
#
# Format: <suite>-<device>
#   suite  : smoke | states | inference | stop | approval | all
#   device : bc72 | emu
#
# bc72 = real device (QV7808CA8G): full suites incl. on-device inference.
# emu  = x86_64 emulator (emulator-5554): UI-only suites — the arm64 .so
#        cannot load there, so inference/stop fail by architecture.

set -euo pipefail

TARGET="$1"
APPIUM_DIR="$(cd "$(dirname "$0")/.." && pwd)/tests/appium"

# ── 1. Extract device (last segment) ─────────────────────────────────────────
DEVICE="${TARGET##*-}"
SUITE="${TARGET%-${DEVICE}}"

# ── 2. Map suite → test file ─────────────────────────────────────────────────
case "$SUITE" in
  smoke)     FILE="tests/00_launch_smoke.test.js" ;;
  states)    FILE="tests/01_conversation_states.test.js" ;;
  inference) FILE="tests/02_inference_streaming.test.js" ;;
  stop)      FILE="tests/03_stop_control.test.js" ;;
  approval)  FILE="tests/04_approval_gate.test.js" ;;
  all)       FILE="'tests/**/*.test.js'" ;;
  *)
    echo "ERROR: unknown suite '${SUITE}'" >&2
    echo "Valid suites: smoke states inference stop approval all" >&2
    exit 1
    ;;
esac

# ── 3. Device ADB serial + timeout ───────────────────────────────────────────
case "$DEVICE" in
  bc72) ADB_SERIAL="QV7808CA8G" ; TIMEOUT=180000 ;;
  emu)  ADB_SERIAL="emulator-5554" ; TIMEOUT=120000 ;;
  *)
    echo "ERROR: unknown device '${DEVICE}' (must be bc72 or emu)" >&2
    exit 1
    ;;
esac

# ── 4. Reject inference suites on the emulator ───────────────────────────────
if [ "$DEVICE" = "emu" ] && { [ "$SUITE" = "inference" ] || [ "$SUITE" = "stop" ]; }; then
  echo "ERROR: suite '${SUITE}' requires the arm64 .so + on-device model; run on bc72" >&2
  exit 1
fi

# ── 5. Force-stop the app for a clean UiAutomator2 session ───────────────────
adb -s "$ADB_SERIAL" shell am force-stop com.zenwayne.zenagent || true
echo "→ suite=${SUITE}  device=${DEVICE}  file=${FILE}"

cd "$APPIUM_DIR"
exec env DEVICE="$DEVICE" npx mocha $FILE --timeout "$TIMEOUT" --exit
