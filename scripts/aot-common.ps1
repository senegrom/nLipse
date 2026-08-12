function Get-AotRuntimeMarkerPath {
  $javaCommand = Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1
  $javaExecutable = $javaCommand.Source
  $javaHome = Split-Path (Split-Path $javaExecutable -Parent) -Parent
  $releaseFile = Join-Path $javaHome "release"
  if ([IO.File]::Exists($releaseFile)) {
    return [IO.Path]::GetFullPath($releaseFile)
  }
  return $javaExecutable
}

function Get-AotMetadataLines {
  param([Parameter(Mandatory)][string]$JarPath)

  $jarHash = (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()
  $runtimeMarker = Get-AotRuntimeMarkerPath
  $runtimeHash = (Get-FileHash -LiteralPath $runtimeMarker -Algorithm SHA256).Hash.ToLowerInvariant()
  return [string[]]@(
    "format=1",
    "jar.sha256=$jarHash",
    "runtime.sha256=$runtimeHash"
  )
}

function Write-AotMetadata {
  param(
    [Parameter(Mandatory)][string]$JarPath,
    [Parameter(Mandatory)][string]$MetadataPath
  )

  $lines = Get-AotMetadataLines -JarPath $JarPath
  $fullPath = [IO.Path]::GetFullPath($MetadataPath)
  $directory = [IO.Path]::GetDirectoryName($fullPath)
  $temporary = [IO.Path]::Combine($directory,
    "." + [IO.Path]::GetFileName($fullPath) + "." + [Guid]::NewGuid().ToString("N") + ".tmp")
  try {
    [IO.File]::WriteAllLines($temporary, $lines, [Text.Encoding]::ASCII)
    # Move-Item -Force overwrites on both Windows PowerShell 5.1 and pwsh 7;
    # the three-argument [IO.File]::Move overload only exists on .NET Core.
    Move-Item -LiteralPath $temporary -Destination $fullPath -Force
  } finally {
    if ([IO.File]::Exists($temporary)) {
      [IO.File]::Delete($temporary)
    }
  }
}

function Test-AotMetadata {
  param(
    [Parameter(Mandatory)][string]$JarPath,
    [Parameter(Mandatory)][string]$MetadataPath
  )

  if (-not (Test-Path -LiteralPath $MetadataPath -PathType Leaf)) {
    return $false
  }
  try {
    $expected = @(Get-AotMetadataLines -JarPath $JarPath)
    $actual = @([IO.File]::ReadAllLines($MetadataPath, [Text.Encoding]::ASCII))
    if ($expected.Count -ne $actual.Count) {
      return $false
    }
    for ($index = 0; $index -lt $expected.Count; $index++) {
      if ($expected[$index] -cne $actual[$index]) {
        return $false
      }
    }
    return $true
  } catch {
    return $false
  }
}
