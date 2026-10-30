package com.meuapp.iptv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import coil.Coil
import coil.ImageLoader
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.TipoConteudo
import com.meuapp.iptv.data.XtreamApi
import com.meuapp.iptv.tema.CarregadorTema
import com.meuapp.iptv.tema.LocalTema
import com.meuapp.iptv.ui.PASTA_TODOS
import com.meuapp.iptv.ui.TelaCanais
import com.meuapp.iptv.ui.TelaConfiguracoes
import com.meuapp.iptv.ui.TelaContinuar
import com.meuapp.iptv.ui.TelaConteudo
import com.meuapp.iptv.ui.TelaHome
import com.meuapp.iptv.ui.TelaLogin
import com.meuapp.iptv.ui.TelaSerie

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Coil.setImageLoader(ImageLoader.Builder(applicationContext).allowHardware(false).build())
        val temaInicial = CarregadorTema.deJson(CarregadorTema.local(this))
        XtreamApi.userAgent = temaInicial.userAgent

        setContent {
            var tema by remember { mutableStateOf(temaInicial) }
            LaunchedEffect(Unit) {
                // Recarrega incluindo o tema remoto (se configurado no tema.json)
                val t = CarregadorTema.carregar(applicationContext)
                XtreamApi.userAgent = t.userAgent
                tema = t
            }
            CompositionLocalProvider(LocalTema provides tema) {
                MaterialTheme(colorScheme = darkColorScheme(primary = tema.corPrimaria)) {
                    Navegacao()
                }
            }
        }
    }
}

@Composable
private fun Navegacao() {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val inicio = remember { if (Sessao.temLogin(ctx)) "home" else "login" }

    fun sair() {
        Sessao.sair(ctx)
        nav.navigate("login") { popUpTo(0) { inclusive = true } }
    }

    NavHost(navController = nav, startDestination = inicio) {
        composable("login") {
            TelaLogin(aoEntrar = {
                nav.navigate("home") { popUpTo(0) { inclusive = true } }
            })
        }
        composable("home") {
            TelaHome(
                abrir = { tipo -> nav.navigate("conteudo/${tipo.name}") },
                abrirConfiguracoes = { nav.navigate("config") },
                abrirContinuar = { nav.navigate("continuar") },
                sair = { sair() }
            )
        }
        composable("continuar") {
            TelaContinuar(
                voltar = { nav.popBackStack() },
                abrirSerie = { e ->
                    val nome = Uri.encode(e.serieNome.ifBlank { "Série" })
                    nav.navigate("serie/${Uri.encode(e.serieId)}/$nome")
                }
            )
        }
        composable("config") {
            TelaConfiguracoes(voltar = { nav.popBackStack() }, sair = { sair() })
        }
        composable("conteudo/{tipo}") { entrada ->
            val tipo = TipoConteudo.valueOf(entrada.arguments?.getString("tipo") ?: "LIVE")
            TelaConteudo(
                tipo = tipo,
                voltar = { nav.popBackStack() },
                abrirSerie = { item ->
                    val nome = Uri.encode(item.nome.ifBlank { "Série" })
                    nav.navigate("serie/${Uri.encode(item.id)}/$nome")
                },
                abrirCanal = { pastaId, item ->
                    nav.navigate("canais/${Uri.encode(pastaId)}/${Uri.encode(item.id)}")
                }
            )
        }
        composable("canais/{pasta}/{canal}") { entrada ->
            val a = entrada.arguments
            TelaCanais(
                pastaInicial = a?.getString("pasta") ?: PASTA_TODOS,
                canalInicial = a?.getString("canal") ?: "",
                voltar = { nav.popBackStack() }
            )
        }
        composable("serie/{id}/{nome}") { entrada ->
            val a = entrada.arguments
            TelaSerie(
                serieId = a?.getString("id") ?: "",
                nome = a?.getString("nome") ?: "",
                voltar = { nav.popBackStack() }
            )
        }
    }
}
