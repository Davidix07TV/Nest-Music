/**
 * Nest Music (C) 2026
 * Licensed under GPL-3.0
 */

package com.nestmusic.music.utils

import com.nestmusic.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    /** Version used for every comparison and for "latest version" labels, e.g. "1.0.10". */
    val versionName: String,
    /** Release name as published on GitHub; free-form, good only for display. */
    val title: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val GITHUB_API_BASE = "https://api.github.com/repos/Davidix07TV/Nest-Music"
    const val KMP_APK_NAME = "NestMusic.apk"

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int = ReleaseVersion.compare(v1, v2)

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    /**
     * Parse release assets from GitHub API response
     */
    private fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.getLong("size")
            
            // Parse architecture and variant from the filename (see ReleaseAssetNaming).
            val (arch, variant) = ReleaseAssetNaming.classify(name)
            
            assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
        }
        
        return assets
    }

    /**
     * Builds a [ReleaseInfo] from one GitHub release object.
     *
     * The version is taken from `tag_name` ("v1.0.10" -> "1.0.10"). The `name` field is a free-form
     * title ("🎵 Nest Music v1.0.9 - Sunset, Night and Aurora") and reading the version out of it
     * turned every release into 0.0.0, so the update prompt never showed up.
     */
    private fun parseRelease(release: JSONObject): ReleaseInfo {
        val tagName = release.optString("tag_name").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val title = release.optString("name").takeIf { it.isNotBlank() && it != "null" }.orEmpty()

        return ReleaseInfo(
            tagName = tagName,
            versionName = ReleaseVersion.fromRelease(tagName, title),
            title = title,
            description = release.optString("body"),
            releaseDate = release.optString("published_at"),
            assets = parseAssets(release.optJSONArray("assets") ?: JSONArray())
        )
    }

    /**
     * Fetch latest release from GitHub API
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Return cached if available and not forcing refresh
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }
                
                val response = client.get("$GITHUB_API_BASE/releases/latest")
                    .bodyAsText()
                val json = JSONObject(response)
                
                val releaseInfo = parseRelease(json)
                
                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }
                
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true
                
                while (hasMore && page <= 10) { // Limit to 10 pages
                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                        .bodyAsText()
                    val json = JSONArray(response)
                    
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }
                    
                    for (i in 0 until json.length()) {
                        releases.add(parseRelease(json.getJSONObject(i)))
                    }
                    
                    page++
                }
                
                cachedAllReleases = releases
                releases
            }
        }

    /**
     * Returns the newest KMP release that provides the migration APK.
     */
    suspend fun getLatestKmpRelease(): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                null
            }
        }

    /**
     * Get the download URL for the correct app variant
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()

        return selectDownloadUrl(releaseInfo.assets, currentArch, currentVariant)
    }

    /**
     * Get all available download URLs for a release
     */
    fun getAllDownloadUrls(releaseInfo: ReleaseInfo): Map<String, String> {
        return releaseInfo.assets.associate { "${it.architecture}-${it.variant}" to it.downloadUrl }
    }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    /**
     * Get the download URL for the correct app variant
     * Returns null if no matching asset is found
     */
    fun getLatestDownloadUrl(): String? {
        return cachedReleaseInfo?.let { getDownloadUrlForCurrentVariant(it) }
    }
    
    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo
}

/**
 * Picks the asset to download for [architecture]/[variant], never falling back to an F-Droid
 * (izzy) APK: those builds ship without the in-app updater, so handing one to a FOSS/GMS install
 * would replace a self-updating app with one that silently never updates again.
 */
internal fun selectDownloadUrl(
    assets: List<ReleaseAsset>,
    architecture: String,
    variant: String
): String? =
    assets.find { it.architecture == architecture && it.variant == variant }?.downloadUrl
        ?: assets.firstOrNull { it.variant != ReleaseAssetNaming.VARIANT_IZZY }?.downloadUrl
