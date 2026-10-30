package com.meuapp.iptv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meuapp.iptv.data.Favoritos
import com.meuapp.iptv.data.ItemConteudo
import com.meuapp.iptv.data.Preferencias
import com.meuapp.iptv.data.ProgramaEpg
import com.meuapp.iptv.data.Repositorio
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.data.TipoPlayer
import com.meuapp.iptv.tema.LocalTema
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hora(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/**
 * Tela do canal (igual ao XCIPTV): lista de canais da pasta à esquerda,
 * telinha com o canal ao vivo em cima à direita e o guia de programação embaixo.
 * Tocar no canal que já está passando (ou na telinha) abre em tela cheia.
 */
@Composable
fun TelaCanais(pastaInicial: String, canalInicial: String, voltar: () -> Unit) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val api = remember { Sessao.api(ctx) }
    var estado by remember { mutableStateOf<Estado<Repositorio.Dados>>(Estado.Carregando) }
    var tentativa by remember { mutableIntStateOf(0) }

    LaunchedEffect(tentativa) {
        if (api == null) return@LaunchedEffect
        estado = Estado.Carregando
        estado = carregar { Repositorio.obter(ctx, api, TipoConteudo.LIVE) }
    }

    Fundo(tema.fundoListas) {
        when (val e = estado) {
            is Estado.Carregando -> Carregando()
            is Estado.Falha -> Erro(e.msg) { tentativa++ }
            is Estado.Ok -> CanaisCarregados(e.dados, pastaInicial, canalInicial, voltar)
        }
    }
}

