// Painel de DNS para o app Meu IPTV - roda de graça na Cloudflare (Workers + KV).
//
// Rotas:
//   /config        público: o app lê daqui (lista de DNS ativos, aviso, imagens e cores)
//   /              página do painel (pede a senha)
//   /api/config    GET/PUT: lê e salva a configuração (exige a senha)
//   /api/testar    POST: testa se um DNS responde, a partir da Cloudflare (exige a senha)
//
// Configuração necessária no Cloudflare:
//   - KV namespace ligado a este Worker com o nome de variável:  CONFIG_KV
//   - Secret (segredo) chamado:                                    ADMIN_PASSWORD

const CHAVE = "config";
const MAX_SERVIDORES = 100;

// Chaves de aparência que o painel pode mudar (as mesmas do tema.json do app)
const CHAVES_TEMA = ["fundo_login", "fundo_home", "fundo_listas", "logo", "cor_primaria"];

const CABECALHOS_JSON = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function json(dados, status = 200) {
  return new Response(JSON.stringify(dados), { status, headers: CABECALHOS_JSON });
}

async function lerConfig(env) {
  const salvo = await env.CONFIG_KV.get(CHAVE, "json");
  const base = salvo && typeof salvo === "object" ? salvo : {};
  return {
    servidores: Array.isArray(base.servidores) ? base.servidores : [],
    aviso: typeof base.aviso === "string" ? base.aviso : "",
    tema: base.tema && typeof base.tema === "object" ? base.tema : {},
    atualizado: base.atualizado || null,
  };
}

// O que o app recebe: só DNS ativos, na ordem, mais aviso e aparência (só o que estiver preenchido)
async function publico(env) {
  const cfg = await lerConfig(env);
  const saida = {
    servidores: cfg.servidores.filter((s) => s.ativo).map((s) => s.dns),
    aviso: cfg.aviso,
  };
  for (const k of CHAVES_TEMA) {
    const v = cfg.tema[k];
    if (typeof v === "string" && v.trim() !== "") saida[k] = v.trim();
  }
  return json(saida);
}

function normalizarDns(texto) {
  let d = String(texto || "").trim().replace(/\/+$/, "");
  if (d.toLowerCase().endsWith("/player_api.php")) d = d.slice(0, -"/player_api.php".length);
  if (!d) return "";
  if (/\s/.test(d)) return "";
  if (!/^https?:\/\//i.test(d)) d = "http://" + d;
  try {
    const u = new URL(d);
    if (!u.hostname) return "";
  } catch (e) {
    return "";
  }
  return d.slice(0, 200);
}

function limpar(corpo) {
  const servidores = [];
  const vistos = new Set();
  for (const item of Array.isArray(corpo.servidores) ? corpo.servidores : []) {
    const dns = normalizarDns(item && item.dns);
    if (!dns || vistos.has(dns.toLowerCase())) continue;
    vistos.add(dns.toLowerCase());
    servidores.push({ dns, ativo: item.ativo !== false });
    if (servidores.length >= MAX_SERVIDORES) break;
  }
  const aviso = String(corpo.aviso || "").trim().slice(0, 300);
  const tema = {};
  const origem = corpo.tema && typeof corpo.tema === "object" ? corpo.tema : {};
  for (const k of CHAVES_TEMA) {
    tema[k] = String(origem[k] || "").trim().slice(0, 300);
  }
  return { servidores, aviso, tema };
}

async function hash(texto) {
  const dados = new TextEncoder().encode(texto);
  return new Uint8Array(await crypto.subtle.digest("SHA-256", dados));
}

async function senhaCorreta(request, env) {
  const cabecalho = request.headers.get("authorization") || "";
  const enviada = cabecalho.startsWith("Bearer ") ? cabecalho.slice(7) : "";
  const [a, b] = await Promise.all([hash(enviada), hash(env.ADMIN_PASSWORD)]);
  let diferenca = 0;
  for (let i = 0; i < a.length; i++) diferenca |= a[i] ^ b[i];
  return diferenca === 0;
}

async function autorizar(request, env) {
  if (!env.CONFIG_KV) {
    return json({ erro: "Falta ligar o KV a este Worker (nome da variável: CONFIG_KV)." }, 500);
  }
  if (!env.ADMIN_PASSWORD) {
    return json({ erro: "Falta criar o segredo ADMIN_PASSWORD nas configurações do Worker." }, 500);
  }
  if (!(await senhaCorreta(request, env))) {
    await new Promise((r) => setTimeout(r, 600)); // atrapalha tentativas em massa
    return json({ erro: "Senha incorreta." }, 401);
  }
  return null;
}

async function testarDns(dns) {
  const alvo = normalizarDns(dns);
  if (!alvo) return { ok: false, mensagem: "Endereço inválido." };
  const controle = new AbortController();
  const timer = setTimeout(() => controle.abort(), 6000);
  try {
    const r = await fetch(alvo + "/player_api.php", { signal: controle.signal, redirect: "follow" });
    return { ok: true, mensagem: "Respondeu (HTTP " + r.status + ")." };
  } catch (e) {
    return { ok: false, mensagem: "Não respondeu a partir da Cloudflare." };
  } finally {
    clearTimeout(timer);
  }
}

async function api(request, env, caminho) {
  const negado = await autorizar(request, env);
  if (negado) return negado;

  if (caminho === "/api/config" && request.method === "GET") {
    return json(await lerConfig(env));
  }
  if (caminho === "/api/config" && request.method === "PUT") {
    let corpo;
    try {
      corpo = await request.json();
    } catch (e) {
      return json({ erro: "Dados inválidos." }, 400);
    }
    const nova = limpar(corpo || {});
    nova.atualizado = new Date().toISOString();
    await env.CONFIG_KV.put(CHAVE, JSON.stringify(nova));
    return json(nova);
  }
  if (caminho === "/api/testar" && request.method === "POST") {
    let corpo;
    try {
      corpo = await request.json();
    } catch (e) {
      return json({ erro: "Dados inválidos." }, 400);
    }
    return json(await testarDns(corpo && corpo.dns));
  }
  return json({ erro: "Não encontrado." }, 404);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/config") {
        if (!env.CONFIG_KV) return json({ servidores: [], aviso: "" });
        return await publico(env);
      }
      if (url.pathname.startsWith("/api/")) return await api(request, env, url.pathname);
      if (url.pathname === "/" || url.pathname === "/admin") {
        return new Response(PAGINA, {
          headers: {
            "content-type": "text/html; charset=utf-8",
            "cache-control": "no-store",
            "x-frame-options": "DENY",
          },
        });
      }
      return new Response("Não encontrado", { status: 404 });
    } catch (e) {
      return json({ erro: "Erro interno." }, 500);
    }
  },
};

