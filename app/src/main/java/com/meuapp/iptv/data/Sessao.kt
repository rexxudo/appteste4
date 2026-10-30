package com.meuapp.iptv.data

import android.content.Context

/** Guarda o login no aparelho para não precisar digitar toda vez. */
object Sessao {
    private const val ARQUIVO = "login_iptv"
    private var apiAtual: XtreamApi? = null
    var infoUsuario: InfoUsuario? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    fun entrar(ctx: Context, api: XtreamApi, info: InfoUsuario) {
        prefs(ctx).edit()
            .putString("dns", api.dns)
            .putString("usuario", api.usuario)
            .putString("senha", api.senha)
            .putBoolean("logado", true)
            .apply()
        Repositorio.limpar(ctx)
        apiAtual = api
        infoUsuario = info
    }

    /** Troca só o servidor (DNS), mantendo o mesmo login e as listas guardadas. */
    fun trocarServidor(ctx: Context, api: XtreamApi, info: InfoUsuario) {
        prefs(ctx).edit().putString("dns", api.dns).apply()
        apiAtual = api
        infoUsuario = info
    }

    /**
     * O servidor salvo caiu: procura outro da lista que aceite o mesmo usuário e senha.
     * Retorna true se achou e já passou a usar o novo.
     */
    suspend fun reconectar(ctx: Context, servidores: List<String>): Boolean {
        if (!temLogin(ctx) || servidores.isEmpty()) return false
        val (dns, usuario, senha) = ultimoLogin(ctx)
        if (usuario.isBlank()) return false
        val r = Conexao.conectar(servidores, usuario, senha, dns)
        if (r is Conexao.Resultado.Ok) {
            trocarServidor(ctx, r.api, r.info)
            return true
        }
        return false
    }

    fun temLogin(ctx: Context): Boolean = prefs(ctx).getBoolean("logado", false)

    /** Retorna dns, usuário e senha usados por último (para preencher o formulário). */
    fun ultimoLogin(ctx: Context): Triple<String, String, String> {
        val p = prefs(ctx)
        return Triple(
            p.getString("dns", "") ?: "",
            p.getString("usuario", "") ?: "",
            p.getString("senha", "") ?: ""
        )
    }

    fun api(ctx: Context): XtreamApi? {
        apiAtual?.let { return it }
        if (!temLogin(ctx)) return null
        val (dns, usuario, senha) = ultimoLogin(ctx)
        if (dns.isBlank() || usuario.isBlank()) return null
        return XtreamApi(dns, usuario, senha).also { apiAtual = it }
    }

    fun sair(ctx: Context) {
        prefs(ctx).edit().putBoolean("logado", false).apply()
        Repositorio.limpar(ctx)
        apiAtual = null
        infoUsuario = null
    }
}
