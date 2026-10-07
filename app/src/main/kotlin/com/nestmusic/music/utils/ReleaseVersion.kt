/**
 * Nest Music (C) 2026
 * Licensed under GPL-3.0
 */

package com.nestmusic.music.utils

/**
 * Version handling for GitHub releases, kept free of Android and Ktor dependencies so it can be
 * unit-tested on the JVM.
 *
 * The tag is the source of truth. Release titles are free-form ("🎵 Nest Music v1.0.9 - Sunset,
 * Night and Aurora") and reading the version out of them used to turn every comparison into
 * `0.0.0`, which silently disabled the in-app update prompt. Only when a tag carries no digits at
 * all does the title get a chance to provide the version.
 */
internal object ReleaseVersion {
    /** First `x.y`-style number in a free-form string: "🎵 Nest Music v1.0.9 - Sunset" -> "1.0.9". */
    private val versionInText = Regex("""\d+(?:\.\d+)+""")

    /**
     * "v1.0.10" -> "1.0.10". Suffixes such as "-lossless" are kept for display and ignored by
     * [compare].
     */
    fun fromTag(tag: String): String = tag.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * First version-looking number inside [title], or an empty string when there is none.
     */
    fun fromTitle(title: String): String = versionInText.find(title)?.value.orEmpty()

    /**
     * Version of a release: [tag] when it looks like a version, otherwise whatever [title] offers.
     */
    fun fromRelease(tag: String, title: String): String {
        val tagVersion = fromTag(tag)
        return if (tagVersion.any(Char::isDigit)) tagVersion else fromTitle(title).ifBlank { tagVersion }
    }

    /**
     * Compares two versions. Returns 1 if [v1] > [v2], -1 if [v1] < [v2], 0 when they are equal.
     *
     * Numeric prefixes are what count, so "1.0.6-lossless" is treated as 1.0.6 and a missing
     * component counts as zero.
     */
    fun compare(v1: String, v2: String): Int {
        val parts1 = toParts(v1)
        val parts2 = toParts(v2)
        val length = maxOf(parts1.size, parts2.size)

        for (i in 0 until length) {
            val part1 = parts1.getOrElse(i) { 0 }
            val part2 = parts2.getOrElse(i) { 0 }
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * "v1.0.6-lossless+flac" -> [1, 0, 6]; each component keeps its leading digits and anything
     * non-numeric counts as zero.
     */
    private fun toParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { component -> component.trim().takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
