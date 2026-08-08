$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "aot-common.ps1")

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
  if (Test-AotMetadata -JarPath $jar -MetadataPath "target/nlipse.aot.meta") {
    $javaArguments += "-XX:AOTCache=target/nlipse.aot"
  } else {
    Write-Warning "Ignoring stale or unverifiable target/nlipse.aot; recreate the cache."
  }
}
$javaArguments += @("-cp", $jar, "nlipse.app.Main")
$javaArguments += $args
& java @javaArguments
if ($LASTEXITCODE -ne 0) {
  throw "nLipse exited with code $LASTEXITCODE"
}
