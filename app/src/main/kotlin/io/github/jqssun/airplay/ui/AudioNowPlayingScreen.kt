package io.github.jqssun.airplay.ui

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@OptIn(UnstableApi::class)
internal fun AudioNowPlayingScreen(
    viewModel: MainViewModel,
    onSettings: () -> Unit,
    isConnecting: Boolean = false
) {
    val track by viewModel.trackInfo.collectAsState()
    val artwork = track.coverArt
    val background by produceState<Bitmap?>(null, artwork) {
        // Retain the previous background while preparing the next one off the UI thread.
        value = artwork?.let { cover ->
            withContext(Dispatchers.Default) { blurArtwork(cover) }
        }
    }
    val player = viewModel.dacpPlayer
    var playing by remember(player) { mutableStateOf(player?.playWhenReady == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.playWhenReady
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    // Not every sender supplies artwork or metadata. Allow a normal fallback player after
    // a short wait, while continuing to accept late metadata and artwork.
    var metadataWaitExpired by remember { mutableStateOf(false) }
    LaunchedEffect(isConnecting, track.title, track.artist) {
        metadataWaitExpired = false
        if (!isConnecting) {
            delay(4000)
            metadataWaitExpired = true
        }
    }
    val loading = isConnecting || player == null ||
        (!metadataWaitExpired && (artwork == null || track.title.isBlank()))
    val artworkScale by animateFloatAsState(
        targetValue = if (loading || playing) 1f else 0.85f,
        animationSpec = tween(350), label = "artworkPlaybackScale"
    )
    val skeletonAlpha = if (loading) {
        val transition = rememberInfiniteTransition(label = "playerLoading")
        val alpha by transition.animateFloat(0.10f, 0.20f,
            infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "playerSkeletonAlpha")
        alpha
    } else 0f
    val playFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(player, loading) {
        if (loading) settingsFocus.requestFocusUntilLanded()
        else if (player != null) playFocus.requestFocusUntilLanded()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(
            targetState = background, animationSpec = tween(800),
            modifier = Modifier.fillMaxSize(), label = "artworkBackgroundCrossfade"
        ) { bitmap ->
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = 0.35f),
            Color.Black.copy(alpha = 0.5f),
            Color.Black.copy(alpha = 0.8f)
        ))))
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 40.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AudioControlButton(
                    icon = Icons.Default.Settings, label = stringResource(R.string.airplay_settings),
                    onClick = onSettings, size = 48.dp, iconSize = 24.dp,
                    modifier = Modifier.focusRequester(settingsFocus)
                )
            }
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(Modifier.weight(1f, fill = false).widthIn(max = 440.dp).aspectRatio(1f)
                    .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale }
                    .shadow(24.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    if (loading && artwork == null) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = skeletonAlpha)))
                    } else {
                        Crossfade(
                            targetState = artwork, animationSpec = tween(500),
                            modifier = Modifier.fillMaxSize(), label = "coverArtworkCrossfade"
                        ) { cover ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (cover != null) {
                                    Image(cover.asImageBitmap(), stringResource(R.string.cd_cover_art),
                                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(80.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    PlayerSkeleton(220.dp, 28.dp, skeletonAlpha)
                    PlayerSkeleton(150.dp, 24.dp, skeletonAlpha, Modifier.padding(bottom = 4.dp))
                } else {
                    Text(track.title.ifEmpty { stringResource(R.string.unknown_track) }, color = Color.White,
                        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    if (track.artist.isNotEmpty()) Text(track.artist, color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            if (loading) {
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PlayerSkeleton(48.dp, 48.dp, skeletonAlpha, round = true)
                    PlayerSkeleton(52.dp, 52.dp, skeletonAlpha, round = true)
                    PlayerSkeleton(48.dp, 48.dp, skeletonAlpha, round = true)
                }
                Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = skeletonAlpha), CircleShape))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PlayerSkeleton(40.dp, 16.dp, skeletonAlpha)
                        PlayerSkeleton(40.dp, 16.dp, skeletonAlpha)
                    }
                }
            } else if (player != null) {
                AudioTransportControls(player, playFocus, playing)
                AudioProgress(player)
            }
        }
    }
}

