#!/usr/bin/env bash
# Production smoke test: real Forge 1.20.1 server + the reobfuscated release jar from build/libs.
# Spawns contestants, powers a saw and a press with redstone, checks that executions happened, then stops.
set -u
FORGE=1.20.1-47.2.0
JAR=$(ls build/libs/satsujinki-*.jar | head -1)
mkdir -p prodserver && cd prodserver
curl -sSLo installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${FORGE}/forge-${FORGE}-installer.jar"
java -jar installer.jar --installServer > install.log 2>&1 || { echo "installer failed"; tail -20 install.log; exit 1; }
echo "eula=true" > eula.txt
printf 'level-type=minecraft\\:flat\nonline-mode=false\nspawn-protection=0\nmax-tick-time=-1\n' > server.properties
mkdir -p mods && cp "../$JAR" mods/
rm -f in && mkfifo in
exec 3<>in
java -Xmx2G @libraries/net/minecraftforge/forge/${FORGE}/unix_args.txt nogui < in > server.log 2>&1 &
JPID=$!
for i in $(seq 1 180); do
  if grep -q "Done (" server.log 2>/dev/null; then break; fi
  if grep -q -E "Crash report|Failed to start|ModLoadingException" server.log 2>/dev/null; then break; fi
  sleep 2
done
grep -q "Done (" server.log || { echo "SERVER DID NOT START"; tail -60 server.log; kill $JPID; exit 1; }
send() { echo "$1" >&3; sleep "${2:-1}"; }
send "forceload add -16 -16 16 16" 3
send "gamerule doMobSpawning false"
# saw: contestant on top, redstone block underneath powers it
send "setblock 0 -61 0 satsujinki:saw"
send "summon satsujinki:contestant 0.5 -60 0.5"
send "setblock 0 -62 0 minecraft:redstone_block" 4
# spike press built by hand: spike bed floor, press head 4 above, powered by a redstone block on top
send "setblock 6 -61 0 satsujinki:spike_bed"
send "setblock 6 -57 0 satsujinki:press_head"
send "summon satsujinki:contestant 6.5 -60.8 0.5" 2
send "setblock 6 -56 0 minecraft:redstone_block" 5
# a plain contestant to see the AI tick for a while
send "summon satsujinki:contestant 3.5 -60 4.5" 4
send "execute if entity @e[type=satsujinki:corpse]" 1
send "execute if entity @e[type=satsujinki:gib]" 1
send "execute if entity @e[type=satsujinki:contestant]" 1
send "stop" 1
exec 3>&-
for i in $(seq 1 60); do
  if ! kill -0 $JPID 2>/dev/null; then break; fi
  sleep 2
done
cp server.log ../prod-server.log
echo "----- mod list"
grep -i -E "satsujinki" server.log | head -5
echo "----- checks"
grep -E "Test passed|Test failed" server.log
FAIL=0
grep -q -E "Exception|Crash report" server.log && { echo "EXCEPTIONS FOUND:"; grep -n -E "Exception|Crash report" server.log | head -20; FAIL=1; }
[ "$(grep -c 'Test passed' server.log)" -ge 3 ] || { echo "EXECUTION CHECKS FAILED"; FAIL=1; }
exit $FAIL
