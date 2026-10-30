package com.meuapp.iptv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.iptv.data.EntradaHistorico
import com.meuapp.iptv.data.Favoritos
import com.meuapp.iptv.data.Historico
import com.meuapp.iptv.data.ItemConteudo
import com.meuapp.iptv.data.Preferencias
import com.meuapp.iptv.data.Repositorio
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.tema.LocalTema
import kotlinx.coroutines.delay

/**
 * Tela de TV ao vivo / Filmes / Séries:
 * pastas em lista à esquerda (Favoritos, Todos e as categorias) e os itens à direita.
 */
@Composable
fun TelaConteudo(
    tipo: TipoConteudo,
    voltar: () -> Unit,
    abrirSerie: (ItemConteudo) -> Unit,
    abrirCanal: (String, ItemConteudo) -> Unit
) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val api = remember { Sessao.api(ctx) }
    var estado by remember { mutableStateOf<Estado<Repositorio.Dados>>(Estado.Carregando) }
    var tentativa by remember { mutableIntStateOf(0) }
    val focoVoltar = remember { FocusRequester() }

    LaunchedEffect(tentativa) {
        if (api == null) return@LaunchedEffect
        estado = Estado.Carregando
        estado = carregar { Repositorio.obter(ctx, api, tipo) }
    }

    Fundo(tema.fundoListas) {
        Column(Modifier.fillMaxSize()) {
            Cabecalho(tipo.titulo, voltar, focoVoltar)
            when (val e = estado) {
                is Estado.Carregando -> Carregando("Carregando ${tipo.titulo.lowercase()}...\nNa primeira vez pode demorar um pouco.")
                is Estado.Falha -> Erro(e.msg) { tentativa++ }
                is Estado.Ok -> ConteudoCarregado(tipo, e.dados, abrirSerie, abrirCanal, focoVoltar)
            }
        }
    }
}

@Composable
private fun ConteudoCarregado(
    tipo: TipoConteudo,
    dados: Repositorio.Dados,
    abrirSerie: (ItemConteudo) -> Unit,
    abrirCanal: (String, ItemConteudo) -> Unit,
    focoVoltar: FocusRequester
) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val api = remember { Sessao.api(ctx) }
    val focoPasta = remember { FocusRequester() }

    val versaoFav = Favoritos.versao.intValue
    val favIds = remember(versaoFav) { Favoritos.ids(ctx, tipo) }
    val pastas = remember(dados, favIds) { montarPastas(dados, favIds) }

    var selecionada by rememberSaveable { mutableStateOf("") }
    val atual = selecionada.ifBlank { pastas.getOrNull(2)?.id ?: PASTA_TODOS }
    var busca by rememberSaveable { mutableStateOf("") }

    val itens = remember(dados, atual, busca, favIds) {
        val base = itensDaPasta(dados, atual, favIds)
        val termo = busca.trim()
        if (termo.isEmpty()) base else base.filter { it.nome.contains(termo, ignoreCase = true) }
    }

    val estadoGrade = rememberLazyGridState()
    LaunchedEffect(atual) { estadoGrade.scrollToItem(0) }

    // Na TV, já deixa a pasta escolhida selecionada para o controle remoto
    LaunchedEffect(Unit) {
        if (ehTv(ctx)) {
            delay(400)
            try {
                focoPasta.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    fun abrir(item: ItemConteudo) {
        val a = api ?: return
        when (tipo) {
            TipoConteudo.LIVE -> abrirCanal(atual, item)
            TipoConteudo.FILMES -> {
                val entrada = EntradaHistorico(TipoConteudo.FILMES, item.id, item.nome, item.imagem, item.extensao)
                val salvo = Historico.entrada(ctx, entrada.chave)
                tocar(
                    ctx, a.urlFilme(item.id, item.extensao), item.nome, tipo, tema.userAgent,
                    entrada, salvo?.posicao ?: 0L
                )
            }
            TipoConteudo.SERIES -> abrirSerie(item)
        }
    }

    fun favoritar(item: ItemConteudo) {
        val adicionou = Favoritos.alternar(ctx, tipo, item.id)
        Toast.makeText(
            ctx,
            if (adicionou) "⭐ ${item.nome} adicionado aos favoritos" else "${item.nome} removido dos favoritos",
            Toast.LENGTH_SHORT
        ).show()
    }

    val ehLive = tipo == TipoConteudo.LIVE

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val larguraLista = if (maxWidth < 600.dp) maxWidth * 0.4f else 300.dp
        Row(Modifier.fillMaxSize()) {
            ListaPastas(
                pastas = pastas,
                selecionada = atual,
                aoSelecionar = { id ->
                    if (id != atual) busca = ""
                    selecionada = id
                },
                modifier = Modifier.width(larguraLista).fillMaxHeight(),
                focoSelecionada = focoPasta,
                aoCima = { focoVoltar.requestFocus() }
            )

            Column(Modifier.weight(1f).fillMaxHeight()) {
                BarraBusca(
                    texto = busca,
                    aoMudar = { busca = it },
                    rotulo = if (atual == PASTA_TODOS) "Pesquisar em todos" else "Pesquisar nesta pasta",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    aoCima = { focoVoltar.requestFocus() }
                )

                if (itens.isEmpty()) {
                    Aviso(
                        when {
                            busca.isNotBlank() -> "Nada encontrado para \"$busca\"."
                            atual == PASTA_FAVORITOS ->
                                "Você ainda não tem favoritos.\n\nPara favoritar, segure o dedo sobre um item " +
                                        "(no controle remoto: segure o botão OK ou aperte MENU)."
                            else -> "Esta pasta está vazia."
                        }
                    )
                } else {
                    LazyVerticalGrid(
                        state = estadoGrade,
                        columns = GridCells.Adaptive(if (ehLive) 130.dp else 110.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(itens, key = { i, item -> "${item.id}_$i" }) { _, item ->
                            CartaoItem(
                                item = item,
                                favorito = item.id in favIds,
                                ehLive = ehLive,
                                onClick = { abrir(item) },
                                onLongClick = { favoritar(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoItem(
    item: ItemConteudo,
    favorito: Boolean,
    ehLive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val tema = LocalTema.current
    CartaoFocavel(
        onClick = onClick,
        onLongClick = onLongClick,
        preenchimento = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (ehLive) 1.25f else 2f / 3f)
                .background(Color(0x55000000))
        ) {
            Capa(
                item.imagem,
                Modifier.fillMaxSize().padding(if (ehLive) 12.dp else 0.dp),
                if (ehLive) ContentScale.Fit else ContentScale.Crop
            )
            if (favorito) {
                Icon(
                    Icons.Filled.Star, "Favorito",
                    tint = tema.corSelecionada,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                )
            }
        }
        Text(
            item.nome,
            color = tema.corTexto,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)
        )
    }
}
