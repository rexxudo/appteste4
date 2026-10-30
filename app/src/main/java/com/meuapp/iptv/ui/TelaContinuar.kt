package com.meuapp.iptv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.iptv.data.EntradaHistorico
import com.meuapp.iptv.data.Historico
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.data.formatarTempo
import com.meuapp.iptv.tema.LocalTema

/**
 * Continuar assistindo: filmes e séries que o usuário começou.
 * Filme: abre o vídeo direto, de onde parou.
 * Série: abre a página da série, já na temporada e no episódio onde parou.
 * Segurar o dedo (ou OK/MENU no controle) remove da lista.
 */
@Composable
fun TelaContinuar(voltar: () -> Unit, abrirSerie: (EntradaHistorico) -> Unit) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val api = remember { Sessao.api(ctx) }
    val versao = Historico.versao.intValue
    val itens = remember(versao) { Historico.lista(ctx) }
    val focoVoltar = remember { FocusRequester() }

    fun abrir(e: EntradaHistorico) {
        val a = api ?: return
        if (e.tipo == TipoConteudo.SERIES) {
            abrirSerie(e)
        } else {
            tocar(ctx, a.urlFilme(e.id, e.extensao), e.titulo, TipoConteudo.FILMES, tema.userAgent, e, e.posicao)
        }
    }

    fun remover(e: EntradaHistorico) {
        Historico.remover(ctx, e.chave)
        Toast.makeText(ctx, "Removido de continuar assistindo", Toast.LENGTH_SHORT).show()
    }

    Fundo(tema.fundoListas) {
        Column(Modifier.fillMaxSize()) {
            Cabecalho("Continuar assistindo", voltar, focoVoltar)
            if (itens.isEmpty()) {
                Aviso(
                    "Nada por aqui ainda.\n\nOs filmes e séries que você começar a assistir " +
                            "aparecem aqui para continuar de onde parou."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(itens, key = { _, e -> e.chave }) { _, e ->
                        CartaoContinuar(e, onClick = { abrir(e) }, onLongClick = { remover(e) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoContinuar(e: EntradaHistorico, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tema = LocalTema.current
    val serie = e.tipo == TipoConteudo.SERIES

    CartaoFocavel(
        onClick = onClick,
        onLongClick = onLongClick,
        preenchimento = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0x55000000))
        ) {
            Capa(e.imagem, Modifier.fillMaxSize(), ContentScale.Crop)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x88FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(e.progresso)
                        .fillMaxHeight()
                        .background(tema.corPrimaria)
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                if (serie) e.serieNome else e.titulo,
                color = tema.corTexto,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (serie) e.titulo.ifBlank { "Série" } else if (e.duracao > 0) "de ${formatarTempo(e.duracao)}" else "Filme",
                color = tema.corTexto.copy(alpha = 0.75f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Parou em ${formatarTempo(e.posicao)}",
                color = tema.corSelecionada,
                fontSize = 12.sp
            )
        }
    }
}
