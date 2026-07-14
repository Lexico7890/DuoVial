package com.duovial.platform

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun IncidentVideoView(
    parts: List<String>,
    modifier: Modifier = Modifier,
    onPlayerCreated: (ExoPlayer) -> Unit = {},
    onPlayerError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val partsKey = parts.joinToString(",")

    val player = remember(partsKey) {
        ExoPlayer.Builder(context).build().apply {
            // Usar playlist nativo de ExoPlayer (mas robusto que ConcatenatingMediaSource)
            val mediaItems = parts.map { MediaItem.fromUri(Uri.parse(it)) }
            setMediaItems(mediaItems)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    onPlayerError(error.message ?: "Error de reproduccion")
                }
            })
        }
    }

    DisposableEffect(partsKey) {
        onPlayerCreated(player)
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier,
        update = { view ->
            view.player = player
        }
    )
}
