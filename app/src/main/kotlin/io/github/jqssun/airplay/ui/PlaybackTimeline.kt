package io.github.jqssun.airplay.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import io.github.jqssun.airplay.R

/** Shared visual timeline. Callers decide whether input can seek. */
@Composable
internal fun PlaybackProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val trackHeight = 7.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val trackSize = Size(size.width, trackHeight)
        val corner = CornerRadius(trackHeight / 2f)
        val playheadX = size.width * fraction
        drawRoundRect(Color(0xFF3D3D3D), Offset(0f, trackTop), trackSize, corner)
        clipRect(right = playheadX) {
            drawRoundRect(Color(0xFFA5A5A5), Offset(0f, trackTop), trackSize, corner)
        }
        val strokeWidth = 1.dp.toPx()
        val markerX = playheadX.coerceIn(strokeWidth / 2f, size.width - strokeWidth / 2f)
        drawLine(Color.White, Offset(markerX, 0f), Offset(markerX, size.height), strokeWidth)
    }
}

/** Status-only timestamps and indicators; no focus targets or seek actions. */
@Composable
internal fun PlaybackTimestamps(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    skipDirection: Int = 0,
    trackWidthPx: Int = 0,
    buffering: Boolean = false,
) {
    val position = positionMs.coerceAtLeast(0L)
    val fraction = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val timestampStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val elapsed = formatVideoTime(position).padStart(5, '0')
        val remaining = if (durationMs > 0) "- ${formatVideoTime((durationMs - position).coerceAtLeast(0)).padStart(5, '0')}" else "--:--"
        val elapsedLayout = textMeasurer.measure(elapsed, timestampStyle, maxLines = 1)
        val remainingLayout = textMeasurer.measure(remaining, timestampStyle, maxLines = 1)
        val colon = elapsedLayout.getBoundingBox(elapsed.lastIndexOf(':'))
        val widthPx = constraints.maxWidth
        val trackWidth = trackWidthPx.takeIf { it > 0 } ?: widthPx
        // Use the actual centered Slider track and anchor the colon, not the whole label.
        // The timestamp follows the playhead all the way to either end of the track.
        val playheadPx = (widthPx - trackWidth) / 2f + trackWidth * fraction
        val elapsedLeftPx = (playheadPx - colon.center.x).roundToInt()
        val trailingIconWidthPx = with(density) { when {
            skipDirection > 0 -> (4.dp + 27.dp).toPx()
            buffering || !playing -> (4.dp + 18.dp).toPx()
            else -> 0f
        } }
        val leadingIconWidthPx = with(density) { if (skipDirection < 0) (27.dp + 4.dp).roundToPx() else 0 }
        val labelRightPx = elapsedLeftPx + elapsedLayout.size.width + trailingIconWidthPx
        val countdownLeftPx = widthPx - remainingLayout.size.width
        val showCountdown = labelRightPx + with(density) { 12.dp.toPx() } <= countdownLeftPx
        Row(Modifier.offset { IntOffset(elapsedLeftPx - leadingIconWidthPx, 0) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (skipDirection < 0) {
                Icon(Icons.Rounded.Replay10, contentDescription = stringResource(R.string.video_rewind_ten),
                    tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Text(elapsed, style = timestampStyle, maxLines = 1, softWrap = false)
            if (skipDirection > 0) {
                Icon(Icons.Rounded.Forward10, contentDescription = stringResource(R.string.video_forward_ten),
                    tint = Color.White, modifier = Modifier.size(27.dp))
            }
            else if (buffering) {
                PlaybackLoadingIndicator(Modifier.size(18.dp))
            }
            // A paused-state marker, not a remote focus target or button.
            else if (!playing) {
                Icon(
                    Icons.Rounded.Pause,
                    contentDescription = stringResource(R.string.video_paused),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                        .border(1.5.dp, Color.White, CircleShape).padding(3.dp),
                )
            }
        }
        if (showCountdown) {
            Text(remaining, modifier = Modifier.align(Alignment.CenterEnd), style = timestampStyle, maxLines = 1, softWrap = false)
        }
    }
}
