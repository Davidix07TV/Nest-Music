package com.nestmusic.music.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {

    // -------------------------------------------------------------------------
    // Map integrity - ensure no literal "?" keys and no collapsed maps
    // -------------------------------------------------------------------------

    @Test
    fun `mapping tables contain no literal question mark keys and have size greater than one`() {
        val instance = LyricsUtils
        val clazz = LyricsUtils::class.java

        val mapFieldNames = listOf(
            "KANA_ROMAJI_MAP",
            "GENERAL_CYRILLIC_ROMAJI_MAP",
            "RUSSIAN_ROMAJI_MAP",
            "DEVANAGARI_ROMAJI_MAP",
            "GURMUKHI_ROMAJI_MAP",
            "UKRAINIAN_ROMAJI_MAP",
            "SERBIAN_ROMAJI_MAP",
            "BULGARIAN_ROMAJI_MAP",
            "BELARUSIAN_ROMAJI_MAP",
            "KYRGYZ_ROMAJI_MAP",
            "MACEDONIAN_ROMAJI_MAP"
        )

        val setFieldNames = listOf(
            "RUSSIAN_CYRILLIC_LETTERS",
            "UKRAINIAN_CYRILLIC_LETTERS",
            "SERBIAN_CYRILLIC_LETTERS",
            "BULGARIAN_CYRILLIC_LETTERS",
            "BELARUSIAN_CYRILLIC_LETTERS",
            "KYRGYZ_CYRILLIC_LETTERS",
            "MACEDONIAN_CYRILLIC_LETTERS",
            "UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS",
            "SERBIAN_SPECIFIC_CYRILLIC_LETTERS",
            "BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS",
            "KYRGYZ_SPECIFIC_CYRILLIC_LETTERS",
            "MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS"
        )

        for (name in mapFieldNames) {
            val field = clazz.getDeclaredField(name)
            field.isAccessible = true
            val value = field.get(instance)
            assertTrue("$name should be a Map", value is Map<*, *>)
            val map = value as Map<*, *>
            assertTrue("$name should have size > 1, got ${map.size}", map.size > 1)
            assertFalse("$name should not contain literal '?' as key", map.containsKey("?"))
            // extra: no key should be "?" or "??" etc that would indicate collapsed encoding corruption
            for (key in map.keys) {
                assertFalse("$name key should not be literal '?': $key", key == "?" || key == "??" || key == "???")
            }
        }

        // HANGUL is nested
        run {
            val field = clazz.getDeclaredField("HANGUL_ROMAJA_MAP")
            field.isAccessible = true
            val value = field.get(instance) as Map<*, *>
            assertTrue("HANGUL_ROMAJA_MAP should have size > 1", value.size > 1)
            assertFalse("HANGUL_ROMAJA_MAP should not contain '?' as outer key", value.containsKey("?"))
            for ((_, inner) in value) {
                val innerMap = inner as Map<*, *>
                assertTrue("HANGUL inner map should have size > 1", innerMap.size > 1)
                assertFalse("HANGUL inner map should not contain '?' as key", innerMap.containsKey("?"))
            }
        }

        for (name in setFieldNames) {
            val field = clazz.getDeclaredField(name)
            field.isAccessible = true
            val value = field.get(instance)
            assertTrue("$name should be a Set", value is Set<*>)
            val set = value as Set<*>
            assertTrue("$name should have size > 1", set.size > 1)
            assertFalse("$name should not contain literal '?'", set.contains("?"))
        }
    }

    // -------------------------------------------------------------------------
    // Functional transliteration tests - Russian (including word-initial Ye)
    // -------------------------------------------------------------------------

    @Test
    fun `russian word-initial E becomes Ye - Yelena`() = runBlocking {
        // \u0415\u043b\u0435\u043d\u0430 = Elena with capital IE at start, expected Yelena
        val input = "\u0415\u043b\u0435\u043d\u0430"
        val result = LyricsUtils.romanizeCyrillic(input, "Russian")
        assertNotNull(result)
        assertEquals("Yelena", result)
    }

    @Test
    fun `russian lowercase word-initial e becomes ye`() = runBlocking {
        // Test lowercase: \u0435\u043b\u0435\u043d\u0430 -> yelena
        val input = "\u0435\u043b\u0435\u043d\u0430"
        val result = LyricsUtils.romanizeCyrillic(input, "Russian")
        assertEquals("yelena", result)
    }

    @Test
    fun `russian simple transliteration - Privet`() = runBlocking {
        // \u041f\u0440\u0438\u0432\u0435\u0442 = Privet
        val input = "\u041f\u0440\u0438\u0432\u0435\u0442"
        val result = LyricsUtils.romanizeCyrillic(input, "Russian")
        assertEquals("Privet", result)
    }

    @Test
    fun `russian transliteration - Moskva`() = runBlocking {
        // \u041c\u043e\u0441\u043a\u0432\u0430 = Moskva
        val input = "\u041c\u043e\u0441\u043a\u0432\u0430"
        val result = LyricsUtils.romanizeCyrillic(input, "Russian")
        assertEquals("Moskva", result)
    }

    // -------------------------------------------------------------------------
    // Functional tests - Ukrainian
    // -------------------------------------------------------------------------

    @Test
    fun `ukrainian transliteration - Kyiv`() = runBlocking {
        // \u041a\u0438\u0457\u0432 = Kyiv variant (map gives Kiyiv due to i+yi)
        val input = "\u041a\u0438\u0457\u0432"
        val result = LyricsUtils.romanizeCyrillic(input, "Ukrainian")
        assertEquals("Kiyiv", result)
    }

    @Test
    fun `ukrainian transliteration - Yevhen`() = runBlocking {
        // \u0404\u0432\u0433\u0435\u043d = Yevhen (Cyrillic IE with grave -> Ye, ghe -> h in Ukrainian)
        val input = "\u0404\u0432\u0433\u0435\u043d"
        val result = LyricsUtils.romanizeCyrillic(input, "Ukrainian")
        // At least starts with Ye and contains vhen; exact mapping per tables is Ye + v + h + e + n
        assertEquals("Yevhen", result)
    }

    @Test
    fun `ukrainian specific letters - Yi`() = runBlocking {
        // Test \u0407 (Yi) mapping
        val input = "\u0407"
        val result = LyricsUtils.romanizeCyrillic(input, "Ukrainian")
        assertEquals("Yi", result)
    }

    // -------------------------------------------------------------------------
    // Functional tests - Japanese Kana
    // -------------------------------------------------------------------------

    @Test
    fun `japanese katakana digraph kya`() {
        // \u30ad\u30e3 = kya
        val result = LyricsUtils.katakanaToRomaji("\u30ad\u30e3")
        assertEquals("kya", result)
    }

    @Test
    fun `japanese katakana basic - ka`() {
        // \u30ab = ka
        val result = LyricsUtils.katakanaToRomaji("\u30ab")
        assertEquals("ka", result)
    }

    @Test
    fun `japanese katakana word - katakana`() {
        // \u30ab\u30bf\u30ab\u30ca = katakana (ka-ta-ka-na)
        val result = LyricsUtils.katakanaToRomaji("\u30ab\u30bf\u30ab\u30ca")
        assertEquals("katakana", result)
    }

    @Test
    fun `japanese katakana with long vowel mark - ramen`() {
        // \u30e9\u30fc\u30e1\u30f3 = ramen (ra + long mark + me + n)
        val result = LyricsUtils.katakanaToRomaji("\u30e9\u30fc\u30e1\u30f3")
        assertEquals("ramen", result)
    }

    @Test
    fun `japanese katakana sha`() {
        // \u30b7\u30e3 = sha
        val result = LyricsUtils.katakanaToRomaji("\u30b7\u30e3")
        assertEquals("sha", result)
    }

    // -------------------------------------------------------------------------
    // Literal question mark must remain untouched
    // -------------------------------------------------------------------------

    @Test
    fun `literal question mark remains untouched - What`() = runBlocking {
        val input = "What?"
        val result = LyricsUtils.romanizeCyrillic(input, "Russian")
        assertEquals("What?", result)
    }

    @Test
    fun `literal question mark single remains question mark`() = runBlocking {
        val result = LyricsUtils.romanizeCyrillic("?", "Russian")
        assertEquals("?", result)
    }

    @Test
    fun `cyrillic with trailing question mark preserves mark`() = runBlocking {
        // \u041f\u0440\u0438\u0432\u0435\u0442? = Privet? should keep ?
        val input = "\u041f\u0440\u0438\u0432\u0435\u0442?"
        val result = LyricsUtils.romanizeCyrillic(input, "Russian")
        assertEquals("Privet?", result)
    }

    @Test
    fun `question mark not converted to ye`() {
        // Direct katakana path also should not convert ?
        val result = LyricsUtils.katakanaToRomaji("?")
        assertEquals("?", result)
    }
}
