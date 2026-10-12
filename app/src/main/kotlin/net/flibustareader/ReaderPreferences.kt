package net.flibustareader

import android.content.SharedPreferences
import net.flibustareader.core.*

class ReaderPreferences(private val prefs: SharedPreferences) {
    fun load(): ReaderOptions {
        val oldTheme = when (prefs.getString("theme", null)) { "DARK" -> ReaderColor.BLACK; "LIGHT" -> ReaderColor.WHITE; else -> ReaderColor.SEPIA }
        return ReaderOptions(
            color = runCatching { ReaderColor.valueOf(prefs.getString("readerColor", oldTheme.name)!!) }.getOrDefault(oldTheme),
            font = runCatching { ReaderFont.valueOf(prefs.getString("readerFont", "BOOK")!!) }.getOrDefault(ReaderFont.BOOK),
            fontSize = prefs.getFloat("fontSize", 1.1f).toDouble(),
            lineHeight = prefs.getFloat("lineHeight", 1.5f).toDouble(),
            margins = prefs.getFloat("pageMargins", 1.2f).toDouble(),
            scroll = prefs.getBoolean("scroll", false),
            systemBrightness = prefs.getBoolean("systemBrightness", true),
            brightness = prefs.getFloat("brightness", .65f).toDouble(),
            clockVisible = prefs.getBoolean("hudClock", true),
            progressVisible = prefs.getBoolean("hudProgress", true),
            batteryVisible = prefs.getBoolean("hudBattery", true)
        ).normalized()
    }
    fun save(options: ReaderOptions) {
        prefs.edit().putString("readerColor", options.color.name).putString("readerFont", options.font.name)
            .putFloat("fontSize", options.fontSize.toFloat()).putFloat("lineHeight", options.lineHeight.toFloat())
            .putFloat("pageMargins", options.margins.toFloat()).putBoolean("scroll", options.scroll)
            .putBoolean("systemBrightness", options.systemBrightness).putFloat("brightness", options.brightness.toFloat())
            .putBoolean("hudClock", options.clockVisible).putBoolean("hudProgress", options.progressVisible)
            .putBoolean("hudBattery", options.batteryVisible).apply()
    }
}
