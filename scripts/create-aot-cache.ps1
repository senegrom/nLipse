$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$mavenArguments = @("--batch-mode", "--no-transfer-progress", "-DskipTests", "package")
& mvn @mavenArguments
if ($LASTEXITCODE -ne 0) {
  throw "Maven exited with code $LASTEXITCODE"
}

$jar = "target/nlipse.jar"
$javaArguments = @(
  "-Djava.awt.headless=true",
  "-XX:+UseCompactObjectHeaders",
  "-XX:AOTCacheOutput=target/nlipse.aot",
  "-cp", $jar,
  "nlipse.app.AotTrainer"
)
& java @javaArguments
if ($LASTEXITCODE -ne 0) {
  throw "AOT training exited with code $LASTEXITCODE"
}

Write-Host "Created target/nlipse.aot"
