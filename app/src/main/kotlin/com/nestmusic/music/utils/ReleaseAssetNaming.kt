/**
 * Nest Music (C) 2026
 * Licensed under GPL-3.0
 */

package com.nestmusic.music.utils

/**
 * Maps a GitHub release asset name to the build it belongs to. Kept free of Android and JSON
 * dependencies so it can be unit-tested on the JVM.
 *
 * Release CI publishes one FOSS APK (`nest-music-<version>.apk`, in-app updater) and one APK for
 * the F-Droid repositories (`nest-music-<version>-izzy.apk`, no updater, no Cast). Only FOSS/GMS
 * assets may ever be offered by the in-app updater: the F-Droid build has no updater of its own,
 * so installing it over an updater-enabled build would silently stop update notifications.
 *
 * Assets from older InnerTune/Metrolist-style releases (`app-<abi>-<release|with-Google-Cast>.apk`)
 * are still understood.
 */
internal object ReleaseAssetNaming {
    const val ARCH_UNIVERSAL = "universal"
    const val VARIANT_FOSS = "foss"
    const val VARIANT_GMS = "gms"
    const val VARIANT_IZZY = "izzy"

    /** Asset name -> (architecture, variant). */
    fun classify(fileName: String): Pair<String, String> = when {
        fileName.contains("izzy", ignoreCase = true) -> ARCH_UNIVERSAL to VARIANT_IZZY
        fileName.startsWith("app-") && fileName.endsWith("-with-Google-Cast.apk") ->
            architectureOf(fileName, "-with-Google-Cast.apk") to VARIANT_GMS
        fileName.startsWith("app-") && fileName.endsWith("-release.apk") ->
            architectureOf(fileName, "-release.apk") to VARIANT_FOSS
        fileName.contains("gms", ignoreCase = true) ||
            fileName.contains("Google-Cast", ignoreCase = true) -> ARCH_UNIVERSAL to VARIANT_GMS
        else -> ARCH_UNIVERSAL to VARIANT_FOSS
    }

    /** `app-arm64-v8a-release.apk` + `-release.apk` -> `arm64-v8a`. */
    private fun architectureOf(fileName: String, suffix: String): String =
        fileName.removePrefix("app-").removeSuffix(suffix).ifBlank { ARCH_UNIVERSAL }
}
