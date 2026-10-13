$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path -LiteralPath 'local.properties')) {
    $taskSdkPath = $env:ANDROID_HOME
    if (!$taskSdkPath) { $taskSdkPath = $env:ANDROID_SDK_ROOT }
    if (!$taskSdkPath) { $taskSdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    if (!(Test-Path -LiteralPath $taskSdkPath)) { throw 'Укажите ANDROID_HOME с установленным Android SDK' }
    ('sdk.dir=' + $taskSdkPath.Replace('\', '/').Replace(':', '\:')) | Set-Content -Encoding ascii local.properties
}
& ./gradlew.bat :core:opds:test :core:format:test :feature:reader:testDebugUnitTest :app:assembleRelease :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с кодом $LASTEXITCODE" }
New-Item -ItemType Directory -Force artifacts | Out-Null
Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination 'artifacts/FlibustaReader-0.3.7-release.apk' -Force
Get-Item -LiteralPath 'artifacts/FlibustaReader-0.3.7-release.apk' | Select-Object FullName,Length
