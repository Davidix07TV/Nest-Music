package com.nestmusic.innertube.pages

import com.nestmusic.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
