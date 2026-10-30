package com.meuapp.iptv.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Um filme ou série que o usuário começou a assistir.
 * Para série, guarda o episódio onde parou (uma entrada por série).
 */
data class EntradaHistorico(
    val tipo: TipoConteudo,
    val id: String,
    val titulo: String,
    val imagem: String,
    val extensao: String,
    val serieId: String = "",
    val serieNome: String = "",
    val posicao: Long = 0L,
    val duracao: Long = 0L,
    val quando: Long = 0L
) {
    val chave: String
        get() = if (tipo == TipoConteudo.SERIES) "S$serieId" else "F$id"

    val progresso: Float
        get() = if (duracao > 0) (posicao.toFloat() / duracao.toFloat()).coerceIn(0f, 1f) else 0f
}

fun formatarTempo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val seg = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%02d:%02d".format(m, seg)
}

/** Lista de "Continuar assistindo", guardada no aparelho. */
object Historico {
    /** Muda sempre que a lista muda, para as telas se atualizarem. */
    val versao = mutableIntStateOf(0)

    private const val MAXIMO = 40
    private const val MINIMO_MS = 10_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("historico", Context.MODE_PRIVATE)

    fun chaveFilme(id: String) = "F$id"
    fun chaveSerie(id: String) = "S$id"

    /** Mais recente primeiro. */
    fun lista(ctx: Context): List<EntradaHistorico> {
        val texto = prefs(ctx).getString("lista", null) ?: return emptyList()
        return try {
            val arr = JSONArray(texto)
            (0 until arr.length())
                .mapNotNull { deJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.quando }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun entrada(ctx: Context, chave: String): EntradaHistorico? =
        lista(ctx).firstOrNull { it.chave == chave }

    /**
     * Guarda o ponto onde o usuário parou.
     * Se já viu 95% ou mais, tira da lista (terminou). Menos de 10 segundos é ignorado.
     */
    fun salvar(ctx: Context, e: EntradaHistorico) {
        val terminou = e.duracao > 0 && e.posicao >= e.duracao * 95 / 100
        if (terminou) {
            remover(ctx, e.chave)
            return
        }
        if (e.posicao < MINIMO_MS) return
        val nova = e.copy(quando = System.currentTimeMillis())
        val resto = lista(ctx).filter { it.chave != nova.chave }
        gravar(ctx, (listOf(nova) + resto).take(MAXIMO))
    }

    fun remover(ctx: Context, chave: String) {
        val atual = lista(ctx)
        if (atual.none { it.chave == chave }) return
        gravar(ctx, atual.filter { it.chave != chave })
    }

    private fun gravar(ctx: Context, itens: List<EntradaHistorico>) {
        val arr = JSONArray()
        itens.forEach { arr.put(paraObjeto(it)) }
        prefs(ctx).edit().putString("lista", arr.toString()).apply()
        versao.intValue++
    }

    fun paraJson(e: EntradaHistorico): String = paraObjeto(e).toString()

    fun deJson(texto: String): EntradaHistorico? =
        try {
            deJson(JSONObject(texto))
        } catch (e: Exception) {
            null
        }

    private fun paraObjeto(e: EntradaHistorico): JSONObject =
        JSONObject()
            .put("t", e.tipo.name)
            .put("i", e.id)
            .put("n", e.titulo)
            .put("m", e.imagem)
            .put("e", e.extensao)
            .put("si", e.serieId)
            .put("sn", e.serieNome)
            .put("p", e.posicao)
            .put("d", e.duracao)
            .put("q", e.quando)

    private fun deJson(o: JSONObject): EntradaHistorico? {
        val tipo = TipoConteudo.entries.firstOrNull { it.name == o.optString("t") } ?: return null
        return EntradaHistorico(
            tipo = tipo,
            id = o.optString("i"),
            titulo = o.optString("n"),
            imagem = o.optString("m"),
            extensao = o.optString("e"),
            serieId = o.optString("si"),
            serieNome = o.optString("sn"),
            posicao = o.optLong("p"),
            duracao = o.optLong("d"),
            quando = o.optLong("q")
        )
    }
}
