package com.nestmusic.music.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the romanisation tables in [LyricsUtils].
 *
 * They were keyed on the literal `"?"` for a long time: an editor round-tripped the file through
 * Windows-1252, every non-ASCII *key* became a question mark, and because a `mapOf` with duplicate
 * keys keeps one entry instead of throwing, romanisation silently returned the source text. Nothing
 * failed. These tests exist so that it cannot happen quietly again.
 *
 * The tables are deliberately kept as readable script characters rather than `"\uXXXX"` escapes:
 * the guard in `.github/workflows/pr-checks.yml` rejects a source file that stops being valid UTF-8,
 * which is the shape this corruption always takes for CJK/Cyrillic text, and these tests fail
 * loudly if a table loses its keys for any other reason.
 */
class LyricsUtilsTest {

    private val tables: Map<String, Set<String>> = mapOf(
        "KANA_ROMAJI_MAP" to LyricsUtils.KANA_ROMAJI_MAP.keys,
        "HANGUL_ROMAJA_MAP.cho" to LyricsUtils.HANGUL_ROMAJA_MAP.getValue("cho").keys,
        "HANGUL_ROMAJA_MAP.jung" to LyricsUtils.HANGUL_ROMAJA_MAP.getValue("jung").keys,
        "HANGUL_ROMAJA_MAP.jong" to LyricsUtils.HANGUL_ROMAJA_MAP.getValue("jong").keys,
        "DEVANAGARI_ROMAJI_MAP" to LyricsUtils.DEVANAGARI_ROMAJI_MAP.keys,
        "GURMUKHI_ROMAJI_MAP" to LyricsUtils.GURMUKHI_ROMAJI_MAP.keys,
        "GENERAL_CYRILLIC_ROMAJI_MAP" to LyricsUtils.GENERAL_CYRILLIC_ROMAJI_MAP.keys,
        "RUSSIAN_ROMAJI_MAP" to LyricsUtils.RUSSIAN_ROMAJI_MAP.keys,
        "UKRAINIAN_ROMAJI_MAP" to LyricsUtils.UKRAINIAN_ROMAJI_MAP.keys,
        "SERBIAN_ROMAJI_MAP" to LyricsUtils.SERBIAN_ROMAJI_MAP.keys,
        "BULGARIAN_ROMAJI_MAP" to LyricsUtils.BULGARIAN_ROMAJI_MAP.keys,
        "BELARUSIAN_ROMAJI_MAP" to LyricsUtils.BELARUSIAN_ROMAJI_MAP.keys,
        "KYRGYZ_ROMAJI_MAP" to LyricsUtils.KYRGYZ_ROMAJI_MAP.keys,
        "MACEDONIAN_ROMAJI_MAP" to LyricsUtils.MACEDONIAN_ROMAJI_MAP.keys,
    )

    /**
     * Exact key counts, so a table that collapses (the duplicate-key failure mode) cannot pass.
     * Adding a mapping on purpose means bumping the number here — that is the point.
     */
    private val expectedSizes: Map<String, Int> = mapOf(
        "KANA_ROMAJI_MAP" to 108,
        "HANGUL_ROMAJA_MAP.cho" to 19,
        "HANGUL_ROMAJA_MAP.jung" to 21,
        "HANGUL_ROMAJA_MAP.jong" to 145,
        "DEVANAGARI_ROMAJI_MAP" to 91,
        "GURMUKHI_ROMAJI_MAP" to 66,
        "GENERAL_CYRILLIC_ROMAJI_MAP" to 166,
        "RUSSIAN_ROMAJI_MAP" to 4,
        "UKRAINIAN_ROMAJI_MAP" to 10,
        "SERBIAN_ROMAJI_MAP" to 16,
        "BULGARIAN_ROMAJI_MAP" to 18,
        "BELARUSIAN_ROMAJI_MAP" to 4,
        "KYRGYZ_ROMAJI_MAP" to 4,
        "MACEDONIAN_ROMAJI_MAP" to 26,
    )

    @Test
    fun `no table is keyed on the placeholder the Windows-1252 round-trip left behind`() {
        for ((name, keys) in tables) {
            assertEquals("$name has no keys at all", expectedSizes.getValue(name), keys.size)
            assertTrue("$name contains a literal \"?\" key", keys.none { it == "?" || it.contains("?") })
        }
    }

    @Test
    fun `every key of every table is a real script character, not ASCII`() {
        // The damage flattened non-ASCII keys to ASCII, so an ASCII-only key means the table is
        // either corrupt or was never a script table.
        for ((name, keys) in tables) {
            val asciiKeys = keys.filter { key -> key.all { it.code < 128 } }
            assertTrue("$name is keyed on ASCII: $asciiKeys", asciiKeys.isEmpty())
        }
    }

