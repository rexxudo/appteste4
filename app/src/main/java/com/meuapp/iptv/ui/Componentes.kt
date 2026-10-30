package com.meuapp.iptv.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.meuapp.iptv.PlayerActivity
import com.meuapp.iptv.VlcPlayerActivity
import com.meuapp.iptv.data.EntradaHistorico
import com.meuapp.iptv.data.Historico
import com.meuapp.iptv.data.Preferencias
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.data.TipoPlayer
import com.meuapp.iptv.tema.LocalTema
import com.meuapp.iptv.tema.imagemUri
import kotlinx.coroutines.CancellationException

/** Estado genérico de carregamento das telas. */
sealed interface Estado<out T> {
    data object Carregando : Estado<Nothing>
    data class Ok<T>(val dados: T) : Estado<T>
    data class Falha(val msg: String) : Estado<Nothing>
}

suspend fun <T> carregar(bloco: suspend () -> T): Estado<T> = try {
    Estado.Ok(bloco())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Estado.Falha(com.meuapp.iptv.data.mensagemErro(e))
}

fun ehTv(ctx: Context): Boolean =
    ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

/**
 * Abre o vídeo em tela cheia no player escolhido em Configurações (ExoPlayer ou VLC).
 * historico/posicao: usados em filmes e séries para lembrar onde o usuário parou.
 */
fun tocar(
    ctx: Context,
    url: String,
    titulo: String,
    tipo: TipoConteudo,
    userAgent: String,
    historico: EntradaHistorico? = null,
    posicao: Long = 0L
) {
    val classe = when (Preferencias.player(ctx, tipo)) {
        TipoPlayer.EXO -> PlayerActivity::class.java
        TipoPlayer.VLC -> VlcPlayerActivity::class.java
    }
    val intent = Intent(ctx, classe)
        .putExtra(PlayerActivity.EXTRA_URL, url)
        .putExtra(PlayerActivity.EXTRA_TITULO, titulo)
        .putExtra(PlayerActivity.EXTRA_UA, userAgent)
        .putExtra(PlayerActivity.EXTRA_AO_VIVO, tipo == TipoConteudo.LIVE)
    if (historico != null) {
        intent.putExtra(PlayerActivity.EXTRA_HIST, Historico.paraJson(historico))
        intent.putExtra(PlayerActivity.EXTRA_POSICAO, posicao)
    }
    ctx.startActivity(intent)
}

/** Mostra uma imagem do tema (arquivo em assets/tema/ ou URL). */
@Composable
fun ImagemTema(
    nome: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    descricao: String? = null
) {
    AsyncImage(
        model = imagemUri(nome),
        contentDescription = descricao,
        modifier = modifier,
        contentScale = contentScale
    )
}

/** Imagem vinda do servidor (logo do canal, capa do filme). Usa a imagem padrão do tema se falhar. */
@Composable
fun Capa(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val tema = LocalTema.current
    val padrao = rememberAsyncImagePainter(imagemUri(tema.imagemPadrao))
    AsyncImage(
        model = url.ifBlank { null },
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = padrao,
        error = padrao,
        fallback = padrao
    )
}

/** Imagem de fundo + camada escura por cima. */
@Composable
fun Fundo(imagem: String, conteudo: @Composable BoxScope.() -> Unit) {
    val tema = LocalTema.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ImagemTema(imagem, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tema.escurecerFundo)))
        conteudo()
    }
}

/** Guarda o estado do botão OK do controle remoto para detectar "segurar". */
private class EstadoTecla {
    var pressionada = false
    var longo = false
}

private fun ehTeclaOk(codigo: Int) = codigo == KeyEvent.KEYCODE_DPAD_CENTER ||
        codigo == KeyEvent.KEYCODE_ENTER ||
        codigo == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        codigo == KeyEvent.KEYCODE_BUTTON_A

