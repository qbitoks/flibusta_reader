$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Force .tools | Out-Null
$metadata = Get-Content artifacts/release/release-metadata.json -Raw | ConvertFrom-Json
$apk = Join-Path 'artifacts/release' $metadata.apkName
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb'
$serial = "emulator-$env:EMULATOR_PORT"
if (!$env:EMULATOR_PORT) { throw 'EMULATOR_PORT is required' }
# Fresh AVDs show a system "Viewing full screen" overlay on first immersive use.
# This is test-device setup, not reader state or dismissal of application errors.
& $adb -s $serial shell settings put secure immersive_mode_confirmations confirmed
if ($LASTEXITCODE -ne 0) { throw 'Cannot configure first-use immersive confirmation on the test emulator' }
# Validate the downloaded package before installing it.
foreach ($line in Get-Content artifacts/release/SHA256SUMS.txt) {
    if ($line -notmatch '^([a-f0-9]{64})  ([\w.\-]+)$') { throw 'Invalid checksum entry' }
    $expected = $Matches[1]
    $path = Join-Path 'artifacts/release' $Matches[2]
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $expected) { throw "Checksum mismatch: $path" }
}
try {
    ./scripts/reader-background-regression.ps1 -Serial $serial -Adb $adb -Apk $apk -RecoverLauncherStartupAnr | Tee-Object -FilePath .tools/ci-background-result.txt
    ./scripts/reader-regression.ps1 -Serial $serial -Adb $adb -Apk $apk -RecoverLauncherStartupAnr | Tee-Object -FilePath .tools/ci-reader-result.txt
} finally {
    & $adb -s $serial logcat -d > .tools/ci-logcat.txt
}
