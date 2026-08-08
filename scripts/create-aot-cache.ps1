$ErrorActionPreference = "Stop"

mvn --batch-mode --no-transfer-progress -DskipTests package
$jar = "target/nlipse-0.5.0-SNAPSHOT.jar"
java -Djava.awt.headless=true -XX:+UseCompactObjectHeaders `
  -XX:AOTCacheOutput=target/nlipse.aot `
  -cp $jar nlipse.app.AotTrainer

Write-Host "Created target/nlipse.aot"
