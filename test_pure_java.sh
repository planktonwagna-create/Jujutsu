#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build/offline-test
mkdir -p build/offline-test
javac --release 17 -d build/offline-test \
 src/main/java/dev/jujutsunpc/game/AbilityCatalog.java \
 src/main/java/dev/jujutsunpc/game/CastPolicy.java \
 src/test/java/dev/jujutsunpc/game/CastPolicySmokeTest.java
java -cp build/offline-test dev.jujutsunpc.game.CastPolicySmokeTest
