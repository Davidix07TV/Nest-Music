/*
 * Adapted from Nest Music (Nest Music) Nest MusicIcons + TransportGlyphs under GPL-3.0.
 */

package com.nestmusic.music.ui.player.nest

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

object NestPlayerIcons {
    private val stroke = SolidColor(Color.White)

    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_music_note",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 18f)
                verticalLineTo(5f)
                lineTo(21f, 3f)
                verticalLineTo(16f)
            }
            path(fill = stroke) {
                circle(6f, 18f, 3f)
                circle(18f, 16f, 3f)
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_chevron_right",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }.build()
    }

    val PlayerPlay: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 2.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.2f, 2.0f)
                lineTo(20.6f, 12f)
                lineTo(5.2f, 22.0f)
                close()
            }
        }.build()
    }

    val PlayerPause: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.0f, 0.4f)
                lineTo(9.6f, 0.4f)
                lineTo(9.6f, 23.6f)
                lineTo(3.0f, 23.6f)
                close()
                moveTo(14.4f, 0.4f)
                lineTo(21.0f, 0.4f)
                lineTo(21.0f, 23.6f)
                lineTo(14.4f, 23.6f)
                close()
            }
        }.build()
    }

    val PlayerQueue: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_queue",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
            ) {
                for (y in listOf(8f, 12f, 16f)) {
                    moveTo(7.8f, y)
                    lineTo(19.5f, y)
                }
            }
            path(fill = stroke) {
                for (y in listOf(8f, 12f, 16f)) {
                    circle(4.5f, y, 1.05f)
                }
            }
        }.build()
    }

    val PlayerQueueRepeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_queue_repeat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            queueRowsWithBadge()
            path(
                stroke = stroke,
                strokeLineWidth = 1.15f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15.85f, 6.20f)
                verticalLineTo(5.65f)
                curveTo(15.85f, 5.18f, 16.22f, 4.82f, 16.68f, 4.82f)
                horizontalLineTo(19.65f)
                moveTo(18.95f, 4.02f)
                lineTo(19.95f, 4.82f)
                lineTo(18.95f, 5.62f)
                moveTo(20.55f, 6.90f)
                verticalLineTo(7.45f)
                curveTo(20.55f, 7.92f, 20.18f, 8.28f, 19.72f, 8.28f)
                horizontalLineTo(16.75f)
                moveTo(17.45f, 7.48f)
                lineTo(16.45f, 8.28f)
                lineTo(17.45f, 9.08f)
            }
        }.build()
    }

    val PlayerQueueShuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_queue_shuffle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            queueRowsWithBadge()
            path(
                stroke = stroke,
                strokeLineWidth = 1.15f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15.00f, 4.90f)
                horizontalLineTo(16.90f)
                lineTo(19.40f, 8.60f)
                horizontalLineTo(20.55f)
                moveTo(15.00f, 8.60f)
                horizontalLineTo(16.90f)
                lineTo(19.40f, 4.90f)
                horizontalLineTo(20.55f)
                moveTo(19.60f, 3.95f)
                lineTo(20.70f, 4.90f)
                lineTo(19.60f, 5.85f)
                moveTo(19.60f, 7.65f)
                lineTo(20.70f, 8.60f)
                lineTo(19.60f, 9.55f)
            }
        }.build()
    }

    val PlayerQueueAutoplay: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_queue_autoplay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            queueRowsWithBadge()
            path(
                stroke = stroke,
                strokeLineWidth = 1.15f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(18f, 6.75f)
                curveTo(17.4f, 5.60f, 16.9f, 5.05f, 16.1f, 5.05f)
                arcToRelative(1.7f, 1.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 3.4f)
                curveTo(16.9f, 8.45f, 17.4f, 7.90f, 18f, 6.75f)
                curveTo(18.6f, 5.60f, 19.1f, 5.05f, 19.9f, 5.05f)
                arcToRelative(1.7f, 1.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 3.4f)
                curveTo(19.1f, 8.45f, 18.6f, 7.90f, 18f, 6.75f)
            }
        }.build()
    }

    val PlayerLyrics: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_lyrics",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.55f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.15f, 4.35f)
                horizontalLineTo(17.85f)
                arcTo(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.65f, 7.15f)
                verticalLineTo(14.85f)
                arcTo(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.85f, 17.65f)
                horizontalLineTo(10.75f)
                lineTo(7.25f, 20.75f)
                verticalLineTo(17.65f)
                horizontalLineTo(6.15f)
                arcTo(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.35f, 14.85f)
                verticalLineTo(7.15f)
                arcTo(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.15f, 4.35f)
                close()
            }
            path(fill = stroke) {
                quoteMark(9.65f)
                quoteMark(14.35f)
            }
        }.build()
    }

    val PlayerChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_chevron_down",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.2f, 9.2f)
                lineTo(12f, 15.5f)
                lineTo(18.8f, 9.2f)
            }
        }.build()
    }

    val PlayerEllipsis: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_ellipsis",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = stroke) {
                circle(5.9f, 12f, 1.75f)
                circle(12f, 12f, 1.75f)
                circle(18.1f, 12f, 1.75f)
            }
        }.build()
    }

    val PlayerStar: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                star()
            }
        }.build()
    }

    val PlayerStarFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_star_filled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                star()
            }
        }.build()
    }

    val PlayerAirPlay: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_airplay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 1.38f, strokeLineCap = StrokeCap.Round) {
                moveTo(9.45f, 13.70f)
                arcTo(3.15f, 3.15f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14.55f, 13.70f)
            }
            path(stroke = stroke, strokeLineWidth = 1.38f, strokeLineCap = StrokeCap.Round) {
                moveTo(7.90f, 15.81f)
                arcTo(5.70f, 5.70f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.10f, 15.81f)
            }
            path(stroke = stroke, strokeLineWidth = 1.38f, strokeLineCap = StrokeCap.Round) {
                moveTo(6.37f, 17.88f)
                arcTo(8.25f, 8.25f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.63f, 17.88f)
            }
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.0f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(11.60f, 14.35f)
                lineTo(6.82f, 19.95f)
                curveTo(6.55f, 20.27f, 6.78f, 20.75f, 7.20f, 20.75f)
                horizontalLineTo(16.80f)
                curveTo(17.22f, 20.75f, 17.45f, 20.27f, 17.18f, 19.95f)
                lineTo(12.40f, 14.35f)
                curveTo(12.19f, 14.10f, 11.81f, 14.10f, 11.60f, 14.35f)
                close()
            }
        }.build()
    }

    val PlayerVolumeLow: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_volume_low",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                speakerBody()
            }
        }.build()
    }

    val PlayerVolumeHigh: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_volume_high",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                speakerBody()
            }
            path(stroke = stroke, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round) {
                moveTo(14.4f, 8.9f)
                quadTo(16.5f, 12f, 14.4f, 15.1f)
                moveTo(17.5f, 6.6f)
                quadTo(20.8f, 12f, 17.5f, 17.4f)
            }
        }.build()
    }

    val PlayerShuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_shuffle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.3f, 7.2f)
                lineTo(6.7f, 7.2f)
                lineTo(17.3f, 16.8f)
                lineTo(19.2f, 16.8f)
                moveTo(3.3f, 16.8f)
                lineTo(6.7f, 16.8f)
                lineTo(9.4f, 14.4f)
                moveTo(13.9f, 9.6f)
                lineTo(17.3f, 7.2f)
                lineTo(19.2f, 7.2f)
            }
            path(fill = stroke) {
                moveTo(17.7f, 5.0f); lineTo(21.0f, 7.2f); lineTo(17.7f, 9.4f); close()
                moveTo(17.7f, 14.6f); lineTo(21.0f, 16.8f); lineTo(17.7f, 19.0f); close()
            }
        }.build()
    }

    val PlayerRepeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_repeat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.2f, 7.2f)
                lineTo(16.0f, 7.2f)
                arcToRelative(4.8f, 4.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 9.6f)
                lineTo(8.2f, 16.8f)
                arcToRelative(4.8f, 4.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -9.6f)
                close()
            }
            path(fill = stroke) {
                moveTo(14.0f, 5.0f); lineTo(17.4f, 7.2f); lineTo(14.0f, 9.4f); close()
                moveTo(10.4f, 14.6f); lineTo(7.0f, 16.8f); lineTo(10.4f, 19.0f); close()
            }
        }.build()
    }

    val PlayerAutoplay: ImageVector by lazy {
        ImageVector.Builder(
            name = "nest_player_autoplay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 12f)
                curveTo(10.12f, 9.1f, 8.7f, 8f, 7.1f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 8f)
                curveTo(8.7f, 16f, 10.12f, 14.9f, 12f, 12f)
                curveTo(13.88f, 9.1f, 15.3f, 8f, 16.9f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8f)
                curveTo(15.3f, 16f, 13.88f, 14.9f, 12f, 12f)
            }
        }.build()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
        moveTo(cx - radius, cy)
        arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, radius * 2f, 0f)
        arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, -radius * 2f, 0f)
        close()
    }

    private fun PathBuilder.speakerBody() {
        moveTo(3.3f, 9.2f)
        lineTo(5.9f, 9.2f)
        lineTo(10.4f, 5.5f)
        lineTo(10.4f, 18.5f)
        lineTo(5.9f, 14.8f)
        lineTo(3.3f, 14.8f)
        close()
    }

    private fun PathBuilder.quoteMark(x: Float) {
        val dx = x - 9.65f
        moveTo(9.65f + dx, 8.55f)
        curveTo(10.57f + dx, 8.55f, 11.25f + dx, 9.23f, 11.25f + dx, 10.20f)
        curveTo(11.25f + dx, 11.60f, 10.43f + dx, 12.90f, 9.30f + dx, 13.67f)
        curveTo(9.00f + dx, 13.87f, 8.63f + dx, 13.55f, 8.85f + dx, 13.23f)
        curveTo(9.47f + dx, 12.40f, 9.77f + dx, 11.80f, 9.79f + dx, 11.33f)
        curveTo(9.63f + dx, 11.39f, 9.47f + dx, 11.43f, 9.30f + dx, 11.43f)
        curveTo(8.57f + dx, 11.43f, 8.05f + dx, 10.85f, 8.05f + dx, 10.00f)
        curveTo(8.05f + dx, 9.17f, 8.73f + dx, 8.55f, 9.65f + dx, 8.55f)
        close()
    }

    private fun ImageVector.Builder.queueRowsWithBadge() {
        path(
            stroke = stroke,
            strokeLineWidth = 1.35f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(6.2f, 10f)
            horizontalLineTo(14.6f)
            moveTo(6.2f, 14f)
            horizontalLineTo(16.9f)
            moveTo(6.2f, 18f)
            horizontalLineTo(16.9f)
        }
        path(fill = stroke) {
            for (y in listOf(10f, 14f, 18f)) {
                circle(3.2f, y, 1.0f)
            }
        }
    }

    private fun PathBuilder.star() {
        val cx = 12f
        val cy = 12.3f
        val outer = 10.3f
        val inner = 4.35f
        moveTo(cx, cy - outer)
        for (index in 1..10) {
            val radius = if (index % 2 == 1) inner else outer
            val angle = -PI / 2 + index * PI / 5
            lineTo(
                (cx + radius * cos(angle)).toFloat(),
                (cy + radius * sin(angle)).toFloat(),
            )
        }
        close()
    }
}