    @Test
    fun `the per-language Cyrillic letter sets still hold their letters`() {
        // Each set is the upper and lower case alphabet of its language, 33 letters for Russian
        // and Ukrainian, 30 for Serbian and Bulgarian, 32 for Belarusian, 36 for Kyrgyz, 31 for
        // Macedonian (2 * letters, since both cases are listed).
        assertEquals(66, LyricsUtils.RUSSIAN_CYRILLIC_LETTERS.size)
        assertEquals(66, LyricsUtils.UKRAINIAN_CYRILLIC_LETTERS.size)
        assertEquals(60, LyricsUtils.SERBIAN_CYRILLIC_LETTERS.size)
        assertEquals(60, LyricsUtils.BULGARIAN_CYRILLIC_LETTERS.size)
        assertEquals(64, LyricsUtils.BELARUSIAN_CYRILLIC_LETTERS.size)
        assertEquals(72, LyricsUtils.KYRGYZ_CYRILLIC_LETTERS.size)
        assertEquals(62, LyricsUtils.MACEDONIAN_CYRILLIC_LETTERS.size)
        val allSets = listOf(
            LyricsUtils.RUSSIAN_CYRILLIC_LETTERS,
            LyricsUtils.UKRAINIAN_CYRILLIC_LETTERS,
            LyricsUtils.SERBIAN_CYRILLIC_LETTERS,
            LyricsUtils.BULGARIAN_CYRILLIC_LETTERS,
            LyricsUtils.BELARUSIAN_CYRILLIC_LETTERS,
            LyricsUtils.KYRGYZ_CYRILLIC_LETTERS,
            LyricsUtils.MACEDONIAN_CYRILLIC_LETTERS,
        )
        assertTrue(allSets.all { set -> set.none { it == "?" } })
        assertTrue(allSets.all { set -> set.none { letter -> letter.all { it.code < 128 } } })
        assertTrue(LyricsUtils.UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS.contains("Є"))
        assertTrue(LyricsUtils.SERBIAN_SPECIFIC_CYRILLIC_LETTERS.contains("Ђ"))
        assertTrue(LyricsUtils.BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS.contains("Ў"))
        assertTrue(LyricsUtils.KYRGYZ_SPECIFIC_CYRILLIC_LETTERS.contains("Ң"))
        assertTrue(LyricsUtils.MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS.contains("Ѓ"))
    }

    // ---- Japanese ----

    @Test
    fun `katakana converts through the kana table`() {
        assertEquals("sakura", LyricsUtils.katakanaToRomaji("サクラ"))
        assertEquals("katakana", LyricsUtils.katakanaToRomaji("カタカナ"))
        assertEquals("sha", LyricsUtils.katakanaToRomaji("シャ"))
        assertEquals("kyou", LyricsUtils.katakanaToRomaji("キョウ"))
    }

    @Test
    fun `a small tsu doubles the following consonant`() {
        assertEquals("kka", LyricsUtils.katakanaToRomaji("ッカ", "カ"))
        assertEquals("macha", LyricsUtils.katakanaToRomaji("マッチャ"))
    }

    @Test
    fun `the long vowel mark is dropped and the moraic n survives`() {
        assertEquals("tokyo", LyricsUtils.katakanaToRomaji("トーキョー"))
        assertEquals("n", LyricsUtils.katakanaToRomaji("ン"))
    }

    @Test
    fun `Japanese romanisation goes surface to kana reading to romaji`() = runBlocking {
        // Katakana input, so the assertion does not depend on kuromoji's dictionary readings, and
        // the spaces between tokens are stripped because kuromoji's segmentation ("サクラ" one
        // token or サ/ク/ラ three) is a property of its dictionary, not of this code.
        assertEquals("sakura", LyricsUtils.romanizeJapanese("サクラ").replace(" ", ""))
        assertEquals("katakana", LyricsUtils.romanizeJapanese("カタカナ").replace(" ", ""))
    }

    // ---- Korean ----

    @Test
    fun `Korean romanises syllable by syllable`() = runBlocking {
        assertEquals("annyeonghaseyo", LyricsUtils.romanizeKorean("안녕하세요"))
        assertEquals("annyeong", LyricsUtils.romanizeKorean("안녕"))
        assertEquals("hanguk", LyricsUtils.romanizeKorean("한국"))
        assertEquals("saranghaeyo", LyricsUtils.romanizeKorean("사랑해요"))
    }

    // ---- Chinese ----

    @Test
    fun `Chinese romanises to pinyin and cleans up the spacing before full-width punctuation`() =
        runBlocking {
            assertEquals("zhong guo", LyricsUtils.romanizeChinese("中国"))
            assertEquals("bei jing", LyricsUtils.romanizeChinese("北京"))
            assertEquals("ni hao，shi jie！", LyricsUtils.romanizeChinese("你好，世界！"))
        }

    // ---- Hindi / Punjabi ----

