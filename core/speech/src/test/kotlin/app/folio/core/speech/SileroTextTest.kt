package app.folio.core.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SileroTextTest {
    @Test fun tokensAreWrappedAndUseTheModelSymbolTable() {
        // «да.»: д = 17, а = 13, «.» = 8 in Silero's table; 2 and 1 wrap the phrase.
        assertArrayEquals(longArrayOf(2, 17, 13, 8, 1), sileroTokens("Да."))
    }

    @Test fun charactersWithoutASymbolAreDropped() {
        assertEquals("привет мир", sileroText("Привет  «mir»\n мир"))
        assertEquals("тире — и многоточие…", sileroText("Тире – и многоточие..."))
        assertArrayEquals(longArrayOf(2, 1), sileroTokens("abc"))
    }

    @Test fun numbersAreSpelledInRussian() {
        assertEquals("в тысяча восемьсот двенадцать году", spellNumbers("в 1812 году"))
        assertEquals("двадцать одна тысяча сто", spellNumbers("21100"))
        assertEquals("две тысячи пять", spellNumbers("2005"))
        assertEquals("одиннадцать тысяч", spellNumbers("11000"))
        assertEquals("один миллион двести тысяч", spellNumbers("1200000"))
        assertEquals("ноль", spellNumbers("0"))
        assertEquals("три целых четырнадцать", spellNumbers("3,14"))
        assertEquals("глава сорок два.", spellNumbers("глава 42."))
    }

    @Test fun longNumbersAreReadDigitByDigit() {
        assertEquals("один ноль ноль ноль ноль ноль ноль ноль ноль ноль ноль ноль ноль", spellNumbers("1000000000000"))
    }

    @Test fun longPhrasesSplitAtCommasThenSpaces() {
        val phrase = List(60) { "слово$it" }.joinToString(", ")
        val chunks = sileroChunks(phrase, max = 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(phrase.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
        assertTrue(chunks.dropLast(1).all { it.endsWith(",") })
        assertEquals(listOf("коротко"), sileroChunks(" коротко "))
        assertEquals(listOf("a".repeat(10), "a".repeat(5)), sileroChunks("a".repeat(15), max = 10))
    }

    @Test fun speakersMatchTheModelTable() {
        assertEquals(29, SileroSpeakers.size)
        assertEquals(20L, SileroSpeakers.first().index)
        assertEquals(48L, sileroSpeaker("ru_zinaida")?.index)
        assertEquals(26L, DefaultSileroSpeaker.index)
        assertEquals("Dmitriy", DefaultSileroSpeaker.name)
        assertTrue(SileroModel.speaks(Locale.forLanguageTag("ru-RU")))
        assertFalse(SileroModel.speaks(Locale.ENGLISH))
    }
}
