#!/usr/bin/env bash
# Commit + push the current branch, wait for the GitHub Actions build, print the result.
# Usage (inside your worktree):   bash tools/ci.sh "commit message"
# Results: STATUS line, compile errors (errors.txt), GameTest failures. Full logs: git show origin/ci-<branch>:build.log
set -u
BR=$(git rev-parse --abbrev-ref HEAD)
REPO=pjp2hyccc2-tech/minecraftproject
git add -A
git commit -q -m "$1" 2>/dev/null || echo "(nothing new to commit)"
retry() { for i in 1 2 3 4 5 6 7 8; do if "$@" 2>/tmp/ci_err_$$; then return 0; fi; sleep $((6 + RANDOM % 20)); done; cat /tmp/ci_err_$$; return 1; }
retry git push -q origin "$BR" || { echo "PUSH FAILED"; exit 1; }
SHA=$(git rev-parse HEAD)
echo "pushed $SHA to $BR - waiting for CI (usually 4-8 min)..."
for i in $(seq 1 70); do
  sleep 25
  ST=$(curl -s "https://api.github.com/repos/$REPO/commits/$SHA/check-runs" | python3 -c "import sys,json
try:
  d=json.load(sys.stdin); r=d.get('check_runs',[])
  print(r[0]['status'] if r else 'none')
except Exception: print('err')")
  if [ "$ST" = "completed" ]; then break; fi
done
sleep 8
for i in 1 2 3 4 5 6; do
  retry git fetch -q origin "+refs/heads/ci-$BR:refs/remotes/origin/ci-$BR" || true
  if [ "$(git show origin/ci-$BR:SOURCE_SHA 2>/dev/null)" = "$SHA" ]; then break; fi
  sleep 20
done
echo "=== STATUS: $(git show origin/ci-$BR:STATUS 2>/dev/null) (for $(git show origin/ci-$BR:SOURCE_SHA 2>/dev/null | cut -c1-8))"
git show origin/ci-$BR:errors.txt 2>/dev/null | head -150
git show origin/ci-$BR:build.log 2>/dev/null | grep -E "BUILD (SUCCESSFUL|FAILED)|What went wrong" -A3 | head -12
git show origin/ci-$BR:gametest.log 2>/dev/null | grep -E "failed|FAILED|Exception|Caused by|required tests|GAME TESTS COMPLETE|crash" | grep -v "DEBUG" | head -40
