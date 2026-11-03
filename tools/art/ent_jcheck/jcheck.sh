#!/usr/bin/env bash
# Type-checks the generated spirit models (and Animated) against minimal stubs of the vanilla 1.20.1 API they use
# (not shipped; the real compile happens in CI). Exit code 1 on any error. Usage: bash tools/art/ent_jcheck/jcheck.sh
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
OUT=$(mktemp -d)
LOG=$(mktemp)
javac --release 17 -Xlint:all -Xlint:-processing -d "$OUT" \
  $(find "$HERE/stubs" -name '*.java') \
  "$ROOT/src/main/java/com/reisatsu/entity/Animated.java" \
  $(find "$ROOT/src/main/java/com/reisatsu/client/model" -name '*.java') "$@" > "$LOG" 2>&1
RC=$?
grep -v "^Picked up" "$LOG"
ls "$OUT/com/reisatsu/client/model" 2>/dev/null | grep -v '\$' | sed 's/^/compiled: /'
rm -rf "$OUT" "$LOG"
exit $RC
