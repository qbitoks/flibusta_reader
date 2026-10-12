package net.flibustareader.core

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.SAXException

internal object Xml {
    fun parse(bytes: ByteArray): Document {
        val declarations = bytes.toString(Charsets.ISO_8859_1).replace("\u0000", "")
            .replace(Regex("<!--.*?-->|<!\\[CDATA\\[.*?\\]\\]>", RegexOption.DOT_MATCHES_ALL), "")
        require(!declarations.contains("<!DOCTYPE", ignoreCase = true)) { "DTD в книге не поддерживается" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        for (feature in listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities", "http://apache.org/xml/features/nonvalidating/load-external-dtd")) {
            runCatching { factory.setFeature(feature, false) }
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("External entities are disabled") }
        return ByteArrayInputStream(bytes).use { builder.parse(it) }
    }
    fun children(element: Element, name: String? = null): List<Element> =
        (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }
            .filter { name == null || (it.localName ?: it.tagName) == name }
    fun descendants(element: Element, name: String): List<Element> {
        val nodes = element.getElementsByTagNameNS("*", name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
