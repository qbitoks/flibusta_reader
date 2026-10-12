# Playbook: maintenance (doc garbage collection)

Trigger: agent-lint reports a file over its limit or prints "maintenance due", or docs contradict the code.

1. Run `sh scripts/agent-lint.sh`; fix its errors first.
2. **Verify against reality.** Every path and command in MAP, the Project section, STATE and module docs:
   does it still exist and work? Fix or delete stale lines. Code is the truth, docs follow it.
3. **Deduplicate.** Same fact in two places → keep it in the deepest relevant file, link from the others.
4. **Compress.** Prose → bullets; drop what a competent engineer infers from the code.
5. **Split, don't grow.**
   - MAP over the limit → `<area>/MAP.md` sub-maps, linked from `.agents/MAP.md`.
   - Module `AGENTS.md` over the limit → details to `<area>/docs/agents/*.md`, linked from it.
   - DECISIONS over the limit → archive entries that no longer constrain anything; keep foundational ones.
6. **Promote / demote.** A rule repeated in 3+ modules → `.agents/rules/`. A root rule relevant to one
   area only → move it into that area.
7. Run `sh scripts/agent-lint.sh --fix` (regenerates ignore files and `CLAUDE.md` stubs).
8. Offer the commit with the subject `docs(agents): maintenance`; lint counts commits since that subject.