enum class TransportShape {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
}

/**
 * Rounded-polygon transport glyphs matching Nest Music's `TransportGlyphs.kt`
 * (Apple Music rounded play triangle, twin pause bars, and double skip triangles),
 * built with pure [Path] corner rounding so no external shape dependency is needed.
 */
@Composable
fun TransportShapeGlyph(
    shape: TransportShape,
    tint: Color,
    contentDescription: String?,
    smoothness: Float = 0.18f,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val path = Path()
        val r = min(w, h) * smoothness.coerceIn(0.04f, 0.32f)
        when (shape) {
            TransportShape.PLAY -> {
                path.addRoundedPolygon(
                    points = listOf(
                        Offset(w * 0.21f, h * 0.08f),
                        Offset(w * 0.89f, h * 0.50f),
                        Offset(w * 0.21f, h * 0.92f),
                    ),
                    radius = r * 0.72f,
                )
            }
            TransportShape.PAUSE -> {
                val barRadius = r * 0.55f
                path.addRoundedPolygon(
                    points = listOf(
                        Offset(w * 0.16f, h * 0.06f),
                        Offset(w * 0.42f, h * 0.06f),
                        Offset(w * 0.42f, h * 0.94f),
                        Offset(w * 0.16f, h * 0.94f),
                    ),
                    radius = barRadius,
                )
                path.addRoundedPolygon(
                    points = listOf(
                        Offset(w * 0.58f, h * 0.06f),
                        Offset(w * 0.84f, h * 0.06f),
                        Offset(w * 0.84f, h * 0.94f),
                        Offset(w * 0.58f, h * 0.94f),
                    ),
                    radius = barRadius,
                )
            }
            TransportShape.NEXT -> {
                val skipRadius = r * 0.52f
                path.addRoundedPolygon(
                    points = listOf(
                        Offset(w * 0.06f, h * 0.16f),
                        Offset(w * 0.50f, h * 0.50f),
                        Offset(w * 0.06f, h * 0.84f),
                    ),
                    radius = skipRadius,
                )
                path.addRoundedPolygon(
                    points = listOf(
                        Offset(w * 0.50f, h * 0.16f),
                        Offset(w * 0.94f, h * 0.50f),
                        Offset(w * 0.50f, h * 0.84f),
                    ),
                    radius = skipRadius,
                )
            }
            TransportShape.PREVIOUS -> {
                val skipRadius = r * 0.52f
                path.addRoundedPolygon(
                    points = listOf(
                        Offset(w * 0.94f, h * 0.16f),
                        Offset(w * 0.50f, h * 0.50f),
                        Offset(w * 0.94f, h * 0.84f),
                    ),
                    radius = skipRadius,
                )
                path.addRoundedPolygon(
                    points = listOf(
                        Offset(w * 0.50f, h * 0.16f),
                        Offset(w * 0.06f, h * 0.50f),
                        Offset(w * 0.50f, h * 0.84f),
                    ),
                    radius = skipRadius,
                )
            }
        }
        drawPath(path = path, color = tint)
    }
}

