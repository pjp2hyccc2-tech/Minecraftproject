#!/usr/bin/env bash
# Commit + push the current branch, wait for the GitHub Actions run, print compile errors / test results.
# Usage:  bash tools/ci.sh "commit message"
# Results are published by the workflow to branch ci-<branch> (fetched here).
set -u
cd "$(git rev-parse --show-toplevel)"
BR=$(git rev-parse --abbrev-ref HEAD)
REPO=pjp2hyccc2-tech/minecraftproject
git add -A
git commit -q -m "$1

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017h8AW6rusF1sbBKY2sPjwn" 2>/dev/null || echo "(nothing new to commit)"
retry() { for i in 1 2 3 4 5 6; do if "$@" 2>/tmp/ci_err_$$; then return 0; fi; sleep $((5 + RANDOM % 10)); done; cat /tmp/ci_err_$$; return 1; }
retry git push -q origin "$BR" || { echo "PUSH FAILED"; exit 1; }
SHA=$(git rev-parse HEAD)
echo "pushed ${SHA:0:8} to $BR - waiting for CI..."
sleep 20
for i in $(seq 1 120); do
  ST=$(gh api "repos/$REPO/commits/$SHA/check-runs" --jq '[.check_runs[] | .status] | if length==0 then "none" else (if all(.=="completed") then "completed" else "running" end) end' 2>/dev/null)
  if [ "$ST" = "completed" ]; then break; fi
  sleep 20
done
for i in 1 2 3 4 5 6 8 10; do
  retry git fetch -q origin "+refs/heads/ci-$BR:refs/remotes/origin/ci-$BR" || true
  if [ "$(git show origin/ci-$BR:SOURCE_SHA 2>/dev/null)" = "$SHA" ]; then break; fi
  sleep 15
done
echo "=== STATUS: $(git show origin/ci-$BR:STATUS 2>/dev/null) (for $(git show origin/ci-$BR:SOURCE_SHA 2>/dev/null | cut -c1-8))"
git show origin/ci-$BR:errors.txt 2>/dev/null | head -200
git show origin/ci-$BR:build.log 2>/dev/null | grep -E "BUILD (SUCCESSFUL|FAILED)|What went wrong" -A3 | head -12
git show origin/ci-$BR:gametest.log 2>/dev/null | grep -E "failed|FAILED|Exception|Caused by|required tests|GAME TESTS|passed|crash" | grep -v DEBUG | head -40
