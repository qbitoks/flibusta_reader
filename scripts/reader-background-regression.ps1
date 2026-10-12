param(
    [string]$Serial = 'emulator-5556',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",
    [string]$Apk = 'artifacts/FlibustaReader-0.2.1-debug.apk'
)
$ErrorActionPreference = 'Stop'
if (!$Serial.StartsWith('emulator-')) { throw 'Use a test emulator' }
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Force .tools | Out-Null
$uiPath = Join-Path (Get-Location) '.tools/background-regression.xml'
$checkpoint = 'files/reader-opening-checkpoint'
function AdbChecked {
    $result = & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed ($LASTEXITCODE): $args" }
    $result
}
function Ui {
    & $Adb -s $Serial shell uiautomator dump /sdcard/reader-background.xml 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { return @() }
    AdbChecked pull /sdcard/reader-background.xml $uiPath 2>$null | Out-Null
    [xml]$document = Get-Content -LiteralPath $uiPath -Raw
    @($document.SelectNodes('//node'))
}
function Find([string]$Value) {
    for ($attempt=0; $attempt -lt 10; $attempt++) {
        $node = Ui | Where-Object { $_.text -eq $Value -or $_.'content-desc' -eq $Value } | Select-Object -First 1
        if ($node) { return $node }
    }
    throw "Missing $Value"
}
function Tap($Node) {
    if (!$Node) { throw 'Cannot tap a missing UI node' }
    $bounds = [regex]::Matches($Node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    AdbChecked shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2))
}
function WaitReader {
    for ($attempt=0; $attempt -lt 10; $attempt++) {
        $web = Ui | Where-Object { $_.class -eq 'android.webkit.WebView' -and $_.SelectNodes('.//node[@text!=""]').Count -gt 0 } | Select-Object -First 1
        if ($web) { return $web }
    }
    throw 'Reader text did not load'
}
function MarkerExists([string]$Name) {
    $names = @(AdbChecked shell run-as net.flibustareader ls $checkpoint)
    $names -contains $Name
}
function WaitMarker([string]$Name) {
    for ($attempt=0; $attempt -lt 80; $attempt++) {
        if (MarkerExists $Name) { return }
        Start-Sleep -Milliseconds 250
    }
    throw "Opening checkpoint not reached: $Name"
}
function AssertStopped {
    $state = (AdbChecked shell dumpsys activity activities) -join "`n"
    $records = [regex]::Matches($state, '(?ms)^\s+\* Hist[^\r\n]*\r?\n.*?(?=^\s+\* Hist|\z)')
    $reader = $records | Where-Object { $_.Value -match '\A[^\r\n]*net\.flibustareader/\.ReaderActivity' } | Select-Object -First 1
    if (!$reader -or [regex]::Match($reader.Value, '(?m)^\s+state=(\w+)').Groups[1].Value -ne 'STOPPED') {
        throw 'ReaderActivity is not confirmed STOPPED'
    }
    if ($reader.Value -notmatch 'mHaveState=true') { throw 'ReaderActivity has not saved its instance state' }
}
AdbChecked install -r $Apk | Out-Null
AdbChecked shell am force-stop net.flibustareader
AdbChecked shell am start -W -n net.flibustareader/.MainActivity | Out-Null
Tap (Find 'Демо-книга')
WaitReader | Out-Null
AdbChecked shell input keyevent 4
Find 'Мои книги' | Out-Null
AdbChecked shell run-as net.flibustareader mkdir -p $checkpoint
try {
    AdbChecked shell run-as net.flibustareader rm -f "$checkpoint/hold" "$checkpoint/reached" "$checkpoint/released" "$checkpoint/attached"
    AdbChecked shell run-as net.flibustareader touch "$checkpoint/hold"
    Tap (Find 'Демо-книга')
    WaitMarker 'reached'
    AdbChecked shell input keyevent 3
    for ($attempt=0; $attempt -lt 20; $attempt++) {
        try { AssertStopped; break } catch { if ($attempt -eq 19) { throw }; Start-Sleep -Milliseconds 250 }
    }
    AdbChecked shell run-as net.flibustareader rm -f "$checkpoint/hold"
    WaitMarker 'released'
    # Let the coroutine reach the lifecycle guard, still with the app in the background.
    Start-Sleep -Milliseconds 1000
    AssertStopped
    if (MarkerExists 'attached') { throw 'Navigator attached while ReaderActivity was STOPPED' }
    $backgroundState = (AdbChecked shell dumpsys activity activities) -join "`n"
    $backgroundState | Set-Content -LiteralPath .tools/background-regression-state.txt
    AdbChecked shell input keyevent 187
    Tap (Find 'Flibusta Reader')
    WaitMarker 'attached'
    $web = WaitReader
    Tap $web
    Find 'Меню чтения' | Out-Null
    Tap (Find 'Вернуться в библиотеку')
    Find 'Мои книги' | Out-Null
    [pscustomobject]@{ Result='PASS'; PreparedWhileStopped=$true; AttachedOnlyAfterResume=$true; ReaderText=$true; ReaderControls=$true } | ConvertTo-Json
} finally {
    # Delete only this scenario's explicitly named marker files; never app books or preferences.
    AdbChecked shell run-as net.flibustareader rm -f "$checkpoint/hold" "$checkpoint/reached" "$checkpoint/released" "$checkpoint/attached"
}
