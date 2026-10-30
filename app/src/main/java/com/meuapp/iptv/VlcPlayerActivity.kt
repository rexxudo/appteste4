package com.meuapp.iptv

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.meuapp.iptv.data.EntradaHistorico
import com.meuapp.iptv.data.Historico
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Player em tela cheia usando o motor do VLC (toca quase qualquer formato e áudio). */
class VlcPlayerActivity : ComponentActivity() {

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private lateinit var video: VLCVideoLayout

    private var url = ""
    private var titulo = ""
    private var aoVivo = false
    private var ultimaPosicao = 0L
    private var entrada: EntradaHistorico? = null
    private var terminou = false
    private val handler = Handler(Looper.getMainLooper())
    private val salvador = object : Runnable {
        override fun run() {
            player?.let { salvarProgresso(it) }
            handler.postDelayed(this, 15_000)
        }
    }

    // Estado mostrado na tela
    private val carregando = mutableStateOf(true)
    private val tocando = mutableStateOf(false)
    private val posicao = mutableLongStateOf(0L)
    private val duracao = mutableLongStateOf(0L)
    private val erro = mutableStateOf<String?>(null)
    private val controles = mutableStateOf(true)
    private val interacoes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra(PlayerActivity.EXTRA_URL) ?: run { finish(); return }
        titulo = intent.getStringExtra(PlayerActivity.EXTRA_TITULO) ?: ""
        aoVivo = intent.getBooleanExtra(PlayerActivity.EXTRA_AO_VIVO, false)
        entrada = intent.getStringExtra(PlayerActivity.EXTRA_HIST)?.let { Historico.deJson(it) }
        ultimaPosicao = intent.getLongExtra(PlayerActivity.EXTRA_POSICAO, 0L)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        video = VLCVideoLayout(this)

