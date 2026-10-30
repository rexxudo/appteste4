package com.meuapp.iptv.tema

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.meuapp.iptv.data.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Todas as imagens e cores do app vêm daqui.
 * Os valores são lidos de app/src/main/assets/tema/tema.json.
 * Cada imagem pode ser o NOME de um arquivo dentro de assets/tema/ ou uma URL (http/https).
 */
data class Tema(
    val nomeApp: String = "Meu IPTV",
    val fundoLogin: String = "fundo_login.jpg",
    val fundoHome: String = "fundo_home.jpg",
    val fundoListas: String = "fundo_listas.jpg",
    val logo: String = "logo.png",
    val iconeTv: String = "icone_tv.png",
    val iconeFilmes: String = "icone_filmes.png",
    val iconeSeries: String = "icone_series.png",
    val iconeSair: String = "icone_sair.png",
    val iconeAtualizar: String = "icone_atualizar.png",
    val iconeConfig: String = "icone_config.png",
    val iconePlayer: String = "icone_player.png",
    val iconeContinuar: String = "icone_continuar.png",
    val iconeFormato: String = "icone_formato.png",
    val iconeConta: String = "icone_conta.png",
    val imagemPadrao: String = "sem_imagem.png",
    val corPrimaria: Color = Color(0xFFE50914),
    val corTexto: Color = Color.White,
    val corCartao: Color = Color(0xCC1E1E28),
    val corSelecionada: Color = Color(0xFFFFC107),
    val escurecerFundo: Float = 0.45f,
    val formatoLive: String = "ts",
    val userAgent: String = "IPTVSmartersPlayer",
    /** Lista de DNS (vem do painel). Vazia = o usuário digita o DNS na tela de login. */
    val servidores: List<String> = emptyList(),
    /** Aviso do painel para os clientes (ex.: manutenção). */
    val aviso: String = "",
    val temaRemotoUrl: String = ""
)

val LocalTema = compositionLocalOf { Tema() }

/** Converte o nome do arquivo do tema em algo que o carregador de imagens entende. */
fun imagemUri(nome: String): String =
    if (nome.startsWith("http://", true) || nome.startsWith("https://", true)) nome
    else "file:///android_asset/tema/$nome"

object CarregadorTema {

    private fun prefsCache(ctx: Context) = ctx.getSharedPreferences("tema_remoto", Context.MODE_PRIVATE)

    /** Só o tema.json de dentro do app. */
    private fun base(ctx: Context): JSONObject = try {
        JSONObject(ctx.assets.open("tema/tema.json").bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        JSONObject()
    }

    private fun mesclar(destino: JSONObject, origem: JSONObject) {
        origem.keys().forEach { k -> destino.put(k, origem.get(k)) }
    }

    /** tema.json + a última configuração remota guardada no aparelho (funciona sem internet). */
    fun local(ctx: Context): JSONObject {
        val json = base(ctx)
        val guardado = prefsCache(ctx).getString("json", null)
        if (guardado != null) {
            try {
                mesclar(json, JSONObject(guardado))
            } catch (_: Exception) {
            }
        }
        return json
    }

    /**
     * Baixa a configuração do painel ("tema_remoto_url"): imagens, cores, lista de DNS e aviso.
     * Se der certo, guarda no aparelho. Sem internet, usa a última guardada.
     */
    suspend fun carregar(ctx: Context): Tema {
        val json = base(ctx)
        val url = json.optString("tema_remoto_url", "")
        var baixou = false
        if (url.startsWith("http", true)) {
            try {
                val texto = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
                    XtreamApi.clienteRapido.newCall(req).execute().use { r ->
                        if (r.isSuccessful) r.body?.string() else null
                    }
                }
                if (texto != null) {
                    val remoto = JSONObject(texto)
                    mesclar(json, remoto)
                    prefsCache(ctx).edit().putString("json", remoto.toString()).apply()
                    baixou = true
                }
            } catch (_: Exception) {
                // Sem internet ou JSON inválido: usa o que já estava guardado.
            }
        }
        if (!baixou) {
            val guardado = prefsCache(ctx).getString("json", null)
            if (guardado != null) {
                try {
                    mesclar(json, JSONObject(guardado))
                } catch (_: Exception) {
                }
            }
        }
        return deJson(json)
    }

    /** Aceita ["http://a", ...] ou [{"dns": "http://a", "ativo": true}, ...]. */
    private fun listaServidores(j: JSONObject): List<String> {
        val arr = j.optJSONArray("servidores") ?: return emptyList()
        val saida = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            if (v is String) {
                saida.add(v)
            } else if (v is JSONObject) {
                if (v.optBoolean("ativo", true)) saida.add(v.optString("dns"))
            }
        }
        return saida.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun deJson(j: JSONObject): Tema {
        val p = Tema()
        fun s(chave: String, padrao: String): String =
            if (j.isNull(chave)) padrao else j.optString(chave, padrao).ifBlank { padrao }
        fun c(chave: String, padrao: Color): Color = try {
            Color(android.graphics.Color.parseColor(j.getString(chave)))
        } catch (e: Exception) {
            padrao
        }
        return Tema(
            nomeApp = s("nome_app", p.nomeApp),
            fundoLogin = s("fundo_login", p.fundoLogin),
            fundoHome = s("fundo_home", p.fundoHome),
            fundoListas = s("fundo_listas", p.fundoListas),
            logo = s("logo", p.logo),
            iconeTv = s("icone_tv", p.iconeTv),
            iconeFilmes = s("icone_filmes", p.iconeFilmes),
            iconeSeries = s("icone_series", p.iconeSeries),
            iconeSair = s("icone_sair", p.iconeSair),
            iconeAtualizar = s("icone_atualizar", p.iconeAtualizar),
            iconeConfig = s("icone_config", p.iconeConfig),
            iconePlayer = s("icone_player", p.iconePlayer),
            iconeContinuar = s("icone_continuar", p.iconeContinuar),
            iconeFormato = s("icone_formato", p.iconeFormato),
            iconeConta = s("icone_conta", p.iconeConta),
            imagemPadrao = s("imagem_padrao", p.imagemPadrao),
            corPrimaria = c("cor_primaria", p.corPrimaria),
            corTexto = c("cor_texto", p.corTexto),
            corCartao = c("cor_cartao", p.corCartao),
            corSelecionada = c("cor_selecionada", p.corSelecionada),
            escurecerFundo = j.optDouble("escurecer_fundo", p.escurecerFundo.toDouble())
                .toFloat().coerceIn(0f, 1f),
            formatoLive = s("formato_live", p.formatoLive),
            userAgent = s("user_agent", p.userAgent),
            servidores = listaServidores(j),
            aviso = if (j.isNull("aviso")) "" else j.optString("aviso", "").trim(),
            temaRemotoUrl = if (j.isNull("tema_remoto_url")) "" else j.optString("tema_remoto_url", "").trim()
        )
    }
}
