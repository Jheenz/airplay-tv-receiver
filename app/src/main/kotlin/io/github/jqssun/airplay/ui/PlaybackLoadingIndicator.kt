package io.github.jqssun.airplay.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics

/** Twelve radial lines, drawn at the destination size to stay crisp on every display. */
@Composable
internal fun PlaybackLoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playbackLoading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "playbackLoadingPhase",
    )
    Canvas(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val diameter = size.minDimension
        repeat(12) { line ->
            val age = (phase.toInt() - line + 12) % 12
            rotate(line * 30f) {
                drawLine(
                    color = Color.White.copy(alpha = 1f - age / 12f * 0.8f),
                    start = Offset(center.x, center.y - diameter * 0.27f),
                    end = Offset(center.x, center.y - diameter * 0.43f),
                    strokeWidth = diameter * 0.08f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
