param([string]$Serial = 'emulator-5556', [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
if (!$Serial.StartsWith('emulator-')) { throw 'Use a test emulator' }
Set-Location (Split-Path $PSScriptRoot -Parent)
function Ui {
    & $Adb -s $Serial shell uiautomator dump /sdcard/regression.xml 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { return @() }
    & $Adb -s $Serial pull /sdcard/regression.xml .tools/regression.xml 2>$null | Out-Null
    [xml]$doc = Get-Content .tools/regression.xml -Raw
    @($doc.SelectNodes('//node'))
}
function Find([string]$Value) {
    for ($attempt=0; $attempt -lt 10; $attempt++) {
        $node = Ui | Where-Object { $_.text -eq $Value -or $_.'content-desc' -eq $Value } | Select-Object -First 1
        if ($node) { return $node }
    }
    throw "Missing $Value"
}
function Tap($Node) {
    $bounds = [regex]::Matches($Node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    & $Adb -s $Serial shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2))
}
function Position {
    [xml]$prefs = (& $Adb -s $Serial shell run-as net.flibustareader cat shared_prefs/reader.xml) -join "`n"
    $id = ($prefs.map.string | Where-Object name -eq 'lastBook').'#text'
    (($prefs.map.string | Where-Object name -eq "position:$id").'#text' | ConvertFrom-Json).locations.progression
}
& $Adb -s $Serial install -r artifacts/FlibustaReader-0.2.1-debug.apk | Out-Null
& $Adb -s $Serial shell am force-stop net.flibustareader
$indexBefore = (& $Adb -s $Serial shell run-as net.flibustareader cat files/books/library.json) -join "`n"
& $Adb -s $Serial shell run-as net.flibustareader touch files/books/review-orphan.epub files/books/review-incomplete.part
& $Adb -s $Serial shell am start -W -n net.flibustareader/.MainActivity | Out-Null
Find 'Мои книги' | Out-Null
$files = & $Adb -s $Serial shell run-as net.flibustareader ls files/books
if ($files -contains 'review-orphan.epub' -or $files -contains 'review-incomplete.part') { throw 'Orphans were not recovered' }
$indexAfter = (& $Adb -s $Serial shell run-as net.flibustareader cat files/books/library.json) -join "`n"
if ($indexBefore -ne $indexAfter) { throw 'Recovery changed completed books' }
Tap (Find 'Поиск')
Tap (Find 'Авторы')
$field = Ui | Where-Object class -eq 'android.widget.EditText' | Select-Object -First 1
Tap $field
& $Adb -s $Serial shell input text ReviewAuthor
& $Adb -s $Serial shell input keyevent 4
try {
    & $Adb -s $Serial shell settings put system accelerometer_rotation 0
    & $Adb -s $Serial shell settings put system user_rotation 1
    Find 'ReviewAuthor' | Out-Null
    Find 'Фамилия автора' | Out-Null
} finally { & $Adb -s $Serial shell settings put system user_rotation 0 }
Tap (Find 'Библиотека')
Tap (Find 'Демо-книга')
for ($attempt=0; $attempt -lt 10; $attempt++) {
    $web = Ui | Where-Object { $_.class -eq 'android.webkit.WebView' -and $_.SelectNodes('.//node[@text!=""]').Count -gt 0 } | Select-Object -First 1
    if ($web) { break }
}
if (!$web) { throw 'Reader did not load' }
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
[pscustomobject]@{ Result='PASS'; Recovery=$true; RotationQuery=$true; StableViewport=$beforeBounds; ArrowsRemoved=$true; TypographyBefore=$beforePosition; TypographyAfter=$afterPosition } | ConvertTo-Json
