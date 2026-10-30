# Meu IPTV — App Android

App IPTV para celular, tablet e Android TV / TV Box.
O login é feito com **DNS + usuário + senha** (padrão Xtream Codes, o mesmo do IPTV Smarters).

**Recursos:**
- TV ao vivo, filmes e séries, com as pastas em lista à esquerda (estilo XCIPTV) e a quantidade de itens em cada uma
- Ao abrir um canal: lista de canais à esquerda, telinha ao vivo em cima e **guia de programação** embaixo
  (toque de novo no canal, ou na telinha, para tela cheia; as setas ◀ ▶ no topo da lista trocam de pasta)
- **Continuar assistindo** (4º quadrado da tela inicial): filmes e séries que você começou. Filme abre de onde parou;
  série abre na temporada e no episódio onde parou. Segure o dedo (ou OK/MENU) para remover da lista
- **Painel de servidores**: o DNS deixa de ser digitado. O app busca a lista num painel gratuito (Cloudflare), testa os DNS
  com o usuário e a senha do cliente e usa o primeiro que aceitar; se um cair, troca sozinho. Veja `painel/PAINEL-LEIA-ME.md`
- Pasta **Favoritos**: segure o dedo sobre um item (no controle remoto: segure OK ou aperte MENU)
- Pasta **Todos** com lupa para pesquisar em todas as pastas
- Botão **Atualizar** na tela inicial (baixa as listas de novo; o ícone gira enquanto atualiza)
- **Configurações**: escolher o player (ExoPlayer ou VLC) para TV, filmes e séries, e o formato da TV ao vivo (MPEG-TS ou HLS)
- Login salvo, funciona com controle remoto de TV
- Todas as imagens e cores trocáveis sem mexer em código
Não é preciso instalar o Android Studio: o APK é gerado online pelo GitHub (seção 1).

---

## 1. Como gerar o APK (sem instalar nada, pelo navegador)

O APK é compilado de graça nos servidores do GitHub. Você só usa o navegador.

1. Crie uma conta gratuita em https://github.com (se ainda não tiver).
2. Clique em **+** (canto superior direito) → **New repository**.
   Dê um nome (ex.: `meu-iptv`), marque **Private** e clique em **Create repository**.
3. Na página do repositório, clique no link **uploading an existing file**.
4. Descompacte o `MeuIPTV.zip` no computador, abra a pasta `MeuIPTV`, **selecione tudo que está dentro dela**
   (as pastas `app`, `gradle`, `.github` e os arquivos) e arraste para a página do GitHub.
   Clique em **Commit changes**.
   > Se a pasta `.github` não aparecer (ela é "oculta"), veja o passo 4b.
5. Abra a aba **Actions**. O processo **Gerar APK** começa sozinho e leva cerca de 5 minutos.
   Quando ficar com o ✓ verde, clique nele e, lá embaixo em **Artifacts**, baixe **MeuIPTV-apk**.
6. Dentro desse zip está o `app-debug.apk`. Passe para o celular/TV Box e instale
   (autorize "fontes desconhecidas").

**4b. Se a pasta `.github` não foi enviada:** no repositório, clique em **Add file → Create new file**,
digite o nome `.github/workflows/gerar-apk.yml`, cole o conteúdo do arquivo de mesmo nome que está no zip
e clique em **Commit changes**.

**Para enviar uma versão nova do projeto:** clique em **Add file → Upload files** na página inicial do
repositório e arraste de novo o conteúdo da pasta `MeuIPTV` (os arquivos com o mesmo nome são substituídos).
A aba **Actions** gera o APK novo sozinha.

**Para trocar uma imagem depois:** entre na pasta `app/src/main/assets/tema/` no site do GitHub,
clique em **Add file → Upload files**, envie a imagem nova com o mesmo nome e confirme.
Um APK novo é gerado automaticamente na aba **Actions**.

---

## 2. Trocar imagens (o mais importante)

**Todas as imagens ficam numa única pasta:**

```
app/src/main/assets/tema/
```

Basta **substituir o arquivo mantendo o mesmo nome** e gerar o APK de novo.

