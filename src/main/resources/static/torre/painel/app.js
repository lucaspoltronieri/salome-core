const params = new URLSearchParams(location.search);
const FILIAL = params.get("filial"); // ADMIN pode forçar a filial; senão usa a do token
const INTERVALO_MS = 15000;

const TOKEN_KEY = "torre_painel_token";
const CRED_KEY = "torre_painel_cred";
let pollTimer = null;

const fmtInt = new Intl.NumberFormat("pt-BR");
const fmtPeso = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 0, maximumFractionDigits: 1 });

let vistas = new Set();

function tempoDecorrido(isoInicio) {
  const seg = Math.max(0, Math.floor((Date.now() - new Date(isoInicio).getTime()) / 1000));
  const h = Math.floor(seg / 3600);
  const m = Math.floor((seg % 3600) / 60);
  return h > 0 ? `${h}h${String(m).padStart(2, "0")}` : `${m} min`;
}

function escapar(s) {
  return (s ?? "").toString().replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}

function relogio() {
  document.getElementById("hora").textContent =
    new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

function fmtDuracao(seg) {
  seg = Math.max(0, Math.round(seg || 0));
  if (seg < 60) return seg + "s";
  const h = Math.floor(seg / 3600);
  const m = Math.floor((seg % 3600) / 60);
  return h > 0 ? `${h}h${String(m).padStart(2, "0")}` : `${m} min`;
}

function renderIndicadores(ind) {
  ind = ind || {};
  document.getElementById("kpiFinalizadas").textContent = fmtInt.format(ind.atividadesFinalizadasHoje || 0);
  document.getElementById("kpiPessoas").textContent = fmtInt.format(ind.pessoasAtivasAgora || 0);
  document.getElementById("kpiHorasHomem").textContent = fmtDuracao(ind.horasHomemHojeSeg);
  document.getElementById("kpiTempoDescarga").textContent =
    ind.tempoMedioDescargaSeg ? fmtDuracao(ind.tempoMedioDescargaSeg) : "—";
  document.getElementById("kpiArmazem").textContent = fmtInt.format(ind.documentosNoArmazem || 0);
  document.getElementById("kpiOcorrencias").textContent = fmtInt.format(ind.ocorrenciasHoje || 0);
}

function renderViagens(viagens) {
  document.getElementById("contViagens").textContent = viagens.length;
  document.getElementById("vazioViagens").hidden = viagens.length > 0;
  document.getElementById("tbViagens").innerHTML = viagens.map(v => {
    const chave = "v" + v.idViagemTransferencia;
    const fresca = vistas.size && !vistas.has(chave) ? "fresca" : "";
    return `<tr class="${fresca}">
      <td class="placa">${escapar(v.placa) || "—"}</td>
      <td>${escapar(v.origem)}</td>
      <td>${escapar(v.motorista) || "—"}</td>
      <td>${escapar(v.dataBaixa)} ${escapar(v.horaBaixa) || ""}</td>
      <td class="num">${fmtInt.format(v.qtdCtes)}</td>
      <td class="num">${fmtInt.format(v.volumes ?? 0)}</td>
      <td class="num">${fmtPeso.format(v.peso ?? 0)}</td>
    </tr>`;
  }).join("");
  vistas = new Set(viagens.map(v => "v" + v.idViagemTransferencia));
}

function renderDescargas(descargas) {
  document.getElementById("contDescargas").textContent = descargas.length;
  document.getElementById("vazioDescargas").hidden = descargas.length > 0;
  document.getElementById("tbDescargas").innerHTML = descargas.map(d => `
    <tr>
      <td>${d.idViagemLegado ?? "—"}</td>
      <td class="placa">${escapar(d.placaVeiculo) || "—"}</td>
      <td class="num">${d.participantes.filter(p => !p.saidaEm).length}</td>
      <td class="num">${tempoDecorrido(d.iniciadaEm)}</td>
    </tr>`).join("");
}

// ---- Autenticação (mesmo login da Torre; modo TV pode manter a sessão) ----

function getToken() { return localStorage.getItem(TOKEN_KEY); }
function authHeaders() { return { "Authorization": "Bearer " + getToken() }; }
function limparSessao() { localStorage.removeItem(TOKEN_KEY); }

// Credencial fica guardada só se "manter conectado" estiver marcado (kiosk de TV).
function salvarCred(login, senha) {
  localStorage.setItem(CRED_KEY, btoa(unescape(encodeURIComponent(JSON.stringify({ login, senha })))));
}
function lerCred() {
  const v = localStorage.getItem(CRED_KEY);
  if (!v) return null;
  try { return JSON.parse(decodeURIComponent(escape(atob(v)))); } catch (e) { return null; }
}
function limparCred() { localStorage.removeItem(CRED_KEY); }

async function autenticar(login, senha) {
  const r = await fetch("/api/torre/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ login, senha })
  });
  if (!r.ok) {
    throw new Error(r.status === 401 ? "Usuário ou senha inválidos." : "Falha no login (HTTP " + r.status + ").");
  }
  const data = await r.json();
  localStorage.setItem(TOKEN_KEY, data.token);
  return data.usuario;
}

