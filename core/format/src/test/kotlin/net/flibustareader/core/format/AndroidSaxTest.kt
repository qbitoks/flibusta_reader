package net.flibustareader.core.format

import net.flibustareader.core.format.fb2.Fb2Parser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.xml.sax.SAXNotRecognizedException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

/**
 * Android's SAX parser (Harmony/expat) rejects Xerces-only features such as
 * `disallow-doctype-decl`; FB2 must still parse there, and a DOCTYPE must still be refused.
 */
class AndroidSaxTest {
    class AndroidLikeFactory : SAXParserFactory() {
        private val delegate = SAXParserFactory.newDefaultInstance()
        override fun newSAXParser(): SAXParser = delegate.apply { isNamespaceAware = this@AndroidLikeFactory.isNamespaceAware }.newSAXParser()
        override fun setFeature(name: String, value: Boolean) {
            if (name.startsWith("http://apache.org/")) throw SAXNotRecognizedException(name)
            delegate.setFeature(name, value)
        }
        override fun getFeature(name: String): Boolean = delegate.getFeature(name)
    }

    private var previous: String? = null

    @Before fun useAndroidLikeFactory() {
        previous = System.getProperty(PROPERTY)
        System.setProperty(PROPERTY, AndroidLikeFactory::class.java.name)
    }

    @After fun restore() {
        previous?.let { System.setProperty(PROPERTY, it) } ?: System.clearProperty(PROPERTY)
    }

    private fun parse(xml: String) = Fb2Parser { _, _ -> }.parse(xml.byteInputStream())

    @Test fun parsesFb2WhenDoctypeFeatureIsUnknown() {
        val book = parse("""<?xml version="1.0" encoding="utf-8"?><FictionBook><description><title-info><book-title>Регистрация</book-title></title-info></description><body><section><p>Текст</p></section></body></FictionBook>""")
        assertEquals("Регистрация", book.metadata.title)
    }

    @Test fun stillRejectsDoctypeWhenFeatureIsUnknown() {
        val xml = """<?xml version="1.0"?><!-- c --><!DOCTYPE FictionBook [<!ENTITY x "boom">]><FictionBook><body><section><p>&x;</p></section></body></FictionBook>"""
        assertTrue(runCatching { parse(xml) }.isFailure)
    }

    private companion object {
        const val PROPERTY = "javax.xml.parsers.SAXParserFactory"
    }
}
