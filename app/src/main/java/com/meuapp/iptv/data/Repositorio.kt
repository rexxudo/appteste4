package com.meuapp.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda as listas de canais, filmes e séries no aparelho.
 * Assim o app abre rápido e só baixa tudo de novo quando o usuário toca em "Atualizar".
 */
object Repositorio {

    class Dados(val categorias: List<Categoria>, val itens: List<ItemConteudo>) {
        val contagem: Map<String, Int> = itens.groupingBy { it.categoriaId }.eachCount()
    }

    private val memoria = mutableMapOf<TipoConteudo, Dados>()
    private val trava = Mutex()

    private fun arquivo(ctx: Context, tipo: TipoConteudo) = File(ctx.filesDir, "conteudo_${tipo.name}.json")

    suspend fun obter(ctx: Context, api: XtreamApi, tipo: TipoConteudo, forcar: Boolean = false): Dados =
        trava.withLock {
            if (!forcar) {
                memoria[tipo]?.let { return it }
                lerDisco(ctx, tipo)?.let { memoria[tipo] = it; return it }
            }
            val categorias = api.categorias(tipo)
            val itens = api.itens(tipo, null)
            val dados = Dados(categorias, itens)
            salvarDisco(ctx, tipo, dados)
            memoria[tipo] = dados
            dados
        }

    /** Baixa tudo de novo (botão Atualizar). */
    suspend fun atualizarTudo(ctx: Context, api: XtreamApi, progresso: (String) -> Unit) {
        for (tipo in TipoConteudo.entries) {
            progresso("Atualizando ${tipo.titulo}...")
            obter(ctx, api, tipo, forcar = true)
        }
        Preferencias.definirUltimaAtualizacao(ctx, System.currentTimeMillis())
    }

    fun limpar(ctx: Context) {
        memoria.clear()
        TipoConteudo.entries.forEach { arquivo(ctx, it).delete() }
        Preferencias.definirUltimaAtualizacao(ctx, 0L)
    }

    private suspend fun salvarDisco(ctx: Context, tipo: TipoConteudo, d: Dados) = withContext(Dispatchers.IO) {
        try {
            val cats = JSONArray()
            d.categorias.forEach { cats.put(JSONObject().put("i", it.id).put("n", it.nome)) }
            val itens = JSONArray()
            d.itens.forEach {
                itens.put(
                    JSONObject().put("i", it.id).put("n", it.nome).put("m", it.imagem)
                        .put("e", it.extensao).put("c", it.categoriaId)
                )
            }
            arquivo(ctx, tipo).writeText(JSONObject().put("cats", cats).put("itens", itens).toString())
        } catch (_: Exception) {
        }
    }

    private suspend fun lerDisco(ctx: Context, tipo: TipoConteudo): Dados? = withContext(Dispatchers.IO) {
        try {
            val f = arquivo(ctx, tipo)
            if (!f.exists()) return@withContext null
            val j = JSONObject(f.readText())
            val cats = j.getJSONArray("cats")
            val itens = j.getJSONArray("itens")
            Dados(
                (0 until cats.length()).map {
                    val o = cats.getJSONObject(it)
                    Categoria(o.str("i"), o.str("n"))
                },
                (0 until itens.length()).map {
                    val o = itens.getJSONObject(it)
                    ItemConteudo(o.str("i"), o.str("n"), o.str("m"), o.str("e"), o.str("c"), tipo)
                }
            )
        } catch (_: Exception) {
            null
        }
    }
}
