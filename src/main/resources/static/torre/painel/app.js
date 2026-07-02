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
  if (!isoInicio) return "";
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

function fmtData(d) {
  const partes = (d || "").split("-");
  return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : (d || "");
}

function fmtDataHora(d, h) {
  return [fmtData(d), h || ""].filter(Boolean).join(" ");
}

function setNum(id, n) { document.getElementById(id).textContent = fmtInt.format(n || 0); }
function setPeso(id, n) { document.getElementById(id).textContent = fmtPeso.format(n || 0); }
function setTexto(id, s) { const el = document.getElementById(id); if (el) el.textContent = s; }

// Pessoas ainda ativas numa atividade (participantes sem saída registrada).
function pessoasAtivas(atv) {
  return (atv.participantes || []).filter(p => !p.saidaEm).length;
}

function setHealth(cardId, cls) {
  const el = document.getElementById(cardId);
  if (!el) return;
  el.classList.remove("ok", "warn", "bad");
  if (cls) el.classList.add(cls);
}

function renderIndicadores(ind, agregDescargas) {
  ind = ind || {};
  const volumesHoje = agregDescargas?.volumes || 0;
  setTexto("kpiVolumes", fmtInt.format(volumesHoje));
  setTexto("kpiDescargasFinalizadas", fmtInt.format(agregDescargas?.qtd || 0));
  setTexto("kpiDescargasFinalizadasSub",
    `${fmtInt.format(volumesHoje)} vol · ${fmtPeso.format(agregDescargas?.peso || 0)} kg`);

  // Produtividade = volumes movimentados hoje ÷ horas-homem trabalhadas hoje.
  const horas = (ind.horasHomemHojeSeg || 0) / 3600;
  setTexto("kpiProdutividade", horas > 0 ? fmtInt.format(Math.round(volumesHoje / horas)) : "—");

  setTexto("kpiPessoas", fmtInt.format(ind.pessoasAtivasAgora || 0));
  setTexto("kpiHorasHomem", fmtDuracao(ind.horasHomemHojeSeg) + " · hoje");
  setTexto("kpiTempoDescarga", ind.tempoMedioDescargaSeg ? fmtDuracao(ind.tempoMedioDescargaSeg) : "—");

  const ocorr = ind.ocorrenciasHoje || 0;
  setTexto("kpiOcorrencias", fmtInt.format(ocorr));
  setHealth("kpiCardOcorrencias", ocorr === 0 ? "ok" : (ocorr >= 5 ? "bad" : "warn"));
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
    if (`${v.dataBaixa} ${v.horaBaixa ?? ""}` > `${g.dataBaixa} ${g.horaBaixa ?? ""}`) {
      g.dataBaixa = v.dataBaixa; g.horaBaixa = v.horaBaixa;
    }
  }
  return [...grupos.values()];
}

// Somatório de volumes/peso de uma lista de caminhões (MapaCaminhao) do mapa.
function somarCaminhoes(lista) {
  return (lista || []).reduce((s, c) => {
    s.volumes += Number(c.volumes ?? 0);
    s.peso += Number(c.peso ?? 0);
    return s;
  }, { volumes: 0, peso: 0 });
}

// Chegando (em trânsito): caminhões vindo de outra base, com previsão de chegada.
function renderChegando(lista) {
  lista = lista || [];
  setTexto("contTransito", fmtInt.format(lista.length));
  setNum("chVeiculos", lista.length);
  const tot = somarCaminhoes(lista);
  setNum("chVolumes", tot.volumes);
  setPeso("chPeso", tot.peso);
  document.getElementById("chDetalhe").innerHTML = lista.slice(0, 6).map(c => `
    <div class="linha">
      <span class="placa">${escapar(c.placa) || "—"}</span>
      <span>${escapar(limparOrigem(c.origem))} · ${escapar(fmtDataHora(c.dataPrevisaoChegada, c.horaPrevisaoChegada))}</span>
      <span class="num">${fmtInt.format(Number(c.volumes || 0))} vol</span>
    </div>`).join("") || '<p class="vazio">Nenhum caminhão a caminho.</p>';
}

