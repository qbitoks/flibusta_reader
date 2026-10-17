package app.folio.core.data.modules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What a module does: a speech engine (`modules` in `modules.json`) or a book viewer (`viewers`). */
enum class ModuleKind { Engine, Viewer }

/** An APK Folio can install or update: a module from `modules.json`, or Folio itself. */
data class ModuleApk(
    val id: String,
    val packageName: String,
    val title: String,
    val description: String,
    val versionCode: Long,
    val versionName: String,
    val url: String,
    /** Hex SHA-256 of the APK; the download is refused when it differs. */
    val sha256: String,
    val bytes: Long,
    val kind: ModuleKind = ModuleKind.Engine,
)

/** Parses the modules repository's `modules.json` and Folio's `release-metadata.json` + `SHA256SUMS.txt`. */
internal object ModuleIndexCodec {
    private val json = Json { ignoreUnknownKeys = true }
    const val VERSION = 1

    /**
     * Published modules (an entry with no release yet has versionCode 0 and is skipped); null on junk or a newer [VERSION].
     * Viewers sit in their own `viewers` array: Folio 0.9 and older read only `modules` and would list them as engines.
     */
    fun modules(value: String): List<ModuleApk>? {
        val root = runCatching { json.parseToJsonElement(value) as? JsonObject }.getOrNull() ?: return null
        if (root.int("version") != VERSION.toLong()) return null
        val engines = root["modules"] as? JsonArray ?: return null
        return rows(engines, ModuleKind.Engine) + rows(root["viewers"] as? JsonArray, ModuleKind.Viewer)
    }

    private fun rows(rows: JsonArray?, kind: ModuleKind): List<ModuleApk> =
        rows.orEmpty().mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val url = o.string("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            ModuleApk(
                id = o.string("id") ?: return@mapNotNull null,
                packageName = o.string("package") ?: return@mapNotNull null,
                title = o.string("title") ?: return@mapNotNull null,
                description = o.string("description").orEmpty(),
                versionCode = o.int("versionCode")?.takeIf { it > 0 } ?: return@mapNotNull null,
                versionName = o.string("versionName").orEmpty(),
                url = url,
                sha256 = o.string("sha256")?.takeIf { it.length == 64 } ?: return@mapNotNull null,
                bytes = o.int("bytes") ?: 0,
                kind = kind,
            )
        }

    /**
     * Folio's latest release from its `release-metadata.json` and `SHA256SUMS.txt` (`<hash> *<file>` lines);
     * null when either lacks the APK.
     */
    fun app(metadata: String, sums: String, packageName: String, releases: String): ModuleApk? {
        val o = runCatching { json.parseToJsonElement(metadata) as? JsonObject }.getOrNull() ?: return null
        val apk = o.string("apkName") ?: return null
        val sha = sums.lineSequence().map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == apk }?.get(0)?.lowercase()?.takeIf { it.length == 64 } ?: return null
        return ModuleApk(
            id = "folio",
            packageName = packageName,
            title = "Folio",
            description = "",
            versionCode = o.int("versionCode") ?: return null,
            versionName = o.string("versionName") ?: o.string("version").orEmpty(),
            url = releases + apk,
            sha256 = sha,
            bytes = 0,
        )
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
}
