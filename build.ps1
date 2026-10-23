# Barcode (Shihab App): unit tests, then the signed GitHub APK and the Google Play AAB into dist\, with SHA-256.
#   .\build.ps1                  build the version in app\build.gradle.kts
#   .\build.ps1 -Version 1.0.1   set that version first (appVersionName, and appVersionCode + 1)
param([string]$Version)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$gradleFile = Join-Path $root 'app\build.gradle.kts'

if (-not $env:JAVA_HOME -and (Test-Path 'D:\SDKs\jdk-17')) { $env:JAVA_HOME = 'D:\SDKs\jdk-17' }
if (-not (Test-Path (Join-Path $root 'keystore.properties'))) { throw 'keystore.properties is missing: the release would not be signed.' }

$text = Get-Content $gradleFile -Raw -Encoding utf8
if ($Version) {
    if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Version must be SemVer x.y.z: $Version" }
    $code = [int]([regex]::Match($text, 'val appVersionCode = (\d+)').Groups[1].Value) + 1
    $text = $text -replace 'val appVersionName = "[^"]+"', "val appVersionName = `"$Version`"" `
                  -replace 'val appVersionCode = \d+', "val appVersionCode = $code"
    [IO.File]::WriteAllText($gradleFile, $text, (New-Object Text.UTF8Encoding $false))
}
$name = [regex]::Match($text, 'val appVersionName = "([^"]+)"').Groups[1].Value
Write-Host "Barcode $name"

Push-Location $root
try {
    # Windows PowerShell turns any stderr line of a native program (JVM warnings) into an error: judge Gradle by its
    # exit code only.
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat --no-configuration-cache testDirectDebugUnitTest :app:assembleDirectRelease :app:bundlePlayRelease 2>&1 |
        ForEach-Object { "$_" }
    $gradleExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($gradleExit -ne 0) { throw "Gradle failed ($gradleExit)" }
} finally { Pop-Location }

$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
$apk = Join-Path $dist "ShihabBarcode-android-$name.apk"
$aab = Join-Path $dist "ShihabBarcode-play-$name.aab"
Copy-Item (Join-Path $root 'app\build\outputs\apk\direct\release\app-direct-release.apk') $apk -Force
Copy-Item (Join-Path $root 'app\build\outputs\bundle\playRelease\app-play-release.aab') $aab -Force

$sums = foreach ($f in $apk, $aab) { '{0}  {1}' -f (Get-FileHash $f -Algorithm SHA256).Hash.ToLowerInvariant(), (Split-Path $f -Leaf) }
[IO.File]::WriteAllLines((Join-Path $dist "SHA256SUMS-$name.txt"), $sums)
$sums | ForEach-Object { Write-Host $_ }
