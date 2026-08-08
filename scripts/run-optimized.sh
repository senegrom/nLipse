#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/aot-common.sh"

JAR="target/nlipse.jar"
if [ ! -f "$JAR" ]; then
  mvn --batch-mode --no-transfer-progress -DskipTests package
fi

if [ -f target/nlipse.aot ] && aot_metadata_is_current "$JAR" target/nlipse.aot.meta; then
  exec java -XX:+UseCompactObjectHeaders -XX:AOTCache=target/nlipse.aot -cp "$JAR" nlipse.app.Main "$@"
fi
if [ -f target/nlipse.aot ]; then
  echo "Warning: ignoring stale or unverifiable target/nlipse.aot; recreate the cache." >&2
fi
exec java -XX:+UseCompactObjectHeaders -cp "$JAR" nlipse.app.Main "$@"
