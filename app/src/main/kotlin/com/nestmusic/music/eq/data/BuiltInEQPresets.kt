package com.nestmusic.music.eq.data

/**
 * Built-in audio equalizer presets for Nest Music
 */
object BuiltInEQPresets {
    val STANDARD_FREQUENCIES = listOf(
        31.0, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
    )

    val FREQUENCY_LABELS = listOf(
        "31Hz", "63Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"
    )

    fun createStandardBands(gains: List<Double>): List<ParametricEQBand> {
        return STANDARD_FREQUENCIES.mapIndexed { index, freq ->
            val gain = gains.getOrElse(index) { 0.0 }
            val filterType = when (index) {
                0 -> FilterType.LSC
                STANDARD_FREQUENCIES.size - 1 -> FilterType.HSC
                else -> FilterType.PK
            }
            ParametricEQBand(
                frequency = freq,
                gain = gain,
                q = 1.41,
                filterType = filterType,
                enabled = true
            )
        }
    }

    val FLAT = SavedEQProfile(
        id = "builtin_flat",
        name = "Flat",
        deviceModel = "Flat",
        bands = createStandardBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        preamp = 0.0,
        source = "builtin",
        isCustom = false
    )

    val SUNSET_VIBES = SavedEQProfile(
        id = "builtin_sunset_vibes",
        name = "Sunset Vibes",
        deviceModel = "Sunset Vibes",
        bands = createStandardBands(listOf(4.5, 3.5, 2.0, 0.5, -0.5, 1.0, 2.5, 3.5, 4.5, 3.0)),
        preamp = -1.5,
        source = "builtin",
        isCustom = false
    )

    val BASS_BOOST = SavedEQProfile(
        id = "builtin_bass_boost",
        name = "Bass Boost+",
        deviceModel = "Bass Boost+",
        bands = createStandardBands(listOf(7.0, 6.0, 4.0, 1.5, 0.0, 0.0, 0.5, 1.0, 1.5, 1.0)),
        preamp = -2.5,
        source = "builtin",
        isCustom = false
    )

    val DEEP_BASS = SavedEQProfile(
        id = "builtin_deep_bass",
        name = "Deep Bass",
        deviceModel = "Deep Bass",
        bands = createStandardBands(listOf(8.5, 6.0, 3.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        preamp = -3.0,
        source = "builtin",
        isCustom = false
    )

    val VOCAL_CLARITY = SavedEQProfile(
        id = "builtin_vocal_clarity",
        name = "Vocal Clarity",
        deviceModel = "Vocal Clarity",
        bands = createStandardBands(listOf(-1.0, -1.0, 0.0, 1.5, 3.0, 4.5, 3.5, 2.0, 1.0, 0.5)),
        preamp = -1.0,
        source = "builtin",
        isCustom = false
    )

    val ACOUSTIC = SavedEQProfile(
        id = "builtin_acoustic",
        name = "Acoustic",
        deviceModel = "Acoustic",
        bands = createStandardBands(listOf(2.5, 2.0, 1.0, 0.0, 1.0, 2.0, 3.0, 3.5, 3.0, 2.5)),
        preamp = -1.0,
        source = "builtin",
        isCustom = false
    )

    val ELECTRONIC = SavedEQProfile(
        id = "builtin_electronic",
        name = "Electronic",
        deviceModel = "Electronic",
        bands = createStandardBands(listOf(5.5, 4.5, 2.0, 0.0, -0.5, 1.0, 2.5, 4.0, 5.0, 4.0)),
        preamp = -2.0,
        source = "builtin",
        isCustom = false
    )

    val ROCK = SavedEQProfile(
        id = "builtin_rock",
        name = "Rock",
        deviceModel = "Rock",
        bands = createStandardBands(listOf(4.5, 3.0, 1.5, -0.5, -1.0, 1.0, 2.5, 4.0, 4.5, 3.5)),
        preamp = -1.5,
        source = "builtin",
        isCustom = false
    )

    val POP = SavedEQProfile(
        id = "builtin_pop",
        name = "Pop",
        deviceModel = "Pop",
        bands = createStandardBands(listOf(2.5, 3.0, 2.0, 0.5, 0.0, 1.5, 2.5, 3.5, 3.0, 2.0)),
        preamp = -1.0,
        source = "builtin",
        isCustom = false
    )

    val CLASSICAL = SavedEQProfile(
        id = "builtin_classical",
        name = "Classical & Jazz",
        deviceModel = "Classical & Jazz",
        bands = createStandardBands(listOf(3.0, 2.0, 1.0, 0.0, 0.0, 1.0, 1.5, 2.5, 3.0, 3.5)),
        preamp = -1.0,
        source = "builtin",
        isCustom = false
    )

    val TREBLE_BOOST = SavedEQProfile(
        id = "builtin_treble_boost",
        name = "Treble Boost",
        deviceModel = "Treble Boost",
        bands = createStandardBands(listOf(-2.0, -1.5, -0.5, 0.0, 0.5, 1.5, 3.0, 4.5, 6.0, 5.5)),
        preamp = -1.5,
        source = "builtin",
        isCustom = false
    )

    val ALL_PRESETS = listOf(
        FLAT,
        SUNSET_VIBES,
        BASS_BOOST,
        DEEP_BASS,
        VOCAL_CLARITY,
        ACOUSTIC,
        ELECTRONIC,
        ROCK,
        POP,
        CLASSICAL,
        TREBLE_BOOST
    )
}
