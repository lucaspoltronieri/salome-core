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

// Origem vem como "EXPRESSO SALOME - OSASCO"; mostra só a cidade (último trecho).
function limparOrigem(origem) {
  const o = (origem ?? "").trim();
  const partes = o.split(" - ");
  const cidade = partes.length > 1 ? partes[partes.length - 1].trim() : o;
  return cidade || o.replace(/^expresso\s+salome\b[\s-]*/i, "").trim() || o;
}

// Vários idViagemTransferencia (manifestos) do mesmo idViagem chegam no mesmo
// caminhão e são descarregados juntos: agrupa numa linha só e soma os totais.
function agruparViagens(viagens) {
  const grupos = new Map();
  for (const v of viagens) {
    const chave = v.idViagem != null ? "g" + v.idViagem : "t" + v.idViagemTransferencia;
    let g = grupos.get(chave);
    if (!g) {
      g = { chave, placa: v.placa, origem: v.origem, dataBaixa: v.dataBaixa, horaBaixa: v.horaBaixa,
            qtdCtes: 0, volumes: 0, peso: 0, manifestos: 0 };
      grupos.set(chave, g);
    }
    g.qtdCtes += v.qtdCtes || 0;
    g.volumes += Number(v.volumes ?? 0);
    g.peso += Number(v.peso ?? 0);
    g.manifestos += 1;
    // mantém a baixa mais recente do grupo
    if (`${v.dataBaixa} ${v.horaBaixa ?? ""}` > `${g.dataBaixa} ${g.horaBaixa ?? ""}`) {
      g.dataBaixa = v.dataBaixa; g.horaBaixa = v.horaBaixa;
    }
  }
  return [...grupos.values()];
}

function renderViagens(viagens) {
  const grupos = agruparViagens(viagens);
  document.getElementById("contViagens").textContent = grupos.length;
  document.getElementById("vazioViagens").hidden = grupos.length > 0;
  document.getElementById("tbViagens").innerHTML = grupos.map(g => {
    const fresca = vistas.size && !vistas.has(g.chave) ? "fresca" : "";
    const badge = g.manifestos > 1
      ? ` <span class="badge-grupo" title="${g.manifestos} manifestos na mesma viagem">×${g.manifestos}</span>`
      : "";
    return `<tr class="${fresca}">
      <td class="placa">${escapar(g.placa) || "—"}${badge}</td>
      <td>${escapar(limparOrigem(g.origem))}</td>
      <td>${escapar(g.dataBaixa)} ${escapar(g.horaBaixa) || ""}</td>
      <td class="num">${fmtInt.format(g.qtdCtes)}</td>
      <td class="num">${fmtInt.format(g.volumes)}</td>
      <td class="num">${fmtPeso.format(g.peso)}</td>
    </tr>`;
  }).join("");
  vistas = new Set(grupos.map(g => g.chave));
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

function setNum(id, n) { document.getElementById(id).textContent = fmtInt.format(n || 0); }

// Esteira: contagem por estágio do ciclo (chegada → expedição).
function renderEsteira(snap) {
  setNum("etAguardando", agruparViagens(snap.viagensAguardando || []).length);
  setNum("etDescarregando", (snap.descargasEmAndamento || []).length);
  setNum("etSeparar", (snap.noArmazem || []).length);
  setNum("etSeparando", (snap.separacoesEmAndamento || []).length);
  setNum("etPronto", (snap.prontosBox || []).length);
  setNum("etCarregando", (snap.carregamentosEmAndamento || []).length);
}

// Aguardando separação: documentos NO_ARMAZEM (descarregados, esperando separar).
function renderSeparar(docs) {
  docs = docs || [];
  document.getElementById("contSeparar").textContent = docs.length;
  document.getElementById("vazioSeparar").hidden = docs.length > 0;
  document.getElementById("tbSeparar").innerHTML = docs.map(d => `<tr>
    <td>${escapar(d.numeroCte) || '<span class="pre">NF</span>'}</td>
    <td>${escapar(d.destinatario)}</td>
    <td>${escapar(d.cidadeDestino)}</td>
    <td class="num">${fmtInt.format(d.volumes || 0)}</td>
  </tr>`).join("");
}

// Carregando agora (atividades) + o que já está pronto no box de distribuição.
function renderCarregar(atividades, prontos) {
  atividades = atividades || [];
  prontos = prontos || [];
  const rows = atividades.map(a => `<tr>
    <td class="placa">${escapar(a.placaVeiculo) || (a.idViagemLegado ? "viagem " + a.idViagemLegado : "—")}</td>
    <td>Carregando</td>
    <td class="num">${a.participantes.filter(p => !p.saidaEm).length}</td>
    <td class="num">${tempoDecorrido(a.iniciadaEm)}</td>
  </tr>`).concat(prontos.map(d => `<tr>
    <td>${escapar(d.numeroCte) || escapar(d.destinatario) || "—"}</td>
    <td>Pronto no box</td><td class="num">—</td><td class="num">—</td>
  </tr>`));
  document.getElementById("contCarregar").textContent = atividades.length + prontos.length;
  document.getElementById("vazioCarregar").hidden = rows.length > 0;
  document.getElementById("tbCarregar").innerHTML = rows.join("");
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
    renderEsteira(snap);
    renderViagens(snap.viagensAguardando || []);
    renderDescargas(snap.descargasEmAndamento || []);
    renderSeparar(snap.noArmazem || []);
    renderCarregar(snap.carregamentosEmAndamento || [], snap.prontosBox || []);
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
