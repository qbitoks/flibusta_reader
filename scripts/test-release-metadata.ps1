$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ReleaseMetadata.ps1')
function AssertEqual($Actual, $Expected) {
    if ($Actual -cne $Expected) { throw "Expected '$Expected', got '$Actual'" }
}
function AssertThrows([scriptblock]$Action, [string]$Message) {
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -like "*$Message*") { return }
        throw
    }
    throw "Expected failure: $Message"
}
$source = 'versionName = "0.2.1-reader-design"; versionCode = 3; minSdk = 34'
$metadata = Get-ReleaseMetadata $source -Tag v0.2.1 -SigningKind preview
AssertEqual $metadata.version '0.2.1'
AssertEqual $metadata.minimumAndroid '14'
AssertEqual $metadata.apkName 'FlibustaReader-v0.2.1-android14plus-universal-preview-debug.apk'
AssertThrows { Get-ReleaseMetadata $source -Tag v0.2.2 } 'does not match'
AssertThrows { Get-ReleaseMetadata $source -Tag 'v0.2.1/../../other' } 'does not match'
AssertThrows { Get-ReleaseMetadata ($source.Replace('0.2.1-reader-design','latest')) } 'Invalid'
AssertThrows { Get-ReleaseMetadata ($source.Replace('versionCode = 3','versionCode = 0')) } 'Invalid'
AssertThrows { Get-ReleaseMetadata 'versionName = "0.2.1"' } 'Missing'
AssertThrows { Get-ReleaseMetadata ($source.Replace('minSdk = 34','minSdk = 33')) } 'compatibility'
$future = Get-ReleaseMetadata ($source.Replace('minSdk = 34','minSdk = 35'))
AssertEqual $future.minimumAndroid '15'
AssertEqual (ConvertFrom-JavaPropertyValue 'C\:\\Users\\reader\\Android\\Sdk') 'C:\Users\reader\Android\Sdk'
AssertEqual (ConvertFrom-JavaPropertyValue '/opt/android-sdk') '/opt/android-sdk'
AssertEqual (ConvertFrom-JavaPropertyValue '\u041a\u043d\u0438\u0433\u0438') 'Книги'
'Release metadata tests: PASS (version, tag, compatibility and SDK paths)'