function mostrarUsuario(usuario) {
  if (!usuario) return;
  document.getElementById("usuarioInfo").textContent =
    `${usuario.nome} · filial ${usuario.idFilial}` + (usuario.perfil === "ADMIN" ? " · ADMIN" : "");
}

function entrar(usuario) {
  mostrarUsuario(usuario);
  document.getElementById("login").hidden = true;
  document.getElementById("sairBtn").hidden = false;
  iniciarPolling();
}

function mostrarLogin() {
  pararPolling();
  document.getElementById("sairBtn").hidden = true;
  document.getElementById("usuarioInfo").textContent = "";
  document.getElementById("login").hidden = false;
  document.getElementById("loginUsuario").focus();
}

function iniciarPolling() {
  carregar();
  if (!pollTimer) pollTimer = setInterval(carregar, INTERVALO_MS);
}
function pararPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

async function aoExpirar() {
  // Token expirou: tenta re-login silencioso com a credencial salva (modo TV).
  const cred = lerCred();
  if (cred) {
    try { mostrarUsuario(await autenticar(cred.login, cred.senha)); return carregar(); } catch (e) { /* cred não vale mais */ }
  }
  limparSessao();
  mostrarLogin();
}

async function carregar() {
  const erro = document.getElementById("erro");
  try {
    const url = "/api/torre/painel/snapshot" + (FILIAL ? "?filial=" + encodeURIComponent(FILIAL) : "");
    const r = await fetch(url, { cache: "no-store", headers: authHeaders() });
    if (r.status === 401) return aoExpirar();
    if (!r.ok) throw new Error("HTTP " + r.status);
    const snap = await r.json();
    renderIndicadores(snap.indicadores);
    renderViagens(snap.viagensAguardando || []);
    renderDescargas(snap.descargasEmAndamento || []);
    document.getElementById("atualizado").textContent =
      "atualizado " + new Date(snap.atualizadoEm).toLocaleTimeString("pt-BR");
    erro.hidden = true;
  } catch (e) {
    erro.textContent = "Falha ao atualizar: " + e.message;
    erro.hidden = false;
  }
}

async function iniciarSessao() {
  const token = getToken();
  if (token) {
    try {
      const r = await fetch("/api/torre/auth/me", { headers: authHeaders() });
      if (r.ok) { return entrar(await r.json()); }
    } catch (e) { /* cai para tentativa por credencial */ }
  }
  const cred = lerCred();
  if (cred) {
    try { return entrar(await autenticar(cred.login, cred.senha)); } catch (e) { /* mostra login */ }
  }
  mostrarLogin();
}

document.getElementById("loginForm").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const btn = document.getElementById("loginBtn");
  const erro = document.getElementById("loginErro");
  const login = document.getElementById("loginUsuario").value.trim();
  const senha = document.getElementById("loginSenha").value;
  const manter = document.getElementById("loginManter").checked;
  btn.disabled = true;
  erro.hidden = true;
  try {
    const usuario = await autenticar(login, senha);
    if (manter) salvarCred(login, senha); else limparCred();
    document.getElementById("loginSenha").value = "";
    entrar(usuario);
  } catch (e) {
    erro.textContent = e.message;
    erro.hidden = false;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById("sairBtn").addEventListener("click", () => {
  limparSessao();
  limparCred();
  mostrarLogin();
});

fetch("/api/versao").then(r => r.ok ? r.json() : null)
  .then(d => { if (d?.versao) document.getElementById("versao").textContent = "v" + d.versao; })
  .catch(() => {});

relogio();
setInterval(relogio, 1000);
iniciarSessao();