const PAGINA = `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Painel de servidores</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background: #f4f4f7; color: #1c1c24; }
  main { max-width: 860px; margin: 0 auto; padding: 20px 16px 60px; }
  h1 { font-size: 22px; font-weight: 500; margin: 8px 0 4px; }
  h2 { font-size: 17px; font-weight: 500; margin: 0 0 4px; }
  p.ajuda { margin: 0 0 12px; color: #5b5b68; font-size: 14px; line-height: 1.5; }
  section { background: #fff; border: 1px solid #dcdce3; border-radius: 12px; padding: 16px; margin: 14px 0; }
  input[type=text], input[type=password] { width: 100%; padding: 9px 10px; border: 1px solid #c5c5d0; border-radius: 8px; font-size: 15px; background: #fff; color: inherit; }
  input[type=color] { width: 44px; height: 38px; padding: 2px; border: 1px solid #c5c5d0; border-radius: 8px; background: #fff; }
  button { padding: 8px 12px; border: 1px solid #b9b9c6; border-radius: 8px; background: #fff; cursor: pointer; font-size: 14px; color: inherit; }
  button:hover { background: #f0f0f5; }
  button.principal { background: #d4181f; border-color: #d4181f; color: #fff; font-size: 16px; padding: 11px 22px; }
  button.principal:hover { background: #b9141a; }
  .linha { display: flex; gap: 6px; align-items: center; margin-bottom: 8px; }
  .linha .dns { flex: 1; min-width: 0; }
  .linha label { display: flex; gap: 4px; align-items: center; font-size: 14px; white-space: nowrap; }
  .num { width: 22px; text-align: right; color: #7a7a88; font-size: 14px; }
  .estado { font-size: 13px; min-width: 120px; }
  .estado.ok { color: #1a7f37; } .estado.ruim { color: #b42318; }
  .campo { margin-bottom: 10px; } .campo label { display: block; font-size: 14px; margin-bottom: 4px; color: #3b3b48; }
  .cor { display: flex; gap: 8px; align-items: center; }
  #msg { margin-left: 12px; font-size: 14px; }
  #msg.ok { color: #1a7f37; } #msg.ruim { color: #b42318; }
  #login { max-width: 360px; margin: 80px auto; }
  code { background: #ececf2; padding: 2px 6px; border-radius: 6px; font-size: 13px; word-break: break-all; }
  @media (max-width: 560px) { .linha { flex-wrap: wrap; } .estado { min-width: 0; width: 100%; } }
</style>
</head>
<body>
<main>
  <div id="login" hidden>
    <section>
      <h1>Painel de servidores</h1>
      <p class="ajuda">Digite a senha do painel.</p>
      <div class="campo"><input type="password" id="senha" autocomplete="current-password"></div>
      <button class="principal" id="entrar" style="width:100%">Entrar</button>
      <p id="erro-login" class="ajuda" style="color:#b42318;margin-top:10px"></p>
    </section>
  </div>

  <div id="painel" hidden>
    <div style="display:flex;justify-content:space-between;align-items:center">
      <h1>Painel de servidores</h1>
      <button id="sair">Sair</button>
    </div>

    <section>
      <h2>Servidores (DNS)</h2>
      <p class="ajuda">O app testa os servidores ativos e usa o primeiro que aceitar o usuário e a senha do cliente. Se um cair, ele passa para o próximo sozinho. Para tirar um DNS de uso, desmarque "Ativo" ou remova.</p>
      <div id="lista"></div>
      <button id="adicionar">+ Adicionar DNS</button>
    </section>

    <section>
      <h2>Aviso para os clientes</h2>
      <p class="ajuda">Aparece na tela de login e na tela inicial do app. Deixe vazio para não mostrar nada.</p>
      <input type="text" id="aviso" maxlength="300" placeholder="Ex.: Servidor em manutenção hoje até as 22h">
    </section>

    <section>
      <h2>Aparência (opcional)</h2>
      <p class="ajuda">Endereços (links) de imagens na internet. Deixe vazio para usar as imagens que já estão no app.</p>
      <div class="campo"><label for="t-fundo_login">Fundo do login</label><input type="text" id="t-fundo_login"></div>
      <div class="campo"><label for="t-fundo_home">Fundo da tela inicial</label><input type="text" id="t-fundo_home"></div>
      <div class="campo"><label for="t-fundo_listas">Fundo das listas</label><input type="text" id="t-fundo_listas"></div>
      <div class="campo"><label for="t-logo">Logo</label><input type="text" id="t-logo"></div>
      <div class="campo"><label for="t-cor_primaria">Cor de destaque</label>
        <div class="cor"><input type="color" id="cor-seletor" value="#e50914"><input type="text" id="t-cor_primaria" placeholder="#E50914" style="max-width:140px"></div>
      </div>
    </section>

    <div>
      <button class="principal" id="salvar">Salvar</button><span id="msg"></span>
    </div>
    <p class="ajuda" style="margin-top:18px">Endereço que o app lê (coloque no <code>tema.json</code> em <code>tema_remoto_url</code>): <code id="url-config"></code></p>
  </div>
</main>

<script>
(function () {
  var TEMA = ["fundo_login", "fundo_home", "fundo_listas", "logo", "cor_primaria"];
  var senha = sessionStorage.getItem("senha") || "";
  var $ = function (id) { return document.getElementById(id); };
  var lista = $("lista");

  function cab() { return { "authorization": "Bearer " + senha, "content-type": "application/json" }; }

  function mostrar(logado) {
    $("login").hidden = logado;
    $("painel").hidden = !logado;
    if (logado) $("url-config").textContent = location.origin + "/config";
  }

  function linhaDns(dns, ativo) {
    var div = document.createElement("div");
    div.className = "linha";

    var num = document.createElement("span"); num.className = "num";

    var campo = document.createElement("input");
    campo.type = "text"; campo.className = "dns"; campo.value = dns || "";
    campo.placeholder = "http://meuservidor.com:8080";
    campo.setAttribute("aria-label", "DNS");

    var rotulo = document.createElement("label");
    var chk = document.createElement("input"); chk.type = "checkbox"; chk.checked = ativo !== false;
    rotulo.appendChild(chk); rotulo.appendChild(document.createTextNode("Ativo"));

    var sobe = document.createElement("button"); sobe.textContent = "↑"; sobe.title = "Subir"; sobe.setAttribute("aria-label", "Subir");
    var desce = document.createElement("button"); desce.textContent = "↓"; desce.title = "Descer"; desce.setAttribute("aria-label", "Descer");
    var testa = document.createElement("button"); testa.textContent = "Testar";
    var remove = document.createElement("button"); remove.textContent = "Remover";
    var estado = document.createElement("span"); estado.className = "estado";

    sobe.onclick = function () { if (div.previousElementSibling) { lista.insertBefore(div, div.previousElementSibling); numerar(); } };
    desce.onclick = function () { if (div.nextElementSibling) { lista.insertBefore(div.nextElementSibling, div); numerar(); } };
    remove.onclick = function () { div.remove(); numerar(); };
    testa.onclick = function () {
      estado.className = "estado"; estado.textContent = "Testando…";
      fetch("/api/testar", { method: "POST", headers: cab(), body: JSON.stringify({ dns: campo.value }) })
        .then(function (r) { return r.json(); })
        .then(function (d) { estado.className = "estado " + (d.ok ? "ok" : "ruim"); estado.textContent = d.mensagem || d.erro || ""; })
        .catch(function () { estado.className = "estado ruim"; estado.textContent = "Falha no teste."; });
    };

    [num, campo, rotulo, sobe, desce, testa, remove, estado].forEach(function (el) { div.appendChild(el); });
    return div;
  }

  function numerar() {
    var itens = lista.querySelectorAll(".linha");
    for (var i = 0; i < itens.length; i++) itens[i].querySelector(".num").textContent = (i + 1);
  }

  function preencher(cfg) {
    lista.innerHTML = "";
    (cfg.servidores || []).forEach(function (s) { lista.appendChild(linhaDns(s.dns, s.ativo)); });
    numerar();
    $("aviso").value = cfg.aviso || "";
    TEMA.forEach(function (k) { $("t-" + k).value = (cfg.tema && cfg.tema[k]) || ""; });
    var cor = $("t-cor_primaria").value;
    if (/^#[0-9a-fA-F]{6}$/.test(cor)) $("cor-seletor").value = cor;
  }

  function coletar() {
    var servidores = [];
    lista.querySelectorAll(".linha").forEach(function (div) {
      var dns = div.querySelector("input.dns").value.trim();
      if (dns) servidores.push({ dns: dns, ativo: div.querySelector("input[type=checkbox]").checked });
    });
    var tema = {};
    TEMA.forEach(function (k) { tema[k] = $("t-" + k).value.trim(); });
    return { servidores: servidores, aviso: $("aviso").value.trim(), tema: tema };
  }

  function carregar() {
    return fetch("/api/config", { headers: cab() }).then(function (r) {
      if (r.status === 401) { sessionStorage.removeItem("senha"); senha = ""; throw new Error("senha"); }
      return r.json().then(function (d) { if (!r.ok) throw new Error(d.erro || "erro"); return d; });
    });
  }

  function entrar() {
    senha = $("senha").value;
    $("erro-login").textContent = "";
    carregar().then(function (cfg) {
      sessionStorage.setItem("senha", senha);
      mostrar(true); preencher(cfg);
    }).catch(function (e) {
      $("erro-login").textContent = e.message === "senha" ? "Senha incorreta." : e.message;
    });
  }

  $("entrar").onclick = entrar;
  $("senha").addEventListener("keydown", function (e) { if (e.key === "Enter") entrar(); });
  $("sair").onclick = function () { sessionStorage.removeItem("senha"); senha = ""; location.reload(); };
  $("adicionar").onclick = function () { var l = linhaDns("", true); lista.appendChild(l); numerar(); l.querySelector("input.dns").focus(); };
  $("cor-seletor").oninput = function () { $("t-cor_primaria").value = this.value.toUpperCase(); };

  $("salvar").onclick = function () {
    var msg = $("msg"); msg.className = ""; msg.textContent = "Salvando…";
    fetch("/api/config", { method: "PUT", headers: cab(), body: JSON.stringify(coletar()) })
      .then(function (r) { return r.json().then(function (d) { if (!r.ok) throw new Error(d.erro || "Erro ao salvar."); return d; }); })
      .then(function (cfg) {
        preencher(cfg);
        msg.className = "ok";
        msg.textContent = "Salvo. Os apps pegam a mudança na próxima vez que abrirem.";
      })
      .catch(function (e) { msg.className = "ruim"; msg.textContent = e.message; });
  };

  if (senha) {
    carregar().then(function (cfg) { mostrar(true); preencher(cfg); }).catch(function () { mostrar(false); });
  } else {
    mostrar(false);
  }
})();
</script>
</body>
</html>`;
