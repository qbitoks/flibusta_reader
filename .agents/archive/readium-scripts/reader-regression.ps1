param(
    [string]$Serial = 'emulator-5556',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",
    [string]$Apk = 'artifacts/Folio-0.2.1-debug.apk',
    [switch]$RecoverLauncherStartupAnr
)
$ErrorActionPreference = 'Stop'
if (!$Serial.StartsWith('emulator-')) { throw 'Use a test emulator' }
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path -LiteralPath $Adb)) { throw "ADB not found: $Adb. Pass -Adb with the Android SDK platform-tools path." }
New-Item -ItemType Directory -Force .tools | Out-Null
$script:launcherAnrRecovered = $false
function AdbChecked {
    $result = & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed ($LASTEXITCODE): $args" }
    $result
}
function Preferences {
    (AdbChecked shell run-as app.folio cat shared_prefs/reader.xml) -join "`n"
}
function ReadingState {
    [xml]$prefs = Preferences
    # Compare only durable reading data, not typography changed by this scenario.
    @($prefs.map.string | Where-Object { $_.name -eq 'lastBook' -or $_.name -like 'position:*' -or $_.name -like 'bookmark:*' } |
        Sort-Object name | ForEach-Object { "$($_.name)=$($_.'#text')" }) -join "`n"
}
function Ui {
    & $Adb -s $Serial shell uiautomator dump /sdcard/regression.xml 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { return @() }
    AdbChecked pull /sdcard/regression.xml .tools/regression.xml 2>$null | Out-Null
    [xml]$doc = Get-Content .tools/regression.xml -Raw
    $nodes = @($doc.SelectNodes('//node'))
    if ($RecoverLauncherStartupAnr -and ($nodes | Where-Object { $_.package -eq 'android' -and $_.text -eq "Pixel Launcher isn't responding" })) {
        if ($script:launcherAnrRecovered) { throw 'Pixel Launcher remains unresponsive after startup recovery' }
        $wait = $nodes | Where-Object { $_.package -eq 'android' -and $_.text -eq 'Wait' } | Select-Object -First 1
        if (!$wait) { throw 'Pixel Launcher startup dialog has no Wait action' }
        Tap $wait
        $script:launcherAnrRecovered = $true
        Write-Host 'Pixel Launcher startup ANR: selected Wait once'
        return @()
    }
    $nodes
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
    $bounds = [regex]::Matches($Node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    AdbChecked shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2))
}
function WaitReader {
    for ($attempt=0; $attempt -lt 10; $attempt++) {
        $web = Ui | Where-Object { $_.class -eq 'android.webkit.WebView' -and $_.SelectNodes('.//node[@text!=""]').Count -gt 0 } | Select-Object -First 1
        if ($web) { return $web }
    }
    throw 'Reader did not load'
}
function Position {
    [xml]$prefs = (& $Adb -s $Serial shell run-as app.folio cat shared_prefs/reader.xml) -join "`n"
    $id = ($prefs.map.string | Where-Object name -eq 'lastBook').'#text'
    (($prefs.map.string | Where-Object name -eq "position:$id").'#text' | ConvertFrom-Json).locations.progression
}
AdbChecked install -r $Apk | Out-Null
# Seed a completed book even on a freshly installed emulator.
AdbChecked shell am start -W -n app.folio/.MainActivity | Out-Null
Tap (Find 'Демо-книга')
WaitReader | Out-Null
AdbChecked shell input keyevent 4
Find 'Мои книги' | Out-Null
AdbChecked shell am force-stop app.folio
$indexBefore = (AdbChecked shell run-as app.folio cat files/books/library.json) -join "`n"
$readingBefore = ReadingState
$bookPaths = @($indexBefore | ConvertFrom-Json | ForEach-Object { $_.path })
$hashesBefore = (AdbChecked shell run-as app.folio sha256sum @bookPaths) -join "`n"
AdbChecked shell run-as app.folio touch files/books/review-orphan.epub files/books/review-incomplete.part
AdbChecked shell am start -W -n app.folio/.MainActivity | Out-Null
Find 'Мои книги' | Out-Null
$files = & $Adb -s $Serial shell run-as app.folio ls files/books
if ($files -contains 'review-orphan.epub' -or $files -contains 'review-incomplete.part') { throw 'Orphans were not recovered' }
$indexAfter = (AdbChecked shell run-as app.folio cat files/books/library.json) -join "`n"
if ($indexBefore -ne $indexAfter) { throw 'Recovery changed completed books' }
if ($readingBefore -ne (ReadingState)) { throw 'Recovery changed reading positions or bookmarks' }
if ($hashesBefore -ne ((AdbChecked shell run-as app.folio sha256sum @bookPaths) -join "`n")) { throw 'Recovery changed completed book contents' }
Tap (Find 'Поиск')
Tap (Find 'Авторы')
$field = Ui | Where-Object class -eq 'android.widget.EditText' | Select-Object -First 1
Tap $field
& $Adb -s $Serial shell input text ReviewAuthor
& $Adb -s $Serial shell input keyevent 4
$originalAutoRotation = (AdbChecked shell settings get system accelerometer_rotation).Trim()
$originalRotation = (AdbChecked shell settings get system user_rotation).Trim()
try {
    AdbChecked shell settings put system accelerometer_rotation 0
    AdbChecked shell settings put system user_rotation 1
    Find 'ReviewAuthor' | Out-Null
    Find 'Фамилия автора' | Out-Null
} finally {
    foreach ($setting in @(@('user_rotation', $originalRotation), @('accelerometer_rotation', $originalAutoRotation))) {
        if ($setting[1] -eq 'null') { AdbChecked shell settings delete system $setting[0] | Out-Null }
        else { AdbChecked shell settings put system $setting[0] $setting[1] }
    }
}
# Home saves the Activity Bundle; am kill preserves its task, unlike force-stop.
AdbChecked shell input keyevent 3
Start-Sleep -Milliseconds 1000
$oldProcess = (AdbChecked shell pidof app.folio) -join ''
if (!$oldProcess.Trim()) { throw 'App process missing before process-death test' }
AdbChecked shell am kill app.folio
for ($attempt=0; $attempt -lt 20; $attempt++) {
    $remaining = & $Adb -s $Serial shell pidof app.folio
    if (!$remaining) { break }
    Start-Sleep -Milliseconds 250
}
if ($remaining) { throw 'am kill did not terminate the background process' }
AdbChecked shell input keyevent 187
Tap (Find 'Folio')
Find 'Найти историю' | Out-Null
Find 'ReviewAuthor' | Out-Null
Find 'Фамилия автора' | Out-Null
$newProcess = (AdbChecked shell pidof app.folio) -join ''
if ($newProcess.Trim() -eq $oldProcess.Trim()) { throw 'Process-death test reused the original process' }
Tap (Find 'Библиотека')
Tap (Find 'Демо-книга')
$web = WaitReader
$beforeBounds = $web.bounds
$beforePosition = Position
Tap $web
Find 'Меню чтения' | Out-Null
$nodes = Ui
$afterBounds = ($nodes | Where-Object class -eq 'android.webkit.WebView' | Select-Object -First 1).bounds
if ($beforeBounds -ne $afterBounds) { throw "Viewport moved: $beforeBounds -> $afterBounds" }
if ($nodes | Where-Object { $_.'content-desc' -in @('Предыдущая страница','Следующая страница') }) { throw 'Page arrows remain in menu' }
Tap (Find 'Настройки текста')
$increase = Find 'Увеличить: Размер текста'
1..3 | ForEach-Object { Tap $increase }
Tap (Find 'Готово')
Find 'Меню чтения' | Out-Null
# Wait for the action row to be enabled again after the CSS layout settles.
for ($attempt=0; $attempt -lt 10; $attempt++) {
    $action = Ui | Where-Object { $_.'content-desc' -eq 'Настройки текста' -and $_.enabled -eq 'true' } | Select-Object -First 1
    if ($action) { break }
}
$afterPosition = Position
if ([Math]::Abs($afterPosition-$beforePosition) -gt .04) { throw "Typography drift: $beforePosition -> $afterPosition" }
Tap (Find 'Вернуться в библиотеку')
Find 'Мои книги' | Out-Null
[pscustomobject]@{ Result='PASS'; Recovery=$true; CompletedBookContents=$true; ReadingStatePreserved=$true; RotationQuery=$true; ProcessDeathQuery=$true; OldProcess=$oldProcess.Trim(); NewProcess=$newProcess.Trim(); StableViewport=$beforeBounds; ArrowsRemoved=$true; TypographyBefore=$beforePosition; TypographyAfter=$afterPosition } | ConvertTo-Json
