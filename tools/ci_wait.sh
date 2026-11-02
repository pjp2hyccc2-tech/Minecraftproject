#!/usr/bin/env bash
# Wait for a CI job ("build" or "shaders") of a commit and print its results.
# Usage: bash tools/ci_wait.sh <sha> [build|shaders]
set -u
SHA=$1; JOB=${2:-build}
BR=$(git rev-parse --abbrev-ref HEAD)
REPO=pjp2hyccc2-tech/Minecraftproject
RB="ci-$BR"; [ "$JOB" = "shaders" ] && RB="ci-$BR-shaders"
retry() { for i in 1 2 3 4 5 6 7 8; do if "$@" 2>/tmp/ci_err_$$; then return 0; fi; sleep $((6 + RANDOM % 20)); done; cat /tmp/ci_err_$$; return 1; }
for i in $(seq 1 200); do
  ST=$(curl -s "https://api.github.com/repos/$REPO/commits/$SHA/check-runs" | python3 -c "import sys,json
try:
  d=json.load(sys.stdin); r=[c for c in d.get('check_runs',[]) if c.get('name')=='$JOB']
  print(r[0]['status']+':'+str(r[0].get('conclusion')) if r else 'none')
except Exception as e: print('err')")
  case "$ST" in completed*) break;; esac
  sleep 30
done
echo "job $JOB: $ST"
sleep 5
for i in 1 2 3 4 5 6; do
  retry git fetch -q origin "+refs/heads/$RB:refs/remotes/origin/$RB" || true
  if [ "$(git show origin/$RB:SOURCE_SHA 2>/dev/null)" = "$SHA" ]; then break; fi
  sleep 20
done
echo "=== STATUS: $(git show origin/$RB:STATUS 2>/dev/null) (for $(git show origin/$RB:SOURCE_SHA 2>/dev/null | cut -c1-8))"
if [ "$JOB" = "build" ]; then
  git show origin/$RB:errors.txt 2>/dev/null | head -250
  git show origin/$RB:build.log 2>/dev/null | grep -E "BUILD (SUCCESSFUL|FAILED)|What went wrong" -A4 | head -16
  git show origin/$RB:meikon-selftest.txt 2>/dev/null | tail -60
  git show origin/$RB:meikon_autoshots.txt 2>/dev/null | tail -40
else
  git show origin/$RB:lint.txt 2>/dev/null | tail -40
  git show origin/$RB:meikon_autoshots.txt 2>/dev/null | tail -30
  git show origin/$RB:shaders-grep.txt 2>/dev/null | grep -i -E "error|exception|fail|compil" | head -40
fi
echo "=== screenshots:"; git ls-tree --name-only origin/$RB:screenshots 2>/dev/null | tr '\n' ' '; echo
