package com.meuapp.iptv.ui

import android.view.KeyEvent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.iptv.data.ItemConteudo
import com.meuapp.iptv.data.Repositorio
import com.meuapp.iptv.tema.LocalTema

/** Pastas especiais que aparecem antes das categorias do servidor. */
const val PASTA_FAVORITOS = "__favoritos"
const val PASTA_TODOS = "__todos"

data class Pasta(val id: String, val nome: String, val quantidade: Int)

/** Favoritos, Todos e depois as categorias do servidor (só as que têm itens). */
fun montarPastas(dados: Repositorio.Dados, favIds: Set<String>): List<Pasta> =
    listOf(
        Pasta(PASTA_FAVORITOS, "Favoritos", dados.itens.count { it.id in favIds }),
        Pasta(PASTA_TODOS, "Todos", dados.itens.size)
    ) + dados.categorias
        .map { Pasta(it.id, it.nome, dados.contagem[it.id] ?: 0) }
        .filter { it.quantidade > 0 }

fun itensDaPasta(dados: Repositorio.Dados, pastaId: String, favIds: Set<String>): List<ItemConteudo> =
    when (pastaId) {
        PASTA_FAVORITOS -> dados.itens.filter { it.id in favIds }
        PASTA_TODOS -> dados.itens
        else -> dados.itens.filter { it.categoriaId == pastaId }
    }

/**
 * Lista de pastas (categorias) no canto esquerdo, igual ao XCIPTV.
 * focoSelecionada: recebe o foco ao abrir (para controle remoto).
 * aoCima: chamado ao apertar a seta para cima no primeiro item (vai para o botão Voltar).
 */
@Composable
fun ListaPastas(
    pastas: List<Pasta>,
    selecionada: String,
    aoSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier,
    focoSelecionada: FocusRequester? = null,
    aoCima: (() -> Unit)? = null
) {
    val estado = rememberLazyListState()
    LazyColumn(state = estado, modifier = modifier.background(Color(0x88000000))) {
        itemsIndexed(pastas) { indice, p ->
            val eSelecionada = p.id == selecionada
            ItemPasta(
                pasta = p,
                selecionada = eSelecionada,
                foco = if (eSelecionada) focoSelecionada else null,
                aoCima = if (indice == 0) aoCima else null,
                onClick = { aoSelecionar(p.id) }
            )
        }
    }
}

@Composable
private fun ItemPasta(
    pasta: Pasta,
    selecionada: Boolean,
    foco: FocusRequester?,
    aoCima: (() -> Unit)?,
    onClick: () -> Unit
) {
    val tema = LocalTema.current
    val interacao = remember { MutableInteractionSource() }
    val focado by interacao.collectIsFocusedAsState()
    val corNome = if (selecionada) tema.corSelecionada else tema.corTexto

    val comFoco = if (foco != null) Modifier.focusRequester(foco) else Modifier
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

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(comFoco)
                .then(teclaCima)
                .background(if (focado) tema.corPrimaria.copy(alpha = 0.45f) else Color.Transparent)
                .border(2.dp, if (focado) tema.corSelecionada else Color.Transparent)
                .clickable(interactionSource = interacao, indication = LocalIndication.current, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (pasta.id) {
                PASTA_FAVORITOS -> {
                    Icon(Icons.Filled.Star, null, tint = tema.corSelecionada, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                PASTA_TODOS -> {
                    Icon(Icons.Filled.Search, null, tint = corNome, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
            }
            Text(
                pasta.nome.uppercase(),
                color = corNome,
                fontSize = 15.sp,
                fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text("(${pasta.quantidade})", color = tema.corTexto.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        HorizontalDivider(color = tema.corTexto.copy(alpha = 0.12f))
    }
}
