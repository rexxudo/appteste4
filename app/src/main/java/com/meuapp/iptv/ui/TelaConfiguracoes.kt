package com.meuapp.iptv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meuapp.iptv.data.Preferencias
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.data.TipoPlayer
import com.meuapp.iptv.tema.LocalTema

/**
 * Configurações: três quadrados grandes (Player, Formato TV, Conta).
 * Cada um abre uma janela pequena que cabe inteira na tela, sem rolagem.
 */
@Composable
fun TelaConfiguracoes(voltar: () -> Unit, sair: () -> Unit) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val api = remember { Sessao.api(ctx) }

    var dialogo by remember { mutableStateOf("") }
    var players by remember {
        mutableStateOf(TipoConteudo.entries.associateWith { Preferencias.player(ctx, it) })
    }
    var formato by remember { mutableStateOf(Preferencias.formatoLive(ctx, tema.formatoLive)) }
    var saidaAudio by remember { mutableStateOf(Preferencias.saidaAudioVlc(ctx)) }

    Fundo(tema.fundoListas) {
        Column(Modifier.fillMaxSize()) {
            Cabecalho("Configurações", voltar)
            GradeTiles(
                itens = listOf(
                    Tile(tema.iconePlayer, "Player") { dialogo = "player" },
                    Tile(tema.iconeFormato, "Formato e áudio") { dialogo = "formato" },
                    Tile(tema.iconeConta, "Conta") { dialogo = "conta" }
                ),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                colunasRetrato = 1
            )
        }
    }

    when (dialogo) {
        "player" -> Dialogo("Player", { dialogo = "" }) {
            Text(
                "Se algum canal ou filme não abrir, experimente o outro player.",
                color = tema.corTexto.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            TipoConteudo.entries.forEach { tipo ->
                LinhaOpcoes(
                    titulo = tipo.titulo,
                    opcoes = TipoPlayer.entries.map { it.name to it.titulo },
                    selecionada = players[tipo]?.name ?: "",
                    aoEscolher = { nome ->
                        val p = TipoPlayer.valueOf(nome)
                        Preferencias.definirPlayer(ctx, tipo, p)
                        players = players + (tipo to p)
                    }
                )
            }
        }

        "formato" -> Dialogo("Formato e áudio", { dialogo = "" }) {
            Text(
                "HLS costuma ser mais estável em internet fraca. " +
                        "Se o VLC ficar sem som, troque a saída de áudio e abra o vídeo de novo.",
                color = tema.corTexto.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            LinhaOpcoes(
                titulo = "Formato",
                opcoes = listOf("ts" to "MPEG-TS", "m3u8" to "HLS"),
                selecionada = formato,
                aoEscolher = {
                    Preferencias.definirFormatoLive(ctx, it)
                    formato = it
                    Toast.makeText(ctx, "Formato alterado", Toast.LENGTH_SHORT).show()
                }
            )
            LinhaOpcoes(
                titulo = "Áudio VLC",
                opcoes = listOf("opensles" to "OpenSL ES", "audiotrack" to "AudioTrack"),
                selecionada = saidaAudio,
                aoEscolher = {
                    Preferencias.definirSaidaAudioVlc(ctx, it)
                    saidaAudio = it
                    Toast.makeText(ctx, "Saída de áudio alterada", Toast.LENGTH_SHORT).show()
                }
            )
        }

        "conta" -> Dialogo("Conta", { dialogo = "" }) {
            val info = Sessao.infoUsuario
            TextoInfo("Usuário", api?.usuario ?: "-")
            TextoInfo("Servidor", api?.dns ?: "-")
            TextoInfo("Vencimento", info?.expira ?: "-")
            TextoInfo("Conexões", info?.conexoesMax?.ifBlank { "-" } ?: "-")
            Spacer(Modifier.height(8.dp))
            CartaoFocavel(
                onClick = { dialogo = ""; sair() },
                modifier = Modifier.fillMaxWidth(),
                centralizar = true
            ) {
                Text(
                    "Sair da conta",
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

/** Janela pequena no meio da tela, com botão OK. */
@Composable
private fun Dialogo(titulo: String, aoFechar: () -> Unit, conteudo: @Composable ColumnScope.() -> Unit) {
    val tema = LocalTema.current
    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF2141420))
                .padding(16.dp)
        ) {
            Text(titulo, color = tema.corSelecionada, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            conteudo()
            Spacer(Modifier.height(8.dp))
            CartaoFocavel(onClick = aoFechar, modifier = Modifier.fillMaxWidth(), centralizar = true) {
                Text("OK", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

/** Linha com um título e botões de escolha (como os "radio buttons" do XCIPTV). */
@Composable
private fun LinhaOpcoes(
    titulo: String,
    opcoes: List<Pair<String, String>>,
    selecionada: String,
    aoEscolher: (String) -> Unit
) {
    val tema = LocalTema.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(titulo, color = tema.corTexto, fontSize = 14.sp, modifier = Modifier.width(84.dp), maxLines = 1)
        opcoes.forEach { (valor, rotulo) ->
            val sel = valor == selecionada
            CartaoFocavel(
                onClick = { aoEscolher(valor) },
                selecionado = sel,
                preenchimento = 6.dp,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    (if (sel) "● " else "○ ") + rotulo,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TextoInfo(rotulo: String, valor: String) {
    val tema = LocalTema.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$rotulo: ", color = tema.corTexto.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(valor, color = tema.corTexto, fontSize = 14.sp, maxLines = 1)
    }
}
