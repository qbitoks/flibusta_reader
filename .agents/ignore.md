# Ignore list (canonical)

Agents: skip these when searching; open one only by explicit path when the task is about it.
Never read secrets. Tool files are generated from the two blocks below by `sh scripts/agent-lint.sh --fix`:
- `ignore-secrets` → `.cursorignore` (hard block) and every file below. Each line must also be in `.gitignore`.
- `ignore-bulk` → `.cursorindexingignore`; both blocks → `.ignore` (ripgrep and VS Code search skip it,
  reads by path still work), `.codeiumignore` (Windsurf indexing), `.aiderignore`, `.geminiignore`
  (these two also block reads; use the shell when a task needs such a file).
Binary files (images, archives, APKs) need no entry: search tools and indexers skip them already.
Bootstrap: add project paths (e.g. the Obsidian attachments folder) to the right block, drop patterns that
match real source dirs in your stack, then run `--fix`.

```ignore-secrets
.env
.env.*
!.env.example
*.pem
*.key
*.p12
*.pfx
*.jks
*.keystore
local.properties
secrets.*
```

```ignore-bulk
# dependencies
node_modules/
vendor/
.venv/
venv/
__pycache__/
Pods/
.gradle/
.m2/
.dart_tool/
# build output
build/
dist/
out/
target/
obj/
.next/
.nuxt/
DerivedData/
coverage/
# lockfiles
*.lock
package-lock.json
pnpm-lock.yaml
npm-shrinkwrap.json
go.sum
# generated, minified, logs
*.min.js
*.min.css
*.js.map
*.css.map
*.pb.go
*_pb2.py
*.g.dart
*.freezed.dart
*_generated.*
core/data/schemas/
*.log
# project tooling and outputs
.tools/
artifacts/
# agent history and scratch
.agents/archive/
.agents/tmp/
```
