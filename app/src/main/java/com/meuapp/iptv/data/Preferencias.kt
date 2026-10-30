package com.meuapp.iptv.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

/** Configurações escolhidas pelo usuário (player e formato). */
object Preferencias {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("configuracoes", Context.MODE_PRIVATE)

    /** Padrão igual ao XCIPTV: TV ao vivo no ExoPlayer, filmes e séries no VLC. */
    fun player(ctx: Context, tipo: TipoConteudo): TipoPlayer {
        val padrao = if (tipo == TipoConteudo.LIVE) TipoPlayer.EXO else TipoPlayer.VLC
        val salvo = prefs(ctx).getString("player_${tipo.name}", null) ?: return padrao
        return TipoPlayer.entries.firstOrNull { it.name == salvo } ?: padrao
    }

    fun definirPlayer(ctx: Context, tipo: TipoConteudo, player: TipoPlayer) {
        prefs(ctx).edit().putString("player_${tipo.name}", player.name).apply()
    }

    /** "ts" (MPEG-TS) ou "m3u8" (HLS). */
    fun formatoLive(ctx: Context, padrao: String): String =
        prefs(ctx).getString("formato_live", null) ?: padrao

    fun definirFormatoLive(ctx: Context, formato: String) {
        prefs(ctx).edit().putString("formato_live", formato).apply()
    }

    /** Saída de áudio do VLC: "opensles" (padrão) ou "audiotrack". */
    fun saidaAudioVlc(ctx: Context): String =
        prefs(ctx).getString("saida_audio_vlc", null) ?: "opensles"

    fun definirSaidaAudioVlc(ctx: Context, saida: String) {
        prefs(ctx).edit().putString("saida_audio_vlc", saida).apply()
    }

    fun ultimaAtualizacao(ctx: Context): Long = prefs(ctx).getLong("ultima_atualizacao", 0L)

    fun definirUltimaAtualizacao(ctx: Context, quando: Long) {
        prefs(ctx).edit().putLong("ultima_atualizacao", quando).apply()
    }
}

/** Favoritos de cada tipo (canais, filmes, séries). */
object Favoritos {
    /** Muda sempre que um favorito é adicionado/removido, para a tela se atualizar. */
    val versao = mutableIntStateOf(0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("favoritos", Context.MODE_PRIVATE)

    fun ids(ctx: Context, tipo: TipoConteudo): Set<String> =
        prefs(ctx).getStringSet(tipo.name, emptySet())?.toSet() ?: emptySet()

    fun eFavorito(ctx: Context, tipo: TipoConteudo, id: String) = id in ids(ctx, tipo)

    /** Retorna true se adicionou, false se removeu. */
    fun alternar(ctx: Context, tipo: TipoConteudo, id: String): Boolean {
        val atual = ids(ctx, tipo).toMutableSet()
        val adicionou = if (id in atual) { atual.remove(id); false } else { atual.add(id); true }
        prefs(ctx).edit().putStringSet(tipo.name, atual).apply()
        versao.intValue++
        return adicionou
    }
}
