package com.meuapp.iptv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.meuapp.iptv.data.EntradaHistorico
import com.meuapp.iptv.data.Historico

/** Player em tela cheia usando o ExoPlayer (Media3). */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITULO = "titulo"
        const val EXTRA_UA = "user_agent"
        const val EXTRA_AO_VIVO = "ao_vivo"
        const val EXTRA_HIST = "hist"
        const val EXTRA_POSICAO = "posicao"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var url = ""
    private var tentouAlternativa = false
    private var posicao = 0L
    private var entrada: EntradaHistorico? = null
    private val handler = Handler(Looper.getMainLooper())
    private val salvador = object : Runnable {
        override fun run() {
            player?.let { salvarProgresso(it) }
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        title = intent.getStringExtra(EXTRA_TITULO) ?: ""
        entrada = intent.getStringExtra(EXTRA_HIST)?.let { Historico.deJson(it) }
        posicao = intent.getLongExtra(EXTRA_POSICAO, 0L)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // TextureView em vez de SurfaceView: não fica preta ao voltar de outro app
        playerView = layoutInflater.inflate(R.layout.player_tela_cheia, null) as PlayerView
        playerView.keepScreenOn = true
        setContentView(playerView)
    }

    override fun onStart() {
        super.onStart()
        iniciar()
    }

    override fun onStop() {
        super.onStop()
        liberar()
    }

    private fun iniciar() {
        if (player != null) return
        val ua = intent.getStringExtra(EXTRA_UA) ?: "IPTVSmartersPlayer"
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        player = p
        playerView.player = p

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                tratarErro(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    entrada?.let { Historico.remover(this@PlayerActivity, it.chave) }
                    entrada = null
                }
            }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        if (posicao > 0) p.seekTo(posicao)
        p.prepare()
        p.playWhenReady = true
        handler.removeCallbacks(salvador)
        handler.postDelayed(salvador, 15_000)
    }

    /** Guarda o ponto onde o usuário está (só filmes e séries). */
    private fun salvarProgresso(p: ExoPlayer) {
        val e = entrada ?: return
        if (p.isCurrentMediaItemLive) return
        Historico.salvar(this, e.copy(posicao = p.currentPosition, duracao = p.duration.coerceAtLeast(0L)))
    }

    private fun liberar() {
        val p = player
        handler.removeCallbacks(salvador)
        if (p != null) {
            salvarProgresso(p)
            posicao = if (p.isCurrentMediaItemLive) 0L else p.currentPosition
            playerView.player = null
            p.release()
        }
        player = null
    }

    private fun tratarErro(erro: PlaybackException) {
        val p = player ?: return
        when {
            // Ao vivo que ficou para trás: volta para o "agora"
            erro.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                p.seekToDefaultPosition()
                p.prepare()
            }
            // Se .ts não funcionar, tenta automaticamente .m3u8 (e vice-versa)
            !tentouAlternativa && (url.endsWith(".ts") || url.endsWith(".m3u8")) -> {
                tentouAlternativa = true
                url = if (url.endsWith(".ts")) url.removeSuffix(".ts") + ".m3u8"
                else url.removeSuffix(".m3u8") + ".ts"
                playerView.post {
                    posicao = 0L
                    liberar()
                    iniciar()
                }
            }
            else -> Toast.makeText(
                this,
                "Não foi possível reproduzir: ${erro.errorCodeName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
