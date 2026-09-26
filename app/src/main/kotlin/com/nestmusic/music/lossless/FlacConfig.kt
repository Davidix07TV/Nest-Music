package com.nestmusic.music.lossless

interface FlacConfig {
    suspend fun qbdlxEnabled(): Boolean
    suspend fun qbdlxAppId(): String
    suspend fun qbdlxAppSecret(): String
    suspend fun qbdlxTokenPool(): String
    suspend fun qbdlxAppSecretsRaw(): String
}

interface FlacKvStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String?)
}