| Arquivo              | Onde aparece                             | Tamanho sugerido |
|----------------------|------------------------------------------|------------------|
| `fundo_login.jpg`    | Fundo da tela de login                   | 1920×1080        |
| `fundo_home.jpg`     | Fundo do menu principal                  | 1920×1080        |
| `fundo_listas.jpg`   | Fundo das categorias, canais, filmes     | 1920×1080        |
| `logo.png`           | Logo no login, menu e cabeçalhos         | 900×300 (fundo transparente) |
| `icone_tv.png`       | Botão "TV ao Vivo"                       | 512×512 (transparente) |
| `icone_filmes.png`   | Botão "Filmes"                           | 512×512 (transparente) |
| `icone_series.png`   | Botão "Séries"                           | 512×512 (transparente) |
| `icone_sair.png`     | Botão de sair                            | 512×512 (transparente) |
| `icone_atualizar.png`| Botão Atualizar (gira enquanto atualiza) | 512×512 (transparente) |
| `icone_config.png`   | Botão Configurações (tela inicial)       | 512×512 (transparente) |
| `icone_continuar.png`| Quadrado "Continuar assistindo"          | 512×512 (transparente) |
| `icone_player.png`   | Quadrado "Player" em Configurações       | 512×512 (transparente) |
| `icone_formato.png`  | Quadrado "Formato TV" em Configurações   | 512×512 (transparente) |
| `icone_conta.png`    | Quadrado "Conta" em Configurações        | 512×512 (transparente) |
| `sem_imagem.png`     | Usada quando o canal/filme não tem capa  | 400×600          |

Quer usar outro nome ou formato (ex.: `fundo.png`, `.webp`)? Coloque o arquivo na pasta e altere o nome no `tema.json`.

### Usar imagem da internet
No `tema.json`, em vez do nome do arquivo, coloque a URL:

```json
"fundo_login": "https://meusite.com/imagens/fundo.jpg"
```

---

## 3. Arquivo `tema.json` (cores e configurações)

Fica na mesma pasta: `app/src/main/assets/tema/tema.json`

| Chave             | O que faz |
|-------------------|-----------|
| `cor_primaria`    | Cor de destaque (botões, borda de seleção). Ex.: `#E50914` |
| `cor_texto`       | Cor dos textos |
| `cor_selecionada` | Cor da pasta escolhida e da borda do item focado (padrão amarelo) |
| `cor_cartao`      | Cor dos cartões. Formato `#AARRGGBB` (AA = transparência) |
| `escurecer_fundo` | Escurece a imagem de fundo: `0` = nada, `1` = preto total |
| `formato_live`    | Formato inicial da TV ao vivo: `ts` ou `m3u8` (o usuário pode trocar em Configurações) |
| `user_agent`      | Identificação enviada ao servidor (alguns painéis exigem um específico) |
| `tema_remoto_url` | Veja abaixo |

### Tema remoto (trocar imagens SEM atualizar o app)
Coloque em `tema_remoto_url` o link de um arquivo JSON hospedado na internet, com as chaves que quiser sobrescrever:

```json
{
  "fundo_login": "https://meusite.com/natal/fundo_login.jpg",
  "fundo_home": "https://meusite.com/natal/fundo_home.jpg",
  "cor_primaria": "#1DB954"
}
```

Toda vez que o app abrir, ele busca esse arquivo. Basta editá-lo para mudar o visual de todos os aparelhos
(ótimo para temas de Natal, Copa, promoções etc.). Se estiver sem internet, usa o tema local.

---

## 4. Ícone, nome e identificador do app

- **Nome embaixo do ícone:** `app/src/main/res/values/strings.xml` → `app_name`
- **Ícone do app:** substitua `app/src/main/res/drawable-nodpi/icone_app_frente.png` (432×432, transparente,
  com o desenho ocupando só o centro, uns 290×290) e `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192×192).
  A cor de fundo do ícone fica em `res/values/themes.xml` → `icone_fundo`.
- **Banner da Android TV:** substitua `app/src/main/res/drawable-nodpi/banner_tv.png` (320×180).
- **Identificador (package):** em `app/build.gradle.kts`, troque `applicationId = "com.meuapp.iptv"`
  (ex.: `com.suaempresa.iptv`). Faça isso antes de distribuir.

---

## 5. Estrutura do código

```
app/src/main/java/com/meuapp/iptv/
├── MainActivity.kt        → navegação entre telas
├── PlayerActivity.kt      → player ExoPlayer (Media3)
├── VlcPlayerActivity.kt   → player VLC
├── ui/TelaCanais.kt       → tela do canal (lista + telinha + guia)
├── ui/PreviewPlayer.kt    → telinha pequena (ExoPlayer / VLC)
├── data/
│   ├── XtreamApi.kt       → comunicação com o servidor (player_api.php)
│   ├── Sessao.kt          → salva o login no aparelho
│   ├── Repositorio.kt     → guarda as listas no aparelho (botão Atualizar)
│   ├── Preferencias.kt    → configurações do player e favoritos
│   └── Modelos.kt
├── tema/Tema.kt           → lê o tema.json e o tema remoto
└── ui/                    → telas (Login, Home, Categorias, Itens, Série) e componentes
```

## Observações

- O DNS pode ser digitado com ou sem `http://` (ex.: `servidor.com:8080`).
- O login fica salvo; o botão de sair (canto superior direito do menu) apaga a sessão.
- O APK de "release" está assinado com a chave de debug, bom para uso próprio/testes.
  Para publicar na Play Store é preciso uma chave de assinatura própria (posso ajudar a configurar no GitHub).
