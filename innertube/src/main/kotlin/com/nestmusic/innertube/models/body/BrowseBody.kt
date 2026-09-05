package com.nestmusic.innertube.models.body

import com.nestmusic.innertube.models.Context
import com.nestmusic.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
