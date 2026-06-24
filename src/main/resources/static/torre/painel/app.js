const params = new URLSearchParams(location.search);
const FILIAL = params.get("filial") || "2";
const INTERVALO_MS = 15000;

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

async function carregar() {
  const erro = document.getElementById("erro");
  try {
    const r = await fetch(`/api/torre/painel/snapshot?filial=${FILIAL}`, { cache: "no-store" });
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

fetch("/api/versao").then(r => r.ok ? r.json() : null)
  .then(d => { if (d?.versao) document.getElementById("versao").textContent = "v" + d.versao; })
  .catch(() => {});

relogio();
setInterval(relogio, 1000);
carregar();
setInterval(carregar, INTERVALO_MS);
