param([Parameter(Mandatory)][string]$Tag)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$metadata = Get-Content artifacts/release/release-metadata.json -Raw | ConvertFrom-Json
if ($Tag -cne "v$($metadata.version)" -or $metadata.signingKind -ne 'preview') { throw 'Only matching, signed preview packages can be released' }
if ($metadata.commit -cne ((git rev-parse HEAD) -join '')) { throw 'Release package does not match checked-out commit' }
$existing = gh api "repos/$env:GITHUB_REPOSITORY/releases/tags/$Tag" 2>&1
$release = $null
if ($LASTEXITCODE -eq 0) {
    $release = ($existing -join "`n") | ConvertFrom-Json
    if (!$release.draft) { throw 'Release is published; refusing to overwrite existing files or notes' }
    if ($release.body.Trim() -cne (Get-Content artifacts/release/README-release-ru.md -Raw).Trim()) { throw 'Existing draft belongs to a different package' }
} elseif (($existing -join "`n") -notmatch 'HTTP 404') { throw 'Cannot safely determine whether the release already exists' }
if (!$release) {
    gh release create $Tag --repo $env:GITHUB_REPOSITORY --verify-tag --draft --prerelease --title "$Tag — тестовая версия для Android $($metadata.minimumAndroid)+" --notes-file artifacts/release/README-release-ru.md
    if ($LASTEXITCODE -ne 0) { throw 'Draft release creation failed' }
    $release = gh api "repos/$env:GITHUB_REPOSITORY/releases/tags/$Tag" | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify created draft' }
}
$assets = @(
    @{ name=$metadata.apkName; label=$metadata.apkLabel },
    @{ name='README-release-ru.md'; label='Для кого подходит APK, установка и ограничения — русский' },
    @{ name='SHA256SUMS.txt'; label='Контрольные суммы SHA-256' },
    @{ name='release-metadata.json'; label='Версия, совместимость и сертификат подписи' }
)
foreach ($asset in $assets) {
    $path = Join-Path artifacts/release $asset.name
    $digest = 'sha256:' + (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $uploaded = $release.assets | Where-Object name -eq $asset.name | Select-Object -First 1
    if ($uploaded) {
        if ($uploaded.digest -cne $digest) { throw "Existing draft asset differs: $($asset.name)" }
        continue
    }
    gh release upload $Tag "$path#$($asset.label)" --repo $env:GITHUB_REPOSITORY
    if ($LASTEXITCODE -ne 0) { throw "Release upload failed: $($asset.name)" }
}
gh release view $Tag --repo $env:GITHUB_REPOSITORY --json url,isDraft,isPrerelease,assets
if ($LASTEXITCODE -ne 0) { throw 'Cannot verify completed draft release' }
