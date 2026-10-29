#!/usr/bin/env bash
# Fetch and summarise the results of the "shaders" CI job for the current branch
# (published to branch ci-<branch>-shaders by .github/workflows/build.yml).
# Usage: bash tools/shader/ci_shaders.sh            -> status, lint output, Oculus log excerpt, screenshot list
#        git show origin/ci-<branch>-shaders:screenshots/shader_village.png > /tmp/x.png
set -u
BR=$(git rev-parse --abbrev-ref HEAD)
RB="ci-$BR-shaders"
git fetch -q origin "+refs/heads/$RB:refs/remotes/origin/$RB" 2>/dev/null || { echo "no results branch $RB yet"; exit 1; }
echo "=== $RB for $(git show origin/$RB:SOURCE_SHA 2>/dev/null | cut -c1-8) (HEAD $(git rev-parse --short HEAD))"
echo "=== STATUS: $(git show origin/$RB:STATUS 2>/dev/null)"
echo "=== lint:"; git show origin/$RB:lint.txt 2>/dev/null | tail -40
echo "=== mods / shaderpacks:"; git show origin/$RB:mods.txt 2>/dev/null; git show origin/$RB:shaderpacks.txt 2>/dev/null
echo "=== shader autotest log:"; git show origin/$RB:shader_autotest_log.txt 2>/dev/null | head -60
echo "=== Oculus / errors (latest.log grep):"
git show origin/$RB:shaders-grep.txt 2>/dev/null | grep -v -E "DEBUG|Mixin|mixin" | grep -i -E "oculus|iris|shaderpack|SHADERTEST|HuntCraft Shaders|GLSL|compil|error|Exception" | head -80
echo "=== screenshots:"; git ls-tree --name-only origin/$RB:screenshots 2>/dev/null
