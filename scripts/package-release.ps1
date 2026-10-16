param(
    [string]$Apk = 'app/build/outputs/apk/release/app-release.apk',
    [string]$OutDir = 'artifacts/release',
    [string]$Tag = '',
    [ValidateSet('preview','ci')][string]$SigningKind = 'ci',
    [string]$ExpectedCertificateSha256 = '',
    [string]$AndroidSdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. (Join-Path $PSScriptRoot 'ReleaseMetadata.ps1')
$metadata = Get-ReleaseMetadata (Get-Content app/build.gradle.kts -Raw) -Tag $Tag -SigningKind $SigningKind
if ($SigningKind -eq 'preview' -and $ExpectedCertificateSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'Preview packaging requires the expected certificate SHA-256' }
if (!$AndroidSdk -and (Test-Path local.properties)) {
    $AndroidSdk = ConvertFrom-JavaPropertyValue ([regex]::Match((Get-Content local.properties -Raw), '(?m)^sdk\.dir=(.+)').Groups[1].Value.Trim())
}
$toolDir = Join-Path $AndroidSdk 'build-tools/36.0.0'
$signer = Join-Path $toolDir $(if ($IsWindows) { 'apksigner.bat' } else { 'apksigner' })
$aapt = Join-Path $toolDir $(if ($IsWindows) { 'aapt.exe' } else { 'aapt' })
$certificateReport = (& $signer verify --print-certs $Apk 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $certificateReport" }
$certificate = [regex]::Match($certificateReport, 'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})').Groups[1].Value
if (!$certificate) { throw 'APK signer certificate not found' }
if ($ExpectedCertificateSha256 -and $certificate -ine $ExpectedCertificateSha256) { throw 'APK certificate differs from existing preview releases' }
$badging = (& $aapt dump badging $Apk 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot read APK manifest' }
if ($badging -notmatch "package: name='app.folio' versionCode='$($metadata.versionCode)' versionName='$([regex]::Escape($metadata.versionName))'" -or
    $badging -notmatch "(?m)^sdkVersion:'$($metadata.minSdk)'" -or
    ($badging -match '(?m)^native-code:' -and $badging -notmatch "(?m)^native-code: 'arm64-v8a' 'x86_64'$")) {
    throw 'APK manifest does not match universal release metadata'
}
$commit = (git rev-parse HEAD) -join ''
if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[a-f0-9]{40}$') { throw 'Cannot determine release commit' }
New-Item -ItemType Directory -Force $OutDir | Out-Null
$destination = Join-Path $OutDir $metadata.apkName
Copy-Item -LiteralPath $Apk -Destination $destination -Force
$upgrade = if ($SigningKind -eq 'preview') {
    'Подписан тем же тестовым ключом, что и предыдущие preview. Устанавливается поверх них, но библиотека версий 0.2.x не переносится: книги нужно добавить заново.'
} else {
    'Промежуточная CI-сборка с временным ключом. Для отдельной тестовой установки; не используйте для обновления ранее установленного preview APK.'
}
$notes = @"
# Folio v$($metadata.version)

**Файл:** ``$($metadata.apkName)``.

**Для кого:** телефоны и планшеты с Android $($metadata.minimumAndroid)+ (API $($metadata.minSdk)+). Одна release-сборка для 64-битных устройств (arm64; x86_64 для эмулятора); отдельные APK для архитектур не нужны. Для технического тестирования прототипа, не для Google Play. Для iPhone/iPad, Windows и более старого Android не подходит.

**Установка:** разрешите установку APK приложению, которым открываете файл. $upgrade

**Поддержка:** EPUB, FB2/FB2.ZIP, OPDS-каталог, библиотека с коллекциями и обложками, темы и шрифты, закладки, выделения и заметки, перелистывание «Загиб». MOBI/PDF и фоновые загрузки пока отсутствуют.

**Проверки:** JVM-тесты и Android lint выполняются при сборке. Подготовка Release разрешена только после установки и запуска на эмуляторе Android 15 (API 35). Физический телефон и прошивки производителей автоматически не проверяются.

**Версия приложения:** $($metadata.versionName), versionCode $($metadata.versionCode).
**Исходный коммит:** ``$commit``.
**SHA-256 сертификата подписи:** ``$certificate``.

``SHA256SUMS.txt`` содержит контрольные суммы вложений. Неизменяемый тег выпуска должен соответствовать версии приложения; опубликованные Release не перезаписываются pipeline.
"@
$notes | Set-Content -LiteralPath (Join-Path $OutDir 'README-release-ru.md') -Encoding utf8NoBOM
$metadata | Add-Member -NotePropertyName commit -NotePropertyValue $commit
$metadata | Add-Member -NotePropertyName certificateSha256 -NotePropertyValue $certificate
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutDir 'release-metadata.json') -Encoding utf8NoBOM
@($metadata.apkName, 'README-release-ru.md', 'release-metadata.json') | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath (Join-Path $OutDir $_) -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $_"
} | Set-Content -LiteralPath (Join-Path $OutDir 'SHA256SUMS.txt') -Encoding ascii
$metadata | ConvertTo-Json
