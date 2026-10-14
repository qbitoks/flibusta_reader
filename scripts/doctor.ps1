param(
    [string]$SdkPath,
    [string]$Serial,
    [switch]$RequireEmulator
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskFailures = [System.Collections.Generic.List[string]]::new()
$taskOnWindows = $env:OS -eq 'Windows_NT'
$taskExecutableSuffix = if ($taskOnWindows) { '.exe' } else { '' }

function Report([string]$Label, [bool]$Passed, [string]$Detail) {
    if ($Passed) { Write-Host "[OK] $Label - $Detail" }
    else {
        Write-Host "[FAIL] $Label - $Detail"
        $taskFailures.Add($Label)
    }
}

function Check-File([string]$Label, [string]$Path) {
    Report $Label (Test-Path -LiteralPath $Path -PathType Leaf) $Path
}

function Invoke-Probe([string]$Executable, [string[]]$Arguments) {
    # java -version writes to stderr, including on Windows PowerShell 5.1.
    $taskPreviousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $taskOutput = & $Executable @Arguments 2>&1
        $taskExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPreviousErrorPreference }
    [pscustomobject]@{ ExitCode = $taskExitCode; Output = ($taskOutput | ForEach-Object { "$_" }) -join "`n" }
}

Write-Host 'Folio environment check (no install, build or app data changes)'
Check-File 'Gradle wrapper script' (Join-Path $taskRoot $(if ($taskOnWindows) { 'gradlew.bat' } else { 'gradlew' }))
Check-File 'Gradle wrapper JAR' (Join-Path $taskRoot 'gradle/wrapper/gradle-wrapper.jar')
$taskWrapperProperties = Join-Path $taskRoot 'gradle/wrapper/gradle-wrapper.properties'
Check-File 'Gradle wrapper properties' $taskWrapperProperties
if (Test-Path -LiteralPath $taskWrapperProperties -PathType Leaf) {
    Report 'Gradle version' ((Get-Content -LiteralPath $taskWrapperProperties -Raw) -match 'gradle-9\.8\.0-bin\.zip') 'Expected wrapper 9.8.0'
}

$taskJava = $null
if ($env:JAVA_HOME) {
    # Match gradlew.bat, which accepts a JAVA_HOME value wrapped in quotes.
    $taskJava = Join-Path $env:JAVA_HOME.Trim('"') "bin/java$taskExecutableSuffix"
    if (!(Test-Path -LiteralPath $taskJava -PathType Leaf)) {
        Report 'JAVA_HOME' $false "java not found: $taskJava"
        $taskJava = $null
    }
} else {
    $taskJavaCommand = Get-Command "java$taskExecutableSuffix" -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($taskJavaCommand) { $taskJava = $taskJavaCommand.Source }
    else { Report 'Java' $false 'Install JDK 17 and set JAVA_HOME or PATH.' }
}
if ($taskJava) {
    $taskJavaResult = Invoke-Probe $taskJava @('-version')
    Report 'JDK 17 runtime' ($taskJavaResult.ExitCode -eq 0 -and $taskJavaResult.Output -match 'version "17[\.\"]') ($taskJavaResult.Output.Split("`n")[0])
    $taskJavac = Join-Path (Split-Path $taskJava -Parent) "javac$taskExecutableSuffix"
    if (Test-Path -LiteralPath $taskJavac -PathType Leaf) {
        $taskJavacResult = Invoke-Probe $taskJavac @('-version')
        Report 'JDK 17 compiler' ($taskJavacResult.ExitCode -eq 0 -and $taskJavacResult.Output -match 'javac 17(?:\.|\s|$)') $taskJavacResult.Output
    } else { Report 'JDK compiler' $false "javac not found next to java: $taskJavac" }
}

