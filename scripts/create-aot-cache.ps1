$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "aot-common.ps1")

$mavenArguments = @("--batch-mode", "--no-transfer-progress", "-DskipTests", "package")
& mvn @mavenArguments
if ($LASTEXITCODE -ne 0) {
  throw "Maven exited with code $LASTEXITCODE"
}

$jar = "target/nlipse.jar"
$metadata = "target/nlipse.aot.meta"
if (Test-Path -LiteralPath $metadata) {
  Remove-Item -LiteralPath $metadata -Force
}
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
Write-AotMetadata -JarPath $jar -MetadataPath $metadata

Write-Host "Created target/nlipse.aot and target/nlipse.aot.meta"
