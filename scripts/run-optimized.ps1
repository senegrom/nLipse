$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$jar = "target/nlipse.jar"
if (-not (Test-Path $jar)) {
  $mavenArguments = @("--batch-mode", "--no-transfer-progress", "-DskipTests", "package")
  & mvn @mavenArguments
  if ($LASTEXITCODE -ne 0) {
    throw "Maven exited with code $LASTEXITCODE"
  }
}

$javaArguments = @("-XX:+UseCompactObjectHeaders")
if (Test-Path "target/nlipse.aot") {
  $javaArguments += "-XX:AOTCache=target/nlipse.aot"
}
$javaArguments += @("-cp", $jar, "nlipse.app.Main")
$javaArguments += $args
& java @javaArguments
if ($LASTEXITCODE -ne 0) {
  throw "nLipse exited with code $LASTEXITCODE"
}
