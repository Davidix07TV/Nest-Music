package com.nestmusic.music.lossless

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nestmusic.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlacKvStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FlacKvStore {
    override suspend fun get(key: String): String? {
        val prefKey = stringPreferencesKey("flac_kv_$key")
        return context.dataStore.data.first()[prefKey]
    }

    override suspend fun put(key: String, value: String?) {
        val prefKey = stringPreferencesKey("flac_kv_$key")
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(prefKey) else prefs[prefKey] = value
        }
    }
}
