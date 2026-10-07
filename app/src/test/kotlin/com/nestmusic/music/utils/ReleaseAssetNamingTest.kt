/**
 * Nest Music (C) 2026
 * Licensed under GPL-3.0
 */

package com.nestmusic.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAssetNamingTest {
    private fun asset(name: String, arch: String, variant: String) =
        ReleaseAsset(name = name, downloadUrl = "https://example.invalid/$name", size = 1L, architecture = arch, variant = variant)

    @Test
    fun `the FOSS release asset is the version-named APK`() {
        assertEquals(
            ReleaseAssetNaming.ARCH_UNIVERSAL to ReleaseAssetNaming.VARIANT_FOSS,
            ReleaseAssetNaming.classify("nest-music-1.0.10.apk"),
        )
    }

    @Test
    fun `the F-Droid APK is recognised as its own variant`() {
        // Release CI attaches it for the F-Droid repositories; it has no in-app updater.
        assertEquals(
            ReleaseAssetNaming.ARCH_UNIVERSAL to ReleaseAssetNaming.VARIANT_IZZY,
            ReleaseAssetNaming.classify("nest-music-1.0.10-izzy.apk"),
        )
    }

    @Test
    fun `upstream asset names are still understood`() {
        assertEquals(
            ReleaseAssetNaming.ARCH_UNIVERSAL to ReleaseAssetNaming.VARIANT_FOSS,
            ReleaseAssetNaming.classify("app-universal-release.apk"),
        )
        assertEquals(
            "arm64-v8a" to ReleaseAssetNaming.VARIANT_FOSS,
            ReleaseAssetNaming.classify("app-arm64-v8a-release.apk"),
        )
        assertEquals(
            ReleaseAssetNaming.ARCH_UNIVERSAL to ReleaseAssetNaming.VARIANT_GMS,
            ReleaseAssetNaming.classify("app-universal-with-Google-Cast.apk"),
        )
        assertEquals(
            "arm64-v8a" to ReleaseAssetNaming.VARIANT_GMS,
            ReleaseAssetNaming.classify("app-arm64-v8a-with-Google-Cast.apk"),
        )
    }

    @Test
    fun `unknown names fall back to the universal FOSS build`() {
        assertEquals(
            ReleaseAssetNaming.ARCH_UNIVERSAL to ReleaseAssetNaming.VARIANT_FOSS,
            ReleaseAssetNaming.classify("NestMusic.apk"),
        )
    }

    @Test
    fun `the in-app updater gets the FOSS APK`() {
        val assets = listOf(
            asset("nest-music-1.0.11-izzy.apk", ReleaseAssetNaming.ARCH_UNIVERSAL, ReleaseAssetNaming.VARIANT_IZZY),
            asset("nest-music-1.0.11.apk", ReleaseAssetNaming.ARCH_UNIVERSAL, ReleaseAssetNaming.VARIANT_FOSS),
        )

        assertEquals(
            "https://example.invalid/nest-music-1.0.11.apk",
            selectDownloadUrl(assets, ReleaseAssetNaming.ARCH_UNIVERSAL, ReleaseAssetNaming.VARIANT_FOSS),
        )
    }

    @Test
    fun `the fallback never hands an F-Droid APK to a self-updating install`() {
        // Regression: the old fallback returned assets.first(), so a release that listed the izzy
        // APK first would have offered it to FOSS/GMS users, permanently disabling their updater.
        val onlyIzzy = listOf(
            asset("nest-music-1.0.11-izzy.apk", ReleaseAssetNaming.ARCH_UNIVERSAL, ReleaseAssetNaming.VARIANT_IZZY),
        )

        assertNull(selectDownloadUrl(onlyIzzy, ReleaseAssetNaming.ARCH_UNIVERSAL, ReleaseAssetNaming.VARIANT_FOSS))

        val withGms = onlyIzzy + asset(
            "app-universal-with-Google-Cast.apk",
            ReleaseAssetNaming.ARCH_UNIVERSAL,
            ReleaseAssetNaming.VARIANT_GMS,
        )

        assertEquals(
            "https://example.invalid/app-universal-with-Google-Cast.apk",
            selectDownloadUrl(withGms, ReleaseAssetNaming.ARCH_UNIVERSAL, ReleaseAssetNaming.VARIANT_FOSS),
        )
    }

    @Test
    fun `no assets means no download`() {
        assertNull(selectDownloadUrl(emptyList(), ReleaseAssetNaming.ARCH_UNIVERSAL, ReleaseAssetNaming.VARIANT_FOSS))
    }
}
