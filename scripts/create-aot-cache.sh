#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/aot-common.sh"

mvn --batch-mode --no-transfer-progress -DskipTests package
JAR="target/nlipse.jar"
METADATA="target/nlipse.aot.meta"
rm -f "$METADATA"
java -Djava.awt.headless=true -XX:+UseCompactObjectHeaders \
  -XX:AOTCacheOutput=target/nlipse.aot \
  -cp "$JAR" nlipse.app.AotTrainer
aot_write_metadata "$JAR" "$METADATA"

echo "Created target/nlipse.aot and target/nlipse.aot.meta"
