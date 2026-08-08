#!/usr/bin/env sh
set -eu

mvn --batch-mode --no-transfer-progress -DskipTests package
JAR="target/nlipse-0.5.0-SNAPSHOT.jar"
java -Djava.awt.headless=true -XX:+UseCompactObjectHeaders \
  -XX:AOTCacheOutput=target/nlipse.aot \
  -cp "$JAR" nlipse.app.AotTrainer

echo "Created target/nlipse.aot"
