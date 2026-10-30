# Painel de servidores (DNS) — passo a passo

Tudo pelo navegador, sem instalar nada, de graça.
Os nomes dos menus da Cloudflare podem mudar um pouco com o tempo; se algum não bater, mande um print.

## 1. Criar a conta
1. Acesse https://dash.cloudflare.com/sign-up e crie a conta (e-mail e senha). Confirme o e-mail.
2. Não precisa colocar cartão nem comprar domínio.

## 2. Criar o Worker (o "site" do painel)
1. No menu da esquerda: **Workers e Pages** (Workers & Pages) → **Criar** (Create) → **Criar Worker** / "Hello World".
2. Nome: `painel-iptv` (ou outro). Clique em **Implantar** (Deploy).
3. Clique em **Editar código** (Edit code). Apague tudo que estiver lá, abra o arquivo `worker.js`, copie TODO o conteúdo e cole.
4. Clique em **Implantar** (Deploy) de novo.

## 3. Criar o armazenamento (onde ficam os DNS)
1. Menu da esquerda: **Armazenamento e bancos de dados** (Storage & databases) → **KV** → **Criar** (Create).
2. Nome: `config-iptv`.

## 4. Ligar o armazenamento ao Worker
1. Volte em **Workers e Pages** → seu Worker → **Configurações** (Settings) → **Associações** (Bindings) → **Adicionar** → **Namespace KV**.
2. Nome da variável: `CONFIG_KV` (exatamente assim, maiúsculas).
3. Namespace: escolha `config-iptv`. Salve / Implante.

## 5. Criar a senha do painel
1. No mesmo Worker: **Configurações** → **Variáveis e segredos** (Variables and Secrets) → **Adicionar**.
2. Tipo: **Segredo** (Secret). Nome: `ADMIN_PASSWORD`. Valor: a senha que você quer (use uma longa).
3. Implante.

## 6. Usar
1. O endereço do Worker aparece na página dele, algo como `https://painel-iptv.SEUNOME.workers.dev`.
2. Abra no navegador, digite a senha, adicione seus DNS e clique em **Salvar**.
3. O endereço que o app lê é o mesmo com `/config` no final.
   Exemplo: `https://painel-iptv.SEUNOME.workers.dev/config`

## 7. Ligar o app ao painel (uma única vez)
1. No GitHub, abra `app/src/main/assets/tema/tema.json` e clique no lápis.
2. Em `"tema_remoto_url": ""` coloque o endereço do passo 6 (com `/config`), entre as aspas.
3. Confirme. Quando a compilação terminar, baixe o APK novo e distribua.

Depois disso, tudo muda pelo painel, sem novo APK: DNS, ordem, aviso, fundos, logo e cor.

## Dicas
- Um DNS caiu? Adicione o novo, salve e, quando quiser, remova o velho. O app já passa para o próximo sozinho.
- O botão **Testar** mostra se o servidor responde a partir da Cloudflare. Serve de pista, mas não prova que funciona
  na internet de cada cliente (um provedor pode bloquear um DNS e outro não).
- O limite gratuito é de 100 mil leituras por dia (cada abertura do app é uma). Se passar, o app usa a última lista salva.
