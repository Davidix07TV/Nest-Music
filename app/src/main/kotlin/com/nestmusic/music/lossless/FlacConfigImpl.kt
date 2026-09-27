package com.nestmusic.music.lossless

import android.content.Context
import com.nestmusic.music.constants.EnableLosslessKey
import com.nestmusic.music.constants.QobuzAppIdKey
import com.nestmusic.music.constants.QobuzAppSecretKey
import com.nestmusic.music.constants.QobuzAppSecretsKey
import com.nestmusic.music.constants.QobuzUserAuthTokenKey
import com.nestmusic.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlacConfigImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FlacConfig {

    private suspend fun <T> getPref(key: androidx.datastore.preferences.core.Preferences.Key<T>, default: T): T {
        return context.dataStore.data.first()[key] ?: default
    }

    override suspend fun qbdlxEnabled(): Boolean = getPref(EnableLosslessKey, true)
    override suspend fun qbdlxAppId(): String = getPref(QobuzAppIdKey, "")
    override suspend fun qbdlxAppSecret(): String = getPref(QobuzAppSecretKey, "")
    override suspend fun qbdlxTokenPool(): String = getPref(QobuzUserAuthTokenKey, "")
    override suspend fun qbdlxAppSecretsRaw(): String = getPref(QobuzAppSecretsKey, "")
}
