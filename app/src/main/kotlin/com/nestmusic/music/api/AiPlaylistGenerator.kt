/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.api

import org.json.JSONObject

/**
 * A plan returned by the AI: a playlist title and a list of YouTube Music
 * search queries, each expected to surface a good match for the request.
 */
data class AiPlaylistPlan(
    val title: String,
    val queries: List<String>,
)

/**
 * Turns a free-form user request into an [AiPlaylistPlan] via the configured
 * AI provider (see OpenRouterService and the AI settings screen).
 */
object AiPlaylistGenerator {
    private const val MAX_QUERIES = 40

    fun systemPrompt(songCount: Int): String {
        val queryCount = (songCount + 5).coerceAtMost(MAX_QUERIES)
        return """
            You are a music playlist generator. You convert a user's music request into YouTube Music search queries.

            Reply with ONLY a valid JSON object. No markdown, no code fences, no explanations.
            Exact format: {"title": "<playlist title>", "queries": ["<query 1>", "<query 2>", ...]}

            Rules:
            - "title": a short, catchy playlist name, max 40 characters.
            - "queries": exactly $queryCount strings. Each query must return a specific, well-matching track.
            - Use "artist - song title" when you can name an exact existing song; otherwise a specific genre, mood or vibe phrase.
            - Only reference real, well-known songs and artists. Vary artists, eras and tempos.
            - Write queries in English unless the user explicitly asks for another language.
            - No duplicates and no generic queries such as "top hits" or "best songs".
        """.trimIndent()
    }

    fun userPrompt(request: String): String = "User request: \"$request\""

    /**
     * Robustly parses the model output, tolerating code fences and stray text
     * around the JSON object.
     */
    fun parsePlan(content: String): Result<AiPlaylistPlan> = runCatching {
        var text = content.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start != -1 && end > start) { "No JSON object in AI response" }

        val json = JSONObject(text.substring(start, end + 1))
        val title = json.optString("title").trim().ifBlank { "AI Mix" }
        val queriesJson = json.optJSONArray("queries") ?: throw Exception("Missing queries in AI response")

        val queries =
            (0 until queriesJson.length())
                .map { queriesJson.optString(it).trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        require(queries.isNotEmpty()) { "Empty query list" }
        AiPlaylistPlan(title = title, queries = queries)
    }
}
