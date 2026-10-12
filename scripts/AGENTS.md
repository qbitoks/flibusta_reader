# scripts

PowerShell tooling: environment check, build, emulator regressions, release packaging; plus agent-lint.
CI pipeline and signing: `.agents/rules/release.md`.

## Entry points
- `scripts/doctor.ps1` — checks JDK 17, wrapper, SDK platform 34, build-tools 34.0.0, ADB. Exit 1 = problems
  found; downloads nothing. SDK from `-SdkPath`, `local.properties`, ANDROID_HOME, ANDROID_SDK_ROOT, LOCALAPPDATA.
  `-RequireEmulator -Serial <serial>` also checks the device.
- `scripts/build.ps1` — `:core:test :app:assembleDebug :app:lintDebug`; creates `local.properties` only if
  missing; copies APK to `artifacts/FlibustaReader-0.2.1-debug.apk`. Doesn't install.
- `scripts/smoke-test.ps1` — HUD toggles and tap areas, battery, restart, panels, settings, page turns.
  Needs the app installed. `-FailAfterBatteryOverride` fails on purpose ("Injected battery cleanup test
  failure"); then `adb shell dumpsys battery` must not show `UPDATES STOPPED`.
- `scripts/reader-regression.ps1` — leftover cleanup (index, book SHA-256, positions, bookmarks), rotation,
  process death (`am kill` + Recents, PID change), viewport, fast typography. Installs the APK itself.
- `scripts/reader-background-regression.ps1` — EPUB preparation finishing in background via the debug checkpoint.
- `scripts/package-release.ps1`, `scripts/ReleaseMetadata.ps1`, `scripts/publish-release.ps1`,
  `scripts/ci-emulator.ps1`, `scripts/test-release-metadata.ps1` — release pipeline.
- `scripts/agent-lint.sh` — agent docs lint (template file; update via `.agents/playbooks/upgrade.md`).

## Emulator workflow
```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $adb devices                      # pick a real serial; never reuse a historical one
./scripts/build.ps1
& $adb -s <serial> install -r ./artifacts/FlibustaReader-0.2.1-debug.apk
./scripts/smoke-test.ps1 -Serial <serial> -Adb $adb
```
- Regressions accept `-Apk`, `-Adb`, `-Serial`. Rebuild first, or they pass on a stale binary.
- Use a dedicated test emulator (API 34+, Russian locale; history used API 35). Scripts reject physical devices.
- They overwrite demo-book data, typography and test-profile settings; rotation/battery are restored in `finally`.
- Never `uninstall`/`pm clear` to test upgrades: `install -r` keeps the profile.
- Scenarios rely on Russian labels, UI hierarchy and geometry tolerances; adapt the script, not the UI.
- A UI PASS doesn't prove the live catalog or physical phones.

## Conventions
- PowerShell 5.1 and 7. Don't change the system execution policy; use direct Gradle commands instead.
- Linux/macOS: `bash ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain`; set
  `sdk.dir` in `local.properties` yourself; ADB scripts there are unverified (pass `-Adb`).
- Diagnostics: screenshots `artifacts/reader-0.2-*.png`, `artifacts/smoke-ui.xml`; regression output `.tools/`.
- Network failure while fetching deps is not a Kotlin error; check repo access first.
