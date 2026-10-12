param(
    [string]$Serial = 'emulator-5556',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!$Serial.StartsWith('emulator-')) { throw 'Этот сценарий предназначен для тестового эмулятора.' }
New-Item -ItemType Directory -Force artifacts | Out-Null
$taskUiFile = Join-Path (Get-Location) 'artifacts/smoke-ui.xml'
function Read-Ui {
    & $Adb -s $Serial shell uiautomator dump /sdcard/reader-smoke.xml 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { return @() }
    & $Adb -s $Serial pull /sdcard/reader-smoke.xml $taskUiFile 2>$null | Out-Null
    [xml]$taskDocument = Get-Content -LiteralPath $taskUiFile -Raw
    return @($taskDocument.SelectNodes('//node') | Where-Object { $_.bounds -ne '[0,0][0,0]' })
}
function Wait-Text([string]$Text) {
    for ($taskAttempt = 0; $taskAttempt -lt 8; $taskAttempt++) {
        $taskNode = Read-Ui | Where-Object { $_.text -eq $Text -and $_.enabled -eq 'true' } | Select-Object -First 1
        if ($taskNode) { return $taskNode }
    }
    throw "Элемент не найден: $Text"
}
function Tap-Text([string]$Text) {
    $taskNode = Wait-Text $Text
    $taskBounds = [regex]::Matches($taskNode.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb -s $Serial shell input tap ([int](($taskBounds[0] + $taskBounds[2]) / 2)) ([int](($taskBounds[1] + $taskBounds[3]) / 2))
}
function Read-Preferences {
    $taskRaw = & $Adb -s $Serial shell run-as net.flibustareader cat shared_prefs/reader.xml
    [xml]$taskPreferences = $taskRaw -join "`n"
    return $taskPreferences
}
function Read-Position {
    $taskPreferences = Read-Preferences
    $taskId = ($taskPreferences.map.string | Where-Object name -eq 'lastBook').'#text'
    $taskPosition = ($taskPreferences.map.string | Where-Object name -eq "position:$taskId").'#text'
    return $taskPosition | ConvertFrom-Json
}
function Wait-Progress([double]$Minimum) {
    for ($taskAttempt = 0; $taskAttempt -lt 20; $taskAttempt++) {
        $taskPosition = Read-Position
        if ($taskPosition.locations.progression -ge $Minimum) { return $taskPosition }
        Start-Sleep -Milliseconds 500
    }
    throw "Позиция не достигла $Minimum"
}
function Wait-StablePosition {
    $taskLast = -1.0
    $taskStable = 0
    for ($taskAttempt = 0; $taskAttempt -lt 20; $taskAttempt++) {
        $taskPosition = Read-Position
        if ([Math]::Abs($taskPosition.locations.progression - $taskLast) -lt .0001) { $taskStable++ } else { $taskStable = 0 }
        if ($taskStable -ge 4) { return $taskPosition }
        $taskLast = $taskPosition.locations.progression
        Start-Sleep -Milliseconds 500
    }
    throw 'Позиция не стабилизировалась после перелистывания'
}
& $Adb -s $Serial shell am force-stop net.flibustareader
& $Adb -s $Serial shell am start -W -n net.flibustareader/.MainActivity | Out-Null
Tap-Text 'Демо-книга'
Wait-Text 'Главы' | Out-Null
Tap-Text 'Aa'
Tap-Text 'Страницы'
Tap-Text 'Светлая'
Tap-Text 'Aa'
Tap-Text 'Главы'
Tap-Text 'Глава 3. Время читать'
$taskBefore = Wait-Progress 0.35
$taskBefore = Wait-StablePosition
Tap-Text '→'
$taskBefore = Wait-Progress ($taskBefore.locations.progression + .005)
$taskBefore = Wait-StablePosition
& $Adb -s $Serial shell am force-stop net.flibustareader
& $Adb -s $Serial shell am start -W -n net.flibustareader/.MainActivity | Out-Null
Tap-Text 'Демо-книга'
Wait-Text 'Главы' | Out-Null
$taskAfter = Wait-Progress 0.35
$taskAfter = Wait-StablePosition
if ([Math]::Abs($taskAfter.locations.progression - $taskBefore.locations.progression) -gt .02) { throw "Позиция чтения не восстановлена: $($taskBefore.locations.progression) -> $($taskAfter.locations.progression)" }
Tap-Text 'Aa'
Tap-Text 'Прокрутка'
Tap-Text 'Тёмная'
Tap-Text 'Aa'
$taskPreferences = Read-Preferences
if (($taskPreferences.map.boolean | Where-Object name -eq 'scroll').value -ne 'true') { throw 'Прокрутка не сохранена' }
if (($taskPreferences.map.string | Where-Object name -eq 'theme').'#text' -ne 'DARK') { throw 'Тема не сохранена' }
& $Adb -s $Serial shell screencap -p /sdcard/reader-smoke.png
& $Adb -s $Serial pull /sdcard/reader-smoke.png artifacts/reader-dark.png 2>$null | Out-Null
Tap-Text '← Книги'
Wait-Text 'Мои книги' | Out-Null
& $Adb -s $Serial shell screencap -p /sdcard/reader-smoke.png
& $Adb -s $Serial pull /sdcard/reader-smoke.png artifacts/library-phone.png 2>$null | Out-Null
[pscustomobject]@{Result='PASS'; Before=$taskBefore.locations.progression; Restored=$taskAfter.locations.progression; Scroll=$true; Theme='DARK'} | ConvertTo-Json
