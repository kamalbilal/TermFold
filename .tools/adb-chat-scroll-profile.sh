#!/usr/bin/env bash
# Profile TermFold agent-chat scroll jank via adb (run while on Cursor/ACP session).
set -euo pipefail
PKG="${TERM_FOLD_PKG:-com.termfold.new}"
SECONDS="${1:-8}"

echo "Package: $PKG — scroll the chat for ~${SECONDS}s, then stats print automatically."
adb logcat -c
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null

end=$((SECONDS + $(date +%s)))
while [[ $(date +%s) -lt $end ]]; do
  adb shell input swipe 540 1600 540 700 90
  sleep 0.12
done

sleep 0.5
echo ""
echo "========== gfxinfo (summary) =========="
adb shell dumpsys gfxinfo "$PKG" | rg "Total frames|Janky|50th percentile|90th percentile|99th percentile|Slow UI|High input|Missed Vsync"

echo ""
echo "========== logcat (skipped frames) =========="
adb logcat -d | rg -i "Choreographer.*Skipped|Davey" | tail -10 || true

echo ""
echo "Tip: enable Developer options > Profile GPU rendering > On screen as bars for live view."