/**
 * Cartão clicável que funciona com toque, mouse, teclado e controle remoto.
 * Quando focado, mostra só uma borda amarela (não muda de tamanho).
 * onLongClick: segurar o dedo (ou segurar OK / apertar MENU no controle).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartaoFocavel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selecionado: Boolean = false,
    semFundo: Boolean = false,
    centralizar: Boolean = false,
    alinhamento: Alignment.Horizontal = Alignment.CenterHorizontally,
    preenchimento: Dp = 8.dp,
    forma: Shape = RoundedCornerShape(12.dp),
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val tema = LocalTema.current
    val interacao = remember { MutableInteractionSource() }
    val focado by interacao.collectIsFocusedAsState()
    val tecla = remember { EstadoTecla() }
    val fundo = when {
        selecionado -> tema.corPrimaria
        semFundo -> Color.Transparent
        else -> tema.corCartao
    }

    Column(
        modifier = modifier
            .clip(forma)
            .background(fundo)
            .border(3.dp, if (focado) tema.corSelecionada else Color.Transparent, forma)
            .onPreviewKeyEvent { ev ->
                val nativo = ev.nativeKeyEvent
                val longo = onLongClick ?: return@onPreviewKeyEvent false
                when {
                    nativo.keyCode == KeyEvent.KEYCODE_MENU -> {
                        if (ev.type == KeyEventType.KeyUp) longo()
                        true
                    }
                    !ehTeclaOk(nativo.keyCode) -> false
                    ev.type == KeyEventType.KeyDown -> {
                        if (nativo.repeatCount == 0) {
                            tecla.pressionada = true
                            tecla.longo = false
                        } else if (!tecla.longo && tecla.pressionada) {
                            tecla.longo = true
                            longo()
                        }
                        true
                    }
                    ev.type == KeyEventType.KeyUp -> {
                        if (tecla.pressionada && !tecla.longo) onClick()
                        tecla.pressionada = false
                        tecla.longo = false
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interacao,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(preenchimento),
        horizontalAlignment = alinhamento,
        verticalArrangement = if (centralizar) Arrangement.Center else Arrangement.Top,
        content = conteudo
    )
}

/** Botão "Voltar" que aparece com borda amarela quando selecionado pelas setas. */
@Composable
fun BotaoVoltar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tema = LocalTema.current
    CartaoFocavel(
        onClick = onClick,
        modifier = modifier,
        preenchimento = 0.dp,
        forma = RoundedCornerShape(8.dp),
        centralizar = true
    ) {
        Text(
            "◀ Voltar",
            color = tema.corTexto,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** Botão redondo pequeno com imagem do tema (ex.: Atualizar). */
@Composable
fun BotaoRedondo(
    imagem: String,
    texto: String,
    onClick: () -> Unit,
    girando: Boolean = false,
    tamanho: Dp = 64.dp
) {
    val tema = LocalTema.current
    val transicao = rememberInfiniteTransition(label = "giro")
    val angulo by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angulo"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CartaoFocavel(
            onClick = onClick,
            modifier = Modifier.size(tamanho),
            forma = CircleShape,
            preenchimento = 10.dp
        ) {
            ImagemTema(
                imagem,
                Modifier.fillMaxSize().rotate(if (girando) angulo else 0f),
                descricao = texto
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(texto, color = tema.corTexto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Um quadrado grande de menu (imagem + nome). */
class Tile(val icone: String, val titulo: String, val onClick: () -> Unit)

@Composable
fun TileMenu(tile: Tile, modifier: Modifier = Modifier) {
    val tema = LocalTema.current
    CartaoFocavel(onClick = tile.onClick, modifier = modifier, centralizar = true) {
        ImagemTema(tile.icone, Modifier.weight(1f).fillMaxWidth().padding(8.dp), descricao = tile.titulo)
        Text(
            tile.titulo,
            color = tema.corTexto,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Distribui os quadrados para ocupar exatamente o espaço disponível, sem rolagem.
 * Deitado: todos na mesma linha. Em pé: [colunasRetrato] por linha.
 */
@Composable
fun GradeTiles(
    itens: List<Tile>,
    modifier: Modifier = Modifier,
    colunasRetrato: Int = 1,
    focoInicial: FocusRequester? = null
) {
    BoxWithConstraints(modifier) {
        val retrato = maxWidth < maxHeight
        val colunas = if (retrato) colunasRetrato else itens.size
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itens.chunked(colunas).forEach { linha ->
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    linha.forEach { t ->
                        val base = Modifier.weight(1f).fillMaxHeight()
                        TileMenu(
                            t,
                            if (t === itens.first() && focoInicial != null) base.focusRequester(focoInicial) else base
                        )
                    }
                    repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun Cabecalho(
    titulo: String,
    voltar: () -> Unit,
    foco: FocusRequester? = null,
    acoes: @Composable () -> Unit = {}
) {
    val tema = LocalTema.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotaoVoltar(voltar, if (foco != null) Modifier.focusRequester(foco) else Modifier)
        Spacer(Modifier.width(12.dp))
        Text(
            titulo,
            color = tema.corTexto,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        acoes()
        ImagemTema(tema.logo, Modifier.height(36.dp).widthIn(max = 140.dp))
    }
}

@Composable
fun coresCampo(): TextFieldColors {
    val tema = LocalTema.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = tema.corTexto,
        unfocusedTextColor = tema.corTexto,
        focusedBorderColor = tema.corPrimaria,
        unfocusedBorderColor = tema.corTexto.copy(alpha = 0.5f),
        focusedLabelColor = tema.corPrimaria,
        unfocusedLabelColor = tema.corTexto.copy(alpha = 0.7f),
        cursorColor = tema.corPrimaria,
        focusedLeadingIconColor = tema.corTexto,
        unfocusedLeadingIconColor = tema.corTexto.copy(alpha = 0.7f)
    )
}

/** aoCima: chamado quando o usuário aperta a seta para cima (usado para ir ao botão Voltar). */
@Composable
fun BarraBusca(
    texto: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    rotulo: String = "Pesquisar",
    aoCima: (() -> Unit)? = null
) {
    val teclaCima = if (aoCima != null) {
        Modifier.onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                aoCima()
                true
            } else {
                false
            }
        }
    } else {
        Modifier
    }
    OutlinedTextField(
        value = texto,
        onValueChange = aoMudar,
        singleLine = true,
        label = { Text(rotulo) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        colors = coresCampo(),
        modifier = modifier.fillMaxWidth().then(teclaCima)
    )
}

@Composable
fun Carregando(mensagem: String? = null) {
    val tema = LocalTema.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = tema.corPrimaria)
        if (mensagem != null) {
            Spacer(Modifier.height(16.dp))
            Text(mensagem, color = tema.corTexto, textAlign = TextAlign.Center, fontSize = 15.sp)
        }
    }
}

@Composable
fun Erro(msg: String, tentarDeNovo: () -> Unit) {
    val tema = LocalTema.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(msg, color = tema.corTexto, textAlign = TextAlign.Center, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = tentarDeNovo,
            colors = ButtonDefaults.buttonColors(containerColor = tema.corPrimaria)
        ) { Text("Tentar de novo", color = Color.White) }
    }
}

/** Mensagem simples no meio da área (ex.: lista vazia). */
@Composable
fun Aviso(msg: String) {
    val tema = LocalTema.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = tema.corTexto.copy(alpha = 0.8f), textAlign = TextAlign.Center, fontSize = 15.sp)
    }
}