// Aguardando descarga: totais grandes + detalhe compacto por caminhão.
function renderAguardandoDescarga(viagens) {
  const grupos = agruparViagens(viagens || []);
  setTexto("contAguardando", fmtInt.format(grupos.length));
  setNum("agdVeiculos", grupos.length);
  setNum("agdVolumes", grupos.reduce((s, g) => s + g.volumes, 0));
  setPeso("agdPeso", grupos.reduce((s, g) => s + g.peso, 0));
  document.getElementById("agdDetalhe").innerHTML = grupos.slice(0, 6).map(g => {
    const fresca = vistas.size && !vistas.has(g.chave) ? "fresca" : "";
    const badge = g.manifestos > 1
      ? ` <span class="badge-grupo" title="${g.manifestos} manifestos na mesma viagem">×${g.manifestos}</span>`
      : "";
    return `<div class="linha ${fresca}">
      <span class="placa">${escapar(g.placa) || "—"}${badge}</span>
      <span class="num">${fmtInt.format(g.qtdCtes || 0)} CT-e</span>
      <span class="num">${fmtInt.format(g.volumes || 0)} vol</span>
      <span class="num">${fmtPeso.format(g.peso || 0)} kg</span>
    </div>`;
  }).join("") || '<p class="vazio">Nenhuma viagem aguardando.</p>';
  vistas = new Set(grupos.map(g => g.chave));
}

// Descarregando agora: atividades de descarga abertas — placa, tempo e pessoas.
function renderDescarregandoAgora(descargas) {
  descargas = descargas || [];
  setTexto("contDescarga", fmtInt.format(descargas.length));
  setNum("dsAtivas", descargas.length);
  const pessoas = descargas.reduce((s, a) => s + pessoasAtivas(a), 0);
  setNum("dsPessoas", pessoas);
  document.getElementById("dsDetalhe").innerHTML = descargas.slice(0, 6).map(a => {
    const gente = pessoasAtivas(a);
    return `<div class="linha">
      <span class="placa">${escapar(a.placaVeiculo) || (a.idViagemLegado ? "Viagem " + escapar(a.idViagemLegado) : "—")}</span>
      <span class="num">${fmtInt.format(Number(a.volumes || 0))} vol</span>
      <span class="num">${fmtPeso.format(Number(a.peso || 0))} kg</span>
      <span class="tempo">${escapar(tempoDecorrido(a.iniciadaEm))}</span>
      <span class="gente num">${fmtInt.format(gente)} 👤</span>
    </div>`;
  }).join("") || '<p class="vazio">Nada descarregando agora.</p>';
}

// No armazém (saldo): total + quebra por etapa do fluxo físico.
function renderSaldo(saldo) {
  saldo = saldo || {};
  const total = saldo.total || {};
  setNum("slCtes", total.qtd || 0);
  setNum("slVolumes", total.volumes || 0);
  setPeso("slPeso", total.peso || 0);
  const etapas = [
    { rot: "Aguardando separação", cls: "s-armazem", a: saldo.noArmazem },
    { rot: "Em separação", cls: "s-separacao", a: saldo.emSeparacao },
    { rot: "Separado (box)", cls: "s-box", a: saldo.separadoBox },
    { rot: "Carregando", cls: "s-carreg", a: saldo.emCarregamento },
  ];
  // Barra de ocupação: cada etapa proporcional à sua qtd de CT-es.
  const somaQtd = etapas.reduce((s, e) => s + (e.a?.qtd || 0), 0) || 1;
  document.getElementById("barraSaldo").innerHTML = etapas.map(e => {
    const q = e.a?.qtd || 0;
    const pct = (q / somaQtd) * 100;
    return `<div class="seg ${e.cls}" style="flex:0 0 ${pct}%" title="${e.rot}: ${q}">${pct >= 8 ? fmtInt.format(q) : ""}</div>`;
  }).join("");
  document.getElementById("slDetalhe").innerHTML = etapas.map(e => `
    <div class="linha">
      <span>${e.rot}</span>
      <span class="num">${fmtInt.format(e.a?.qtd || 0)} CT-es · ${fmtInt.format(e.a?.volumes || 0)} vol</span>
    </div>`).join("");
}

