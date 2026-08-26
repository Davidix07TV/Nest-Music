package com.nestmusic.music.ui.screens.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nestmusic.music.eq.EqualizerService
import com.nestmusic.music.eq.data.BuiltInEQPresets
import com.nestmusic.music.eq.data.EQProfileRepository
import com.nestmusic.music.eq.data.ParametricEQ
import com.nestmusic.music.eq.data.ParametricEQBand
import com.nestmusic.music.eq.data.ParametricEQParser
import com.nestmusic.music.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

/**
 * ViewModel for EQ Screen and player equalizer controls
 * Manages EQ presets, custom profiles, real-time multi-band sliders, and applies them to EqualizerService
 */
@HiltViewModel
class EQViewModel @Inject constructor(
    private val eqProfileRepository: EQProfileRepository,
    private val equalizerService: EqualizerService
) : ViewModel() {

    private val _state = MutableStateFlow(EQState())
    val state: StateFlow<EQState> = _state.asStateFlow()

    init {
        loadProfiles()
    }

    /**
     * Load all saved EQ profiles and observe active profile
     */
    private fun loadProfiles() {
        viewModelScope.launch {
            eqProfileRepository.profiles.collect { _ ->
                val sortedProfiles = eqProfileRepository.getSortedProfiles()
                _state.update {
                    it.copy(customProfiles = sortedProfiles)
                }
            }
        }

        viewModelScope.launch {
            eqProfileRepository.activeProfile.collect { activeProfile ->
                if (activeProfile != null) {
                    _state.update {
                        it.copy(
                            isEnabled = true,
                            activeProfileId = activeProfile.id,
                            currentBands = activeProfile.bands,
                            preamp = activeProfile.preamp
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isEnabled = false,
                            activeProfileId = null
                        )
                    }
                }
            }
        }
    }

    /**
     * Toggle equalizer on/off
     */
    fun toggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val profileId = _state.value.activeProfileId ?: BuiltInEQPresets.SUNSET_VIBES.id
                selectProfile(profileId)
            } else {
                selectProfile(null)
            }
        }
    }

    /**
     * Select and apply an EQ profile or preset
     * Pass null to disable EQ
     */
    fun selectProfile(profileId: String?) {
        viewModelScope.launch {
            if (profileId == null) {
                equalizerService.disable()
                eqProfileRepository.setActiveProfile(null)
                _state.update {
                    it.copy(
                        isEnabled = false,
                        activeProfileId = null,
                        currentBands = BuiltInEQPresets.createStandardBands(List(10) { 0.0 }),
                        preamp = 0.0
                    )
                }
            } else {
                val profile = eqProfileRepository.getProfileById(profileId)
                if (profile != null) {
                    val result = equalizerService.applyProfile(profile)
                    result.onSuccess {
                        eqProfileRepository.setActiveProfile(profileId)
                        _state.update {
                            it.copy(
                                isEnabled = true,
                                activeProfileId = profileId,
                                currentBands = profile.bands,
                                preamp = profile.preamp
                            )
                        }
                    }.onFailure { e ->
                        _state.update { it.copy(error = e.message ?: "Unknown error") }
                    }
                }
            }
        }
    }

    /**
     * Update gain of a specific frequency band in real time
     */
    fun updateBandGain(bandIndex: Int, gain: Double) {
        val updatedBands = _state.value.currentBands.mapIndexed { idx, band ->
            if (idx == bandIndex) band.copy(gain = gain) else band
        }
        _state.update {
            it.copy(
                isEnabled = true,
                currentBands = updatedBands,
                activeProfileId = "custom_manual"
            )
        }
        applyCurrentStateToEngine()
    }

    /**
     * Update preamp gain in real time
     */
    fun updatePreamp(preampGain: Double) {
        _state.update {
            it.copy(
                isEnabled = true,
                preamp = preampGain
            )
        }
        applyCurrentStateToEngine()
    }

    /**
     * Update bass boost level in real time
     */
    fun updateBassBoost(bassGain: Double) {
        _state.update { it.copy(bassBoost = bassGain) }
        val baseBands = _state.value.currentBands
        if (baseBands.isNotEmpty()) {
            val updatedBands = baseBands.mapIndexed { index, band ->
                when (index) {
                    0 -> band.copy(gain = (band.gain + bassGain).coerceIn(-12.0, 15.0))
                    1 -> band.copy(gain = (band.gain + (bassGain * 0.7)).coerceIn(-12.0, 15.0))
                    else -> band
                }
            }
            _state.update { it.copy(currentBands = updatedBands) }
            applyCurrentStateToEngine()
        }
    }

    /**
     * Update 3D surround / virtualizer level
     */
    fun updateVirtualizer(amount: Float) {
        _state.update { it.copy(virtualizer = amount) }
    }

    /**
     * Reset all bands and preamp to Flat response
     */
    fun resetToFlat() {
        selectProfile(BuiltInEQPresets.FLAT.id)
    }

    /**
     * Apply the current bands and preamp directly to the audio engine
     */
    private fun applyCurrentStateToEngine() {
        val currentProfile = SavedEQProfile(
            id = _state.value.activeProfileId ?: "custom_manual",
            name = "Custom",
            deviceModel = "Custom",
            bands = _state.value.currentBands,
            preamp = _state.value.preamp,
            isCustom = true
        )
        equalizerService.applyProfile(currentProfile)
    }

    /**
     * Save the current EQ curve as a custom profile
     */
    fun saveCurrentAsPreset(name: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val id = "custom_${System.currentTimeMillis()}_${name.hashCode()}"
            val newProfile = SavedEQProfile(
                id = id,
                name = name,
                deviceModel = name,
                bands = _state.value.currentBands,
                preamp = _state.value.preamp,
                isCustom = true
            )
            eqProfileRepository.saveProfile(newProfile)
            eqProfileRepository.setActiveProfile(id)
            _state.update {
                it.copy(
                    activeProfileId = id,
                    importStatus = "Saved preset $name"
                )
            }
            onSuccess()
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Delete an EQ profile
     */
    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            eqProfileRepository.deleteProfile(profileId)
        }
    }

    /**
     * Import a custom EQ profile from a file
     */
    fun importCustomProfile(
        fileName: String,
        inputStream: InputStream,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val content = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                val parametricEQ = ParametricEQParser.parseText(content)
                val validationErrors = ParametricEQParser.validate(parametricEQ)
                if (validationErrors.isNotEmpty()) {
                    onError(Exception("Invalid EQ file: ${validationErrors.first()}"))
                    return@launch
                }

                val profileName = fileName.removeSuffix(".txt")
                eqProfileRepository.importCustomProfile(profileName, parametricEQ)

                _state.update { it.copy(importStatus = "Successfully imported $profileName") }
                onSuccess()
            } catch (e: Exception) {
                onError(Exception("Failed to import EQ profile: ${e.message}"))
            }
        }
    }
}
