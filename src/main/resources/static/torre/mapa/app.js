const params = new URLSearchParams(location.search);
const FILIAL = params.get("filial"); // ADMIN pode forçar a filial; senão usa a do token
const INTERVALO_MS = 30000;

const TOKEN_KEY = "torre_mapa_token";
const CRED_KEY = "torre_mapa_cred";
let pollTimer = null;
let snap = null;

const $ = (id) => document.getElementById(id);
const fmtInt = new Intl.NumberFormat("pt-BR");
const fmtPeso = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 0, maximumFractionDigits: 1 });

function escapar(s) {
  return (s ?? "").toString().replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}

// Origem vem como "EXPRESSO SALOME - OSASCO"; mostra só a cidade.
function limparOrigem(origem) {
  const o = (origem ?? "").trim();
  const partes = o.split(" - ");
  const cidade = partes.length > 1 ? partes[partes.length - 1].trim() : o;
  return cidade || o.replace(/^expresso\s+salome\b[\s-]*/i, "").trim() || o;
}

function fmtData(d) { return d ? d.split("-").reverse().join("/") : ""; }
function dataHora(d, h) { return (fmtData(d) + " " + (h || "")).trim() || "—"; }

function tempoMin(iso) {
  if (!iso) return "—";
  const seg = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  const h = Math.floor(seg / 3600), m = Math.floor((seg % 3600) / 60);
  return h > 0 ? `${h}h${String(m).padStart(2, "0")}` : `${m} min`;
}

// ---- Filtros (espelham o MapaFiltro do servidor) ----

function contem(alvo, termo) {
  if (!termo) return true;
  if (alvo == null) return false;
  return alvo.toString().toLowerCase().includes(termo.toLowerCase());
}
function filtros() {
  return { texto: $("fTexto").value.trim(), cidade: $("fCidade").value.trim(), situacao: $("fSituacao").value.trim() };
}
function casaTexto(f, ...campos) {
  if (!f.texto) return true;
  return campos.some(c => contem(c, f.texto));
}
function casaCte(f, cte, ctxCampos = []) {
  return casaTexto(f, cte.cte, cte.notasFiscais, cte.remetente, cte.destinatario, cte.cidadeDestinatario, cte.placaVeiculo, ...ctxCampos)
      && contem(cte.cidadeDestinatario, f.cidade)
      && contem(cte.situacaoCte, f.situacao);
}

// ---- Render ----

function setCont(id, n) { $(id).textContent = n; }
function setVazio(id, vazio) { $(id).hidden = !vazio; }

function render() {
  if (!snap) return;
  const f = filtros();
  renderCaminhoes("Vindo", snap.vindoDeOutrasBases || [], f, true);
  renderCaminhoes("Entrega", snap.emRotaEntrega || [], f, false);
  renderAguardando(snap.aguardandoDescarga || [], f);
  renderDescarregando(snap.descarregando || [], f);
  renderCtes("Armazenado", snap.armazenado || [], f, cteArmazenadoRow);
  renderCtes("Outros", snap.outrosArmazens || [], f, cteOutrosRow);
}

// Caminhões (vindo / entrega): linha-mãe expansível + CT-es filtrados
function renderCaminhoes(suf, caminhoes, f, comOrigem) {
  const linhas = [];
  let visiveis = 0;
  caminhoes.forEach((c, i) => {
    const ctxCte = comOrigem ? [c.placa, c.origem, c.motorista] : [c.placa, c.motorista];
    const ctes = (c.ctes || []).filter(cte => casaCte(f, cte, ctxCte));
    if (!ctes.length) return;
    visiveis++;
    const vol = ctes.reduce((s, x) => s + (+x.volumes || 0), 0);
    const peso = ctes.reduce((s, x) => s + (+x.peso || 0), 0);
    const idRow = `${suf}_${i}`;
    const colMeio = comOrigem
      ? `<td>${escapar(limparOrigem(c.origem))}</td><td>${escapar(c.motorista) || "—"}</td>
         <td>${dataHora(c.dataPrevisaoSaida, c.horaPrevisaoSaida)}</td><td>${dataHora(c.dataPrevisaoChegada, c.horaPrevisaoChegada)}</td>`
      : `<td>${escapar(c.motorista) || "—"}</td><td>${dataHora(c.dataPrevisaoSaida, c.horaPrevisaoSaida)}</td>`;
    linhas.push(`<tr class="mae" data-row="${idRow}">
      <td class="expansor" data-row="${idRow}">▸</td>
      <td class="placa">${escapar(c.placa) || "—"}</td>
      ${colMeio}
      <td class="num">${fmtInt.format(ctes.length)}</td>
      <td class="num">${fmtInt.format(vol)}</td>
      <td class="num">${fmtPeso.format(peso)}</td>
    </tr>`);
    const det = ctes.map(cte => `<tr>
      <td>${escapar(cte.cte) || "—"}</td><td>${escapar(cte.notasFiscais)}</td>
      <td>${escapar(cte.destinatario)}</td><td>${escapar(cte.cidadeDestinatario)}</td>
      <td>${escapar(cte.setorRegiao)}</td>
      <td class="num">${fmtInt.format(+cte.volumes || 0)}</td><td class="num">${fmtPeso.format(+cte.peso || 0)}</td>
    </tr>`).join("");
    const colspan = comOrigem ? 9 : 7;
    linhas.push(`<tr class="detalhe" data-det="${idRow}" hidden><td colspan="${colspan}">
      <table><thead><tr>
        <th>CT-e</th><th>NFs</th><th>Destinatário</th><th>Cidade</th><th>Setor/Região</th>
        <th class="num">Volumes</th><th class="num">Peso (kg)</th>
      </tr></thead><tbody>${det}</tbody></table>
    </td></tr>`);
  });
  $(`tb${suf}`).innerHTML = linhas.join("");
  setCont(`c${suf}`, visiveis);
  setVazio(`v${suf}`, visiveis === 0);
}

