#!/usr/bin/env bash
# Type-checks the generated monster models (and Animated) against minimal stubs of the vanilla 1.20.1 API
# they use (not shipped; the real compile happens in CI). Usage: bash tools/art/jcheck/jcheck.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
OUT=$(mktemp -d)
javac --release 17 -Xlint:all -Xlint:-processing -d "$OUT" \
  $(find "$HERE/stubs" -name '*.java') \
  "$ROOT/src/main/java/com/hitori/entity/Animated.java" \
  $(find "$ROOT/src/main/java/com/hitori/client/model" -name '*.java') "$@" 2>&1 | grep -v "^Picked up" || true
ls "$OUT/com/hitori/client/model" 2>/dev/null | sed 's/^/compiled: /'
rm -rf "$OUT"