@Composable
private fun PlayerSkeleton(width: Dp, height: Dp, alpha: Float, modifier: Modifier = Modifier, round: Boolean = false) {
    Box(modifier.size(width, height).background(Color.White.copy(alpha = alpha), if (round) CircleShape else RoundedCornerShape(6.dp)))
}

/** Focus is a filled, gently lifted control, shared by remote and pointer input. */
@Composable
private fun AudioControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 32.dp,
    prominent: Boolean = false,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = enabled && (focused || hovered)
    val scale by animateFloatAsState(
        if (pressed) 0.96f else if (highlighted) 1.1f else 1f,
        animationSpec = tween(160), label = "audioControlScale"
    )
    val fill by animateColorAsState(
        if (highlighted) Color.White.copy(alpha = 0.95f)
        else Color.White.copy(alpha = if (prominent) 0.12f else 0f),
        animationSpec = tween(160), label = "audioControlFill"
    )
    val tint by animateColorAsState(
        if (!enabled) Color.White.copy(alpha = 0.3f)
        else if (highlighted) Color(0xFF181818) else Color.White,
        animationSpec = tween(160), label = "audioControlTint"
    )
    Box(
        modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .shadow(if (highlighted) 8.dp else 0.dp, CircleShape)
            .clip(CircleShape).background(fill)
            .hoverable(interactionSource = interaction, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun AudioTransportControls(player: Player, playFocus: FocusRequester, playing: Boolean) {
    var commands by remember(player) { mutableStateOf(player.availableCommands) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                commands = player.availableCommands
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Row(
        Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        AudioControlButton(Icons.Rounded.FastRewind, stringResource(R.string.audio_previous_track),
            onClick = { player.seekToPreviousMediaItem() }, size = 48.dp, iconSize = 26.dp,
            enabled = commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        AudioControlButton(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (playing) R.string.audio_pause else R.string.audio_play),
            onClick = { if (player.playWhenReady) player.pause() else player.play() },
            modifier = Modifier.focusRequester(playFocus), size = 52.dp, iconSize = 30.dp, prominent = true,
            enabled = commands.contains(Player.COMMAND_PLAY_PAUSE)
        )
        AudioControlButton(Icons.Rounded.FastForward, stringResource(R.string.audio_next_track),
            onClick = { player.seekToNextMediaItem() }, size = 48.dp, iconSize = 26.dp,
            enabled = commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    }
}

/** Read-only sender progress: no focus target, pointer actions, or remote seek commands. */
@Composable
private fun AudioProgress(player: Player) {
    var position by remember(player) { mutableLongStateOf(0) }
    var duration by remember(player) { mutableLongStateOf(0) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
            delay(250)
        }
    }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val progressLabel = stringResource(R.string.audio_playback_position)
    Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(48.dp).semantics {
                contentDescription = progressLabel
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White.copy(alpha = 0.9f)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatVideoTime(position), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Text(if (duration > 0) "−${formatVideoTime((duration - position).coerceAtLeast(0))}" else "--:--",
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
    }
}

/** Blur a small copy so artwork transitions stay inexpensive on TV hardware. */
private fun blurArtwork(artwork: Bitmap): Bitmap {
    val size = 32
    val small = Bitmap.createScaledBitmap(artwork, size, size, true)
    var pixels = IntArray(size * size)
    small.getPixels(pixels, 0, size, 0, 0, size, size)
    repeat(3) {
        val result = IntArray(pixels.size)
        for (y in 0 until size) for (x in 0 until size) {
            var red = 0; var green = 0; var blue = 0
            for (dy in -2..2) for (dx in -2..2) {
                val color = pixels[(y + dy).coerceIn(0, size - 1) * size + (x + dx).coerceIn(0, size - 1)]
                red += (color ushr 16) and 255
                green += (color ushr 8) and 255
                blue += color and 255
            }
            result[y * size + x] = android.graphics.Color.rgb(red / 25, green / 25, blue / 25)
        }
        pixels = result
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