    @Test
    fun `Devanagari and Gurmukhi convert through their tables`() = runBlocking {
        assertEquals("nmste", LyricsUtils.romanizeHindi("नमस्ते"))
        assertEquals("dhnyvaad", LyricsUtils.romanizeHindi("धन्यवाद"))
        assertEquals("st sree akaal", LyricsUtils.romanizePunjabi("ਸਤ ਸ੍ਰੀ ਅਕਾਲ"))
    }

    // ---- Cyrillic ----

    @Test
    fun `Russian romanises with the general Cyrillic table`() = runBlocking {
        assertEquals("Privet", LyricsUtils.romanizeCyrillic("Привет"))
        assertEquals("Privet, mir", LyricsUtils.romanizeCyrillic("Привет, мир"))
        assertEquals("Moskva", LyricsUtils.romanizeCyrillic("Москва", "Russian"))
    }

    @Test
    fun `a word-initial e is ye and yo stays its own letter`() = runBlocking {
        // The old file compared against the literal "?" here, so a question mark inside a lyric
        // became "ye". Both halves of the comparison are pinned by these two assertions.
        assertEquals("Yelka", LyricsUtils.romanizeCyrillic("Елка"))
        assertEquals("Yolka", LyricsUtils.romanizeCyrillic("Ёлка"))
        assertEquals("ye-e", LyricsUtils.romanizeCyrillic("е-е"))
        assertEquals("Privet? Kak dela?", LyricsUtils.romanizeCyrillic("Привет? Как дела?"))
    }

    @Test
    fun `yu and ya keep their consonant-context spelling`() = runBlocking {
        assertEquals("moyu", LyricsUtils.romanizeCyrillic("мою"))
        assertEquals("moya", LyricsUtils.romanizeCyrillic("моя"))
    }

    @Test
    fun `Ukrainian Serbian Bulgarian Belarusian Kyrgyz and Macedonian use their own tables`() = runBlocking {
        assertEquals("Privit", LyricsUtils.romanizeCyrillic("Привіт", "Ukrainian"))
        assertEquals("Kiyiv", LyricsUtils.romanizeCyrillic("Київ", "Ukrainian"))
        assertEquals("Ukrayina", LyricsUtils.romanizeCyrillic("Україна", "Ukrainian"))
        assertEquals("Liubov", LyricsUtils.romanizeCyrillic("Любов", "Ukrainian"))

        assertEquals("Ljubav", LyricsUtils.romanizeCyrillic("Љубав", "Serbian"))
        assertEquals("ljubov", LyricsUtils.romanizeCyrillic("љубов", "Serbian"))

        assertEquals("Zdravey", LyricsUtils.romanizeCyrillic("Здравей", "Bulgarian"))
        assertEquals("Balgariya", LyricsUtils.romanizeCyrillic("България", "Bulgarian"))

        assertEquals("Pryvitanne", LyricsUtils.romanizeCyrillic("Прывітанне", "Belarusian"))
        // U+02B9 MODIFIER LETTER PRIME, the soft-sign spelling the table chose.
        assertEquals("Belarus\u02B9", LyricsUtils.romanizeCyrillic("Беларусь", "Belarusian"))

        assertEquals("Kyrgyzstan", LyricsUtils.romanizeCyrillic("Кыргызстан", "Kyrgyz"))

        assertEquals("Zdravo", LyricsUtils.romanizeCyrillic("Здраво", "Macedonian"))
        assertEquals("Makedonija", LyricsUtils.romanizeCyrillic("Македонија", "Macedonian"))
    }

    @Test
    fun `a lone Cyrillic e is not enough to trigger romanisation`() = runBlocking {
        // Upstream treats a single `е` as a false positive (it appears in Latin text); the
        // comparison used to be `cyrillicChars[0] == '?'`, which no real letter could match.
        assertNull(LyricsUtils.romanizeCyrillic("е"))
        assertNull(LyricsUtils.romanizeCyrillic("Е"))
        assertNull(LyricsUtils.romanizeCyrillic("hello"))
        assertNull(LyricsUtils.romanizeCyrillic("yes"))
        assertNull(LyricsUtils.romanizeCyrillic("Where Are You? (feat. Someone)"))
    }

    // ---- detection ----

    @Test
    fun `script detection still recognises the scripts the tables cover`() {
        assertTrue(LyricsUtils.isJapanese("さくら"))
        assertTrue(LyricsUtils.isKorean("안녕"))
        assertTrue(LyricsUtils.isChinese("你好"))
        assertTrue(LyricsUtils.isHindi("नमस्ते"))
        assertTrue(LyricsUtils.isPunjabi("ਪਿਆਰ"))
        assertTrue(LyricsUtils.isRussian("Привет"))
        assertTrue(LyricsUtils.isUkrainian("Привіт"))
        assertTrue(LyricsUtils.isSerbian("Љубав"))
        assertTrue(LyricsUtils.isBulgarian("Здравей"))
        assertTrue(LyricsUtils.isBelarusian("Прывітанне"))
        assertTrue(LyricsUtils.isMacedonian("Здраво"))
    }
}
