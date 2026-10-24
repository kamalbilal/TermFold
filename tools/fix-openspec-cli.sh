#!/usr/bin/env bash
# Restores commander/lib/help.js when a broken global install only has help.j
# (openspec then fails with: Cannot find module './help.js').
set -euo pipefail
spec="$(npm root -g 2>/dev/null)/@fission-ai/openspec/node_modules/commander/lib" || true
if [[ -z "$spec" || ! -d "$spec" ]]; then
  echo "Global @fission-ai/openspec not found; install with: npm install -g @fission-ai/openspec" >&2
  exit 1
fi
if [[ -f "$spec/help.js" ]]; then
  echo "help.js already present"
  exit 0
fi
if [[ ! -f "$spec/help.j" ]]; then
  echo "Neither help.js nor help.j under $spec" >&2
  exit 1
fi
cp "$spec/help.j" "$spec/help.js"
echo "Restored $spec/help.js from help.j"
openspec --version
