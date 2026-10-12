$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (!(Test-Path -LiteralPath 'local.properties')) {
    $taskSdkPath = $env:ANDROID_HOME
    if (!$taskSdkPath) { $taskSdkPath = $env:ANDROID_SDK_ROOT }
    if (!$taskSdkPath) { $taskSdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    if (!(Test-Path -LiteralPath $taskSdkPath)) { throw 'Укажите ANDROID_HOME с установленным Android SDK' }
    ('sdk.dir=' + $taskSdkPath.Replace('\', '/')) | Set-Content -Encoding ascii local.properties
}
& ./gradlew.bat :core:opds:test :core:format:test :app:assembleDebug :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с кодом $LASTEXITCODE" }
New-Item -ItemType Directory -Force artifacts | Out-Null
Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination 'artifacts/FlibustaReader-0.3.1-debug.apk' -Force
Get-Item -LiteralPath 'artifacts/FlibustaReader-0.3.1-debug.apk' | Select-Object FullName,Length
