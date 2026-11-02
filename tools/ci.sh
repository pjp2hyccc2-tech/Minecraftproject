#!/usr/bin/env bash
# Commit + push the current branch, wait for the GitHub Actions "build" job, print the result.
# Usage: bash tools/ci.sh "commit message" [--no-wait]
# Full logs: git show origin/ci-<branch>:build.log   Screenshots: git show origin/ci-<branch>:screenshots/<x>.png
set -u
BR=$(git rev-parse --abbrev-ref HEAD)
REPO=pjp2hyccc2-tech/Minecraftproject
git add -A
git commit -q -m "$1" 2>/dev/null || echo "(nothing new to commit)"
retry() { for i in 1 2 3 4 5 6 7 8; do if "$@" 2>/tmp/ci_err_$$; then return 0; fi; sleep $((6 + RANDOM % 20)); done; cat /tmp/ci_err_$$; return 1; }
retry git push -q origin "$BR" || { echo "PUSH FAILED"; exit 1; }
SHA=$(git rev-parse HEAD)
echo "pushed $SHA to $BR"
[ "${2:-}" = "--no-wait" ] && exit 0
bash tools/ci_wait.sh "$SHA" build
