package com.nestmusic.innertube.pages

import com.nestmusic.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