$taskLocalSdk = $null
$taskLocalProperties = Join-Path $taskRoot 'local.properties'
if (Test-Path -LiteralPath $taskLocalProperties -PathType Leaf) {
    foreach ($taskProperty in Get-Content -LiteralPath $taskLocalProperties) {
        if ($taskProperty -match '^\s*sdk\.dir\s*[=:]\s*(.+?)\s*$') {
            $taskLocalSdk = $Matches[1].Replace('\:', ':').Replace('\\', '\')
        }
    }
}
$taskResolvedSdk = $SdkPath
if (!$taskResolvedSdk) { $taskResolvedSdk = $taskLocalSdk }
if (!$taskResolvedSdk) { $taskResolvedSdk = $env:ANDROID_HOME }
if (!$taskResolvedSdk) { $taskResolvedSdk = $env:ANDROID_SDK_ROOT }
if (!$taskResolvedSdk -and $env:LOCALAPPDATA) { $taskResolvedSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }

if (!$taskResolvedSdk) {
    Report 'Android SDK' $false 'Set ANDROID_HOME or pass -SdkPath with the installed SDK directory.'
} else {
    $taskResolvedSdk = [System.IO.Path]::GetFullPath($taskResolvedSdk)
    Report 'Android SDK directory' (Test-Path -LiteralPath $taskResolvedSdk -PathType Container) $taskResolvedSdk
    if ($SdkPath -and $taskLocalSdk) {
        Report 'SDK matches local.properties' ($taskResolvedSdk.TrimEnd('/\') -eq [System.IO.Path]::GetFullPath($taskLocalSdk).TrimEnd('/\')) 'Gradle uses sdk.dir when present; align local.properties with the SDK being checked.'
    }
    $taskPlatformJar = @('platforms/android-37/android.jar', 'platforms/android-37.0/android.jar') |
        ForEach-Object { Join-Path $taskResolvedSdk $_ } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
        Select-Object -First 1
    if (!$taskPlatformJar) { $taskPlatformJar = Join-Path $taskResolvedSdk 'platforms/android-37/android.jar' }
    Check-File 'Android platform 37' $taskPlatformJar
    $taskAdb = Join-Path $taskResolvedSdk "platform-tools/adb$taskExecutableSuffix"
    Check-File 'ADB platform-tools' $taskAdb
    if ($RequireEmulator -and !$Serial) {
        Report 'Test emulator serial' $false 'Pass -Serial emulator-NNNN from adb devices. No device is selected automatically.'
    }
    if ($Serial) {
        $taskValidSerial = $Serial -match '^emulator-\d+$'
        Report 'Test emulator serial' $taskValidSerial $Serial
        if ($taskValidSerial -and (Test-Path -LiteralPath $taskAdb -PathType Leaf)) {
            $taskState = Invoke-Probe $taskAdb @('-s', $Serial, 'get-state')
            $taskDeviceReady = $taskState.ExitCode -eq 0 -and $taskState.Output.Trim() -eq 'device'
            Report 'Emulator connection' $taskDeviceReady $taskState.Output.Trim()
            if ($taskDeviceReady) {
                $taskApi = Invoke-Probe $taskAdb @('-s', $Serial, 'shell', 'getprop', 'ro.build.version.sdk')
                $taskApiNumber = 0
                $taskApiParsed = [int]::TryParse($taskApi.Output.Trim(), [ref]$taskApiNumber)
                Report 'Emulator API >= 34' ($taskApi.ExitCode -eq 0 -and $taskApiParsed -and $taskApiNumber -ge 34) $taskApi.Output.Trim()
            }
        }
    } elseif (!$RequireEmulator) {
        Write-Host '[INFO] Emulator checks skipped. Use -RequireEmulator -Serial emulator-NNNN for UI tests.'
    }
}

if ($taskFailures.Count -gt 0) {
    Write-Host "Environment check FAILED: $($taskFailures.Count) issue(s). Nothing was installed or repaired."
    exit 1
}
Write-Host 'Environment check PASSED. Dependency downloads, build and UI tests are separate checks.'
exit 0
