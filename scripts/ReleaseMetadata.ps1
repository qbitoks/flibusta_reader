function ConvertFrom-JavaPropertyValue([string]$Value) {
    [regex]::Replace($Value, '\\(u[0-9a-fA-F]{4}|.)', {
        param($match)
        $escaped = $match.Groups[1].Value
        if ($escaped.Length -eq 5 -and $escaped.StartsWith('u')) { return [string][char][Convert]::ToInt32($escaped.Substring(1), 16) }
        switch ($escaped) {
            't' { "`t" }
            'n' { "`n" }
            'r' { "`r" }
            'f' { [string][char]12 }
            default { $escaped }
        }
    })
}

function Get-ReleaseMetadata {
    param([Parameter(Mandatory)][string]$GradleText, [string]$Tag = '', [ValidateSet('preview','ci')][string]$SigningKind = 'ci')
    $versionMatch = [regex]::Match($GradleText, 'versionName\s*=\s*"([^"]+)"')
    $codeMatch = [regex]::Match($GradleText, 'versionCode\s*=\s*(\d+)')
    $sdkMatch = [regex]::Match($GradleText, 'minSdk\s*=\s*(\d+)')
    if (!$versionMatch.Success -or !$codeMatch.Success -or !$sdkMatch.Success) { throw 'Missing Android version metadata' }
    $versionName = $versionMatch.Groups[1].Value
    $version = [regex]::Match($versionName, '^\d+\.\d+\.\d+(?=-|$)').Value
    if (!$version -or [int]$codeMatch.Groups[1].Value -lt 1) { throw 'Invalid Android version metadata' }
    if ($Tag -and $Tag -cne "v$version") { throw "Tag $Tag does not match Android version v$version" }
    $android = @{34='14'; 35='15'; 36='16'}[[int]$sdkMatch.Groups[1].Value]
    if (!$android) { throw 'Update Android compatibility mapping before releasing this minSdk' }
    [pscustomobject]@{
        version = $version
        versionName = $versionName
        versionCode = [int]$codeMatch.Groups[1].Value
        minSdk = [int]$sdkMatch.Groups[1].Value
        minimumAndroid = $android
        signingKind = $SigningKind
        apkName = "FlibustaReader-v$version-android${android}plus-universal-$SigningKind-debug.apk"
        apkLabel = "Android $android+ · телефоны и планшеты · универсальная $SigningKind debug-сборка"
    }
}
