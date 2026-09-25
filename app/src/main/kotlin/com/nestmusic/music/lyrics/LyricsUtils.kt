/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.lyrics

import android.text.format.DateUtils
import com.atilika.kuromoji.ipadic.Tokenizer
import com.github.promeg.pinyinhelper.Pinyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.*)".toRegex()
val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

// Regex for rich sync format: [MM:SS.mm]<MM:SS.mm> word <MM:SS.mm> word ...
private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.*)".toRegex()
private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>([^<]+)".toRegex()

// Regex for Paxsenix v1/v2/bg format
// [00:00.000]v1: <00:00.000>I <00:00.154>promise...
// [bg: <02:18.078>Yeah<02:19.341>]
private val PAXSENIX_AGENT_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](v\\d+):\\s*(.*)".toRegex()
private val PAXSENIX_BG_LINE_REGEX = "^\\[bg:\\s*(.*)\\]$".toRegex()

// Regex for agent and background markers (existing format)
private val AGENT_REGEX = "\\{agent:([^}]+)\\}".toRegex()
private val BACKGROUND_REGEX = "^\\{bg\\}".toRegex()

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    fun cleanTitleForSearch(title: String): String {
        return title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
    }

    fun filterLyricsCreditLines(lyrics: String): String {
        return lyrics.lines().filter { line ->
            // Strip leading bracketed/braced content, version tags, and timestamps
            // Handles [00:00.00], {agent:v1}, {bg}, [bg: ...], v1: etc.
            var textContent = line.trim()
            
            // Repeatedly strip prefixes while they match common patterns
            var stripping = true
            while (stripping) {
                val prevLength = textContent.length
                textContent = textContent
                    .replaceFirst(Regex("^\\[\\d\\d:\\d\\d\\.\\d{2,3}\\]"), "")
                    .replaceFirst(Regex("^\\{agent:[^}]+\\}"), "")
                    .replaceFirst(Regex("^\\{bg\\}"), "")
                    .replaceFirst(Regex("^\\[bg:.*\\]"), "")
                    .replaceFirst(Regex("^v\\d+:"), "")
                    .trim()
                stripping = textContent.length < prevLength
            }

            val lowerText = textContent.lowercase(Locale.getDefault())
            
            val isCredit = lowerText.startsWith("synced by") ||
                    lowerText.startsWith("lyrics by") ||
                    lowerText.startsWith("music by") ||
                    lowerText.startsWith("arranged by") ||
                    (lowerText.startsWith("[") && lowerText.endsWith("]") && lowerText.length < 40 && lowerText.contains("synced by"))
            
            !isCredit
        }.joinToString("\n")
    }

    private val KANA_ROMAJI_MAP: Map<String, String> = mapOf(
        // Digraphs (Yoon - combinations like kya, sho)
        "\u30ad\u30e3" to "kya", "\u30ad\u30e5" to "kyu", "\u30ad\u30e7" to "kyo",
        "\u30b7\u30e3" to "sha", "\u30b7\u30e5" to "shu", "\u30b7\u30e7" to "sho",
        "\u30c1\u30e3" to "cha", "\u30c1\u30e5" to "chu", "\u30c1\u30e7" to "cho",
        "\u30cb\u30e3" to "nya", "\u30cb\u30e5" to "nyu", "\u30cb\u30e7" to "nyo",
        "\u30d2\u30e3" to "hya", "\u30d2\u30e5" to "hyu", "\u30d2\u30e7" to "hyo",
        "\u30df\u30e3" to "mya", "\u30df\u30e5" to "myu", "\u30df\u30e7" to "myo",
        "\u30ea\u30e3" to "rya", "\u30ea\u30e5" to "ryu", "\u30ea\u30e7" to "ryo",
        "\u30ae\u30e3" to "gya", "\u30ae\u30e5" to "gyu", "\u30ae\u30e7" to "gyo",
        "\u30b8\u30e3" to "ja", "\u30b8\u30e5" to "ju", "\u30b8\u30e7" to "jo",
        "\u30c2\u30e3" to "ja", "\u30c2\u30e5" to "ju", "\u30c2\u30e7" to "jo",
        "\u30d3\u30e3" to "bya", "\u30d3\u30e5" to "byu", "\u30d3\u30e7" to "byo",
        "\u30d4\u30e3" to "pya", "\u30d4\u30e5" to "pyu", "\u30d4\u30e7" to "pyo",
        // Basic Katakana Characters
        "\u30a2" to "a", "\u30a4" to "i", "\u30a6" to "u", "\u30a8" to "e", "\u30aa" to "o",
        "\u30ab" to "ka", "\u30ad" to "ki", "\u30af" to "ku", "\u30b1" to "ke", "\u30b3" to "ko",
        "\u30b5" to "sa", "\u30b7" to "shi", "\u30b9" to "su", "\u30bb" to "se", "\u30bd" to "so",
        "\u30bf" to "ta", "\u30c1" to "chi", "\u30c4" to "tsu", "\u30c6" to "te", "\u30c8" to "to",
        "\u30ca" to "na", "\u30cb" to "ni", "\u30cc" to "nu", "\u30cd" to "ne", "\u30ce" to "no",
        "\u30cf" to "ha", "\u30d2" to "hi", "\u30d5" to "fu", "\u30d8" to "he", "\u30db" to "ho",
        "\u30de" to "ma", "\u30df" to "mi", "\u30e0" to "mu", "\u30e1" to "me", "\u30e2" to "mo",
        "\u30e4" to "ya", "\u30e6" to "yu", "\u30e8" to "yo",
        "\u30e9" to "ra", "\u30ea" to "ri", "\u30eb" to "ru", "\u30ec" to "re", "\u30ed" to "ro",
        "\u30ef" to "wa", "\u30f2" to "o", "\u30f3" to "n",
        // Dakuten (voiced consonants)
        "\u30ac" to "ga", "\u30ae" to "gi", "\u30b0" to "gu", "\u30b2" to "ge", "\u30b4" to "go",
        "\u30b6" to "za", "\u30b8" to "ji", "\u30ba" to "zu", "\u30bc" to "ze", "\u30be" to "zo",
        "\u30c0" to "da", "\u30c2" to "ji", "\u30c5" to "zu", "\u30c7" to "de", "\u30c9" to "do",
        // Handakuten (p-sounds for 'h' group)
        "\u30d0" to "ba", "\u30d3" to "bi", "\u30d6" to "bu", "\u30d9" to "be", "\u30dc" to "bo",
        "\u30d1" to "pa", "\u30d4" to "pi", "\u30d7" to "pu", "\u30da" to "pe", "\u30dd" to "po",
        // Choonpu (long vowel mark)
        "\u30fc" to "",
        // Hiragana (for completeness, Modified Hepburn)
        "\u3042" to "a", "\u3044" to "i", "\u3046" to "u", "\u3048" to "e", "\u304a" to "o",
        "\u304b" to "ka", "\u304d" to "ki", "\u304f" to "ku", "\u3051" to "ke", "\u3053" to "ko"
    )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> = mapOf(
        "cho" to mapOf(
            "\u1100" to "g", "\u1101" to "kk", "\u1102" to "n", "\u1103" to "d",
            "\u1104" to "tt", "\u1105" to "r", "\u1106" to "m", "\u1107" to "b",
            "\u1108" to "pp", "\u1109" to "s", "\u110a" to "ss", "\u110b" to "",
            "\u110c" to "j", "\u110d" to "jj", "\u110e" to "ch", "\u110f" to "k",
            "\u1110" to "t", "\u1111" to "p", "\u1112" to "h"
        ),
        "jung" to mapOf(
            "\u1161" to "a", "\u1162" to "ae", "\u1163" to "ya", "\u1164" to "yae",
            "\u1165" to "eo", "\u1166" to "e", "\u1167" to "yeo", "\u1168" to "ye",
            "\u1169" to "o", "\u116a" to "wa", "\u116b" to "wae", "\u116c" to "oe",
            "\u116d" to "yo", "\u116e" to "u", "\u116f" to "wo", "\u1170" to "we",
            "\u1171" to "wi", "\u1172" to "yu", "\u1173" to "eu", "\u1174" to "eui",
            "\u1175" to "i"
        ),
        "jong" to mapOf(
            "\u11a8" to "k", "\u11a8\u110b" to "g", "\u11a8\u1102" to "ngn", "\u11a8\u1105" to "ngn", "\u11a8\u1106" to "ngm", "\u11a8\u1112" to "kh",
            "\u11a9" to "kk", "\u11a9\u110b" to "kg", "\u11a9\u1102" to "ngn", "\u11a9\u1105" to "ngn", "\u11a9\u1106" to "ngm", "\u11a9\u1112" to "kh",
            "\u11aa" to "k", "\u11aa\u110b" to "ks", "\u11aa\u1102" to "ngn", "\u11aa\u1105" to "ngn", "\u11aa\u1106" to "ngm", "\u11aa\u1112" to "kch",
            "\u11ab" to "n", "\u11ab\u1105" to "ll", "\u11ac" to "n", "\u11ac\u110b" to "nj", "\u11ac\u1102" to "nn", "\u11ac\u1105" to "nn",
            "\u11ac\u1106" to "nm", "\u11ac\u314e" to "nch", "\u11ad" to "n", "\u11ad\u110b" to "nh", "\u11ad\u1105" to "nn", "\u11ae" to "t",
            "\u11ae\u110b" to "d", "\u11ae\u1102" to "nn", "\u11ae\u1105" to "nn", "\u11ae\u1106" to "nm", "\u11ae\u1112" to "th", "\u11af" to "l",
            "\u11af\u110b" to "r", "\u11af\u1102" to "ll", "\u11af\u1105" to "ll", "\u11b0" to "k", "\u11b0\u110b" to "lg", "\u11b0\u1102" to "ngn",
            "\u11b0\u1105" to "ngn", "\u11b0\u1106" to "ngm", "\u11b0\u1112" to "lkh", "\u11b1" to "m", "\u11b1\u110b" to "lm", "\u11b1\u1102" to "mn",
            "\u11b1\u1105" to "mn", "\u11b1\u1106" to "mm", "\u11b1\u1112" to "lmh", "\u11b2" to "p", "\u11b2\u110b" to "lb", "\u11b2\u1102" to "mn",
            "\u11b2\u1105" to "mn", "\u11b2\u1106" to "mm", "\u11b2\u1112" to "lph", "\u11b3" to "t", "\u11b3\u110b" to "ls", "\u11b3\u1102" to "nn",
            "\u11b3\u1105" to "nn", "\u11b3\u1106" to "nm", "\u11b3\u1112" to "lsh", "\u11b4" to "t", "\u11b4\u110b" to "lt", "\u11b4\u1102" to "nn",
            "\u11b4\u1105" to "nn", "\u11b4\u1106" to "nm", "\u11b4\u1112" to "lth", "\u11b5" to "p", "\u11b5\u110b" to "lp", "\u11b5\u1102" to "mn",
            "\u11b5\u1105" to "mn", "\u11b5\u1106" to "mm", "\u11b5\u1112" to "lph", "\u11b6" to "l", "\u11b6\u110b" to "lh", "\u11b6\u1102" to "ll",
            "\u11b6\u1105" to "ll", "\u11b6\u1106" to "lm", "\u11b6\u1112" to "lh", "\u11b7" to "m", "\u11b7\u1105" to "mn", "\u11b8" to "p",
            "\u11b8\u110b" to "b", "\u11b8\u1102" to "mn", "\u11b8\u1105" to "mn", "\u11b8\u1106" to "mm", "\u11b8\u1112" to "ph", "\u11b9" to "p",
            "\u11b9\u110b" to "ps", "\u11b9\u1102" to "mn", "\u11b9\u1105" to "mn", "\u11b9\u1106" to "mm", "\u11b9\u1112" to "psh", "\u11ba" to "t",
            "\u11ba\u110b" to "s", "\u11ba\u1102" to "nn", "\u11ba\u1105" to "nn", "\u11ba\u1106" to "nm", "\u11ba\u1112" to "sh", "\u11bb" to "t",
            "\u11bb\u110b" to "ss", "\u11bb\u1102" to "tn", "\u11bb\u1105" to "tn", "\u11bb\u1106" to "nm", "\u11bb\u1112" to "th", "\u11bc" to "ng",
            "\u11bd" to "t", "\u11bd\u110b" to "j", "\u11bd\u1102" to "nn", "\u11bd\u1105" to "nn", "\u11bd\u1106" to "nm", "\u11bd\u1112" to "ch",
            "\u11be" to "t", "\u11be\u110b" to "ch", "\u11be\u1102" to "nn", "\u11be\u1105" to "nn", "\u11be\u1106" to "nm", "\u11be\u1112" to "ch",
            "\u11bf" to "k", "\u11bf\u110b" to "k", "\u11bf\u1102" to "ngn", "\u11bf\u1105" to "ngn", "\u11bf\u1106" to "ngm", "\u11bf\u1112" to "kh",
            "\u11c0" to "t", "\u11c0\u110b" to "t", "\u11c0\u1102" to "nn", "\u11c0\u1105" to "nn", "\u11c0\u1106" to "nm", "\u11c0\u1112" to "th",
            "\u11c1" to "p", "\u11c1\u110b" to "p", "\u11c1\u1102" to "mn", "\u11c1\u1105" to "mn", "\u11c1\u1106" to "mm", "\u11c1\u1112" to "ph",
            "\u11c2" to "t", "\u11c2\u110b" to "h", "\u11c2\u1102" to "nn", "\u11c2\u1105" to "nn", "\u11c2\u1106" to "mm", "\u11c2\u1112" to "t",
            "\u11c2\u1100" to "k"
        )
    )

    private val DEVANAGARI_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0905" to "a", "\u0906" to "aa", "\u0907" to "i", "\u0908" to "ee", "\u0909" to "u", "\u090a" to "oo",
        "\u090b" to "ri", "\u090f" to "e", "\u0910" to "ai", "\u0913" to "o", "\u0914" to "au",
        "\u0915" to "k", "\u0916" to "kh", "\u0917" to "g", "\u0918" to "gh", "\u0919" to "ng",
        "\u091a" to "ch", "\u091b" to "chh", "\u091c" to "j", "\u091d" to "jh", "\u091e" to "ny",
        "\u091f" to "t", "\u0920" to "th", "\u0921" to "d", "\u0922" to "dh", "\u0923" to "n",
        "\u0924" to "t", "\u0925" to "th", "\u0926" to "d", "\u0927" to "dh", "\u0928" to "n",
        "\u092a" to "p", "\u092b" to "ph", "\u092c" to "b", "\u092d" to "bh", "\u092e" to "m",
        "\u092f" to "y", "\u0930" to "r", "\u0932" to "l", "\u0935" to "v",
        "\u0936" to "sh", "\u0937" to "sh", "\u0938" to "s", "\u0939" to "h",
        "\u0915\u094d\u0937" to "ksh", "\u0924\u094d\u0930" to "tr", "\u091c\u094d\u091e" to "gy", "\u0936\u094d\u0930" to "shr",
        "\u093e" to "aa", "\u093f" to "i", "\u0940" to "ee", "\u0941" to "u", "\u0942" to "oo",
        "\u0943" to "ri", "\u0947" to "e", "\u0948" to "ai", "\u094b" to "o", "\u094c" to "au",
        "\u0902" to "n", "\u0903" to "h", "\u0901" to "n", "\u093c" to "", "\u094d" to "",
        "\u0966" to "0", "\u0967" to "1", "\u0968" to "2", "\u0969" to "3", "\u096a" to "4",
        "\u096b" to "5", "\u096c" to "6", "\u096d" to "7", "\u096e" to "8", "\u096f" to "9",
        "\u0950" to "Om", "\u093d" to "",
        "\u0958" to "q", "\u0959" to "kh", "\u095a" to "g", "\u095b" to "z", "\u095c" to "r", "\u095d" to "rh", "\u095e" to "f", "\u095f" to "y",
        // Decomposed characters with Nukta
        "\u0915\u093C" to "q", "\u0916\u093C" to "kh", "\u0917\u093C" to "g", "\u091c\u093C" to "z", "\u0921\u093C" to "r", "\u0922\u093C" to "rh", "\u092b\u093C" to "f", "\u092f\u093C" to "y"
    )

    private val GURMUKHI_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0a73" to "o", "\u0a05" to "a", "\u0a72" to "e", "\u0a38" to "s", "\u0a39" to "h",
        "\u0a15" to "k", "\u0a16" to "kh", "\u0a17" to "g", "\u0a18" to "gh", "\u0a19" to "ng",
        "\u0a1a" to "ch", "\u0a1b" to "chh", "\u0a1c" to "j", "\u0a1d" to "jh", "\u0a1e" to "ny",
        "\u0a1f" to "t", "\u0a20" to "th", "\u0a21" to "d", "\u0a22" to "dh", "\u0a23" to "n",
        "\u0a24" to "t", "\u0a25" to "th", "\u0a26" to "d", "\u0a27" to "dh", "\u0a28" to "n",
        "\u0a2a" to "p", "\u0a2b" to "ph", "\u0a2c" to "b", "\u0a2d" to "bh", "\u0a2e" to "m",
        "\u0a2f" to "y", "\u0a30" to "r", "\u0a32" to "l", "\u0a35" to "v", "\u0a5c" to "r",
        "\u0a36" to "sh", "\u0a59" to "kh", "\u0a5a" to "g", "\u0a5b" to "z", "\u0a5e" to "f", "\u0a33" to "l",
        "\u0a3e" to "aa", "\u0a3f" to "i", "\u0a40" to "ee", "\u0a41" to "u", "\u0a42" to "oo",
        "\u0a47" to "e", "\u0a48" to "ai", "\u0a4b" to "o", "\u0a4c" to "au",
        "\u0a70" to "n", "\u0a02" to "n", "\u0a71" to "", "\u0a4d" to "", "\u0a3c" to "",
        "\u0a74" to "Ek Onkar",
        "\u0a66" to "0", "\u0a67" to "1", "\u0a68" to "2", "\u0a69" to "3", "\u0a6a" to "4",
        "\u0a6b" to "5", "\u0a6c" to "6", "\u0a6d" to "7", "\u0a6e" to "8", "\u0a6f" to "9"
    )

    private val GENERAL_CYRILLIC_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0410" to "A", "\u0411" to "B", "\u0412" to "V", "\u0413" to "G", "\u0490" to "G", "\u0414" to "D",
        "\u0403" to "G\u0301", "\u0402" to "\u0110", "\u0415" to "E", "\u0401" to "Yo", "\u0404" to "Ye", "\u0416" to "Zh",
        "\u0417" to "Z", "\u0405" to "Dz", "\u0418" to "I", "\u0406" to "I", "\u0407" to "Yi", "\u0419" to "Y",
        "\u0408" to "Y", "\u041a" to "K", "\u041b" to "L", "\u0409" to "Ly", "\u041c" to "M", "\u041d" to "N",
        "\u040a" to "Ny", "\u041e" to "O", "\u041f" to "P", "\u0420" to "R", "\u0421" to "S", "\u0422" to "T",
        "\u040b" to "\u0106", "\u0423" to "U", "\u040e" to "\u016c", "\u0424" to "F", "\u0425" to "Kh", "\u0426" to "Ts",
        "\u0427" to "Ch", "\u040f" to "D\u017e", "\u0428" to "Sh", "\u0429" to "Shch", "\u042a" to "\u02ba", "\u042b" to "Y",
        "\u042c" to "\u02b9", "\u042d" to "E", "\u042e" to "Yu", "\u042f" to "Ya",
        "\u0460" to "O", "\u0462" to "Ya", "\u0464" to "Ye", "\u0466" to "Ya", "\u0468" to "Ya",
        "\u046a" to "U", "\u046c" to "Yu", "\u046e" to "Ks", "\u0470" to "Ps", "\u0472" to "F",
        "\u0474" to "I", "\u0476" to "I", "\u0492" to "Gh", "\u0494" to "G", "\u0496" to "Zh",
        "\u0498" to "Dz", "\u049a" to "Q", "\u049c" to "K", "\u049e" to "K", "\u04a0" to "K",
        "\u04a2" to "Ng", "\u04a4" to "Ng", "\u04a6" to "P", "\u04a8" to "O", "\u04aa" to "S",
        "\u04ac" to "T", "\u04ae" to "U", "\u04b0" to "U", "\u04b2" to "Kh", "\u04b4" to "Ts",
        "\u04b6" to "Ch", "\u04b8" to "Ch", "\u04ba" to "H", "\u04bc" to "Ch", "\u04be" to "Ch",
        "\u040c" to "K\u0301", "\u04e8" to "\u00d6",

        "\u0430" to "a", "\u0431" to "b", "\u0432" to "v", "\u0433" to "g", "\u0491" to "g", "\u0434" to "d",
        "\u0453" to "g\u0301", "\u0452" to "\u0111", "\u0435" to "e", "\u0451" to "yo", "\u0454" to "ye", "\u0436" to "zh",
        "\u0437" to "z", "\u0455" to "dz", "\u0438" to "i", "\u0456" to "i", "\u0457" to "yi", "\u0439" to "y",
        "\u0458" to "y", "\u043a" to "k", "\u043b" to "l", "\u0459" to "ly", "\u043c" to "m", "\u043d" to "n",
        "\u045a" to "ny", "\u043e" to "o", "\u043f" to "p", "\u0440" to "r", "\u0441" to "s", "\u0442" to "t",
        "\u045b" to "\u0107", "\u0443" to "u", "\u045e" to "\u016d", "\u0444" to "f", "\u0445" to "kh", "\u0446" to "ts",
        "\u0447" to "ch", "\u045f" to "d\u017e", "\u0448" to "sh", "\u0449" to "shch", "\u044a" to "\u02ba", "\u044b" to "y",
        "\u044c" to "\u02b9", "\u044d" to "e", "\u044e" to "yu", "\u044f" to "ya",
        "\u0461" to "o", "\u0463" to "ya", "\u0465" to "ye", "\u0467" to "ya", "\u0469" to "ya",
        "\u046b" to "u", "\u046d" to "yu", "\u046f" to "ks", "\u0471" to "ps", "\u0473" to "f",
        "\u0475" to "i", "\u0477" to "i", "\u0493" to "gh", "\u0495" to "g", "\u0497" to "zh",
        "\u0499" to "dz", "\u049b" to "q", "\u049d" to "k", "\u049f" to "k", "\u04a1" to "k",
        "\u04a3" to "ng", "\u04a5" to "ng", "\u04a7" to "p", "\u04a9" to "o", "\u04ab" to "s",
        "\u04ad" to "t", "\u04af" to "u", "\u04b1" to "u", "\u04b3" to "kh", "\u04b5" to "ts",
        "\u04b7" to "ch", "\u04b9" to "ch", "\u04bb" to "h", "\u04bd" to "ch", "\u04bf" to "ch",
        "\u045c" to "\u1e31", "\u04e9" to "\u00f6"
    )

    private val RUSSIAN_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u043e\u0433\u043e" to "ovo", "\u041e\u0433\u043e" to "Ovo", "\u0435\u0433\u043e" to "evo", "\u0415\u0433\u043e" to "Evo"
    )

    private val UKRAINIAN_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0413" to "H", "\u0433" to "h",
        "\u0490" to "G", "\u0491" to "g",
        "\u0404" to "Ye", "\u0454" to "ye",
        "\u0406" to "I", "\u0456" to "i",
        "\u0407" to "Yi", "\u0457" to "yi"
    )

    private val SERBIAN_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0416" to "\u017d", "\u0409" to "Lj", "\u040a" to "Nj", "\u0426" to "C", "\u0427" to "\u010c",
        "\u040f" to "D\u017e", "\u0428" to "\u0160", "\u0425" to "H",

        "\u0436" to "\u017e", "\u0459" to "lj", "\u045a" to "nj", "\u0446" to "c", "\u0447" to "\u010d",
        "\u045f" to "d\u017e", "\u0448" to "\u0161", "\u0445" to "h"
    )

    private val BULGARIAN_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0416" to "Zh", "\u0426" to "Ts", "\u0427" to "Ch", "\u0428" to "Sh", "\u0429" to "Sht",
        "\u042a" to "A", "\u042c" to "Y", "\u042e" to "Yu", "\u042f" to "Ya",

        "\u0436" to "zh", "\u0446" to "ts", "\u0447" to "ch", "\u0448" to "sh", "\u0449" to "sht",
        "\u044a" to "a", "\u044c" to "y", "\u044e" to "yu", "\u044f" to "ya"
    )

    private val BELARUSIAN_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0413" to "H", "\u0433" to "h", "\u040e" to "W", "\u045e" to "w"
    )

    private val KYRGYZ_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u04ae" to "\u00dc", "\u04af" to "\u00fc", "\u042b" to "Y", "\u044b" to "y"
    )

    private val MACEDONIAN_ROMAJI_MAP: Map<String, String> = mapOf(
        "\u0403" to "Gj", "\u0405" to "Dz", "\u0418" to "I", "\u0408" to "J", "\u0409" to "Lj",
        "\u040a" to "Nj", "\u040c" to "Kj", "\u040f" to "D\u017e", "\u0427" to "\u010c", "\u0428" to "Sh",
        "\u0416" to "Zh", "\u0426" to "C", "\u0425" to "H",

        "\u0453" to "gj", "\u0455" to "dz", "\u0438" to "i", "\u0458" to "j", "\u0459" to "lj",
        "\u045a" to "nj", "\u045c" to "kj", "\u045f" to "d\u017e", "\u0447" to "\u010d", "\u0448" to "sh",
        "\u0436" to "zh", "\u0446" to "c", "\u0445" to "h"
    )

    private val RUSSIAN_CYRILLIC_LETTERS = setOf(
        "\u0410", "\u0411", "\u0412", "\u0413", "\u0414", "\u0415", "\u0401", "\u0416", "\u0417", "\u0418", "\u0419", "\u041a", "\u041b", "\u041c", "\u041d",
        "\u041e", "\u041f", "\u0420", "\u0421", "\u0422", "\u0423", "\u0424", "\u0425", "\u0426", "\u0427", "\u0428", "\u0429", "\u042a", "\u042b", "\u042c",
        "\u042d", "\u042e", "\u042f",

        "\u0430", "\u0431", "\u0432", "\u0433", "\u0434", "\u0435", "\u0451", "\u0436", "\u0437", "\u0438", "\u0439", "\u043a", "\u043b", "\u043c", "\u043d",
        "\u043e", "\u043f", "\u0440", "\u0441", "\u0442", "\u0443", "\u0444", "\u0445", "\u0446", "\u0447", "\u0448", "\u0449", "\u044a", "\u044b", "\u044c",
        "\u044d", "\u044e", "\u044f"
    )

    private val UKRAINIAN_CYRILLIC_LETTERS = setOf(
       "\u0410", "\u0411", "\u0412", "\u0413", "\u0490", "\u0414", "\u0415", "\u0404", "\u0416", "\u0417", "\u0418", "\u0406", "\u0407", "\u0419",
        "\u041a", "\u041b", "\u041c", "\u041d", "\u041e", "\u041f", "\u0420", "\u0421", "\u0422", "\u0423", "\u0424", "\u0425", "\u0426", "\u0427",
        "\u0428", "\u0429", "\u042c", "\u042e", "\u042f",

        "\u0430", "\u0431", "\u0432", "\u0433", "\u0491", "\u0434", "\u0435", "\u0454", "\u0436", "\u0437", "\u0438", "\u0456", "\u0457", "\u0439",
        "\u043a", "\u043b", "\u043c", "\u043d", "\u043e", "\u043f", "\u0440", "\u0441", "\u0442", "\u0443", "\u0444", "\u0445", "\u0446", "\u0447",
        "\u0448", "\u0449", "\u044c", "\u044e", "\u044f"
    )

    private val SERBIAN_CYRILLIC_LETTERS = setOf(
        "\u0410", "\u0411", "\u0412", "\u0413", "\u0414", "\u0402", "\u0415", "\u0416", "\u0417", "\u0418", "\u0408", "\u041a", "\u041b", "\u0409", "\u041c",
        "\u041d", "\u040a", "\u041e", "\u041f", "\u0420", "\u0421", "\u0422", "\u040b", "\u0423", "\u0424", "\u0425", "\u0426", "\u0427", "\u040f", "\u0428",

        "\u0430", "\u0431", "\u0432", "\u0433", "\u0434", "\u0452", "\u0435", "\u0436", "\u0437", "\u0438", "\u0458", "\u043a", "\u043b", "\u0459", "\u043c",
        "\u043d", "\u045a", "\u043e", "\u043f", "\u0440", "\u0441", "\u0442", "\u045b", "\u0443", "\u0444", "\u0445", "\u0446", "\u0447", "\u045f", "\u0448"
    )

    private val BULGARIAN_CYRILLIC_LETTERS = setOf(
        "\u0410", "\u0411", "\u0412", "\u0413", "\u0414", "\u0415", "\u0416", "\u0417", "\u0418", "\u0419", "\u041a", "\u041b", "\u041c",
        "\u041d", "\u041e", "\u041f", "\u0420", "\u0421", "\u0422", "\u0423", "\u0424", "\u0425", "\u0426", "\u0427", "\u0428", "\u0429",
        "\u042a", "\u042c", "\u042e", "\u042f",

        "\u0430", "\u0431", "\u0432", "\u0433", "\u0434", "\u0435", "\u0436", "\u0437", "\u0438", "\u0439", "\u043a", "\u043b", "\u043c",
        "\u043d", "\u043e", "\u043f", "\u0440", "\u0441", "\u0442", "\u0443", "\u0444", "\u0445", "\u0446", "\u0447", "\u0448", "\u0449",
        "\u044a", "\u044c", "\u044e", "\u044f"
    )

    private val BELARUSIAN_CYRILLIC_LETTERS = setOf(
        "\u0410", "\u0411", "\u0412", "\u0413", "\u0414", "\u0415", "\u0401", "\u0416", "\u0417", "\u0406", "\u0419", "\u041a", "\u041b", "\u041c", "\u041d",
        "\u041e", "\u041f", "\u0420", "\u0421", "\u0422", "\u0423", "\u040e", "\u0424", "\u0425", "\u0426", "\u0427", "\u0428", "\u042c", "\u042e", "\u042f",
        "\u042b", "\u042d",

        "\u0430", "\u0431", "\u0432", "\u0433", "\u0434", "\u0435", "\u0451", "\u0436", "\u0437", "\u0456", "\u0439", "\u043a", "\u043b", "\u043c", "\u043d",
        "\u043e", "\u043f", "\u0440", "\u0441", "\u0442", "\u0443", "\u045e", "\u0444", "\u0445", "\u0446", "\u0447", "\u0448", "\u044c", "\u044e", "\u044f",
        "\u044b", "\u044d"
    )

    private val KYRGYZ_CYRILLIC_LETTERS = setOf(
        "\u0410", "\u0411", "\u0412", "\u0413", "\u0414", "\u0415", "\u0401", "\u0416", "\u0417", "\u0418", "\u0419", "\u041a", "\u041b", "\u041c", "\u041d",
        "\u04a2", "\u041e", "\u04e8", "\u041f", "\u0420", "\u0421", "\u0422", "\u0423", "\u04ae", "\u0424", "\u0425", "\u0426", "\u0427", "\u0428", "\u0429",
        "\u042a", "\u042b", "\u042c", "\u042d", "\u042e", "\u042f",

        "\u0430", "\u0431", "\u0432", "\u0433", "\u0434", "\u0435", "\u0451", "\u0436", "\u0437", "\u0438", "\u0439", "\u043a", "\u043b", "\u043c", "\u043d",
        "\u04a3", "\u043e", "\u04e9", "\u043f", "\u0440", "\u0441", "\u0442", "\u0443", "\u04af", "\u0444", "\u0445", "\u0446", "\u0447", "\u0448", "\u0449",
        "\u044a", "\u044b", "\u044c", "\u044d", "\u044e", "\u044f"
    )

    private val MACEDONIAN_CYRILLIC_LETTERS = setOf(
        "\u0410", "\u0411", "\u0412", "\u0413", "\u0414", "\u0403", "\u0415", "\u0416", "\u0417", "\u0405", "\u0418", "\u0408", "\u041a", "\u041b",
        "\u0409", "\u041c", "\u041d", "\u040a", "\u041e", "\u041f", "\u0420", "\u0421", "\u0422", "\u040c", "\u0423", "\u0424", "\u0425",
        "\u0426", "\u0427", "\u040f", "\u0428",

        "\u0430", "\u0431", "\u0432", "\u0433", "\u0434", "\u0453", "\u0435", "\u0436", "\u0437", "\u0455", "\u0438", "\u0458", "\u043a", "\u043b",
        "\u0459", "\u043c", "\u043d", "\u045a", "\u043e", "\u043f", "\u0440", "\u0441", "\u0442", "\u045c", "\u0443", "\u0444", "\u0445",
        "\u0446", "\u0447", "\u045f", "\u0448"
    )

    private val UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS = setOf(
        "\u0490", "\u0491", "\u0404", "\u0454", "\u0406", "\u0456", "\u0407", "\u0457"
    )

    private val SERBIAN_SPECIFIC_CYRILLIC_LETTERS = setOf(
        "\u0402", "\u0452", "\u0408", "\u0458", "\u0409", "\u0459", "\u040a", "\u045a", "\u040b", "\u045b", "\u040f", "\u045f"
    )

    private val BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS = setOf(
        "\u040e", "\u045e", "\u0406", "\u0456"
    )

    private val KYRGYZ_SPECIFIC_CYRILLIC_LETTERS = setOf(
        "\u04a2", "\u04a3", "\u04e8", "\u04e9", "\u04ae", "\u04af"
    )

    private val MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS = setOf(
        "\u0403", "\u0453", "\u0405", "\u0455", "\u040c", "\u045c"
    )

    // Lazy initialized Tokenizer
    private val kuromojiTokenizer: Tokenizer by lazy {
        Tokenizer()
    }

    private val HEX_ENTITY_REGEX = "&#x([0-9a-fA-F]+);".toRegex()
    private val DEC_ENTITY_REGEX = "&#(\\d+);".toRegex()

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val end = text.indexOf(';', i + 1)
                if (end != -1 && end - i < 12) {
                    val entity = text.substring(i, end + 1)
                    val decoded = when {
                        entity == "&apos;" -> "'"
                        entity == "&quot;" -> "\""
                        entity == "&lt;" -> "<"
                        entity == "&gt;" -> ">"
                        entity == "&nbsp;" -> " "
                        entity == "&amp;" -> "&"
                        entity.startsWith("&#x") -> {
                            entity.substring(3, entity.length - 1).toIntOrNull(16)?.let { codePoint ->
                                if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else "\uFFFD"
                            }
                        }
                        entity.startsWith("&#") -> {
                            entity.substring(2, entity.length - 1).toIntOrNull()?.let { codePoint ->
                                if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else "\uFFFD"
                            }
                        }
                        else -> null
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        if (lyrics.isBlank()) return emptyList()

        // Fast unescape
        val unescapedLyrics = if (lyrics.contains('\\') || lyrics.startsWith("\"")) {
            val s = lyrics.trim().removePrefix("\"").removeSuffix("\"")
            val sb = StringBuilder(s.length)
            var j = 0
            while (j < s.length) {
                val c = s[j]
                if (c == '\\' && j + 1 < s.length) {
                    when (val next = s[j + 1]) {
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(c).append(next)
                    }
                    j += 2
                } else {
                    sb.append(c)
                    j++
                }
            }
            sb.toString()
        } else lyrics

        val decodedLyrics = decodeHtmlEntities(unescapedLyrics)

        val lines = decodedLyrics.lines()
            .filter { 
                it.isNotBlank() || it.trim().startsWith("[") || it.trim().startsWith("<")
            }
            .filter { !it.trim().startsWith("[offset:") }

        // Check if this is rich sync format (contains <MM:SS.mm> patterns)
        val isRichSync = lines.any { line ->
            RICH_SYNC_LINE_REGEX.matches(line.trim()) &&
            RICH_SYNC_WORD_REGEX.containsMatchIn(line)
        }

        return if (isRichSync) {
            parseRichSyncLyrics(lines)
        } else {
            parseStandardLyrics(lines)
        }
    }

    /**
     * Parse rich sync lyrics format: [MM:SS.mm]<MM:SS.mm> word <MM:SS.mm> word ...
     * This format provides word-by-word timing for karaoke-style highlighting
     */
    private fun parseRichSyncLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()
        var lastNonBgAgent: String? = null

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            
            // Try Paxsenix bg format first: [bg: <02:18.078>Yeah<02:19.341>]
            val bgMatch = PAXSENIX_BG_LINE_REGEX.find(trimmedLine)
            if (bgMatch != null) {
                val content = bgMatch.groupValues[1]
                
                // Parse word-level timestamps from content
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }
                
                // Extract plain text (remove all <MM:SS.mm> tags)
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
                
                val lineTimeMs = wordTimings?.firstOrNull()?.startTime?.let { (it * 1000).toLong() } ?: 0L
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = lastNonBgAgent ?: "bg", isBackground = true))
                return@forEachIndexed
            }
            
            // Try Paxsenix agent format: [00:00.000]v1: <00:00.000>I <00:00.154>promise...
            val agentMatch = PAXSENIX_AGENT_LINE_REGEX.find(trimmedLine)
            if (agentMatch != null) {
                val minutes = agentMatch.groupValues[1].toLongOrNull() ?: 0L
                val seconds = agentMatch.groupValues[2].toLongOrNull() ?: 0L
                val centiseconds = agentMatch.groupValues[3].toLongOrNull() ?: 0L
                val agent = agentMatch.groupValues[4] // v1, v2, etc.
                val content = agentMatch.groupValues[5]
                
                val millisPart = if (agentMatch.groupValues[3].length == 3) centiseconds else centiseconds * 10
                val lineTimeMs = minutes * DateUtils.MINUTE_IN_MILLIS + seconds * DateUtils.SECOND_IN_MILLIS + millisPart
                
                // Parse word-level timestamps from content
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }
                
                // Extract plain text (remove all <MM:SS.mm> tags)
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
                
                if (!agent.isNullOrBlank()) {
                    lastNonBgAgent = agent
                }
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = agent, isBackground = false))
                return@forEachIndexed
            }
            
            // Try existing format: [MM:SS.mm]{agent:v1}... or [MM:SS.mm]{bg}...
            val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(trimmedLine)
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLongOrNull() ?: 0L
                val seconds = matchResult.groupValues[2].toLongOrNull() ?: 0L
                val centiseconds = matchResult.groupValues[3].toLongOrNull() ?: 0L

                // Convert to milliseconds
                val millisPart = if (matchResult.groupValues[3].length == 3) centiseconds else centiseconds * 10
                val lineTimeMs = minutes * DateUtils.MINUTE_IN_MILLIS + seconds * DateUtils.SECOND_IN_MILLIS + millisPart

                var content = matchResult.groupValues[4].trimStart()

                // Parse agent marker {agent:v1}
                val oldAgentMatch = AGENT_REGEX.find(content)
                val agent = oldAgentMatch?.groupValues?.get(1)
                if (oldAgentMatch != null) {
                    content = content.replaceFirst(AGENT_REGEX, "")
                }

                // Parse background marker {bg}
                val isBackground = BACKGROUND_REGEX.containsMatchIn(content)
                if (isBackground) {
                    content = content.replaceFirst(BACKGROUND_REGEX, "")
                }

                // Parse word-level timestamps from content
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }

                // Extract plain text (remove all <MM:SS.mm> tags)
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()

                if (!isBackground && !agent.isNullOrBlank()) {
                    lastNonBgAgent = agent
                }
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = if (isBackground) lastNonBgAgent ?: "bg" else agent, isBackground = isBackground))
            }
        }

        return result.sorted()
    }

    /**
     * Parse word timestamps from rich sync content
     * Format: <MM:SS.mm> word <MM:SS.mm> word ...
     */
    private fun parseRichSyncWords(content: String, currentIndex: Int, allLines: List<String>): List<WordTimestamp>? {
        val wordMatches = RICH_SYNC_WORD_REGEX.findAll(content).toList()

        if (wordMatches.isEmpty()) return null

        // Check for a trailing end timestamp after the last word.
        // The provider uses two formats:
        //   - Angle brackets: <MM:SS.mmm> (used in v1:/v2: prefixed lines)
        //   - Square brackets: [MM:SS.xx] (used in non-prefixed lines)
        val lastMatchEnd = wordMatches.last().range.last
        val trailingContent = content.substring(lastMatchEnd + 1).trim()
        val angleTrailingMatch = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>".toRegex().find(trailingContent)
        val squareTrailingMatch = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex().find(trailingContent)
        val trailingTimeMatch = angleTrailingMatch ?: squareTrailingMatch
        val trailingEndTime: Double? = if (trailingTimeMatch != null && trailingContent.substring(trailingTimeMatch.range.last + 1).removeSuffix("]").isBlank()) {
            val tMin = trailingTimeMatch.groupValues[1].toLongOrNull() ?: 0L
            val tSec = trailingTimeMatch.groupValues[2].toLongOrNull() ?: 0L
            val tFrac = trailingTimeMatch.groupValues[3].toLongOrNull() ?: 0L
            val tFracPart = if (trailingTimeMatch.groupValues[3].length == 3) tFrac / 1000.0 else tFrac / 100.0
            tMin * 60.0 + tSec + tFracPart
        } else null

        val wordTimings = mutableListOf<WordTimestamp>()

        wordMatches.forEachIndexed { index, match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3].toLongOrNull() ?: 0L

            val fractionPart = if (match.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
            val startTimeSeconds = minutes * 60.0 + seconds + fractionPart

            val rawText = match.groupValues[4]
            val hasTrailingSpace = rawText.endsWith(" ")
            val words = rawText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

            // Get the next timestamp for end time calculation
            val nextTimestamp: Double
            val nextLineTime: Double?

            if (index < wordMatches.size - 1) {
                val nextMatch = wordMatches[index + 1]
                val nextMin = nextMatch.groupValues[1].toLongOrNull() ?: 0L
                val nextSec = nextMatch.groupValues[2].toLongOrNull() ?: 0L
                val nextFrac = nextMatch.groupValues[3].toLongOrNull() ?: 0L
                val nextFracPart = if (nextMatch.groupValues[3].length == 3) nextFrac / 1000.0 else nextFrac / 100.0
                nextTimestamp = nextMin * 60.0 + nextSec + nextFracPart
                nextLineTime = null
            } else {
                nextLineTime = getNextLineStartTime(currentIndex, allLines)
                nextTimestamp = trailingEndTime ?: nextLineTime ?: (startTimeSeconds + 0.5)
            }

            words.forEachIndexed { wordIndex, word ->
                val isLastWordInGroup = wordIndex == words.lastIndex
                val isLastWordOverall = index == wordMatches.lastIndex && isLastWordInGroup

                val wordStartTime = startTimeSeconds + (nextTimestamp - startTimeSeconds) * wordIndex / words.size
                val wordEndTime = if (!isLastWordInGroup) {
                    startTimeSeconds + (nextTimestamp - startTimeSeconds) * (wordIndex + 1) / words.size
                } else if (!isLastWordOverall) {
                    nextTimestamp
                } else {
                    trailingEndTime ?: nextLineTime ?: (startTimeSeconds + 0.5)
                }

                val wordHasTrailingSpace = if (!isLastWordInGroup) {
                    true
                } else if (!isLastWordOverall) {
                    hasTrailingSpace
                } else {
                    // Last word of last match - check if there's text after it (excluding our optional trailing timestamp)
                    val textAfterMatch = if (trailingTimeMatch != null) {
                        trailingContent.substring(0, trailingTimeMatch.range.first)
                    } else {
                        trailingContent
                    }
                    textAfterMatch.isNotBlank()
                }

                if (word.isNotBlank()) {
                    wordTimings.add(WordTimestamp(word, wordStartTime, wordEndTime, wordHasTrailingSpace))
                }
            }
        }

        return if (wordTimings.isNotEmpty()) wordTimings else null
    }

    /**
     * Get the start time of the next line for calculating the last word's end time
     */
    private fun getNextLineStartTime(currentIndex: Int, allLines: List<String>): Double? {
        if (currentIndex + 1 >= allLines.size) return null

        val nextLine = allLines[currentIndex + 1].trim()
        
        // Try standard rich sync line
        val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(nextLine)
        if (matchResult != null) {
            val minutes = matchResult.groupValues[1].toLongOrNull() ?: return null
            val seconds = matchResult.groupValues[2].toLongOrNull() ?: return null
            val fraction = matchResult.groupValues[3].toLongOrNull() ?: 0L

            val fractionPart = if (matchResult.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
            return minutes * 60.0 + seconds + fractionPart
        }
        
        // Try background line
        val bgMatch = PAXSENIX_BG_LINE_REGEX.matchEntire(nextLine)
        if (bgMatch != null) {
            val content = bgMatch.groupValues[1]
            val wordMatch = RICH_SYNC_WORD_REGEX.find(content) ?: return null
            val minutes = wordMatch.groupValues[1].toLongOrNull() ?: return null
            val seconds = wordMatch.groupValues[2].toLongOrNull() ?: return null
            val fraction = wordMatch.groupValues[3].toLongOrNull() ?: 0L
            val fractionPart = if (wordMatch.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
            return minutes * 60.0 + seconds + fractionPart
        }

        return null
    }

    /**
     * Parse standard synced lyrics format: [MM:SS.mm] text
     */
    private fun parseStandardLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trim().startsWith("<") || !line.trim().endsWith(">")) {
                val entries = parseLine(line, null)
                if (entries != null) {
                    val wordTimestamps = if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        if (nextLine.trim().startsWith("<") && nextLine.trim().endsWith(">")) {
                            parseWordTimestamps(nextLine.trim().removeSurrounding("<", ">"))
                        } else null
                    } else null

                    if (wordTimestamps != null) {
                        result.addAll(entries.map { entry ->
                            LyricsEntry(entry.time, entry.text, wordTimestamps, agent = entry.agent, isBackground = entry.isBackground)
                        })
                    } else {
                        result.addAll(entries)
                    }
                }
            }
            i++
        }
        return result.sorted()
    }

    private fun parseWordTimestamps(data: String): List<WordTimestamp>? {
        if (data.isBlank()) return null
        return try {
            data.split("|").mapNotNull { wordData ->
                val parts = wordData.split(":")
                if (parts.size >= 3) {
                    val text = parts.dropLast(2).joinToString(":")
                    val startTime = parts[parts.size - 2].toDoubleOrNull() ?: 0.0
                    val endTime = parts[parts.size - 1].toDoubleOrNull() ?: 0.0
                    val isLast = wordData == data.split("|").last()
                    WordTimestamp(
                        text = text,
                        startTime = startTime,
                        endTime = endTime,
                        hasTrailingSpace = !isLast
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLine(line: String, words: List<WordTimestamp>? = null): List<LyricsEntry>? {
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        var text = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times)

        // Parse agent marker {agent:v1}
        val agentMatch = AGENT_REGEX.find(text)
        val agent = agentMatch?.groupValues?.get(1)
        if (agentMatch != null) {
            text = text.replaceFirst(AGENT_REGEX, "")
        }

        // Parse background marker {bg}
        val isBackground = BACKGROUND_REGEX.containsMatchIn(text)
        if (isBackground) {
            text = text.replaceFirst(BACKGROUND_REGEX, "")
        }

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text, words, agent = agent, isBackground = isBackground)
            }.toList()
    }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int {
        val threshold = 100L
        for (index in lines.indices) {
            if (lines[index].time >= position + threshold) {
                return index - 1
            }
        }
        return lines.lastIndex
    }

    /**
     * Returns the set of line indices that are currently active (being sung).
     * A line is active if playback position >= line.time AND position < line end time.
     * Line end time = the last word's endTime if word timings exist, otherwise the next line's start time.
     * This supports simultaneous singers whose lines overlap in time.
     */
    fun findActiveLineIndices(
        lines: List<LyricsEntry>,
        position: Long,
    ): Set<Int> {
        val active = mutableSetOf<Int>()
        val hasWordTimings = lines.any { !it.words.isNullOrEmpty() }

        for (index in lines.indices) {
            val line = lines[index]
            if (line.time > position) break // Past current position, stop early

            // Determine this line's end time
            val lineEndMs: Long = if (!line.words.isNullOrEmpty()) {
                // Use last word's endTime converted to ms
                (line.words.last().endTime * 1000).toLong()
            } else {
                // Fallback: next line's start time
                if (index + 1 < lines.size) lines[index + 1].time else Long.MAX_VALUE
            }

            if (position <= lineEndMs) {
                active.add(index)
            }
        }

        if (!hasWordTimings && active.size > 1) {
            val mainActive = active.filter { lines[it].isBackground == false }
            if (mainActive.size > 1) {
                val maxTime = mainActive.maxOf { lines[it].time }
                active.removeAll { it in mainActive && lines[it].time < maxTime }
            }
        }

        return active
    }

    // TODO: Will be useful if we let the user pick the language, useless for now
    /* enum class CyrillicLanguage {
        RUSSIAN,
        UKRAINIAN,
        SERBIAN,
        BULGARIAN,
        BELARUSIAN,
        KYRGYZ,
        MACEDONIAN
    } */

    suspend fun romanizeJapanese(text: String): String = withContext(Dispatchers.Default) {
        val tokens = kuromojiTokenizer.tokenize(text)
        val romanizedTokens = tokens.mapIndexed { index, token ->
            val currentReading = if (token.reading.isNullOrEmpty() || token.reading == "*") {
                token.surface
            } else {
                token.reading
            }
            val nextTokenReading = if (index + 1 < tokens.size) {
                tokens[index + 1].reading?.takeIf { it.isNotEmpty() && it != "*" } ?: tokens[index + 1].surface
            } else {
                null
            }
            katakanaToRomaji(currentReading, nextTokenReading)
        }
        romanizedTokens.joinToString(" ")
    }

    fun katakanaToRomaji(katakana: String?, nextKatakana: String? = null): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length)
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed && katakana[i] == '\u30c3') {
                val nextCharToDouble = nextKatakana?.getOrNull(0)
                if (nextCharToDouble != null) {
                    val nextCharRomaji = KANA_ROMAJI_MAP[nextCharToDouble.toString()]?.getOrNull(0)?.toString()
                        ?: nextCharToDouble.toString()
                    romajiBuilder.append(nextCharRomaji.lowercase().trim())
                }
                i += 1
                consumed = true
            }

            if (!consumed) {
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    suspend fun romanizeKorean(text: String): String = withContext(Dispatchers.Default) {
        val romajaBuilder = StringBuilder()
        var prevFinal: String? = null

        for (i in text.indices) {
            val char = text[i]
            if (char in '\uAC00'..'\uD7A3') {
                val syllableIndex = char.code - 0xAC00
                val choIndex = syllableIndex / (21 * 28)
                val jungIndex = (syllableIndex % (21 * 28)) / 28
                val jongIndex = syllableIndex % 28

                val choChar = (0x1100 + choIndex).toChar().toString()
                val jungChar = (0x1161 + jungIndex).toChar().toString()
                val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                if (prevFinal != null) {
                    val contextKey = prevFinal + choChar
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                        ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal)
                        ?: prevFinal
                    romajaBuilder.append(jong)
                }

                val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                romajaBuilder.append(cho).append(jung)
                prevFinal = jongChar
            } else {
                if (prevFinal != null) {
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                    romajaBuilder.append(jong)
                    prevFinal = null
                }
                romajaBuilder.append(char)
            }
        }

        if (prevFinal != null) {
            val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
            romajaBuilder.append(jong)
        }

        romajaBuilder.toString()
    }

    suspend fun romanizeChinese(text: String): String = withContext(Dispatchers.Default) {
        if (text.isEmpty()) return@withContext ""
        val builder = StringBuilder(text.length * 2)
        for (ch in text) {
            if (ch in '\u4E00'..'\u9FFF') {
                val py = Pinyin.toPinyin(ch).lowercase(Locale.getDefault())
                builder.append(py).append(' ')
            } else {
                builder.append(ch)
            }
        }
        // Remove whitespaces before ASCII and CJK punctuations
        builder.toString()
            .replace(Regex("\\s+([,.!?;:])"), "$1")
            .replace(Regex("\\s+([\uff0c\u3002\uff01\uff1f\uff1b\uff1a\u3001\uff08\uff09\u300a\u300b\u3008\u3009\u3010\u3011\u300e\u300f\u300c\u300d])"), "$1")
            .trim()
    }

    suspend fun romanizeCyrillic(text: String, language: String? = null): String? = withContext(Dispatchers.Default) {
        if (text.isEmpty()) return@withContext null

        val cyrillicChars = text.filter { it in '\u0400'..'\u04FF' }

        if (cyrillicChars.isEmpty() ||
            (cyrillicChars.length == 1 && (cyrillicChars[0] == '\u0435' || cyrillicChars[0] == '\u0415'))) {
            return@withContext null
        }

        when (language) {
            "Russian" -> romanizeRussianInternal(text)
            "Ukrainian" -> romanizeUkrainianInternal(text)
            "Serbian" -> romanizeSerbianInternal(text)
            "Bulgarian" -> romanizeBulgarianInternal(text)
            "Belarusian" -> romanizeBelarusianInternal(text)
            "Kyrgyz" -> romanizeKyrgyzInternal(text)
            "Macedonian" -> romanizeMacedonianInternal(text)
            else -> when {
                isRussian(text) -> romanizeRussianInternal(text)
                isUkrainian(text) -> romanizeUkrainianInternal(text)
                isSerbian(text) -> romanizeSerbianInternal(text)
                isBulgarian(text) -> romanizeBulgarianInternal(text)
                isBelarusian(text) -> romanizeBelarusianInternal(text)
                isKyrgyz(text) -> romanizeKyrgyzInternal(text)
                isMacedonian(text) -> romanizeMacedonianInternal(text)
                else -> null
            }
        }
    }

    private fun romanizeRussianInternal(text: String): String {
        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    var consumed = false
                    // Check for 3-character sequences
                    if (charIndex + 2 < word.length) {
                        val threeCharCandidate = word.substring(charIndex, charIndex + 3)
                        if (RUSSIAN_ROMAJI_MAP.containsKey(threeCharCandidate)) {
                            romajiBuilder.append(RUSSIAN_ROMAJI_MAP[threeCharCandidate])
                            charIndex += 3
                            consumed = true
                        }
                    }

                    if (!consumed) {
                        val charStr = word[charIndex].toString()
                        // Special case for '\u0435' or '\u0415' at the start of a word
                        if ((charStr == "\u0435" || charStr == "\u0415") && (charIndex == 0 || word[charIndex - 1].isWhitespace())) {
                            romajiBuilder.append(if (charStr == "\u0435") "ye" else "Ye")
                        } else {
                            // Apply general Cyrillic mapping (Russian is no different so there's no need to apply a russian map)
                            val romanizedChar = GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr
                            romajiBuilder.append(romanizedChar)
                        }
                        charIndex += 1
                    }
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun romanizeUkrainianInternal(text: String): String {
        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    var processed = false

                    if (charIndex > 0 && word[charIndex - 1].isLetter() && !isCyrillicVowel(word[charIndex - 1])) {
                        // Check if the current character is \u042e/\u044e or \u042f/\u044f and is preceded by a consonant
                        if (charStr == "\u042e") {
                            romajiBuilder.append("Iu")
                            processed = true
                        } else if (charStr == "\u044e") {
                            romajiBuilder.append("iu")
                            processed = true
                        } else if (charStr == "\u042f") {
                            romajiBuilder.append("Ia")
                            processed = true
                        } else if (charStr == "\u044f") {
                            romajiBuilder.append("ia")
                            processed = true
                        }
                    }

                    if (!processed) {
                        romajiBuilder.append(UKRAINIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr)
                    }
                    charIndex++
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun romanizeSerbianInternal(text: String): String {
        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    val romanizedChar = SERBIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr
                    romajiBuilder.append(romanizedChar)
                    charIndex++
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun romanizeBulgarianInternal(text: String): String {
        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    val romanizedChar = BULGARIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr
                    romajiBuilder.append(romanizedChar)
                    charIndex++
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun romanizeBelarusianInternal(text: String): String {
        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEach { word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    // Special case for '\u0435' or '\u0415' at the start of a word
                    if ((charStr == "\u0435" || charStr == "\u0415") && (charIndex == 0 || word[charIndex - 1].isWhitespace())) {
                        romajiBuilder.append(if (charStr == "\u0435") "ye" else "Ye")
                    } else {
                        // General mapping
                        val romanizedChar = BELARUSIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr
                        romajiBuilder.append(romanizedChar)
                    }
                    charIndex += 1
                }
            }
        }

        return romajiBuilder.toString()
    }

    private fun romanizeKyrgyzInternal(text: String): String {
        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    val romanizedChar = KYRGYZ_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr
                    romajiBuilder.append(romanizedChar)
                    charIndex++
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun romanizeMacedonianInternal(text: String): String {
        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    val romanizedChar = MACEDONIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr
                    romajiBuilder.append(romanizedChar)
                    charIndex++
                }
            }
        }
        return romajiBuilder.toString()
    }

    // TODO: This function might be used later if we let the user choose the language manually
    /** private suspend fun romanizeCyrillicWithLanguage(text: String, language: CyrillicLanguage): String = withContext(Dispatchers.Default) {
        if (text.isEmpty()) return@withContext ""

        val detectedLanguage = language ?: when {
            isRussian(text) -> CyrillicLanguage.RUSSIAN
            isUkrainian(text) -> CyrillicLanguage.UKRAINIAN
            isSerbian(text) -> CyrillicLanguage.SERBIAN
            isBelarusian(text) -> CyrillicLanguage.BELARUSIAN
            isKyrgyz(text) -> CyrillicLanguage.KYRGYZ
            isMacedonian(text) -> CyrillicLanguage.MACEDONIAN
            else -> return@withContext text
        }

        val languageMap: Map<String, String> = when (detectedLanguage) {
            CyrillicLanguage.RUSSIAN -> RUSSIAN_ROMAJI_MAP
            CyrillicLanguage.UKRAINIAN -> UKRAINIAN_ROMAJI_MAP
            CyrillicLanguage.SERBIAN -> SERBIAN_ROMAJI_MAP
            CyrillicLanguage.BELARUSIAN -> BELARUSIAN_ROMAJI_MAP
            CyrillicLanguage.KYRGYZ -> KYRGYZ_ROMAJI_MAP
            CyrillicLanguage.MACEDONIAN -> MACEDONIAN_ROMAJI_MAP
            // else -> emptyMap()
        }
        val languageLetters = when (language) {
            CyrillicLanguage.RUSSIAN -> RUSSIAN_CYRILLIC_LETTERS
            CyrillicLanguage.UKRAINIAN -> UKRAINIAN_CYRILLIC_LETTERS
            CyrillicLanguage.SERBIAN -> SERBIAN_CYRILLIC_LETTERS
            CyrillicLanguage.BELARUSIAN -> BELARUSIAN_CYRILLIC_LETTERS
            CyrillicLanguage.KYRGYZ -> KYRGYZ_CYRILLIC_LETTERS
            CyrillicLanguage.MACEDONIAN -> MACEDONIAN_CYRILLIC_LETTERS
            else -> GENERAL_CYRILLIC_ROMAJI_MAP.keys
        }

        val romajiBuilder = StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex())
            .filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                // Preserve punctuation or spaces as is
                romajiBuilder.append(word)
            } else {
                // Process word
                var charIndex = 0
                while (charIndex < word.length) {
                    var consumed = false
                    // Check for 3-character sequences (language-specific, e.g., Russian)
                    if (detectedLanguage == CyrillicLanguage.RUSSIAN && charIndex + 2 < word.length) {
                        val threeCharCandidate = word.substring(charIndex, charIndex + 3)
                        if (languageLetters is Set<*> && languageLetters.containsAll(threeCharCandidate.toList().map { it.toString() })) {
                            val mappedThreeChar = languageMap[threeCharCandidate]
                            if (mappedThreeChar != null) {
                                romajiBuilder.append(mappedThreeChar)
                                charIndex += 3
                                consumed = true
                            }
                        }
                    }
                    if (!consumed) {
                        val charStr = word[charIndex].toString()
                        val isSpecificLanguageChar = languageLetters is Set<*> && languageLetters.contains(charStr)
                        val isGeneralCyrillicChar = GENERAL_CYRILLIC_ROMAJI_MAP.containsKey(charStr)

                        if (isSpecificLanguageChar || isGeneralCyrillicChar) {
                            if (detectedLanguage == CyrillicLanguage.RUSSIAN && (charStr == "\u0435" || charStr == "\u0415") && charIndex == 0 && (charIndex == 0 || word[charIndex-1].isWhitespace())) {
                                romajiBuilder.append(if (charStr == "\u0435") "ye" else "Ye")
                            } else {
                                val romanizedChar = languageMap[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr]
                                if (romanizedChar != null) {
                                    romajiBuilder.append(romanizedChar)
                                } else {
                                    romajiBuilder.append(charStr)
                                }
                            }
                        } else {
                            romajiBuilder.append(charStr)
                        }
                        charIndex += 1
                    }
                }
            }
        }
        romajiBuilder.toString()
    } */

    fun isRussian(text: String): Boolean {
        return text.any { char ->
            RUSSIAN_CYRILLIC_LETTERS.contains(char.toString())
        } && text.all { char ->
            val charStr = char.toString()
            RUSSIAN_CYRILLIC_LETTERS.contains(charStr) || !charStr.matches("[\\u0400-\\u04FF]".toRegex())
        }
    }

    fun isUkrainian(text: String): Boolean {
        return text.any { char ->
            UKRAINIAN_CYRILLIC_LETTERS.contains(char.toString()) || UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString())
        } && text.all { char ->
            UKRAINIAN_CYRILLIC_LETTERS.contains(char.toString()) || UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString()) || !char.toString().matches("[\\u0400-\\u04FF]".toRegex())
        }
    }

    fun isSerbian(text: String): Boolean {
        return text.any { char ->
            SERBIAN_CYRILLIC_LETTERS.contains(char.toString()) || SERBIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString())
        } && text.all { char ->
            SERBIAN_CYRILLIC_LETTERS.contains(char.toString()) || SERBIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString()) || !char.toString().matches("[\\u0400-\\u04FF]".toRegex())
        }
    }

    fun isBulgarian(text: String): Boolean {
        return text.any { char ->
            BULGARIAN_CYRILLIC_LETTERS.contains(char.toString()) // Bulgarian doesn't have any language specific letters
        } && text.all { char ->
            BULGARIAN_CYRILLIC_LETTERS.contains(char.toString()) || !char.toString().matches("[\\u0400-\\u04FF]".toRegex())
        }
    }

    fun isBelarusian(text: String): Boolean {
        return text.any { char ->
            BELARUSIAN_CYRILLIC_LETTERS.contains(char.toString()) || BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString())
        } && text.all { char ->
            BELARUSIAN_CYRILLIC_LETTERS.contains(char.toString()) || BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString()) || !char.toString().matches("[\\u0400-\\u04FF]".toRegex())
        }
    }

    fun isKyrgyz(text: String): Boolean {
        return text.any { char ->
            KYRGYZ_CYRILLIC_LETTERS.contains(char.toString()) || KYRGYZ_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString())
        } && text.all { char ->
            KYRGYZ_CYRILLIC_LETTERS.contains(char.toString()) || KYRGYZ_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString()) || !char.toString().matches("[\\u0400-\\u04FF]".toRegex())
        }
    }

    fun isMacedonian(text: String): Boolean {
        return text.any { char ->
            MACEDONIAN_CYRILLIC_LETTERS.contains(char.toString()) || MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString())
        } && text.all { char ->
            MACEDONIAN_CYRILLIC_LETTERS.contains(char.toString()) || MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS.contains(char.toString()) || !char.toString().matches("[\\u0400-\\u04FF]".toRegex())
        }
    }

    fun isJapanese(text: String): Boolean {
        return text.any { char ->
            (char in '\u3040'..'\u309F') || // Hiragana
                    (char in '\u30A0'..'\u30FF') || // Katakana
                    (char in '\u4E00'..'\u9FFF') // CJK Unified Ideographs
        }
    }

    fun isKorean(text: String): Boolean {
        return text.any { char ->
            (char in '\uAC00'..'\uD7A3') // Hangul Syllables
        }
    }

    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false
        val cjkCharCount = text.count { char -> char in '\u4E00'..'\u9FFF' }
        val hiraganaKatakanaCount = text.count { char -> (char in '\u3040'..'\u309F') || (char in '\u30A0'..'\u30FF') }
        return cjkCharCount > 0 && (hiraganaKatakanaCount.toDouble() / text.length.toDouble()) < 0.1
    }

    fun isHindi(text: String): Boolean {
        return text.any { char ->
            char in '\u0900'..'\u097F'
        }
    }

    suspend fun romanizeHindi(text: String): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var consumed = false
            // Check for 2-character sequences (e.g. char + nukta)
            if (i + 1 < text.length) {
                val twoCharCandidate = text.substring(i, i + 2)
                val mappedTwoChar = DEVANAGARI_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    sb.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed) {
                val charStr = text[i].toString()
                sb.append(DEVANAGARI_ROMAJI_MAP[charStr] ?: charStr)
                i += 1
            }
        }
        sb.toString()
    }

    fun isPunjabi(text: String): Boolean {
        return text.any { char ->
            char in '\u0A00'..'\u0A7F'
        }
    }

    suspend fun romanizePunjabi(text: String): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val char = text[i]
            var consumed = false

            // Check for Adhak (Gemination)
            if (char == '\u0A71') {
                 // Double next consonant if possible
                 if (i + 1 < text.length) {
                     val nextCharStr = text[i+1].toString()
                     val nextMapped = GURMUKHI_ROMAJI_MAP[nextCharStr]
                     if (nextMapped != null && nextMapped.isNotEmpty()) {
                         sb.append(nextMapped[0])
                     }
                 }
                 i++
                 continue
            }

            // Check for 2-character sequences (e.g. char + nukta)
            if (i + 1 < text.length) {
                val twoCharCandidate = text.substring(i, i + 2)
                val mappedTwoChar = GURMUKHI_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    sb.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed) {
                val str = char.toString()
                sb.append(GURMUKHI_ROMAJI_MAP[str] ?: str)
                i++
            }
        }
        sb.toString()
    }

    suspend fun romanize(
        text: String,
        line: String,
        enabledLanguages: List<String>,
        romanizeCyrillicByLine: Boolean
    ): String? {
        val detectionText = if (romanizeCyrillicByLine) line else text
        return when {
            "Japanese" in enabledLanguages && isJapanese(detectionText) && !isChinese(detectionText) -> romanizeJapanese(line)
            "Korean" in enabledLanguages && isKorean(detectionText) -> romanizeKorean(line)
            "Chinese" in enabledLanguages && isChinese(detectionText) -> romanizeChinese(line)
            "Hindi" in enabledLanguages && isHindi(detectionText) -> romanizeHindi(line)
            "Ukrainian" in enabledLanguages && isUkrainian(detectionText) -> romanizeCyrillic(line, "Ukrainian")
            "Russian" in enabledLanguages && isRussian(detectionText) -> romanizeCyrillic(line, "Russian")
            "Serbian" in enabledLanguages && isSerbian(detectionText) -> romanizeCyrillic(line, "Serbian")
            "Bulgarian" in enabledLanguages && isBulgarian(detectionText) -> romanizeCyrillic(line, "Bulgarian")
            "Belarusian" in enabledLanguages && isBelarusian(detectionText) -> romanizeCyrillic(line, "Belarusian")
            "Kyrgyz" in enabledLanguages && isKyrgyz(detectionText) -> romanizeCyrillic(line, "Kyrgyz")
            "Macedonian" in enabledLanguages && isMacedonian(detectionText) -> romanizeCyrillic(line, "Macedonian")
            else -> null
        }
    }

    private fun isCyrillicVowel(char: Char): Boolean {
        return "\u0410\u0430\u0415\u0435\u0404\u0454\u0418\u0438\u0406\u0456\u0407\u0457\u041e\u043e\u0423\u0443\u042e\u044e\u042f\u044f\u042b\u044b\u042d\u044d".contains(char)
    }

    fun isWordSynced(lyrics: String): Boolean {
        return (lyrics.contains("<") && lyrics.contains(">") && (lyrics.contains("|") || lyrics.contains(":"))) ||
                lyrics.contains(RICH_SYNC_WORD_REGEX)
    }

    fun isLineSynced(lyrics: String): Boolean {
        return lyrics.contains(TIME_REGEX) ||
                lyrics.contains(PAXSENIX_AGENT_LINE_REGEX) ||
                lyrics.contains(PAXSENIX_BG_LINE_REGEX)
    }

    fun getLyricsQuality(lyrics: String): Int {
        if (lyrics.isBlank() || lyrics == "Lyrics not found") return 0
        if (isWordSynced(lyrics)) return 3
        if (isLineSynced(lyrics)) return 2
        return 1
    }
}
