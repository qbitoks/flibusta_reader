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
# The old ADB regressions drove the Readium UI and are retired; until new ones exist, CI proves that
# the packaged APK installs, starts, survives a rotation and a restart without crashing.
function Assert-Running([string]$step) {
    Start-Sleep -Seconds 6
    $appPid = (& $adb -s $serial shell pidof app.folio) -join ''
    if (!$appPid) { throw "App is not running after: $step" }
    $crash = (& $adb -s $serial logcat -d -b crash) -join "`n"
    if ($crash -match 'app.folio') { throw "Crash after: $step`n$crash" }
}
try {
    & $adb -s $serial logcat -c
    & $adb -s $serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK install failed' }
    & $adb -s $serial shell am start -W -n app.folio/.MainActivity | Out-Null
    Assert-Running 'first launch'
    & $adb -s $serial shell settings put system accelerometer_rotation 0
    & $adb -s $serial shell settings put system user_rotation 1
    Assert-Running 'rotation'
    & $adb -s $serial shell settings put system user_rotation 0
    & $adb -s $serial shell am force-stop app.folio
    & $adb -s $serial shell am start -W -n app.folio/.MainActivity | Out-Null
    Assert-Running 'restart'
    & $adb -s $serial shell screencap -p /sdcard/ci-launch.png
    & $adb -s $serial pull /sdcard/ci-launch.png .tools/ci-launch.png | Out-Null
    'PASS: install, launch, rotation, restart' | Tee-Object -FilePath .tools/ci-launch-result.txt
} finally {
    & $adb -s $serial logcat -d > .tools/ci-logcat.txt
}