function renderAguardando(viagens, f) {
  const vis = viagens.filter(v => casaTexto(f, v.placa, v.origem));
  $("tbAguardando").innerHTML = vis.map(v => `<tr>
    <td class="placa">${escapar(v.placa) || "—"}</td>
    <td>${escapar(limparOrigem(v.origem))}</td>
    <td>${dataHora(v.dataBaixa, v.horaBaixa)}</td>
    <td class="num">${fmtInt.format(v.qtdCtes)}</td>
    <td class="num">${fmtInt.format(+v.volumes || 0)}</td>
    <td class="num">${fmtPeso.format(+v.peso || 0)}</td>
  </tr>`).join("");
  setCont("cAguardando", vis.length);
  setVazio("vAguardando", vis.length === 0);
}

function renderDescarregando(atividades, f) {
  const vis = atividades.filter(a => casaTexto(f, a.idViagemLegado, a.placaVeiculo));
  $("tbDescarregando").innerHTML = vis.map(a => {
    const ativos = (a.participantes || []).filter(p => !p.saidaEm).length;
    return `<tr>
      <td>${escapar(a.idViagemLegado) || "—"}</td>
      <td class="placa">${escapar(a.placaVeiculo) || "—"}</td>
      <td class="num">${fmtInt.format(ativos)}</td>
      <td class="num">${tempoMin(a.iniciadaEm)}</td>
    </tr>`;
  }).join("");
  setCont("cDescarregando", vis.length);
  setVazio("vDescarregando", vis.length === 0);
}

function renderCtes(suf, ctes, f, rowFn) {
  const vis = ctes.filter(cte => casaCte(f, cte));
  $(`tb${suf}`).innerHTML = vis.map(rowFn).join("");
  setCont(`c${suf}`, vis.length);
  setVazio(`v${suf}`, vis.length === 0);
}
function cteArmazenadoRow(cte) {
  return `<tr>
    <td>${escapar(cte.cte) || "—"}</td>
    <td>${dataHora(cte.dataEntradaArmazem, cte.horaEntradaArmazem)}</td>
    <td>${escapar(cte.destinatario)}</td><td>${escapar(cte.cidadeDestinatario)}</td>
    <td>${escapar(cte.setorRegiao)}</td>
    <td class="num">${fmtInt.format(+cte.volumes || 0)}</td><td class="num">${fmtPeso.format(+cte.peso || 0)}</td>
    <td>${escapar(cte.situacaoCte)}</td><td>${fmtData(cte.dataPrevistaEntrega) || "—"}</td>
  </tr>`;
}
function cteOutrosRow(cte) {
  return `<tr>
    <td>${escapar(cte.cte) || "—"}</td><td>${escapar(cte.armazemAtual)}</td>
    <td>${escapar(cte.destinatario)}</td><td>${escapar(cte.cidadeDestinatario)}</td>
    <td class="num">${fmtInt.format(+cte.volumes || 0)}</td><td class="num">${fmtPeso.format(+cte.peso || 0)}</td>
    <td>${escapar(cte.situacaoCte)}</td>
  </tr>`;
}

// Expandir/recolher caminhão (delegação de clique)
document.addEventListener("click", (ev) => {
  const cell = ev.target.closest(".mae, .expansor");
  if (!cell) return;
  const row = cell.dataset.row;
  if (!row) return;
  const det = document.querySelector(`tr.detalhe[data-det="${row}"]`);
  const mae = document.querySelector(`tr.mae[data-row="${row}"]`);
  const exp = document.querySelector(`.expansor[data-row="${row}"]`);
  if (!det) return;
  const abrir = det.hidden;
  det.hidden = !abrir;
  if (mae) mae.classList.toggle("aberta", abrir);
  if (exp) exp.textContent = abrir ? "▾" : "▸";
});

// ---- Carregar / export ----

