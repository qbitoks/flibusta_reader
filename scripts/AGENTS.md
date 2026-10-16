# Scripts and device checks

## Entry points
- Use `scripts/doctor.ps1` for environment checks; `-RequireEmulator -Serial <serial>` also checks a device.
- Use `scripts/build.ps1` for the root Check command; it copies the APK to `artifacts/Folio-<versionName>-release.apk`, no install.
- Use `scripts/ci-emulator.ps1` as the canonical automated device check: packaged APK install, launch, rotation and restart.
- Read `.agents/rules/release.md` before changing packaging, publishing, metadata, signing or CI.
- Use `scripts/agent-lint.sh` for docs; its maintenance workflow is `.agents/playbooks/maintenance.md`.

## CI launch check
- Run `scripts/ci-emulator.ps1` after packaging `artifacts/release/release-metadata.json` and `artifacts/release/SHA256SUMS.txt`.
- Set `ANDROID_HOME` to the SDK and `EMULATOR_PORT` to the current emulator port; the script derives its serial.
- Expect package checksum validation, process/crash checks and `.tools/ci-launch.png`, result text and logcat.
- Treat this as a launch gate; verify reader interactions and the live catalog separately below.
- Keep retired Readium drivers in `.agents/archive/readium-scripts/` as history; write new checks against the current UI.

## Phone APK first
- After finishing implementation and writing tests, run `./gradlew.bat :app:assembleRelease --console=plain` first.
- Copy `app/build/outputs/apk/release/app-release.apk` to `artifacts/Folio-<versionName>-release.apk`.
- Immediately send an absolute Markdown link to that APK in chat; state that checks are still pending.
- Then run the root Check command, test suites, lint and device checks; report their results separately.
- If APK assembly fails, fix the build and retry before starting unrelated checks.
- This local testing APK needs no tag, push, publication or version bump; see `.agents/rules/release.md` for publication.

## User's phone (Wi-Fi ADB)
- The user's phone (vivo V2514) is paired over Wi-Fi ADB; it shows in `adb devices` as `adb-...._adb-tls-connect._tcp`.
- Standing authorization: install the release APK (`install -r`, keep app data) and launch/test on it without asking.
- Not listed → run `adb kill-server; adb start-server`, wait ~10 s (mDNS rediscovery) and check again; still missing →
  the phone is off Wi-Fi or asleep: say so. Never uninstall or clear data.
- Scroll settings with `input swipe` at x≈30 (screen edge): a swipe starting on a slider track sets that setting.
  Find tap targets with `uiautomator dump` (`MSYS_NO_PATHCONV=1` in Git Bash) instead of guessed coordinates.

## Manual device workflow
```powershell
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $taskAdb devices
./scripts/build.ps1
& $taskAdb -s <serial> install -r app/build/outputs/apk/release/app-release.apk
& $taskAdb -s <serial> shell am start -W -n app.folio/.MainActivity
```
- Select a currently listed serial; use a dedicated API 34+ test emulator with Russian locale for repeatable UI checks.
- Use `install -r` for upgrade checks; keep the profile instead of uninstalling or clearing app data.
- Import EPUB and FB2 samples, open from Home/library, and reopen a large book after switching to the background during preparation.
- Check left/right thirds, centre controls, swipes and rapid turns across chapter boundaries; wait for layout before judging a stall.
- Check TOC, text search, bookmarks and internal-link jumps; verify history arrows with controls visible.
- Check scroll chapter seams and jumps, text selection/copy/highlights/notes, then reopen and verify position and annotations.
- Change font, size, spacing and page-turn mode; rotate, background, restart and confirm the same reading location.
- Check app appearance and book override/follow-app, header choices, footer page format, time and battery display.
- Check catalog browse/search, EPUB/FB2 acquisition, pause/resume after network loss and opening the completed book.
- Capture screenshots/logcat under `artifacts/` or `.tools/`; report emulator and physical-phone coverage separately.

## Conventions
- SDK platform 37 may be installed as `android-37.0`; keep doctor checks aligned with the wrapper and SDK layout.
- Keep working build/release scripts independent of archived drivers; adapt assertions to the current UI.
- Preserve PowerShell 5.1 support where it exists; packaging/CI uses PowerShell 7. Do not change system execution policy.
- Linux/macOS: set SDK configuration and use `bash ./gradlew` with the Gradle tasks in root `AGENTS.md`; local ADB scripts are unverified there.
- Check dependency repository access before treating a network fetch failure as a Kotlin error.
- Escape the drive colon in generated `sdk.dir`; Android lint rejects an unescaped Windows path.
