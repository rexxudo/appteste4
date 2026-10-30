package com.meuapp.iptv.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.iptv.data.Preferencias
import com.meuapp.iptv.data.Repositorio
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.data.XtreamApi
import com.meuapp.iptv.data.mensagemErro
import com.meuapp.iptv.tema.LocalTema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tela inicial. Tudo cabe na tela, sem rolagem (celular, tablet e TV). */
@Composable
fun TelaHome(
    abrir: (TipoConteudo) -> Unit,
    abrirConfiguracoes: () -> Unit,
    abrirContinuar: () -> Unit,
    sair: () -> Unit
) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var geracao by remember { mutableIntStateOf(0) }
    val api = remember(geracao) { Sessao.api(ctx) }
    var info by remember { mutableStateOf(Sessao.infoUsuario) }
    var erro by remember { mutableStateOf<String?>(null) }
    var atualizando by remember { mutableStateOf(false) }
    var statusAtualizacao by remember { mutableStateOf<String?>(null) }
    var ultima by remember { mutableLongStateOf(Preferencias.ultimaAtualizacao(ctx)) }
    val focoInicial = remember { FocusRequester() }

    if (api == null) {
        LaunchedEffect(Unit) { sair() }
        return
    }

    LaunchedEffect(Unit) {
        if (ehTv(ctx)) try { focoInicial.requestFocus() } catch (_: Exception) {}
        if (info == null) {
            try {
                // Teste rápido: se o servidor salvo caiu, procura outro da lista
                info = XtreamApi(api.dns, api.usuario, api.senha, rapido = true).login()
                    .also { Sessao.infoUsuario = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val trocou = Sessao.reconectar(ctx, tema.servidores)
                if (trocou) {
                    info = Sessao.infoUsuario
                    geracao++
                    Toast.makeText(ctx, "Servidor trocado automaticamente", Toast.LENGTH_SHORT).show()
                } else {
                    erro = mensagemErro(e)
                }
            }
        }
    }

    fun atualizar() {
        if (atualizando) return
        escopo.launch {
            atualizando = true
            erro = null
            try {
                info = api.login().also { Sessao.infoUsuario = it }
                Repositorio.atualizarTudo(ctx, api) { statusAtualizacao = it }
                ultima = Preferencias.ultimaAtualizacao(ctx)
                statusAtualizacao = null
                Toast.makeText(ctx, "Conteúdo atualizado!", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                statusAtualizacao = null
                erro = "Falha ao atualizar: ${mensagemErro(e)}"
            } finally {
                atualizando = false
            }
        }
    }

    Fundo(tema.fundoHome) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compacto = maxHeight < 420.dp
            val margem = if (compacto) 12.dp else 24.dp

            Column(Modifier.fillMaxSize().padding(margem)) {
                // Topo: logo, dados da conta e botão sair
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ImagemTema(
                        tema.logo,
                        Modifier.height(if (compacto) 40.dp else 56.dp).widthIn(max = 220.dp),
                        descricao = tema.nomeApp
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Usuário: ${api.usuario}", color = tema.corTexto, fontSize = 14.sp)
                        info?.let {
                            Text(
                                "Vencimento: ${it.expira}",
                                color = tema.corTexto.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    CartaoFocavel(
                        onClick = sair,
                        modifier = Modifier.size(if (compacto) 44.dp else 56.dp),
                        preenchimento = 6.dp
                    ) {
                        ImagemTema(tema.iconeSair, Modifier.fillMaxSize(), descricao = "Sair")
                    }
                }

                erro?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
                if (tema.aviso.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(tema.aviso, color = tema.corSelecionada, fontSize = 13.sp)
                }

                Spacer(Modifier.height(if (compacto) 8.dp else 16.dp))

                // TV ao vivo, Filmes, Séries: ocupam todo o espaço que sobra
                GradeTiles(
                    itens = listOf(
                        Tile(tema.iconeTv, TipoConteudo.LIVE.titulo) { abrir(TipoConteudo.LIVE) },
                        Tile(tema.iconeFilmes, TipoConteudo.FILMES.titulo) { abrir(TipoConteudo.FILMES) },
                        Tile(tema.iconeSeries, TipoConteudo.SERIES.titulo) { abrir(TipoConteudo.SERIES) },
                        Tile(tema.iconeContinuar, "Continuar assistindo") { abrirContinuar() }
                    ),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    colunasRetrato = 2,
                    focoInicial = focoInicial
                )

                Spacer(Modifier.height(if (compacto) 6.dp else 12.dp))

                // Botões pequenos: Atualizar e Configurações
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
                ) {
                    BotaoRedondo(
                        imagem = tema.iconeAtualizar,
                        texto = if (atualizando) "Atualizando..." else "Atualizar",
                        girando = atualizando,
                        onClick = { atualizar() },
                        tamanho = if (compacto) 48.dp else 60.dp
                    )
                    BotaoRedondo(
                        imagem = tema.iconeConfig,
                        texto = "Configurações",
                        onClick = abrirConfiguracoes,
                        tamanho = if (compacto) 48.dp else 60.dp
                    )
                }

                val legenda = statusAtualizacao ?: if (ultima > 0) {
                    "Última atualização: " +
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ultima))
                } else null
                legenda?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        color = tema.corTexto.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
