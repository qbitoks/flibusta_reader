package app.folio.core.opds

import java.net.URI
import java.net.URLEncoder

internal object Links {
    /**
     * [href] against [base]; `http` links are upgraded to `https` (some catalogs write absolute
     * `http://` links but serve HTTPS), other schemes are dropped. Template braces survive resolving.
     */
    fun resolve(base: String, href: String): String? {
        val value = href.trim().replace("{", "%7B").replace("}", "%7D")
        if (value.isEmpty()) return null
        val address = runCatching {
            if (value.startsWith("?")) base.substringBefore('#').substringBefore('?') + value else URI(base).resolve(value).toString()
        }.getOrNull() ?: return null
        val upgraded = if (address.startsWith("http://", ignoreCase = true)) "https://" + address.substring(7) else address
        if (!upgraded.startsWith("https://", ignoreCase = true)) return null
        return upgraded.replace("%7B", "{").replace("%7D", "}")
    }
}

/** Fills a search template: OpenSearch `{searchTerms}` or an RFC 6570 `{?query,…}` form (OPDS 2). */
object SearchTemplate {
    private val queryNames = listOf("query", "q", "searchTerms", "search", "title")

    fun fill(template: String, query: String): String {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        return template
            .replace(Regex("""\{([?&])([^}]*)\}""")) { m ->
                val names = m.groupValues[2].split(',').map { it.trim() }
                val name = names.firstOrNull { it in queryNames } ?: names.first()
                "${m.groupValues[1]}$name=$encoded"
            }
            .replace("{searchTerms}", encoded)
            .replace(Regex("""[?&][^?&=]+=\{[^}]*\?\}"""), "")
            .replace(Regex("""\{[^}]*\}"""), "")
    }

    fun isTemplate(href: String): Boolean = href.contains("{searchTerms}") || Regex("""\{[?&][^}]*\}""").containsMatchIn(href)
}

/** OpenSearch description → its OPDS/Atom search template; null when it offers only an HTML search. */
object OpenSearch {
    fun template(xml: String, base: String): String? {
        val root = Xml.parse(xml.toByteArray()).documentElement
        require(root.localName == "OpenSearchDescription") { "Источник вернул не описание поиска" }
        val urls = Xml.children(root, "Url")
        val url = urls.firstOrNull { it.getAttribute("type").contains("opds") }
            ?: urls.firstOrNull { it.getAttribute("type").contains("atom+xml") }
            ?: return null
        return Links.resolve(base, url.getAttribute("template"))
    }
}