        setContent {
            // Esconde os controles depois de 4 segundos sem mexer
            LaunchedEffect(controles.value, interacoes.intValue) {
                if (controles.value) {
                    delay(4000)
                    controles.value = false
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { mostrarOuEsconderControles() }
            ) {
                AndroidView(factory = { video }, modifier = Modifier.fillMaxSize())

                if (carregando.value && erro.value == null) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                }

                val mensagemErro = erro.value
                if (mensagemErro != null) {
                    Text(
                        mensagemErro,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }

                if (controles.value) Controles(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    @Composable
    private fun Controles(modifier: Modifier) {
        Column(
            modifier
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(titulo, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!aoVivo && duracao.longValue > 0) {
                Slider(
                    value = posicao.longValue.toFloat().coerceIn(0f, duracao.longValue.toFloat()),
                    onValueChange = {
                        posicao.longValue = it.toLong()
                        interacoes.intValue++
                    },
                    onValueChangeFinished = { player?.setTime(posicao.longValue) },
                    valueRange = 0f..duracao.longValue.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFE50914))
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatar(posicao.longValue), color = Color.White, fontSize = 13.sp)
                    Box(Modifier.weight(1f))
                    Text(formatar(duracao.longValue), color = Color.White, fontSize = 13.sp)
                }
            } else if (aoVivo) {
                Text("● AO VIVO", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!aoVivo) TextButton(onClick = { pular(-10_000) }) { Text("⏪ 10s", color = Color.White, fontSize = 16.sp) }
                TextButton(onClick = { alternarPausa() }) {
                    Text(if (tocando.value) "❚❚ Pausar" else "▶ Continuar", color = Color.White, fontSize = 16.sp)
                }
                if (!aoVivo) TextButton(onClick = { pular(10_000) }) { Text("10s ⏩", color = Color.White, fontSize = 16.sp) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        iniciar()
    }

    override fun onStop() {
        super.onStop()
        liberar()
    }

    private fun aoEvento(ev: MediaPlayer.Event) {
        when (ev.type) {
            MediaPlayer.Event.Playing -> {
                tocando.value = true
                carregando.value = false
                player?.setVolume(100)
            }
            MediaPlayer.Event.Paused -> tocando.value = false
            MediaPlayer.Event.Stopped -> tocando.value = false
            MediaPlayer.Event.Buffering -> carregando.value = ev.buffering < 100f
            MediaPlayer.Event.TimeChanged -> posicao.longValue = ev.timeChanged
            MediaPlayer.Event.LengthChanged -> duracao.longValue = ev.lengthChanged
            MediaPlayer.Event.EncounteredError -> {
                carregando.value = false
                erro.value = "Não foi possível reproduzir.\nTente trocar o player em Configurações."
            }
            MediaPlayer.Event.EndReached -> {
                terminou = true
                entrada?.let { Historico.remover(this, it.chave) }
                if (!aoVivo) finish()
            }
            else -> {}
        }
    }

    private fun iniciar() {
        if (player != null) return
        erro.value = null
        carregando.value = true
        val ua = intent.getStringExtra(PlayerActivity.EXTRA_UA) ?: "IPTVSmartersPlayer"

        val saida = com.meuapp.iptv.data.Preferencias.saidaAudioVlc(this)
        val lib = LibVLC(this, arrayListOf("--network-caching=2000", "--http-reconnect", "--aout=$saida"))
        val p = MediaPlayer(lib)
        libVlc = lib
        player = p
        // true = usa TextureView: não fica preta ao voltar de outro app
        p.attachViews(video, null, false, true)
        p.setEventListener { ev -> aoEvento(ev) }

        val media = Media(lib, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":http-user-agent=$ua")
        media.addOption(":network-caching=2000")
        if (!aoVivo && ultimaPosicao > 0) media.addOption(":start-time=${ultimaPosicao / 1000}")
        p.setMedia(media)
        media.release()
        p.setVolume(100)
        p.play()
        handler.removeCallbacks(salvador)
        handler.postDelayed(salvador, 15_000)
    }

    /** Guarda o ponto onde o usuário está (só filmes e séries). */
    private fun salvarProgresso(p: MediaPlayer) {
        val e = entrada ?: return
        if (aoVivo || terminou) return
        Historico.salvar(this, e.copy(posicao = p.time.coerceAtLeast(0L), duracao = p.length.coerceAtLeast(0L)))
    }

    private fun liberar() {
        val p = player
        if (p != null) {
            if (!aoVivo) {
                salvarProgresso(p)
                ultimaPosicao = p.time.coerceAtLeast(0L)
            }
            p.setEventListener(null)
            p.stop()
            p.detachViews()
            p.release()
        }
        player = null
        handler.removeCallbacks(salvador)
        libVlc?.release()
        libVlc = null
    }

    private fun mostrarOuEsconderControles() {
        controles.value = !controles.value
        interacoes.intValue++
    }

    private fun mostrarControles() {
        controles.value = true
        interacoes.intValue++
    }

    private fun alternarPausa() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        mostrarControles()
    }

    private fun pular(ms: Long) {
        val p = player ?: return
        if (aoVivo) return
        val limite = if (duracao.longValue > 0) duracao.longValue else Long.MAX_VALUE
        val novo = (p.time + ms).coerceIn(0L, limite)
        p.setTime(novo)
        posicao.longValue = novo
        mostrarControles()
    }

    /** Controle remoto: OK pausa, esquerda/direita voltam/avançam 10s, cima/baixo mostram os controles. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val tecla = event.keyCode
        val nossa = tecla == KeyEvent.KEYCODE_DPAD_CENTER || tecla == KeyEvent.KEYCODE_ENTER ||
                tecla == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || tecla == KeyEvent.KEYCODE_SPACE ||
                tecla == KeyEvent.KEYCODE_DPAD_LEFT || tecla == KeyEvent.KEYCODE_MEDIA_REWIND ||
                tecla == KeyEvent.KEYCODE_DPAD_RIGHT || tecla == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
                tecla == KeyEvent.KEYCODE_DPAD_UP || tecla == KeyEvent.KEYCODE_DPAD_DOWN ||
                tecla == KeyEvent.KEYCODE_MENU
        if (!nossa) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (tecla) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> alternarPausa()
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> pular(-10_000)
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> pular(10_000)
                else -> mostrarControles()
            }
        }
        return true
    }

    private fun formatar(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val seg = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%02d:%02d".format(m, seg)
    }
}
