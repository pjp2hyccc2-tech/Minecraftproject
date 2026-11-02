#!/usr/bin/env bash
# CI only: runs the Minecraft client under Xvfb with software OpenGL. The mod's AutoShots tour plays the
# tutorial, visits every repair chamber and saves screenshots to run/screenshots/.
set -o pipefail
mkdir -p run
cp ci/options.txt run/options.txt
rm -rf run/screenshots run/saves/ClockworkCI* run/clockworkdiver_autoshots.txt
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=4.5
timeout 2100 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -Pclienttest=true --no-daemon > clienttest-full.log 2>&1
code=$?
echo "client exit code: $code"
tail -c 300000 clienttest-full.log > clienttest-tail.log
cp run/logs/latest.log client-latest.log 2>/dev/null || true
test -f run/clockworkdiver_autoshots.txt && cat run/clockworkdiver_autoshots.txt
grep -q finished run/clockworkdiver_autoshots.txt
