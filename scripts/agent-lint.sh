#!/usr/bin/env sh
# agent_template version: 2026-10-12
# Checks agent docs (size limits, broken paths, MAP index, adapters) and the files generated from them.
# Usage: sh scripts/agent-lint.sh [--fix]
#   --fix  first regenerates tool ignore files (from .agents/ignore.md) and CLAUDE.md stubs.
# Exit 1 on any ERROR; WARN and NOTE never fail. Limits mirror .agents/rules/writing-agent-docs.md.
cd "$(dirname "$0")/.." || exit 1
fix=0; [ "${1:-}" = --fix ] && fix=1
fail=0
nl='
'
IFS=$nl; set -f # split command output on newlines only, never glob

err() { echo "ERROR: $*"; fail=1; }
warn() { echo "WARN: $*"; }
exist() { for f in "$@"; do [ -f "$f" ] && printf '%s\n' "$f"; done; }

# Repo files: tracked + untracked minus .gitignore'd (fast, skips deps). Outside git: find with pruning.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  in_git=1; all=$(git -c core.quotePath=false ls-files -co --exclude-standard)
else
  in_git=0; all=$(find . \( -name .git -o -name node_modules -o -name .venv -o -name vendor \
    -o -name build -o -name dist -o -name target -o -name .gradle -o -name Pods \) -prune \
    -o -type f -print | sed 's|^\./||')
fi
named() { exist $(printf '%s\n' "$all" | grep -E "(^|/)$1\$"); }

# Print files with HTML comments and fenced code blocks blanked out; line numbers stay the same.
strip() {
  awk 'FNR == 1 { inc = 0; fence = 0 }
    /^ *```/ { fence = !fence; print ""; next }
    fence { print ""; next }
    { out = ""; line = $0
      while (1) {
        if (inc) { i = index(line, "-->"); if (!i) { line = ""; break }; line = substr(line, i + 3); inc = 0 }
        i = index(line, "<!--"); if (!i) { out = out line; break }
        out = out substr(line, 1, i - 1); line = substr(line, i + 4); inc = 1
      }
      print out }' "$@"
}

# "line:path" for each backticked path a doc references: tokens ending in .md/.sh without spaces;
# in MAP list items also the first token (may contain spaces or non-ASCII). Placeholders are skipped.
refs() { # is_map file
  strip "$2" | awk -v map="$1" '{
    s = $0; first = 1; item = (s ~ /^[ \t]*- /)
    while ((i = index(s, "`")) > 0) {
      s = substr(s, i + 1); j = index(s, "`"); if (!j) break
      t = substr(s, 1, j - 1); s = substr(s, j + 1)
      if (t != "" && t !~ /[<>*?{}$|@]|YYYY|NNNN/) {
        if (map && item && first) print NR ":" t
        else if (t !~ / / && t ~ /[A-Za-z0-9_-]\.(md|sh)$/) print NR ":" t
      }
      first = 0
    }
  }'
}

# ---- generated files: tool ignore files and CLAUDE.md stubs ----
gen() { # file content
  [ -f "$1" ] && [ "$(cat "$1")" = "$2" ] && return 0
  if [ "$fix" -eq 1 ]; then printf '%s\n' "$2" > "$1" && echo "fixed: wrote $1"
  else err "$1 is missing or out of date; run: sh scripts/agent-lint.sh --fix"; fi
}
block() { awk -v b="$1" '$0 == "```" b { on = 1; next } on && /^```/ { on = 0 } on' .agents/ignore.md; }

secrets=$(block ignore-secrets); bulk=$(block ignore-bulk)
if [ -z "$secrets" ] || [ -z "$bulk" ]; then
  err ".agents/ignore.md needs fenced blocks ignore-secrets and ignore-bulk"
else
  hdr="# GENERATED from .agents/ignore.md by scripts/agent-lint.sh --fix. Edit that file, not this one."
  gen .cursorignore "$hdr$nl$secrets"
  gen .cursorindexingignore "$hdr$nl$bulk"
  for f in .ignore .codeiumignore .aiderignore .geminiignore; do gen "$f" "$hdr$nl$secrets$nl$bulk"; done
  for p in $secrets; do
    case $p in '' | '#'*) continue ;; esac
    grep -qxF -- "$p" .gitignore 2>/dev/null || err ".gitignore lacks secret pattern $p (from .agents/ignore.md)"
  done
fi

