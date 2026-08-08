$ErrorActionPreference = "Stop"

$jar = "target/nlipse-0.5.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
  mvn --batch-mode --no-transfer-progress -DskipTests package
}

if (Test-Path "target/nlipse.aot") {
  java -XX:+UseCompactObjectHeaders -XX:AOTCache=target/nlipse.aot -cp $jar nlipse.app.Main @args
} else {
  java -XX:+UseCompactObjectHeaders -cp $jar nlipse.app.Main @args
}
