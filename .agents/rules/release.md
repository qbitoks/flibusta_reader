# Rule: CI, signing and releases

Trigger: changing `.github/workflows/android.yml`, release scripts, version, signing, or cutting a release.
Push tags and publish releases only on explicit user request.

## Workflow `.github/workflows/android.yml`
- Push/PR to main: JDK 17, SDK platform 37 + build-tools 36.0.0, `:core:opds:test :core:format:test :feature:reader:testDebugUnitTest
  :app:assembleDebug :app:lintDebug`, metadata tests.
  APK signed with a throwaway CI key, labelled `ci-debug`; reports kept 14 days. No ADB regressions.
- Manual run: same with the permanent preview key; by default `scripts/ci-emulator.ps1` on API 35
  (install, launch, rotation, restart, no crash). No Release.
- Tag `vX.Y.Z`: version and signature checks, build, emulator launch check, release published
  automatically (draft while files upload, then published as a regular, latest release).
- Doctor and `:app:compileReleaseKotlin` are not run by CI; run them locally.
- Actions are pinned by commit SHA; bumping them is CI maintenance, not an app dependency update.

## Signing
- Secret `ANDROID_DEBUG_KEYSTORE_BASE64` (test keystore). Never put keystores or tokens in git or workflows.
- Preview cert SHA-256 `b3e3703d07d76a6474ef19620632a092763d101db5750ab2c00f311582e8358f`; packaging
  fails on mismatch. Emulator and publish jobs never get the key.
- This is a debug signature, not a Play production key. Switching keys needs a data-migration decision.

## Cutting a release
1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`. Base version must equal the tag
   (a suffix like `-reader-design` is allowed).
2. Commit, push main, wait for green CI.
3. `git tag -a vX.Y.Z -m "Flibusta Reader X.Y.Z"` on the verified commit; `git push origin vX.Y.Z`.
4. Wait for build, emulator check and publication. No manual step; the phone check happens after.
- Release job failed after creating the draft: `gh run rerun <id> --failed` fills in the missing files
  and publishes. A release that reached publication is final.
- Never move tags; published releases are never overwritten. Re-running the release job only fills in
  missing files of a matching draft (same SHA-256); mismatches fail.

## Assets
- `FlibustaReader-vX.Y.Z-android14plus-universal-preview-debug.apk`, README-release-ru.md (generated),
  `SHA256SUMS.txt`, `release-metadata.json` (version, minSdk, APK name, certificate).

## Local
- `./scripts/test-release-metadata.ps1`
- `./scripts/package-release.ps1 -SigningKind preview -ExpectedCertificateSha256 <sha256>`
