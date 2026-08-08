#!/usr/bin/env sh
set -eu

JAR="target/nlipse.jar"
if [ ! -f "$JAR" ]; then
  mvn --batch-mode --no-transfer-progress -DskipTests package
fi

if [ -f target/nlipse.aot ]; then
  exec java -XX:+UseCompactObjectHeaders -XX:AOTCache=target/nlipse.aot -cp "$JAR" nlipse.app.Main "$@"
fi
exec java -XX:+UseCompactObjectHeaders -cp "$JAR" nlipse.app.Main "$@"
