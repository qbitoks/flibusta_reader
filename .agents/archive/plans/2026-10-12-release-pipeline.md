# GitHub Actions release pipeline

**Goal:** Verify main/PR changes and prepare signed, installable Android preview releases from version tags.

**Architecture:** One workflow builds and packages the APK. Tags and manual runs use the existing debug signing identity from an encrypted GitHub Actions secret; PRs never receive it. A separate emulator job tests the exact packaged APK. Tag publication requires both jobs to pass and creates a draft pre-release with APK, compatibility notes and SHA-256. Published releases are never overwritten. The existing v0.2.1 tag and assets are preserved.

**Implementation:** Execute inline in the existing checkout, following the user's request to configure the proposed pipeline.

- [ ] Add PowerShell package tests: mismatched tags, invalid versions and missing version metadata fail; known project version yields deterministic file names and compatibility metadata.
- [ ] Implement packaging helper: derive version/minimum Android SDK from Gradle, generate notes/checksums, verify APK signature, optionally require the existing preview certificate fingerprint. Keep local build behavior compatible.
- [ ] Add an APK parameter to the existing reader regression script so CI can test the packaged version rather than a hardcoded filename.
- [ ] Add SHA-pinned checkout/Java/artifact/emulator actions. CI uses JDK 17, Android platform 34/build-tools 34.0.0, JVM tests, debug APK and lint. Emulator runs Android 15/API 35 with Google APIs and both offline reader regressions.
- [ ] Configure the current test keystore as an encrypted repository secret, without storing plaintext keystore/token in the repository or logs. Verify the certificate matches existing APKs.
- [ ] Add tag publication and manual validation: require version/tag correspondence and successful emulator results, create draft pre-release, refuse to change existing releases, upload labelled APK, notes and checksums.
- [ ] Validate locally, review changes, commit and push. Run GitHub Actions on main and a manual signed/emulator validation; diagnose failures until both pass. Document the actual result and the steps for the next release.

Public releases remain preview/debug builds while this prototype is being tested. A future production signing identity and stable release build require an explicit migration plan; this pipeline does not silently change existing users' signature.
