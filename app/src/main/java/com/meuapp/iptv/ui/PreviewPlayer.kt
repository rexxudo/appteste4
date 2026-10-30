package com.meuapp.iptv.ui

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.meuapp.iptv.R
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
private fun SobrePreview(carregando: Boolean, falhou: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (falhou) {
            Text(
                "Sem sinal.\nTente em tela cheia ou troque o player.",
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        } else if (carregando) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

/** Telinha pequena com o ExoPlayer. */
@Composable
fun PreviewExo(url: String, userAgent: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var carregando by remember { mutableStateOf(true) }
    var falhou by remember { mutableStateOf(false) }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
    }

    DisposableEffect(player) {
        val ouvinte = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                carregando = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY) falhou = false
            }

            override fun onPlayerError(error: PlaybackException) {
                carregando = false
                falhou = true
            }
        }
        player.addListener(ouvinte)
        onDispose {
            player.removeListener(ouvinte)
            player.release()
        }
    }

    LaunchedEffect(url) {
        falhou = false
        carregando = true
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    Box(modifier) {
        AndroidView(
            factory = { c ->
                val v = LayoutInflater.from(c).inflate(R.layout.player_miniatura, null) as PlayerView
                v.player = player
                v
            },
            modifier = Modifier.fillMaxSize()
        )
        SobrePreview(carregando, falhou)
    }
}

/** Telinha pequena com o VLC. */
@Composable
fun PreviewVlc(url: String, userAgent: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var carregando by remember { mutableStateOf(true) }
    var falhou by remember { mutableStateOf(false) }

    val layout = remember { VLCVideoLayout(ctx) }
    val lib = remember {
        val saida = com.meuapp.iptv.data.Preferencias.saidaAudioVlc(ctx)
        LibVLC(ctx, arrayListOf("--network-caching=1500", "--http-reconnect", "--aout=$saida"))
    }
    val player = remember { MediaPlayer(lib) }

    DisposableEffect(player) {
        player.attachViews(layout, null, false, true)
        val ouvinte = MediaPlayer.EventListener { ev ->
            if (ev.type == MediaPlayer.Event.Playing) {
                carregando = false
                falhou = false
            }
            if (ev.type == MediaPlayer.Event.Buffering) {
                carregando = ev.buffering < 100f
            }
            if (ev.type == MediaPlayer.Event.EncounteredError) {
                carregando = false
                falhou = true
            }
        }
        player.setEventListener(ouvinte)
        onDispose {
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            lib.release()
        }
    }

    LaunchedEffect(url) {
        falhou = false
        carregando = true
        val media = Media(lib, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":http-user-agent=$userAgent")
        player.setMedia(media)
        media.release()
        player.setVolume(100)
        player.play()
    }

    Box(modifier) {
        AndroidView(factory = { layout }, modifier = Modifier.fillMaxSize())
        SobrePreview(carregando, falhou)
    }
}
