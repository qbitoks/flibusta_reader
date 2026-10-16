$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path -LiteralPath 'local.properties')) {
    $taskSdkPath = $env:ANDROID_HOME
    if (!$taskSdkPath) { $taskSdkPath = $env:ANDROID_SDK_ROOT }
    if (!$taskSdkPath) { $taskSdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    if (!(Test-Path -LiteralPath $taskSdkPath)) { throw 'Укажите ANDROID_HOME с установленным Android SDK' }
    ('sdk.dir=' + $taskSdkPath.Replace('\', '/').Replace(':', '\:')) | Set-Content -Encoding ascii local.properties
}
& ./gradlew.bat :core:opds:test :core:format:test :core:layout:testDebugUnitTest :core:data:testDebugUnitTest :core:speech:testDebugUnitTest :core:designsystem:testDebugUnitTest :feature:reader:testDebugUnitTest :feature:catalog:testDebugUnitTest :app:assembleRelease :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с кодом $LASTEXITCODE" }
$taskVersion = [regex]::Match((Get-Content -Raw 'app/build.gradle.kts'), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (!$taskVersion) { throw 'Не найден versionName в app/build.gradle.kts' }
$taskApk = "artifacts/Folio-$taskVersion-release.apk"
New-Item -ItemType Directory -Force artifacts | Out-Null
Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination $taskApk -Force
Get-Item -LiteralPath $taskApk | Select-Object FullName,Length
