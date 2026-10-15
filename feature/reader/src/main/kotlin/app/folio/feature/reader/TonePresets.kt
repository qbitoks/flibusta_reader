package app.folio.feature.reader

/**
 * Ready tone/contrast pairs. Values keep body text at ≥ 7:1 on every theme except «Тишина», whose
 * own light palette starts at 4.5:1 (soft presets take it to about 3.5:1, still readable on grey).
 */
internal enum class TonePreset(val warmth: Int, val contrast: Int) {
    Normal(0, 0), Warm(5, 0), Soft(2, -3), Crisp(0, 5), Night(7, -2), Cool(-3, 2);

    companion object {
        /** The preset with exactly these values, or null for custom slider values. */
        fun of(warmth: Int, contrast: Int): TonePreset? = entries.firstOrNull { it.warmth == warmth && it.contrast == contrast }
    }
}
