#!/bin/sh
# Byte-for-byte check of the XML this library sends to SIFEN.
#
# Baseline: the root commit of this repository (0.2.4b.4, the production sources), on Java 8.
# Under test: the working tree, on Java 21.
# Both build the same requests from tools/sifen-xml-check/samples/*.xml, signed with the same
# throwaway test certificate, and every output file must be identical. Exit code 1 on any difference.
#
# Usage (Git Bash on Windows, or any POSIX shell):
#   JDK8_HOME=/path/to/jdk8 JDK21_HOME=/path/to/jdk21 tools/sifen-xml-check/check.sh
set -e

: "${JDK8_HOME:?Set JDK8_HOME to a JDK 8 (it builds the 0.2.4b.4 baseline)}"
: "${JDK21_HOME:?Set JDK21_HOME to a JDK 21}"

ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"
TOOL=tools/sifen-xml-check
W=target/sifen-xml-check
SEP=':'
case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) SEP=';' ;; esac

echo "Building the working tree with JDK 21..."
JAVA_HOME="$JDK21_HOME" mvn -q -B package -DskipTests
JAVA_HOME="$JDK21_HOME" mvn -q -B dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt
JAR=$(ls target/rshk-jsifenlib-*.jar | head -n 1)

rm -rf "$W"
mkdir -p "$W/base-src" "$W/base-cls" "$W/gen" "$W/cls-base" "$W/cls-new"

BASE=$(git rev-list --max-parents=0 HEAD)
echo "Building the baseline ($(git log -1 --format='%h %s' "$BASE")) with JDK 8..."
git archive "$BASE" src/main/java | tar -x -C "$W/base-src"
find "$W/base-src" -name '*.java' > "$W/base-sources.txt"
"$JDK8_HOME/bin/javac" -nowarn -encoding UTF-8 -d "$W/base-cls" "@$W/base-sources.txt" 2> "$W/javac-base.log" \
    || { cat "$W/javac-base.log"; exit 1; }

echo "Generating the test certificate..."
"$JDK8_HOME/bin/javac" -nowarn -XDignore.symbol.file -d "$W/gen" "$TOOL/src/GenCert.java" 2> "$W/javac-gen.log" \
    || { cat "$W/javac-gen.log"; exit 1; }
"$JDK8_HOME/bin/java" -cp "$W/gen" GenCert "$W/test.pfx"

DUMP="$TOOL/src/com/roshka/sifen/internal/request/WireDump.java"
MAIN=com.roshka.sifen.internal.request.WireDump
"$JDK8_HOME/bin/javac" -encoding UTF-8 -cp "$W/base-cls" -d "$W/cls-base" "$DUMP"
"$JDK8_HOME/bin/java" -cp "$W/cls-base$SEP$W/base-cls" $MAIN "$W/test.pfx" "$W/out-base" "$TOOL"/samples/*.xml > "$W/run-base.log" 2>&1 \
    || { cat "$W/run-base.log"; exit 1; }
grep "samples done" "$W/run-base.log"

CP="$JAR$SEP$(cat target/runtime-classpath.txt)"
"$JDK21_HOME/bin/javac" -encoding UTF-8 -cp "$CP" -d "$W/cls-new" "$DUMP"
"$JDK21_HOME/bin/java" -cp "$W/cls-new$SEP$CP" $MAIN "$W/test.pfx" "$W/out-new" "$TOOL"/samples/*.xml > "$W/run-new.log" 2>&1 \
    || { cat "$W/run-new.log"; exit 1; }
grep "samples done" "$W/run-new.log"

status=0
for f in "$W"/out-base/*; do
    name=$(basename "$f")
    if cmp -s "$f" "$W/out-new/$name"; then
        echo "SAME  $name ($(wc -c < "$f") bytes)"
    else
        echo "DIFF  $name"
        cmp "$f" "$W/out-new/$name" || true
        status=1
    fi
done
for f in "$W"/out-new/*; do
    [ -e "$W/out-base/$(basename "$f")" ] || { echo "ONLY IN NEW  $(basename "$f")"; status=1; }
done

if [ $status -eq 0 ]; then echo "OK: all outputs are byte-identical to the baseline."; else echo "FAILED: outputs differ from the baseline (see $W)."; fi
exit $status
