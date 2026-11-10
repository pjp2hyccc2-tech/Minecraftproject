#!/usr/bin/env bash
# Compiles and unit-tests the Minecraft-independent ("pure") modules WITHOUT Forge or network access.
#
# The real build (./gradlew build) remains the source of truth. This script exists for restricted environments
# (e.g. sandboxes without access to maven.minecraftforge.net) so the domain/persistence/network logic can still be
# verified against the Java 17 API.
#
# Required jars are auto-detected from local Gradle/Maven installations, or can be given explicitly:
#   ER_GSON_JAR, ER_SLF4J_JAR, ER_JUNIT_JAR, ER_HAMCREST_JAR   (or ER_LIB_DIRS="dir1 dir2" to search)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.offline-build"
PURE_DIRS="core season character player persistence network combat job skill item economy party guild ranking quest world monster boss dungeon pvp story npc"

find_jar() {
  local pattern="$1" dir found
  for dir in ${ER_LIB_DIRS:-} /opt/gradle*/lib /opt/gradle*/lib/plugins /opt/apache-maven*/lib \
             "$HOME/.m2/repository" "$HOME/.gradle/caches/modules-2/files-2.1"; do
    [ -d "$dir" ] || continue
    found=$(find "$dir" -name "$pattern" 2>/dev/null | sort | tail -1)
    if [ -n "$found" ]; then
      echo "$found"
      return 0
    fi
  done
  echo "ERROR: could not find $pattern (set the matching ER_*_JAR variable)" >&2
  return 1
}

GSON="${ER_GSON_JAR:-$(find_jar 'gson-2.*.jar')}"
SLF4J="${ER_SLF4J_JAR:-$(find_jar 'slf4j-api-*.jar')}"
JUNIT="${ER_JUNIT_JAR:-$(find_jar 'junit-4.13*.jar')}"
HAMCREST="${ER_HAMCREST_JAR:-$(find_jar 'hamcrest-core-1.3.jar')}"

echo "javac : $(javac -version 2>&1 | grep -v 'Picked up')"
echo "gson  : $GSON"
echo "slf4j : $SLF4J"
echo "junit : $JUNIT"

rm -rf "$OUT"
mkdir -p "$OUT/main" "$OUT/test"

mapfile -t MAIN_SOURCES < <(for d in $PURE_DIRS; do
  [ -d "$ROOT/src/main/java/com/eternalrealm/$d" ] || continue
  find "$ROOT/src/main/java/com/eternalrealm/$d" -name '*.java' -not -path '*/forge/*'
done | sort)
mapfile -t TEST_SOURCES < <(find "$ROOT/src/test/java" -name '*.java' | sort)

echo "Compiling ${#MAIN_SOURCES[@]} pure main sources (--release 17)..."
javac --release 17 -encoding UTF-8 -Xlint:all -Werror -d "$OUT/main" -cp "$GSON:$SLF4J" "${MAIN_SOURCES[@]}"

echo "Compiling ${#TEST_SOURCES[@]} test sources..."
javac --release 17 -encoding UTF-8 -Xlint:all,-try -Werror -d "$OUT/test" \
  -cp "$OUT/main:$GSON:$SLF4J:$JUNIT:$HAMCREST" "${TEST_SOURCES[@]}"

mapfile -t TEST_CLASSES < <(cd "$ROOT/src/test/java" && find . -name '*Test.java' | sed -e 's|^\./||' -e 's|\.java$||' -e 's|/|.|g' | sort)

echo "Running ${#TEST_CLASSES[@]} test classes..."
cd "$ROOT"
java -cp "$OUT/test:$OUT/main:$GSON:$SLF4J:$JUNIT:$HAMCREST" org.junit.runner.JUnitCore "${TEST_CLASSES[@]}"
