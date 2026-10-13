# Book model

## Entry points
- Use `core/model/src/main/kotlin/net/flibustareader/core/model/Book.kt` for chapters, metadata and TOC.
- Use `core/model/src/main/kotlin/net/flibustareader/core/model/Block.kt` for block kinds and inline spans.
- Use `core/model/src/main/kotlin/net/flibustareader/core/model/Position.kt` for locations, links and progress.
- Use `core/model/src/main/kotlin/net/flibustareader/core/model/BookCodec.kt` as the canonical cache codec.

## Invariants
- Keep positions independent of pagination; offsets index Kotlin text, and span ends are exclusive.
- Route in-book links through `internalTarget` and `parseInternalTarget`; footnote keys use `footnoteBlocks`.
- Preserve persisted position compatibility; see `.agents/rules/storage.md` before changing encoding.
- Bump `BookCodec.VERSION` when changing binary fields or serialized enum order; stale caches are rebuilt.
- Serialize long text with the codec's long-string helpers; `writeUTF` has a 64 KiB limit.
- Keep this module JVM-only; leave resource extraction to `core/format/` and file ownership to `core/data/`.
