package net.flibustareader.core

object ReadingProgress {
    fun percent(resource: Double?, total: Double?, resourceCount: Int): Int? {
        val amount = if (resourceCount == 1) resource ?: total else total
        return amount?.takeIf { it.isFinite() }?.let { (it.coerceIn(0.0, 1.0) * 100).toInt() }
    }
}