# Claude Code reads nested CLAUDE.md on demand but skips AGENTS.md once any CLAUDE.md exists.
stub="@AGENTS.md$nl<!-- Claude Code entry point: rules live in AGENTS.md next to this file. Do not add rules here. -->"
agents=$(named 'AGENTS\.md')
for f in $agents; do
  c=$(dirname "$f")/CLAUDE.md; c=${c#./}
  if [ ! -f "$c" ]; then gen "$c" "$stub"
  elif ! grep -qxF '@AGENTS.md' "$c"; then err "$c must import its AGENTS.md (line: @AGENTS.md)"; fi
done
for c in $(named 'CLAUDE\.md'); do
  [ -f "$(dirname "$c")/AGENTS.md" ] && continue
  [ "$(cat "$c")" = "$stub" ] || continue
  if [ "$fix" -eq 1 ]; then rm -f "$c" && echo "fixed: removed $c (no AGENTS.md next to it)"
  else err "$c is a stub without AGENTS.md next to it; run --fix"; fi
done

# ---- size limits ----
hint="split it (.agents/playbooks/maintenance.md)"
limit() { # max files...
  max=$1; shift
  for f in "$@"; do
    [ -f "$f" ] || continue
    n=$(wc -l < "$f" | tr -d ' ')
    [ "$n" -le "$max" ] || err "$f: $n lines, limit $max; $hint"
  done
}
modules=$(printf '%s\n' "$agents" | grep -v '^AGENTS\.md$')
subs=$(strip .agents/MAP.md | grep -o '`[^`]*MAP\.md`' | tr -d '`' | grep -v '^\.agents/MAP\.md$')
maps=$(exist .agents/MAP.md $subs)
pbr=$(exist $(printf '%s\n' "$all" | grep -E '^\.agents/(playbooks|rules)/[^/]+\.md$'))
limit 120 AGENTS.md
limit 60 $modules
limit 40 .agents/STATE.md
limit 100 $maps
limit 150 .agents/DECISIONS.md
limit 80 $pbr
hint="adapters only point to AGENTS.md; move the rules there"
limit 5 $(named 'CLAUDE\.md') GEMINI.md .github/copilot-instructions.md .aider.conf.yml

docs=$(exist AGENTS.md $modules .agents/STATE.md $maps .agents/ignore.md $pbr)
LC_ALL=C awk 'length($0) > 200 { printf "ERROR: %s:%d: line over 200 bytes; split it\n", FILENAME, FNR; bad = 1 }
  END { exit bad }' $docs $(exist .agents/DECISIONS.md) || fail=1

# ---- referenced paths exist (DECISIONS is history and may name removed files) ----
for f in $docs; do
  case $f in *MAP.md) m=1 ;; *) m=0 ;; esac
  for r in $(refs "$m" "$f"); do
    p=${r#*:}
    [ -e "$p" ] || err "$f:${r%%:*}: missing path $p (paths are repo-root-relative)"
  done
done

# ---- every module doc and rule is reachable from MAP (or from a parent module doc) ----
index=$(strip $maps)
for f in $modules; do
  d=$(dirname "$f"); p=$d; hit=0
  case $index in *"\`$d/"*) hit=1 ;; esac
  while [ "$hit" -eq 0 ]; do
    p=$(dirname "$p"); [ "$p" = . ] && break
    [ -f "$p/AGENTS.md" ] && grep -qF -- "\`$d/" "$p/AGENTS.md" && hit=1
  done
  [ "$hit" -eq 1 ] || warn "$f is not listed in .agents/MAP.md or a parent AGENTS.md (add \`$d/\`)"
done
for r in $(printf '%s\n' "$pbr" | grep '^\.agents/rules/'); do
  case $index in *"\`$r\`"*) ;; *) warn "$r is missing from .agents/MAP.md → Rules index" ;; esac
done

# ---- maintenance reminder: agents have no memory between chats, git does ----
if [ "$in_git" -eq 1 ] && git rev-parse -q --verify HEAD >/dev/null; then
  last=$(git log -1 --format=%H --grep='^docs(agents): maintenance')
  range=HEAD; [ -n "$last" ] && range="$last..HEAD"
  n=$(git rev-list --count "$range")
  if [ "$n" -ge 25 ]; then
    echo "NOTE: $n commits since the last 'docs(agents): maintenance' commit: maintenance due" \
      "(.agents/playbooks/maintenance.md)"
  fi
fi

[ "$fail" -eq 0 ] && echo "agent-lint: OK"
exit "$fail"