private fun Path.addRoundedPolygon(points: List<Offset>, radius: Float) {
    val n = points.size
    if (n < 3) return
    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]
        val curr = points[i]
        val next = points[(i + 1) % n]

        val v1x = prev.x - curr.x
        val v1y = prev.y - curr.y
        val v2x = next.x - curr.x
        val v2y = next.y - curr.y
        val len1 = hypot(v1x, v1y).coerceAtLeast(1e-4f)
        val len2 = hypot(v2x, v2y).coerceAtLeast(1e-4f)

        val cut = min(radius, min(len1, len2) * 0.42f)
        val p1 = Offset(curr.x + v1x / len1 * cut, curr.y + v1y / len1 * cut)
        val p2 = Offset(curr.x + v2x / len2 * cut, curr.y + v2y / len2 * cut)

        if (i == 0) {
            moveTo(p1.x, p1.y)
        } else {
            lineTo(p1.x, p1.y)
        }
        // Smooth cubic G2-like corner rounding matching AndroidX RoundedPolygon smoothing
        val c1 = Offset(p1.x + (curr.x - p1.x) * 0.68f, p1.y + (curr.y - p1.y) * 0.68f)
        val c2 = Offset(p2.x + (curr.x - p2.x) * 0.68f, p2.y + (curr.y - p2.y) * 0.68f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    close()
}
