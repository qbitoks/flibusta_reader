package net.flibustareader.core.designsystem

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import net.flibustareader.core.layout.TypefaceProvider
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.text.font.Font as ComposeFont

/** Reading fonts bundled with the app (all SIL OFL, all with Cyrillic). */
enum class ReaderFont(val key: String, val displayName: String, @FontRes val family: Int?) {
    Literata("literata", "Literata", R.font.literata),
    PtSerif("pt_serif", "PT Serif", R.font.pt_serif),
    SourceSerif("source_serif", "Source Serif", R.font.source_serif),
    Lora("lora", "Lora", R.font.lora),
    Inter("inter", "Inter", R.font.inter),
    System("system", "System", null);

    /** Compose family used to preview the font name in the font picker. */
    val composeFamily: FontFamily
        get() = when (this) {
            Literata -> FontFamily(ComposeFont(R.font.literata_regular_400), ComposeFont(R.font.literata_regular_700, FontWeight.Bold), ComposeFont(R.font.literata_italic_400, FontWeight.Normal, FontStyle.Italic))
            PtSerif -> FontFamily(ComposeFont(R.font.pt_serif_regular_400), ComposeFont(R.font.pt_serif_regular_700, FontWeight.Bold))
            SourceSerif -> FontFamily(ComposeFont(R.font.source_serif_regular_400), ComposeFont(R.font.source_serif_regular_700, FontWeight.Bold))
            Lora -> FontFamily(ComposeFont(R.font.lora_regular_400), ComposeFont(R.font.lora_regular_700, FontWeight.Bold))
            Inter -> FontFamily(ComposeFont(R.font.inter_regular_400), ComposeFont(R.font.inter_regular_700, FontWeight.Bold))
            System -> FontFamily.Default
        }

    companion object {
        fun of(key: String): ReaderFont = entries.firstOrNull { it.key == key } ?: Literata
    }
}

class ResourceTypefaceProvider(private val context: Context) : TypefaceProvider {
    private val cache = ConcurrentHashMap<String, Typeface>()

    override fun typeface(font: String): Typeface = cache.getOrPut(font) {
        ReaderFont.of(font).family?.let { ResourcesCompat.getFont(context, it) } ?: Typeface.DEFAULT
    }
}
