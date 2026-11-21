# Local source checks (no Android SDK required)

Run from the project root with Java 17:

```bash
mkdir -p build/standalone-tests
javac -d build/standalone-tests app/src/main/java/org/dhakaflexplus/player/IptvParser.java tests/TestIptv.java tests/TestIptvExtra.java
java -cp build/standalone-tests TestIptv
java -cp build/standalone-tests TestIptvExtra
javac -d build/standalone-tests app/src/main/java/org/dhakaflexplus/player/DefaultServerCatalog.java tests/TestServerPresets.java
java -cp build/standalone-tests TestServerPresets
```

These are source-level tests only, not Android hardware, server access, or native VLC decoding tests.
