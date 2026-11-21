#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build/standalone-tests
mkdir -p build/standalone-tests
javac -d build/standalone-tests \
  app/src/main/java/org/dhakaflexplus/player/IptvParser.java \
  app/src/main/java/org/dhakaflexplus/player/DefaultServerCatalog.java \
  tests/TestIptv.java tests/TestIptvExtra.java tests/TestServerPresets.java
java -cp build/standalone-tests TestIptv
java -cp build/standalone-tests TestIptvExtra
java -cp build/standalone-tests TestServerPresets