@Composable
private fun CanaisCarregados(
    dados: Repositorio.Dados,
    pastaInicial: String,
    canalInicial: String,
    voltar: () -> Unit
) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val api = remember { Sessao.api(ctx) } ?: return

    val versaoFav = Favoritos.versao.intValue
    val favIds = remember(versaoFav) { Favoritos.ids(ctx, TipoConteudo.LIVE) }
    val pastas = remember(dados, favIds) { montarPastas(dados, favIds) }

    var pastaId by rememberSaveable { mutableStateOf(pastaInicial) }
    var canalId by rememberSaveable { mutableStateOf(canalInicial) }
    val indice = pastas.indexOfFirst { it.id == pastaId }.coerceAtLeast(0)
    val pasta = pastas[indice]
    val canais = remember(dados, pasta.id, favIds) { itensDaPasta(dados, pasta.id, favIds) }
    val canalAtual = remember(dados, canalId) { dados.itens.firstOrNull { it.id == canalId } } ?: canais.firstOrNull()

    // A telinha só toca enquanto o app está na frente (e para antes de abrir a tela cheia)
    var preview by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) {
                preview = false
            }
            if (evento == Lifecycle.Event.ON_START) {
                preview = true
            }
        }
        lifecycle.addObserver(observador)
        onDispose { lifecycle.removeObserver(observador) }
    }

    // Guia de programação do canal escolhido
    var epg by remember { mutableStateOf<Estado<List<ProgramaEpg>>>(Estado.Carregando) }
    LaunchedEffect(canalAtual?.id) {
        val id = canalAtual?.id ?: return@LaunchedEffect
        epg = Estado.Carregando
        delay(250) // evita pedidos demais quando o usuário troca de canal rápido
        epg = carregar { api.epg(id) }
    }

    val focoSelecionado = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (ehTv(ctx)) {
            delay(500)
            try {
                focoSelecionado.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    fun mudarPasta(delta: Int) {
        val novo = (indice + delta + pastas.size) % pastas.size
        pastaId = pastas[novo].id
    }

    fun telaCheia(c: ItemConteudo) {
        // Para a telinha primeiro, para não usar duas conexões do servidor ao mesmo tempo
        preview = false
        escopo.launch {
            delay(350)
            tocar(
                ctx,
                api.urlLive(c.id, Preferencias.formatoLive(ctx, tema.formatoLive)),
                c.nome,
                TipoConteudo.LIVE,
                tema.userAgent
            )
        }
    }

    fun aoClicarCanal(c: ItemConteudo) {
        if (c.id == canalAtual?.id) telaCheia(c) else canalId = c.id
    }

    fun favoritar(c: ItemConteudo) {
        val adicionou = Favoritos.alternar(ctx, TipoConteudo.LIVE, c.id)
        Toast.makeText(
            ctx,
            if (adicionou) "⭐ ${c.nome} adicionado aos favoritos" else "${c.nome} removido dos favoritos",
            Toast.LENGTH_SHORT
        ).show()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val retrato = maxWidth < maxHeight

        if (retrato) {
            // Celular em pé: lista embaixo, telinha em cima (sem o guia, falta espaço)
            Column(Modifier.fillMaxSize()) {
                CabecalhoPasta(pasta.nome, { mudarPasta(-1) }, { mudarPasta(1) })
                PreviewCanal(
                    canal = canalAtual,
                    ativo = preview,
                    aoClicar = { canalAtual?.let { telaCheia(it) } },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                InfoCanal(canalAtual, epg, voltar, Modifier.fillMaxWidth())
                ListaCanais(
                    canais = canais,
                    selecionadoId = canalAtual?.id,
                    favIds = favIds,
                    aoClicar = { aoClicarCanal(it) },
                    aoFavoritar = { favoritar(it) },
                    focoSelecionado = focoSelecionado,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        } else {
            val larguraEsquerda = (maxWidth * 0.32f).coerceIn(200.dp, 340.dp)
            val alturaTopo = maxHeight * 0.46f
            val larguraPreview = minOf(alturaTopo * 16f / 9f, (maxWidth - larguraEsquerda) * 0.62f)

            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(larguraEsquerda).fillMaxHeight()) {
                    CabecalhoPasta(pasta.nome, { mudarPasta(-1) }, { mudarPasta(1) })
                    ListaCanais(
                        canais = canais,
                        selecionadoId = canalAtual?.id,
                        favIds = favIds,
                        aoClicar = { aoClicarCanal(it) },
                        aoFavoritar = { favoritar(it) },
                        focoSelecionado = focoSelecionado,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().height(alturaTopo)) {
                        PreviewCanal(
                            canal = canalAtual,
                            ativo = preview,
                            aoClicar = { canalAtual?.let { telaCheia(it) } },
                            modifier = Modifier.width(larguraPreview).fillMaxHeight()
                        )
                        InfoCanal(canalAtual, epg, voltar, Modifier.weight(1f).fillMaxHeight())
                    }
                    ListaEpg(epg, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** Faixa no topo da lista: ◀ nome da pasta ▶ (troca de pasta sem sair da tela). */
@Composable
private fun CabecalhoPasta(nome: String, anterior: () -> Unit, proxima: () -> Unit) {
    val tema = LocalTema.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(tema.corPrimaria),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CartaoFocavel(
            onClick = anterior,
            modifier = Modifier.width(48.dp).fillMaxHeight(),
            semFundo = true,
            centralizar = true,
            preenchimento = 0.dp,
            forma = RectangleShape
        ) {
            Text("◀", color = Color.White, fontSize = 18.sp)
        }
        Text(
            nome.uppercase(),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        CartaoFocavel(
            onClick = proxima,
            modifier = Modifier.width(48.dp).fillMaxHeight(),
            semFundo = true,
            centralizar = true,
            preenchimento = 0.dp,
            forma = RectangleShape
        ) {
            Text("▶", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ListaCanais(
    canais: List<ItemConteudo>,
    selecionadoId: String?,
    favIds: Set<String>,
    aoClicar: (ItemConteudo) -> Unit,
    aoFavoritar: (ItemConteudo) -> Unit,
    focoSelecionado: FocusRequester,
    modifier: Modifier
) {
    val tema = LocalTema.current
    val estado = rememberLazyListState()

    // Ao abrir, deixa o canal que está passando visível na lista
    LaunchedEffect(canais) {
        val i = canais.indexOfFirst { it.id == selecionadoId }
        if (i > 0) estado.scrollToItem(i)
    }

    if (canais.isEmpty()) {
        Box(modifier) { Aviso("Nenhum canal nesta pasta.") }
        return
    }

    LazyColumn(
        state = estado,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        itemsIndexed(canais, key = { i, c -> "${c.id}_$i" }) { _, c ->
            val selecionado = c.id == selecionadoId
            CartaoFocavel(
                onClick = { aoClicar(c) },
                onLongClick = { aoFavoritar(c) },
                selecionado = selecionado,
                preenchimento = 0.dp,
                forma = RectangleShape,
                alinhamento = Alignment.Start,
                modifier = if (selecionado) {
                    Modifier.fillMaxWidth().focusRequester(focoSelecionado)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        c.nome,
                        color = tema.corTexto,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (c.id in favIds) {
                        Icon(
                            Icons.Filled.Star, "Favorito",
                            tint = tema.corSelecionada,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCanal(
    canal: ItemConteudo?,
    ativo: Boolean,
    aoClicar: () -> Unit,
    modifier: Modifier
) {
    val ctx = LocalContext.current
    val tema = LocalTema.current
    val api = remember { Sessao.api(ctx) }
    val clique by rememberUpdatedState(aoClicar)

    CartaoFocavel(
        onClick = aoClicar,
        modifier = modifier,
        preenchimento = 0.dp,
        forma = RectangleShape
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            if (ativo && canal != null && api != null) {
                val url = api.urlLive(canal.id, Preferencias.formatoLive(ctx, tema.formatoLive))
                when (Preferencias.player(ctx, TipoConteudo.LIVE)) {
                    TipoPlayer.EXO -> PreviewExo(url, tema.userAgent, Modifier.fillMaxSize())
                    TipoPlayer.VLC -> PreviewVlc(url, tema.userAgent, Modifier.fillMaxSize())
                }
            }
            // Camada transparente: tocar na telinha abre em tela cheia
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { clique() }) }
            )
        }
    }
}

@Composable
private fun InfoCanal(
    canal: ItemConteudo?,
    epg: Estado<List<ProgramaEpg>>,
    voltar: () -> Unit,
    modifier: Modifier
) {
    val tema = LocalTema.current
    val programas = if (epg is Estado.Ok) epg.dados else emptyList()
    val atual = programas.firstOrNull()

    Column(modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TV AO VIVO",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(tema.corPrimaria).padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.weight(1f))
            BotaoVoltar(voltar)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            (canal?.nome ?: "").uppercase(),
            color = tema.corTexto,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (atual != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                atual.titulo,
                color = tema.corTexto.copy(alpha = 0.8f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${hora(atual.inicio)} - ${hora(atual.fim)}",
                color = tema.corTexto.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ListaEpg(epg: Estado<List<ProgramaEpg>>, modifier: Modifier) {
    Box(modifier.background(Color(0xCC000000))) {
        when (epg) {
            is Estado.Carregando -> Carregando()
            is Estado.Falha -> Aviso("Guia de programação indisponível.")
            is Estado.Ok -> {
                if (epg.dados.isEmpty()) {
                    Aviso("Este canal não tem guia de programação.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(epg.dados) { _, p -> ItemEpg(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemEpg(p: ProgramaEpg) {
    val tema = LocalTema.current
    // Focável para o controle remoto conseguir descer e rolar a lista
    CartaoFocavel(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        preenchimento = 0.dp,
        forma = RectangleShape,
        semFundo = true,
        alinhamento = Alignment.Start
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${hora(p.inicio)} - ${hora(p.fim)}",
                    color = tema.corSelecionada,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    p.titulo,
                    color = tema.corTexto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (p.descricao.isNotBlank()) {
                Text(
                    p.descricao,
                    color = tema.corTexto.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(color = tema.corTexto.copy(alpha = 0.12f))
    }
}
