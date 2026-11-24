package com.nestmusic.music.ui.player.nest.canvas

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Seamless looping motion-artwork player backed by Media3 (`HlsMediaSource` + `ProgressiveMediaSource`)
 * rendered onto a center-cropped `TextureView` so Compose `graphicsLayer` clipping, `BlendMode.DstIn`
 * bottom fades, and periodic `Bitmap` frame sampling for the ambient mesh backdrop all work natively.
 */
@OptIn(UnstableApi::class)
@Composable
fun NestCanvasArtworkPlayer(
    artwork: NestCanvasArtwork,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onFrameSampled: ((Bitmap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnFrameSampled by rememberUpdatedState(onFrameSampled)

    var hasRenderedFirstFrame by remember(artwork.url) { mutableStateOf(false) }
    var videoWidth by remember(artwork.url) { mutableStateOf(0) }
    var videoHeight by remember(artwork.url) { mutableStateOf(0) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    val exoPlayer = remember(context) {
        ExoPlayer
            .Builder(context.applicationContext)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ false,
                )
            }
    }

    DisposableEffect(exoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoWidth = videoSize.width
                        videoHeight = videoSize.height
                        textureViewRef?.let { tv ->
                            applyCenterCropTransform(tv, videoSize.width, videoSize.height)
                        }
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.clearVideoSurface()
            exoPlayer.release()
        }
    }

    LaunchedEffect(artwork.url) {
        hasRenderedFirstFrame = false
        val dsFactory = NestCanvasCache.dataSourceFactory(context.applicationContext)
        val item = MediaItem.fromUri(artwork.url)
        val mediaSource =
            if (artwork.isHls) {
                HlsMediaSource.Factory(dsFactory).createMediaSource(item)
            } else {
                ProgressiveMediaSource.Factory(dsFactory).createMediaSource(item)
            }
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                    Lifecycle.Event.ON_START -> if (isPlaying) exoPlayer.play()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Sample a compact 96x96 frame every 3s while playing so the Liquid Glass mesh backdrop
    // smoothly tracks color shifts in the animated cover.
    LaunchedEffect(hasRenderedFirstFrame, isPlaying, textureViewRef) {
        val tv = textureViewRef ?: return@LaunchedEffect
        if (!hasRenderedFirstFrame || latestOnFrameSampled == null) return@LaunchedEffect
        while (isActive && isPlaying) {
            runCatching {
                if (tv.isAvailable && tv.width > 0 && tv.height > 0) {
                    tv.getBitmap(96, 96)?.let { bmp ->
                        latestOnFrameSampled?.invoke(bmp)
                    }
                }
            }
            delay(3_000L)
        }
    }

    val revealAlpha by animateFloatAsState(
        targetValue = if (hasRenderedFirstFrame) 1f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "nestCanvasReveal",
    )

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                isOpaque = false
                var boundSurface: Surface? = null
                surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            boundSurface?.release()
                            val s = Surface(surfaceTexture)
                            boundSurface = s
                            exoPlayer.setVideoSurface(s)
                            if (videoWidth > 0 && videoHeight > 0) {
                                applyCenterCropTransform(this@apply, videoWidth, videoHeight)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            if (videoWidth > 0 && videoHeight > 0) {
                                applyCenterCropTransform(this@apply, videoWidth, videoHeight)
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            exoPlayer.clearVideoSurface()
                            boundSurface?.release()
                            boundSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                    }
                textureViewRef = this
            }
        },
        update = { tv ->
            if (videoWidth > 0 && videoHeight > 0 && tv.width > 0 && tv.height > 0) {
                applyCenterCropTransform(tv, videoWidth, videoHeight)
            }
        },
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = revealAlpha },
    )
}

private fun applyCenterCropTransform(
    textureView: TextureView,
    videoWidth: Int,
    videoHeight: Int,
) {
    val viewW = textureView.width.toFloat()
    val viewH = textureView.height.toFloat()
    if (viewW <= 0f || viewH <= 0f || videoWidth <= 0 || videoHeight <= 0) return

    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
    val viewRatio = viewW / viewH

    val scaleX: Float
    val scaleY: Float
    if (videoRatio > viewRatio) {
        scaleX = videoRatio / viewRatio
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewRatio / videoRatio
    }

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
    textureView.setTransform(matrix)
}
