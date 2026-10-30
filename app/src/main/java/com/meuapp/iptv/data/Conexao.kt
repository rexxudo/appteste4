package com.meuapp.iptv.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Procura, entre os servidores (DNS) da lista, o primeiro que aceita o usuário e a senha.
 * Testa até 5 ao mesmo tempo, cada um com tempo curto, e usa o que responder certo primeiro.
 * Se um servidor está fora do ar, ou responde mas não conhece o usuário, segue para os outros.
 */
object Conexao {

    sealed interface Resultado {
        data class Ok(val api: XtreamApi, val info: InfoUsuario) : Resultado
        /** Pelo menos um servidor respondeu e nenhum aceitou o usuário/senha (ou a conta está inativa). */
        data class Recusado(val msg: String) : Resultado
        /** Nenhum servidor respondeu. */
        data class Falha(val msg: String) : Resultado
    }

    private sealed interface Tentativa {
        data class Ok(val dns: String, val info: InfoUsuario) : Tentativa
        data object Recusada : Tentativa
        data class Inativa(val msg: String) : Tentativa
        data object Fora : Tentativa
    }

    private const val POR_LOTE = 5

    private suspend fun tentar(dns: String, usuario: String, senha: String): Tentativa =
        try {
            val api = XtreamApi(dns, usuario, senha, rapido = true)
            Tentativa.Ok(api.dns, api.login())
        } catch (e: CredenciaisInvalidas) {
            Tentativa.Recusada
        } catch (e: ContaInativa) {
            Tentativa.Inativa(e.message ?: "Conta inativa")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Tentativa.Fora
        }

    /**
     * preferido: o último DNS que funcionou neste aparelho (entra na frente da fila).
     * progresso: quantos servidores já responderam (para mostrar "2 de 5").
     */
    suspend fun conectar(
        servidores: List<String>,
        usuario: String,
        senha: String,
        preferido: String?,
        progresso: (Int, Int) -> Unit = { _, _ -> }
    ): Resultado {
        val lista = servidores.map { XtreamApi.normalizarDns(it) }.distinct()
        if (lista.isEmpty()) return Resultado.Falha("Nenhum servidor configurado.")

        val pref = preferido?.takeIf { it.isNotBlank() }?.let { XtreamApi.normalizarDns(it) }
        val ordem = if (pref != null && pref in lista) listOf(pref) + lista.filter { it != pref } else lista

        var feitos = 0
        var algumRespondeu = false
        progresso(0, ordem.size)

        for (lote in ordem.chunked(POR_LOTE)) {
            // Escopo próprio: quando um servidor aceita, não esperamos os que estão travados.
            val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val canal = Channel<Tentativa>(Channel.UNLIMITED)
            try {
                lote.forEach { dns -> escopo.launch { canal.send(tentar(dns, usuario, senha)) } }
                for (i in lote.indices) {
                    val t = canal.receive()
                    feitos++
                    progresso(feitos, ordem.size)
                    when (t) {
                        is Tentativa.Ok -> return Resultado.Ok(XtreamApi(t.dns, usuario, senha), t.info)
                        is Tentativa.Inativa -> return Resultado.Recusado(t.msg)
                        is Tentativa.Recusada -> algumRespondeu = true
                        is Tentativa.Fora -> {}
                    }
                }
            } finally {
                escopo.cancel()
            }
        }

        return if (algumRespondeu) {
            Resultado.Recusado("Usuário ou senha incorretos.")
        } else {
            Resultado.Falha("Não foi possível conectar. Verifique sua internet e tente de novo.")
        }
    }
}
