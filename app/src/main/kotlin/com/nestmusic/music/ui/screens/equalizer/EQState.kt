package com.nestmusic.music.ui.screens.equalizer

import com.nestmusic.music.eq.data.BuiltInEQPresets
import com.nestmusic.music.eq.data.ParametricEQBand
import com.nestmusic.music.eq.data.SavedEQProfile

/**
 * UI State for EQ Screen and Player Equalizer
 */
data class EQState(
    val profiles: List<SavedEQProfile> = emptyList(),
    val isEnabled: Boolean = false,
    val activeProfileId: String? = null,
    val builtInPresets: List<SavedEQProfile> = BuiltInEQPresets.ALL_PRESETS,
    val customProfiles: List<SavedEQProfile> = emptyList(),
    val currentBands: List<ParametricEQBand> = BuiltInEQPresets.createStandardBands(List(10) { 0.0 }),
    val preamp: Double = 0.0,
    val bassBoost: Double = 0.0,
    val virtualizer: Float = 0.0f,
    val importStatus: String? = null,
    val error: String? = null
)
