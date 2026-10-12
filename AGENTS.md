# AGENTS.md

Rules for every AI agent in this repo. Deeper `AGENTS.md` files add rules for their area.
Precedence: the user's explicit request > nearest `AGENTS.md` > this file > `.agents/rules/`.

## 1. Orientation (in order; stop as soon as you know enough)

1. Read `.agents/STATE.md` (status, focus, next steps) and `.agents/MAP.md` (areas → their docs).
2. Read the `AGENTS.md` of each area your task touches, unless your tool already loaded it.
   Never read all of them.
3. Before changing architecture, a library, a data format or the layout: grep `.agents/DECISIONS.md`
   for the area or keyword. Don't reverse a recorded decision without telling the user.
4. Search (grep/glob/symbols) for exact names. Read small files (< ~300 lines) whole, large ones by range.
5. Load a playbook (§4) only when the task matches its trigger.

`status: uninitialized` in STATE: if the task is to start or build the project, run
`.agents/playbooks/bootstrap.md` first. Work on the template itself (agent docs, scripts) needs no bootstrap.

## 2. Context hygiene

- Read the minimum. Every tool call re-sends the context: one whole read beats five tiny ones.
- Skip what `.agents/ignore.md` lists (deps, build output, lockfiles, generated code). Open such a file
  only by explicit path when the task is about it. Never read secrets.
- Don't re-read files you just wrote. Pipe noisy output through `tail`/`grep`; run single tests while iterating.
- Broad exploration → a sub-agent or search tool if your harness has one; keep only the conclusions.
- One fact, one place. Link (`see path/file.md`) instead of copying.

## 3. Working rules

- Match surrounding code: style, naming, comment density, idioms. No speculative abstractions.
- Small, verifiable steps. Run the Check command (Project section below) before saying "done".
- Docs disagree with the code → the code wins; fix the doc in the same task.
- Ask the user only for decisions that are theirs; otherwise pick the conventional default and say so.
- Never commit secrets. Destructive commands (force-push, `rm -rf`, DB drops) need explicit approval.
- Commit only when the user asks. Messages: conventional (`feat:`, `fix:`, `docs(agents):`), summary + why.
- Scratch files go to `.agents/tmp/` (gitignored). Don't create report or summary files; report in chat.
- Reply in the user's language. Write agent docs in English.

## 4. Playbooks (load on demand)

| Trigger | File |
|---|---|
| Start/build a project while `status: uninitialized`, or adopt the template in an existing repo | `.agents/playbooks/bootstrap.md` |
| Add a module, package, service, feature area or vault section | `.agents/playbooks/new-module.md` |
| Lint: over a limit (now) or "maintenance due" (offer after the task); docs feel stale | `.agents/playbooks/maintenance.md` |
| Pull agent-infrastructure updates from the upstream template | `.agents/playbooks/upgrade.md` |
| Write or edit any agent doc (AGENTS.md, STATE, MAP, DECISIONS, playbook, rule) | `.agents/rules/writing-agent-docs.md` |

Topic rules live in `.agents/rules/`, indexed in `.agents/MAP.md`. Create new ones there, not here.

## 5. Before saying "done" (every task; most need zero doc edits)

- The Check command passes, or you report the failure.
- Added, moved or removed an area → `.agents/MAP.md` + that area's `AGENTS.md` (new-module playbook).
- Had to explore an undocumented area to understand it → write its `AGENTS.md` (entry points,
  canonical example) so the next agent doesn't pay for it again.
- New or changed command → Project section below.
- Non-obvious decision or rejected alternative → one line in `.agents/DECISIONS.md`.
- Gotcha that cost time and will recur → "Gotchas" in the closest `AGENTS.md`.
- Focus, next steps or blockers changed → rewrite `.agents/STATE.md`.
- Don't write session diaries, changelogs (git has them) or what the code already says.

Put knowledge in the deepest file whose scope covers it. This file holds only repo-wide rules.

<!-- PROJECT-OWNED BELOW: filled by bootstrap; template upgrades never touch it (.agents/playbooks/upgrade.md). -->
## Project

- What: Flibusta Reader, a native Android e-reader with an OPDS catalog. No app server, no accounts.
- Stack: Kotlin, Jetpack Compose, Readium 3.0.0, Android 14+ (API 34). EPUB via Readium; FB2/FB2.ZIP
  converted locally to EPUB. Areas: `.agents/MAP.md`. User-facing product description: `README.md`.
- Env: JDK 17 (JAVA_HOME → JDK), Android SDK platform 34 + build-tools 34.0.0; versions live in Gradle files.
  First build needs Google Maven, Maven Central, Gradle Plugin Portal. Use the wrapper, not system Gradle.

Commands (PowerShell, repo root; Linux/macOS: `bash ./gradlew`, see `scripts/AGENTS.md`):
- Check before "done": `./scripts/build.ps1` (JVM tests, debug APK, Android lint) + agent-lint:
  `& "$env:ProgramFiles\Git\bin\sh.exe" scripts/agent-lint.sh`. Docs-only change: agent-lint + link check.
- Setup / env check: `./scripts/doctor.ps1`
- Core tests: `./gradlew.bat :core:test --console=plain`
- Test one: `./gradlew.bat :core:test --tests 'net.flibustareader.core.OpdsParserTest' --console=plain`
- Extra checks by change (`<serial>` from `adb devices`; workflow and caveats: `scripts/AGENTS.md`):
  - Panels, HUD, settings: `./scripts/smoke-test.ps1 -Serial <serial>`
  - Storage, rotation, process death, typography: `./scripts/reader-regression.ps1 -Serial <serial>`
  - Open-book lifecycle, debug/release checkpoint: `./scripts/reader-background-regression.ps1 -Serial <serial>`
    and `./gradlew.bat :app:compileReleaseKotlin --console=plain`
  - Bug in `core`: reproduce with a regression test first.
- A check you can't run: name the reason; never report it as passed.

## Project rules
- `core` must not depend on Android, Compose or Readium; Android adapters stay in `app`.
- Keep `library.json`, `reader.xml`, JSON Locator and book IDs compatible. Schema change needs a migration
  described in `.agents/rules/storage.md` and a test on existing data.
- HTTPS only, redirects included. Limits stay: book/unpacked FB2 50 MiB, OPDS feed 5 MiB, FB2 depth 128,
  no DTD or external XML entities. Don't relax them to make a sample pass.
- Coroutine cancellation is not a user error. No blocking I/O on the UI thread.
- Publish, push, release and deleting user data only on explicit user request.
- Behaviour change → update the affected `AGENTS.md`/rule and, if user-visible, `README.md` (Russian).
  Long rationale → `.agents/decisions/NNNN-title.md` + one line in `.agents/DECISIONS.md`.
- Don't commit `local.properties`, `.tools/`, `.gradle/`, `**/build/`, `artifacts/`, personal books, keys.
- Reader/viewport/lifecycle invariants: `.agents/rules/reader-invariants.md`.
