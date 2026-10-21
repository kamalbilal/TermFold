#!/bin/bash
# TermFold guest performance snapshot. Run in a shell after setup; timings vary with thermals.
set -u

dim=$'\033[2m'; green=$'\033[32m'; orange=$'\033[38;5;208m'; reset=$'\033[0m'

printf '%sTermFold bench%s  %s(plug in, performance mode; run twice if numbers look noisy)%s\n\n' \
  "$orange" "$reset" "$dim" "$reset"

cpus="$(nproc 2>/dev/null || echo '?')"
printf 'cpus: %s\n' "$cpus"

if command -v free >/dev/null 2>&1; then
  free -h | awk '/^Mem:/{printf "mem:  total %s  avail %s\n", $2, $7}'
else
  awk '/^MemTotal:|^MemAvailable:/{print}' /proc/meminfo 2>/dev/null | tr '\n' ' '
  printf '\n'
fi

if [ -f /var/lib/termfold/l2s-migrated ]; then
  printf 'GuestCompat: migrated (no PRoot link2symlink)\n'
else
  printf '%sGuestCompat: NOT migrated — legacy link emulation may still be active%s\n' "$dim" "$reset"
fi

bench_node() {
  if ! command -v node >/dev/null 2>&1; then
    printf 'node: not installed (run termfold-setup)\n'
    return
  fi
  start=$(date +%s%N 2>/dev/null || echo 0)
  node -e '' 2>/dev/null
  end=$(date +%s%N 2>/dev/null || echo 0)
  if [ "$start" != 0 ] && [ "$end" != 0 ]; then
    ms=$(( (end - start) / 1000000 ))
    printf 'node empty -e: %sms\n' "$ms"
  else
    SECONDS=0
    node -e ''
    printf 'node empty -e: %ss\n' "$SECONDS"
  fi
}

bench_read() {
  f="${TMPDIR:-/tmp}/termfold-bench-read.dat"
  printf 'x' >"$f"
  start=$(date +%s%N 2>/dev/null || echo 0)
  i=0
  while [ "$i" -lt 1000 ]; do
    cat "$f" >/dev/null
    i=$((i + 1))
  done
  end=$(date +%s%N 2>/dev/null || echo 0)
  rm -f "$f"
  if [ "$start" != 0 ] && [ "$end" != 0 ]; then
    ms=$(( (end - start) / 1000000 ))
    printf '1000x read small file in %s: %sms\n' "${TMPDIR:-/tmp}" "$ms"
  else
    printf '1000x read small file: done (no high-res clock)\n'
  fi
}

printf '\n'
bench_node
bench_read
printf '\n%sTip:%s add ESLint --cache to npm run lint; run termfold-setup tips for more.\n' "$dim" "$reset"
