param(
    [string]$Serial = 'emulator-5556',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",
    [switch]$FailAfterBatteryOverride
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
function Wait-Description([string]$Description) {
    for ($taskAttempt = 0; $taskAttempt -lt 8; $taskAttempt++) {
        $taskNode = Read-Ui | Where-Object { $_.'content-desc' -eq $Description -and $_.enabled -eq 'true' } | Select-Object -First 1
        if ($taskNode) { return $taskNode }
    }
    throw "Не найдено действие: $Description"
}
function Tap-Node($Node) {
    $taskBounds = [regex]::Matches($Node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb -s $Serial shell input tap ([int](($taskBounds[0] + $taskBounds[2]) / 2)) ([int](($taskBounds[1] + $taskBounds[3]) / 2))
}
function Tap-Description([string]$Description) { Tap-Node (Wait-Description $Description) }
function Tap-Reading([double]$Fraction = .5) {
    $taskView = Read-Ui | Where-Object { $_.class -eq 'android.webkit.WebView' } | Select-Object -First 1
    if (!$taskView) { throw 'Не найдено окно Readium' }
    $taskBounds = [regex]::Matches($taskView.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $Adb -s $Serial shell input tap ([int]($taskBounds[0] + ($taskBounds[2]-$taskBounds[0]) * $Fraction)) ([int](($taskBounds[1]+$taskBounds[3])/2))
}
function Save-Screen([string]$Name) {
    & $Adb -s $Serial shell screencap -p /sdcard/reader-smoke.png
    & $Adb -s $Serial pull /sdcard/reader-smoke.png "artifacts/$Name.png" 2>$null | Out-Null
}
function Open-Demo {
    & $Adb -s $Serial shell am start -W -n net.flibustareader/.MainActivity | Out-Null
    Tap-Text 'Демо-книга'
    $taskNodes = Read-Ui
    if ($taskNodes | Where-Object text -eq 'Got it') { Tap-Text 'Got it' }
    # The permanent HUD appears before the EPUB loads; wait for its WebView as well.
    for ($taskAttempt = 0; $taskAttempt -lt 8; $taskAttempt++) {
        if (Read-Ui | Where-Object class -eq 'android.webkit.WebView') { return }
    }
    throw 'Демонстрационная книга не открылась'
}
function Preference-Bool([string]$Name) { return ((Read-Preferences).map.boolean | Where-Object name -eq $Name).value -eq 'true' }

& $Adb -s $Serial shell am force-stop net.flibustareader
Open-Demo
Tap-Reading
Tap-Description 'Меню чтения'
Tap-Text 'Показать все индикаторы'
Tap-Description 'Настройки текста'
Tap-Text 'Сепия'
if (Preference-Bool 'scroll') { Tap-Description 'Прокрутка текста' }
Tap-Description 'Увеличить: Размер текста'
Tap-Description 'Увеличить: Межстрочный интервал'
Tap-Description 'Увеличить: Поля'
if (Preference-Bool 'systemBrightness') { Tap-Text 'Яркость как в системе' }
Tap-Text 'Готово'
Wait-Description 'Меню чтения' | Out-Null
Tap-Description 'Оглавление'
Tap-Text 'Глава 3. Время читать'
Wait-Progress .35 | Out-Null
$taskBeforePanels = Wait-StablePosition
Tap-Reading
Wait-Description 'Скрыть время' | Out-Null
$taskAfterPanels = Wait-StablePosition
if ([Math]::Abs($taskBeforePanels.locations.progression - $taskAfterPanels.locations.progression) -gt .0005) { throw 'Показ панелей меняет позицию книги' }

try {
& $Adb -s $Serial shell dumpsys battery set level 73
if ($FailAfterBatteryOverride) { throw 'Injected battery cleanup test failure' }
$taskHudNames = @('время','прогресс книги','заряд телефона')
$taskHudBounds = @{}
Write-Output 'Проверка независимых индикаторов'
foreach ($taskName in $taskHudNames) { $taskHudBounds[$taskName] = (Wait-Description "Скрыть $taskName").bounds }
foreach ($taskName in $taskHudNames) {
    Tap-Description "Скрыть $taskName"
    $taskHidden = Wait-Description "Показать $taskName"
    if ($taskHidden.bounds -ne $taskHudBounds[$taskName]) { throw 'Место скрытого индикатора изменилось' }
    foreach ($taskOther in $taskHudNames | Where-Object { $_ -ne $taskName }) {
        if ((Wait-Description "Скрыть $taskOther").bounds -ne $taskHudBounds[$taskOther]) { throw 'Другой индикатор сдвинулся' }
    }
    Tap-Node $taskHidden
    Wait-Description "Скрыть $taskName" | Out-Null
}
$taskBatteryNode = Wait-Description 'Скрыть заряд телефона'
if (!($taskBatteryNode.ParentNode.SelectNodes('.//node') | Where-Object text -eq '73%')) { throw 'Индикатор заряда не обновляется' }
Save-Screen 'reader-0.2-sepia'
foreach ($taskName in $taskHudNames) { Tap-Description "Скрыть $taskName"; Wait-Description "Показать $taskName" | Out-Null }
Save-Screen 'reader-0.2-hidden-hud'
Write-Output 'Проверка перезапуска со скрытыми индикаторами'
$taskBeforeRestart = Wait-StablePosition
& $Adb -s $Serial shell am force-stop net.flibustareader
Open-Demo
foreach ($taskName in $taskHudNames) { Wait-Description "Показать $taskName" | Out-Null }
$taskAfterRestart = Wait-StablePosition
if ([Math]::Abs($taskBeforeRestart.locations.progression - $taskAfterRestart.locations.progression) -gt .02) { throw "Позиция не восстановилась: $($taskBeforeRestart.locations.progression) -> $($taskAfterRestart.locations.progression)" }
foreach ($taskName in $taskHudNames) { Tap-Description "Показать $taskName"; Wait-Description "Скрыть $taskName" | Out-Null }
Write-Output 'Проверка перелистывания и тем'
Tap-Reading .9
Wait-Progress ($taskAfterRestart.locations.progression + .005) | Out-Null
Tap-Reading
Wait-Description 'Меню чтения' | Out-Null
Save-Screen 'reader-0.2-panels'
Tap-Description 'Настройки текста'
Save-Screen 'reader-0.2-settings'
Tap-Text 'Серый'
Tap-Text 'Готово'
Wait-Description 'Меню чтения' | Out-Null
Tap-Reading
Wait-Description 'Скрыть время' | Out-Null
Save-Screen 'reader-0.2-gray'
Tap-Reading
Tap-Description 'Настройки текста'
Tap-Text 'Сепия'
if (!(Preference-Bool 'systemBrightness')) { Tap-Text 'Яркость как в системе' }
Tap-Text 'Готово'
Wait-Description 'Меню чтения' | Out-Null
Tap-Description 'Вернуться в библиотеку'
Wait-Text 'Мои книги' | Out-Null
& $Adb -s $Serial shell dumpsys battery reset
[pscustomobject]@{Result='PASS'; BeforeRestart=$taskBeforeRestart.locations.progression; Restored=$taskAfterRestart.locations.progression; IndependentIndicators=$true; HiddenPositionsStable=$true; BatteryBroadcast=$true; Themes=$true; Typography=$true} | ConvertTo-Json

} finally { & $Adb -s $Serial shell dumpsys battery reset }
