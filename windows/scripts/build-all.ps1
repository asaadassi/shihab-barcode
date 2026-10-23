# Barcode Receiver (Windows): one script from the code to the installer.
#   1) runs the tests (stops on any failure)
#   2) raises the version in VERSION (patch, unless -NoBump or -Version x.y.z)
#   3) publishes the app (Release, self-contained, single file, with adb) to releases\_publish
#   4) builds the installer with Inno Setup: releases\ShihabBarcodeReceiver-Setup-<v>.exe
#   5) writes releases\SHA256SUMS-<v>.txt
# -SignThumbprint (or env SHIHAB_CODESIGN_THUMBPRINT): Authenticode-sign the app and the installer (without a
# certificate the files stay unsigned and SmartScreen warns on the first install from a browser).
# Publishing is a separate step: ShihabShared\scripts\Publish-GitHubRelease.ps1, tag "receiver-<v>" in the public
# repo asaadassi/shihab-barcode-releases (shared with the Android app, whose tags are "v<v>").
# Usage: powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 [-NoBump] [-Version x.y.z] [-SignThumbprint <sha1>]
param([switch]$NoBump, [string]$Version, [string]$SignThumbprint = $env:SHIHAB_CODESIGN_THUMBPRINT)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$versionFile = Join-Path $root "VERSION"
$iscc = "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) { throw "Inno Setup 6 not found: $iscc" }

$signtool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($SignThumbprint) {
    if (-not $signtool) { throw "signtool.exe not found: install the Windows SDK signing tools" }
    if (-not (Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert | Where-Object Thumbprint -eq $SignThumbprint)) {
        throw "Code-signing certificate $SignThumbprint not found in CurrentUser\My"
    }
} else { Write-Warning "No code-signing certificate (-SignThumbprint): files stay unsigned." }
function Sign([string[]]$files) {
    if (-not $SignThumbprint) { return }
    & $signtool.FullName sign /sha1 $SignThumbprint /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $files
    if ($LASTEXITCODE -ne 0) { throw "signtool failed (exit $LASTEXITCODE)" }
}

# 1) tests
& dotnet test (Join-Path $root "tests\BarcodeReceiver.Tests\BarcodeReceiver.Tests.csproj") -c Release
if ($LASTEXITCODE -ne 0) { throw "tests failed" }

# 2) version (SemVer)
$current = (Get-Content $versionFile -Raw).Trim()
if ($Version) { $new = $Version }
elseif ($NoBump) { $new = $current }
else { $p = $current.Split('.'); $new = "$($p[0]).$($p[1]).$([int]$p[2] + 1)" }
if ($new -notmatch '^\d+\.\d+\.\d+$') { throw "Bad version: $new" }
Set-Content $versionFile $new -NoNewline -Encoding ascii
Write-Host "Version $new"

# 3) publish
$publish = Join-Path $root "releases\_publish"
if (Test-Path $publish) { Remove-Item $publish -Recurse -Force }
& dotnet publish (Join-Path $root "src\BarcodeReceiver\BarcodeReceiver.csproj") -c Release -o $publish -p:Version=$new
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed" }
if (-not (Test-Path (Join-Path $publish "adb\adb.exe"))) { throw "adb missing from the publish folder (set -p:AdbDir=<platform-tools>)" }
Get-ChildItem $publish -Recurse -File -Filter *.pdb | Remove-Item -Force
Sign (Join-Path $publish "ShihabBarcodeReceiver.exe")

# 4) installer
& $iscc "/DMyVersion=$new" (Join-Path $root "installer\ShihabBarcodeReceiver.iss")
if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed" }
$setup = Join-Path $root "releases\ShihabBarcodeReceiver-Setup-$new.exe"
Sign $setup

# 5) checksum
$hash = (Get-FileHash $setup -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  ShihabBarcodeReceiver-Setup-$new.exe" | Set-Content (Join-Path $root "releases\SHA256SUMS-$new.txt") -Encoding ascii
Write-Host "Done: $setup"
Write-Host "SHA-256: $hash"
