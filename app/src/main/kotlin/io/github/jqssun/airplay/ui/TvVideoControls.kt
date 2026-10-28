package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.gestures.VideoContentScale

/** TV overlay based on the captured TCL player in docs/references/tcl-video-player.png. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TvVideoControls(
    title: String,
    author: String,
    playing: Boolean,
    skipDirection: Int,
    positionMs: Long,
    durationMs: Long,
    contentScale: VideoContentScale,
    optionsOpen: Boolean,
    onOptionsOpenChange: (Boolean) -> Unit,
    onContentScale: () -> Unit,
    onSpeed: () -> Unit,
    onLock: () -> Unit,
    onPip: (() -> Unit)?,
    downloadLabel: String?,
    onDownload: () -> Unit,
    onCopyUrl: (() -> Unit)?,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    actionsModifier: Modifier,
) {
    val position = positionMs.coerceAtLeast(0L)
    val seekLabel = stringResource(R.string.video_seek)
    val fraction = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 40.dp).padding(bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (author.isNotBlank()) {
                    Text(author, modifier = Modifier.widthIn(max = 500.dp), color = Color(0xFFBDBDBD),
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (title.isNotBlank()) {
                    Text(
                        title,
                        modifier = Modifier.widthIn(max = 500.dp).padding(bottom = 2.dp),
                        color = Color(0xFFEAEAEA), fontSize = 28.sp, lineHeight = 33.sp,
                        fontWeight = FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvVideoAction(contentScale.icon(), stringResource(contentScale.nameRes()), onContentScale, actionsModifier)
                Box {
                    TvVideoAction(Icons.Rounded.Tune, stringResource(R.string.video_player_options), { onOptionsOpenChange(true) })
                    DropdownMenu(expanded = optionsOpen, onDismissRequest = { onOptionsOpenChange(false) }, containerColor = Color(0xFF303032)) {
                        @Composable
                        fun option(label: String, action: () -> Unit) {
                            DropdownMenuItem(text = { Text(label, color = Color.White) }, onClick = {
                                onOptionsOpenChange(false)
                                action()
                            })
                        }
                        option(stringResource(R.string.select_playback_speed), onSpeed)
                        if (downloadLabel != null) option(downloadLabel, onDownload)
                        if (onCopyUrl != null) option(stringResource(R.string.cd_copy_url), onCopyUrl)
                        if (onPip != null) option(stringResource(R.string.cd_pip), onPip)
                        option(stringResource(R.string.cd_lock_controls), onLock)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            var trackWidthPx by remember { mutableIntStateOf(0) }
            Slider(
                value = fraction,
                onValueChange = { onSeek((it * durationMs).toLong()) },
                onValueChangeFinished = onSeekEnd,
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth().height(20.dp)
                    .focusProperties { canFocus = false }
                    .semantics { contentDescription = seekLabel },
                // Draw the playhead with the track so Slider's separate thumb layout
                // cannot offset it vertically or disagree with the filled progress.
                thumb = { Spacer(Modifier.size(0.dp)) },
                track = {
                    PlaybackProgressTrack(fraction, Modifier.onSizeChanged { trackWidthPx = it.width })
                },
            )
            PlaybackTimestamps(position, durationMs, playing, skipDirection, trackWidthPx)
        }
    }
}

@Composable
private fun TvVideoAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp).onFocusChanged { focused = it.hasFocus }
            .background(if (focused) Color.White else Color(0xFF303032), CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
    ) {
        Icon(icon, label, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(24.dp))
    }
}
