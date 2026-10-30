package com.meuapp.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.iptv.data.EntradaHistorico
import com.meuapp.iptv.data.Favoritos
import com.meuapp.iptv.data.Historico
import com.meuapp.iptv.data.InfoSerie
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.data.formatarTempo
import com.meuapp.iptv.tema.LocalTema

@Composable
fun TelaSerie(serieId: String, nome: String, voltar: () -> Unit) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val api = remember { Sessao.api(ctx) }
    var estado by remember { mutableStateOf<Estado<InfoSerie>>(Estado.Carregando) }
    var tentativa by remember { mutableIntStateOf(0) }
    var temporadaSel by rememberSaveable { mutableStateOf(-1) }
    val lista = rememberLazyListState()
    val focoVoltar = remember { FocusRequester() }

    // Onde o usuário parou nesta série (atualiza sozinho quando volta do player)
    val versaoHistorico = Historico.versao.intValue
    val historico = remember(versaoHistorico) { Historico.entrada(ctx, Historico.chaveSerie(serieId)) }

    val versaoFav = Favoritos.versao.intValue
    val favorito = remember(versaoFav) { Favoritos.eFavorito(ctx, TipoConteudo.SERIES, serieId) }

    LaunchedEffect(tentativa) {
        if (api == null) return@LaunchedEffect
        estado = Estado.Carregando
        estado = carregar { api.serieInfo(serieId) }
    }

    Fundo(tema.fundoListas) {
        Column(Modifier.fillMaxSize()) {
            Cabecalho(nome, voltar, focoVoltar) {
                CartaoFocavel(
                    onClick = { Favoritos.alternar(ctx, TipoConteudo.SERIES, serieId) },
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        if (favorito) "★ Favorito" else "☆ Favoritar",
                        color = if (favorito) tema.corSelecionada else tema.corTexto,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
            when (val e = estado) {
                is Estado.Carregando -> Carregando()
                is Estado.Falha -> Erro(e.msg) { tentativa++ }
                is Estado.Ok -> {
                    val serie = e.dados
                    if (serie.temporadas.isEmpty()) {
                        Erro("Esta série ainda não tem episódios.") { tentativa++ }
                    } else {
                        // Na primeira vez, abre direto na temporada e no episódio onde parou
                        LaunchedEffect(serie) {
                            if (temporadaSel < 0) {
                                val idTemporada = serie.temporadas.indexOfFirst { t ->
                                    t.episodios.any { it.id == historico?.id }
                                }
                                temporadaSel = if (idTemporada >= 0) idTemporada else 0
                                if (idTemporada >= 0) {
                                    val idEpisodio = serie.temporadas[idTemporada].episodios
                                        .indexOfFirst { it.id == historico?.id }
                                    lista.scrollToItem(2 + idEpisodio)
                                }
                            }
                        }

                        val selecionada = temporadaSel.coerceIn(0, serie.temporadas.lastIndex)
                        val temporada = serie.temporadas[selecionada]

                        LazyColumn(
                            state = lista,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Row {
                                    Capa(
                                        serie.capa,
                                        Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        serie.sinopse.ifBlank { "Sem sinopse." },
                                        color = tema.corTexto.copy(alpha = 0.9f),
                                        fontSize = 14.sp,
                                        maxLines = 9,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(serie.temporadas) { i, t ->
                                        val sel = i == selecionada
                                        CartaoFocavel(onClick = { temporadaSel = i }) {
                                            Text(
                                                "Temporada ${t.numero}",
                                                color = if (sel) tema.corPrimaria else tema.corTexto,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            itemsIndexed(temporada.episodios) { _, ep ->
                                val retomar = historico != null && historico.id == ep.id
                                CartaoFocavel(
                                    onClick = {
                                        api?.let { a ->
                                            val entrada = EntradaHistorico(
                                                tipo = TipoConteudo.SERIES,
                                                id = ep.id,
                                                titulo = ep.titulo,
                                                imagem = ep.imagem.ifBlank { serie.capa },
                                                extensao = ep.extensao,
                                                serieId = serieId,
                                                serieNome = nome
                                            )
                                            val posicao = if (retomar) historico?.posicao ?: 0L else 0L
                                            tocar(
                                                ctx, a.urlEpisodio(ep.id, ep.extensao), ep.titulo,
                                                TipoConteudo.SERIES, tema.userAgent, entrada, posicao
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Capa(
                                            ep.imagem.ifBlank { serie.capa },
                                            Modifier.size(width = 120.dp, height = 68.dp).clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Episódio ${ep.numero}",
                                                color = tema.corPrimaria,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                ep.titulo,
                                                color = tema.corTexto,
                                                fontSize = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (retomar && historico != null) {
                                                Text(
                                                    "▶ Continuar em ${formatarTempo(historico.posicao)}",
                                                    color = tema.corSelecionada,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text("▶", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
