package com.meuapp.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.iptv.data.Conexao
import com.meuapp.iptv.data.Sessao
import com.meuapp.iptv.data.XtreamApi
import com.meuapp.iptv.data.mensagemErro
import com.meuapp.iptv.tema.CarregadorTema
import com.meuapp.iptv.tema.LocalTema
import kotlinx.coroutines.launch

@Composable
fun TelaLogin(aoEntrar: () -> Unit) {
    val tema = LocalTema.current
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val salvo = remember { Sessao.ultimoLogin(ctx) }

    // Com painel configurado, o cliente só digita usuário e senha (o app acha o servidor sozinho)
    val usaPainel = tema.servidores.isNotEmpty() || tema.temaRemotoUrl.isNotBlank()

    var dns by rememberSaveable { mutableStateOf(salvo.first) }
    var usuario by rememberSaveable { mutableStateOf(salvo.second) }
    var senha by rememberSaveable { mutableStateOf(salvo.third) }
    var mostrarSenha by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var andamento by remember { mutableStateOf<String?>(null) }

    fun entrar() {
        if (carregando) return
        if ((!usaPainel && dns.isBlank()) || usuario.isBlank() || senha.isBlank()) {
            erro = if (usaPainel) "Preencha o usuário e a senha." else "Preencha o DNS, o usuário e a senha."
            return
        }
        escopo.launch {
            carregando = true
            erro = null
            andamento = null
            try {
                if (usaPainel) {
                    var lista = tema.servidores
                    if (lista.isEmpty()) {
                        // A lista ainda não chegou: tenta buscar agora
                        lista = CarregadorTema.carregar(ctx).servidores
                    }
                    if (lista.isEmpty()) {
                        erro = "Não foi possível obter a lista de servidores. Verifique sua internet."
                    } else {
                        val preferido = salvo.first.ifBlank { null }
                        val r = Conexao.conectar(lista, usuario.trim(), senha.trim(), preferido) { feitos, total ->
                            andamento = if (total > 1) "Procurando servidor… $feitos de $total" else "Conectando…"
                        }
                        when (r) {
                            is Conexao.Resultado.Ok -> {
                                Sessao.entrar(ctx, r.api, r.info)
                                aoEntrar()
                            }
                            is Conexao.Resultado.Recusado -> erro = r.msg
                            is Conexao.Resultado.Falha -> erro = r.msg
                        }
                    }
                } else {
                    val api = XtreamApi(dns, usuario.trim(), senha.trim())
                    val info = api.login()
                    Sessao.entrar(ctx, api, info)
                    aoEntrar()
                }
            } catch (e: Exception) {
                erro = mensagemErro(e)
            } finally {
                carregando = false
                andamento = null
            }
        }
    }

    Fundo(tema.fundoLogin) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ImagemTema(tema.logo, Modifier.height(110.dp).widthIn(max = 320.dp), descricao = tema.nomeApp)
            Spacer(Modifier.height(16.dp))

            if (tema.aviso.isNotBlank()) {
                Text(
                    tema.aviso,
                    color = tema.corSelecionada,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 440.dp).padding(bottom = 12.dp)
                )
            }

            Column(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(tema.corCartao)
                    .padding(20.dp)
            ) {
                if (!usaPainel) {
                    OutlinedTextField(
                        value = dns,
                        onValueChange = { dns = it },
                        label = { Text("DNS / URL do servidor") },
                        placeholder = { Text("http://servidor.com:8080") },
                        singleLine = true,
                        colors = coresCampo(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    label = { Text("Usuário") },
                    singleLine = true,
                    colors = coresCampo(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = senha,
                    onValueChange = { senha = it },
                    label = { Text("Senha") },
                    singleLine = true,
                    colors = coresCampo(),
                    visualTransformation = if (mostrarSenha) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { entrar() }),
                    trailingIcon = {
                        TextButton(onClick = { mostrarSenha = !mostrarSenha }) {
                            Text(if (mostrarSenha) "Ocultar" else "Ver", color = tema.corTexto)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                erro?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
                andamento?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = tema.corTexto.copy(alpha = 0.8f), fontSize = 14.sp)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { entrar() },
                    enabled = !carregando,
                    colors = ButtonDefaults.buttonColors(containerColor = tema.corPrimaria),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (carregando) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text("ENTRAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
