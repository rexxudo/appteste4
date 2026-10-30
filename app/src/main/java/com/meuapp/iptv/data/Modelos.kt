package com.meuapp.iptv.data

enum class TipoConteudo(val titulo: String) {
    LIVE("TV ao Vivo"),
    FILMES("Filmes"),
    SERIES("Séries")
}

enum class TipoPlayer(val titulo: String) {
    EXO("ExoPlayer"),
    VLC("VLC Player")
}

data class InfoUsuario(
    val status: String,
    val expira: String,
    val conexoesMax: String
)

data class Categoria(val id: String, val nome: String)

data class ItemConteudo(
    val id: String,
    val nome: String,
    val imagem: String,
    val extensao: String,
    val categoriaId: String,
    val tipo: TipoConteudo
)

data class Episodio(
    val id: String,
    val titulo: String,
    val numero: String,
    val extensao: String,
    val imagem: String
)

data class Temporada(val numero: String, val episodios: List<Episodio>)

data class InfoSerie(
    val nome: String,
    val capa: String,
    val sinopse: String,
    val temporadas: List<Temporada>
)

/** Um programa do guia de programação (EPG). Horários em milissegundos. */
data class ProgramaEpg(
    val titulo: String,
    val descricao: String,
    val inicio: Long,
    val fim: Long
)