// Semáforo de cada estágio: verde=operando, amarelo=fila parada (tem carga esperando
// e ninguém trabalhando), cinza=vazio. Insight típico de torre de controle de WMS.
function atualizarSemaforos(snap) {
  const chegando = (snap.emTransito || []).length;
  const aguardando = agruparViagens(snap.viagensAguardando || []).length;
  const descargas = (snap.descargasEmAndamento || []).length;
  const separacoes = (snap.separacoesEmAndamento || []).length;
  const aguardandoSep = snap.saldoArmazem?.noArmazem?.qtd || 0;

  const set = (id, cls) => { const el = document.getElementById(id); if (el) el.className = "status-dot " + cls; };
  set("stTransito", chegando > 0 ? "ok" : "idle");
  set("stAguardando", aguardando > 0 ? (descargas > 0 ? "ok" : "warn") : "idle");
  set("stDescarga", descargas > 0 ? "ok" : "idle");
  set("stSeparacao", separacoes > 0 ? "ok" : (aguardandoSep > 0 ? "warn" : "idle"));
}

// Separando agora: atividades de separação abertas + quanto aguarda separação.
function renderSeparandoAgora(separacoes, aguardandoAgregado) {
  separacoes = separacoes || [];
  setTexto("contSeparacao", fmtInt.format(separacoes.length));
  setNum("spAtivas", separacoes.length);
  setNum("spPessoas", separacoes.reduce((s, a) => s + pessoasAtivas(a), 0));
  setNum("spAguardando", aguardandoAgregado?.qtd || 0);
  document.getElementById("spDetalhe").innerHTML = separacoes.slice(0, 6).map(a => {
    const gente = pessoasAtivas(a);
    return `<div class="linha">
      <span class="placa">${escapar(a.placaVeiculo) || (a.idViagemLegado ? "Viagem " + escapar(a.idViagemLegado) : "—")}</span>
      <span class="num">${fmtInt.format(Number(a.volumes || 0))} vol</span>
      <span class="num">${fmtPeso.format(Number(a.peso || 0))} kg</span>
      <span class="tempo">${escapar(tempoDecorrido(a.iniciadaEm))}</span>
      <span class="gente num">${fmtInt.format(gente)} 👤</span>
    </div>`;
  }).join("") || '<p class="vazio">Nada em separação agora.</p>';
}

// Pra rua (em rota de entrega): caminhões que saíram para entrega.
function renderPraRua(lista) {
  lista = lista || [];
  setTexto("contRua", fmtInt.format(lista.length));
  setNum("ruVeiculos", lista.length);
  const tot = somarCaminhoes(lista);
  setNum("ruVolumes", tot.volumes);
  setPeso("ruPeso", tot.peso);
  document.getElementById("ruDetalhe").innerHTML = lista.slice(0, 6).map(c => `
    <div class="linha">
      <span class="placa">${escapar(c.placa) || "—"}</span>
      <span>${fmtInt.format(c.qtdCtes || 0)} CT-es</span>
      <span class="num">${fmtInt.format(Number(c.volumes || 0))} vol</span>
      <span class="num">${fmtPeso.format(Number(c.peso || 0))} kg</span>
    </div>`).join("") || '<p class="vazio">Nenhum veículo em rota.</p>';
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
    renderIndicadores(snap.indicadores, snap.descargasFinalizadasAgregado);
    renderChegando(snap.emTransito || []);
    renderAguardandoDescarga(snap.viagensAguardando || []);
    renderDescarregandoAgora(snap.descargasEmAndamento || []);
    renderSaldo(snap.saldoArmazem);
    renderSeparandoAgora(snap.separacoesEmAndamento || [], snap.saldoArmazem?.noArmazem);
    renderPraRua(snap.emRotaEntrega || []);
    atualizarSemaforos(snap);
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