async function carregar() {
  try {
    const url = "/api/torre/mapa/snapshot" + (FILIAL ? "?filial=" + encodeURIComponent(FILIAL) : "");
    const r = await fetch(url, { cache: "no-store", headers: authHeaders() });
    if (r.status === 401) return aoExpirar();
    if (!r.ok) throw new Error("HTTP " + r.status);
    snap = await r.json();
    render();
    $("atualizado").textContent = "atualizado " + new Date(snap.atualizadoEm).toLocaleTimeString("pt-BR");
    $("janela").textContent = "baixas desde " + fmtData(snap.dataCorte);
    $("erro").hidden = true;
  } catch (e) {
    $("erro").textContent = "Falha ao atualizar: " + e.message;
    $("erro").hidden = false;
  }
}

async function exportar() {
  const f = filtros();
  const qs = new URLSearchParams();
  if (FILIAL) qs.set("filial", FILIAL);
  if (f.texto) qs.set("texto", f.texto);
  if (f.cidade) qs.set("cidade", f.cidade);
  if (f.situacao) qs.set("situacao", f.situacao);
  try {
    const r = await fetch("/api/torre/mapa/export?" + qs.toString(), { headers: authHeaders() });
    if (r.status === 401) return aoExpirar();
    if (!r.ok) throw new Error("HTTP " + r.status);
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = "mapa-armazem.xlsx";
    document.body.appendChild(a); a.click(); a.remove();
    URL.revokeObjectURL(url);
  } catch (e) {
    $("erro").textContent = "Falha ao exportar: " + e.message;
    $("erro").hidden = false;
  }
}

// ---- Autenticação (mesmo login da Torre) ----

function getToken() { return localStorage.getItem(TOKEN_KEY); }
function authHeaders() { return { "Authorization": "Bearer " + getToken() }; }
function limparSessao() { localStorage.removeItem(TOKEN_KEY); }
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
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ login, senha })
  });
  if (!r.ok) throw new Error(r.status === 401 ? "Usuário ou senha inválidos." : "Falha no login (HTTP " + r.status + ").");
  const data = await r.json();
  localStorage.setItem(TOKEN_KEY, data.token);
  return data.usuario;
}

function mostrarUsuario(u) {
  if (!u) return;
  $("filialNome").textContent = "· filial " + u.idFilial;
  $("usuarioInfo").textContent = `${u.nome}` + (u.perfil === "ADMIN" ? " · ADMIN" : "");
}
function entrar(u) {
  mostrarUsuario(u);
  $("login").hidden = true;
  $("sairBtn").hidden = false;
  iniciarPolling();
}
function mostrarLogin() {
  pararPolling();
  $("sairBtn").hidden = true;
  $("usuarioInfo").textContent = "";
  $("login").hidden = false;
  $("loginUsuario").focus();
}
function iniciarPolling() {
  carregar();
  if (!pollTimer) pollTimer = setInterval(carregar, INTERVALO_MS);
}
function pararPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}
async function aoExpirar() {
  const cred = lerCred();
  if (cred) {
    try { mostrarUsuario(await autenticar(cred.login, cred.senha)); return carregar(); } catch (e) { /* cred velha */ }
  }
  limparSessao();
  mostrarLogin();
}
async function iniciarSessao() {
  const token = getToken();
  if (token) {
    try {
      const r = await fetch("/api/torre/auth/me", { headers: authHeaders() });
      if (r.ok) return entrar(await r.json());
    } catch (e) { /* tenta credencial */ }
  }
  const cred = lerCred();
  if (cred) {
    try { return entrar(await autenticar(cred.login, cred.senha)); } catch (e) { /* mostra login */ }
  }
  mostrarLogin();
}

// ---- Listeners ----

$("loginForm").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const btn = $("loginBtn"), erro = $("loginErro");
  const login = $("loginUsuario").value.trim();
  const senha = $("loginSenha").value;
  const manter = $("loginManter").checked;
  btn.disabled = true; erro.hidden = true;
  try {
    const u = await autenticar(login, senha);
    if (manter) salvarCred(login, senha); else limparCred();
    $("loginSenha").value = "";
    entrar(u);
  } catch (e) {
    erro.textContent = e.message; erro.hidden = false;
  } finally {
    btn.disabled = false;
  }
});
$("sairBtn").addEventListener("click", () => { limparSessao(); limparCred(); mostrarLogin(); });
$("atualizarBtn").addEventListener("click", carregar);
$("exportBtn").addEventListener("click", exportar);
$("limparBtn").addEventListener("click", () => {
  $("fTexto").value = ""; $("fCidade").value = ""; $("fSituacao").value = ""; render();
});
["fTexto", "fCidade", "fSituacao"].forEach(id => $(id).addEventListener("input", render));
$("cabOutros").addEventListener("click", () => {
  const w = $("wrapOutros");
  w.hidden = !w.hidden;
  document.querySelector('[data-sec="outros"]').classList.toggle("recolhido", w.hidden);
});

fetch("/api/versao").then(r => r.ok ? r.json() : null)
  .then(d => { if (d?.versao) $("versao").textContent = "v" + d.versao; })
  .catch(() => {});

iniciarSessao();
