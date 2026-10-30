package com.meuapp.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Cliente da API padrão "Xtream Codes" (player_api.php), usada pela maioria dos painéis IPTV.
 * Login = DNS + usuário + senha.
 */
class XtreamApi(
    dns: String,
    val usuario: String,
    val senha: String,
    /** true = tempos curtos (para testar vários servidores rápido). */
    private val rapido: Boolean = false
) {

    val dns: String = normalizarDns(dns)

    companion object {
        /** Definido a partir do tema.json ("user_agent"). */
        var userAgent: String = "IPTVSmartersPlayer"

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        /** Usado para testar servidores: desiste logo se o DNS não responde. */
        val clienteRapido: OkHttpClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        fun normalizarDns(entrada: String): String {
            var d = entrada.trim().trimEnd('/')
            if (d.endsWith("/player_api.php")) d = d.removeSuffix("/player_api.php")
            if (!d.startsWith("http://", true) && !d.startsWith("https://", true)) d = "http://$d"
            return d
        }
    }

    private suspend fun chamar(params: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val base = "$dns/player_api.php".toHttpUrlOrNull()
                ?: throw IOException("DNS inválido: $dns")
            val url = base.newBuilder()
                .addQueryParameter("username", usuario)
                .addQueryParameter("password", senha)
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
            (if (rapido) clienteRapido else client).newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Servidor respondeu com erro HTTP ${resp.code}")
                resp.body?.string() ?: throw IOException("Resposta vazia do servidor")
            }
        }

    suspend fun login(): InfoUsuario {
        val texto = chamar()
        val json = try {
            JSONObject(texto)
        } catch (e: Exception) {
            throw IOException("Resposta inválida do servidor. Verifique o DNS.")
        }
        val u = json.optJSONObject("user_info") ?: throw CredenciaisInvalidas("Usuário ou senha incorretos")
        if (u.optInt("auth", 0) != 1) throw CredenciaisInvalidas("Usuário ou senha incorretos")
        val status = u.str("status")
        if (status.isNotBlank() && !status.equals("Active", true)) {
            throw ContaInativa("Conta com status: $status")
        }
        return InfoUsuario(status, formatarData(u.str("exp_date")), u.str("max_connections"))
    }

    suspend fun categorias(tipo: TipoConteudo): List<Categoria> {
        val acao = when (tipo) {
            TipoConteudo.LIVE -> "get_live_categories"
            TipoConteudo.FILMES -> "get_vod_categories"
            TipoConteudo.SERIES -> "get_series_categories"
        }
        val arr = paraArray(chamar(mapOf("action" to acao)))
        return objetos(arr).map { Categoria(it.str("category_id"), it.str("category_name")) }
    }

    /** categoriaId = null traz todos os itens. */
    suspend fun itens(tipo: TipoConteudo, categoriaId: String?): List<ItemConteudo> {
        val acao = when (tipo) {
            TipoConteudo.LIVE -> "get_live_streams"
            TipoConteudo.FILMES -> "get_vod_streams"
            TipoConteudo.SERIES -> "get_series"
        }
        val p = mutableMapOf("action" to acao)
        if (categoriaId != null) p["category_id"] = categoriaId
        val arr = paraArray(chamar(p))
        return objetos(arr).map { o ->
            when (tipo) {
                TipoConteudo.SERIES -> ItemConteudo(
                    o.str("series_id"), o.str("name"), o.str("cover"), "", o.str("category_id"), tipo
                )
                else -> ItemConteudo(
                    o.str("stream_id"), o.str("name"), o.str("stream_icon"),
                    o.str("container_extension").ifBlank { if (tipo == TipoConteudo.LIVE) "ts" else "mp4" },
                    o.str("category_id"),
                    tipo
                )
            }
        }
    }

    suspend fun serieInfo(id: String): InfoSerie {
        val o = try {
            JSONObject(chamar(mapOf("action" to "get_series_info", "series_id" to id)))
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Não foi possível carregar a série")
        }
        val info = o.optJSONObject("info") ?: JSONObject()
        val temporadas = mutableListOf<Temporada>()

        fun lerLista(numero: String, arr: JSONArray) {
            val lista = objetos(arr).map { e ->
                Episodio(
                    id = e.str("id"),
                    titulo = e.str("title"),
                    numero = e.str("episode_num"),
                    extensao = e.str("container_extension").ifBlank { "mp4" },
                    imagem = e.optJSONObject("info")?.str("movie_image") ?: ""
                )
            }
            if (lista.isNotEmpty()) temporadas.add(Temporada(numero, lista))
        }

        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { lerLista(k, it) } }
            is JSONArray -> for (i in 0 until eps.length()) {
                eps.optJSONArray(i)?.let { arr ->
                    val numero = arr.optJSONObject(0)?.str("season")?.ifBlank { null } ?: "${i + 1}"
                    lerLista(numero, arr)
                }
            }
        }
        temporadas.sortBy { it.numero.toIntOrNull() ?: 0 }
        return InfoSerie(info.str("name"), info.str("cover"), info.str("plot"), temporadas)
    }

    /** Guia de programação (EPG) de um canal. Lista vazia se o canal não tiver guia. */
    suspend fun epg(streamId: String): List<ProgramaEpg> {
        val texto = chamar(mapOf("action" to "get_short_epg", "stream_id" to streamId, "limit" to "20"))
        val json = try {
            JSONObject(texto)
        } catch (e: Exception) {
            return emptyList()
        }
        val arr = json.optJSONArray("epg_listings") ?: return emptyList()
        val agora = System.currentTimeMillis()
        val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val lista = mutableListOf<ProgramaEpg>()
        for (o in objetos(arr)) {
            var inicio = o.str("start_timestamp").toLongOrNull()?.times(1000L)
            var fim = o.str("stop_timestamp").toLongOrNull()?.times(1000L)
            if (inicio == null) inicio = try { formato.parse(o.str("start"))?.time } catch (e: Exception) { null }
            if (fim == null) fim = try { formato.parse(o.str("end"))?.time } catch (e: Exception) { null }
            if (inicio == null || fim == null) continue
            if (fim <= agora) continue
            lista.add(
                ProgramaEpg(
                    decodificar(o.str("title")),
                    decodificar(o.str("description")),
                    inicio,
                    fim
                )
            )
        }
        return lista.sortedBy { it.inicio }
    }

    /** Os painéis mandam título e descrição em Base64. Se não for Base64, usa o texto como veio. */
    private fun decodificar(s: String): String {
        if (s.isBlank()) return ""
        return try {
            val bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT)
            val d = String(bytes, Charsets.UTF_8)
            val estranho = d.any { it == '\uFFFD' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }
            if (estranho) s else d.trim()
        } catch (e: IllegalArgumentException) {
            s
        }
    }

    fun urlLive(id: String, formato: String) = "$dns/live/$usuario/$senha/$id.$formato"
    fun urlFilme(id: String, ext: String) = "$dns/movie/$usuario/$senha/$id.$ext"
    fun urlEpisodio(id: String, ext: String) = "$dns/series/$usuario/$senha/$id.$ext"

    private fun paraArray(texto: String): JSONArray =
        try { JSONArray(texto) } catch (e: Exception) { JSONArray() }

    private fun objetos(arr: JSONArray): List<JSONObject> =
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

    private fun formatarData(ts: String): String {
        val s = ts.toLongOrNull() ?: return "Ilimitado"
        return SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(s * 1000))
    }
}

/** O servidor respondeu, mas não reconhece esse usuário/senha. */
class CredenciaisInvalidas(msg: String) : IOException(msg)

/** O servidor reconhece o usuário, mas a conta não está ativa (vencida, bloqueada...). */
class ContaInativa(msg: String) : IOException(msg)

/** Lê uma string do JSON tratando null como vazio. */
fun JSONObject.str(chave: String): String {
    if (isNull(chave)) return ""
    return optString(chave, "")
}

fun mensagemErro(e: Throwable): String = when (e) {
    is UnknownHostException -> "Servidor não encontrado. Verifique o DNS."
    is SocketTimeoutException -> "O servidor demorou para responder. Tente novamente."
    is java.net.ConnectException -> "Não foi possível conectar ao servidor. Verifique o DNS e a porta."
    else -> e.message ?: "Erro desconhecido"
}
