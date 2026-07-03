/* Produtividade por carga: 1 linha por caminhão que chegou e descarregou (agrega as
   atividades de descarga da mesma viagem). Três leituras de tempo lado a lado —
   Janela (início→fim, inclui parada), Efetivo (união das presenças, desconta parada)
   e Horas-homem (soma por pessoa) — além de produtividade (peso/h-h). Drill-down por
   atividade/pessoa. Fonte: /api/torre/relatorios/cargas (+ /cargas/export para XLSX). */
(() => {
  const T = Torre;

  const TIPO_ROT = { DESCARGA_TRANSFERENCIA: "Transferência", DESCARGA_COLETA: "Coleta" };
  function tipoRot(t) { return TIPO_ROT[t] || t || "—"; }

  function hojeStr() {
    const d = new Date();
    const z = n => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())}`;
  }
  function fmtHora(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    const z = n => String(n).padStart(2, "0");
    return `${z(d.getDate())}/${z(d.getMonth() + 1)} ${z(d.getHours())}:${z(d.getMinutes())}`;
  }
  function pesoHora(c) {
    return c.horasHomemSeg > 0 ? (Number(c.peso || 0) * 3600) / c.horasHomemSeg : 0;
  }

  // Colunas ordenáveis: chave → função que extrai o valor comparável da carga.
  const COLS = [
    { k: "placa", rot: "Placa", val: c => (c.placa || "").toLowerCase(), tipo: "txt" },
    { k: "origem", rot: "Origem", val: c => (c.origem || "").toLowerCase(), tipo: "txt" },
    { k: "inicio", rot: "Início", val: c => c.inicio || "", tipo: "txt", fmt: c => fmtHora(c.inicio) },
    { k: "fim", rot: "Fim", val: c => c.fim || "", tipo: "txt", fmt: c => fmtHora(c.fim) },
    { k: "janelaSeg", rot: "Janela", num: true, dica: "Do início da 1ª descarga ao fim (relógio de parede — inclui parada).", fmt: c => T.fmtDuracao(c.janelaSeg) },
    { k: "efetivoSeg", rot: "Efetivo", num: true, dica: "União das presenças: tempo com ao menos 1 pessoa (desconta a parada, se derem 'Sair').", fmt: c => T.fmtDuracao(c.efetivoSeg) },
    { k: "horasHomemSeg", rot: "Horas-homem", num: true, dica: "Soma do tempo de cada pessoa (mede paralelismo/nº de gente).", fmt: c => T.fmtDuracao(c.horasHomemSeg) },
    { k: "qtdOperadores", rot: "Pessoas", num: true, fmt: c => T.fmtInt.format(c.qtdOperadores) },
    { k: "qtdCtes", rot: "CT-es", num: true, fmt: c => T.fmtInt.format(c.qtdCtes) },
    { k: "volumes", rot: "Vol", num: true, fmt: c => T.fmtInt.format(c.volumes) },
    { k: "peso", rot: "Peso (kg)", num: true, fmt: c => T.fmtPeso.format(Number(c.peso || 0)) },
    { k: "pesoHora", rot: "Peso/h-h", num: true, dica: "Produtividade: kg por hora-homem.", val: c => pesoHora(c), fmt: c => T.fmtPeso.format(pesoHora(c)) },
  ];

  Torre.registrar("/relatorios/produtividade-cargas", {
    titulo: "Produtividade por carga",
    eyebrow: "Tempo, pessoas e produtividade por caminhão",
    mount(root) {
      const hoje = hojeStr();
      let dados = null;              // { totais, cargas } cru da API
      let ordem = { k: null, dir: 1 };
      const abertas = new Set();     // idViagem com drill-down aberto

      root.innerHTML = `
        <section class="painel">
          <div class="painel-corpo" style="display:flex;gap:12px;align-items:flex-end;flex-wrap:wrap">
            <label>De<br><input type="date" id="pcDe" value="${hoje}"></label>
            <label>Até<br><input type="date" id="pcAte" value="${hoje}"></label>
            <label>Placa<br><input type="text" id="pcPlaca" placeholder="Filtrar placa…" style="text-transform:uppercase"></label>
            <label>Tipo<br>
              <select id="pcTipo">
                <option value="">Todas</option>
                <option value="DESCARGA_TRANSFERENCIA">Transferência</option>
                <option value="DESCARGA_COLETA">Coleta</option>
              </select>
            </label>
            <button class="btn" id="pcBtn">Gerar</button>
            <button class="btn btn-export" id="pcExport">Exportar Excel</button>
            <p class="dica" style="margin:0;flex:1 1 240px">
              <b>Efetivo</b> só desconta a parada se o operador der <b>"Sair"</b> ao se ausentar; senão
              incha até a <b>Janela</b>. As 3 medidas ficam lado a lado de propósito.
            </p>
          </div>
        </section>
        <div id="pcKpis"></div>
        <div id="pcSaida"></div>`;

      const $ = id => document.getElementById(id);

      function cargasFiltradas() {
        if (!dados) return [];
        const placa = $("pcPlaca").value.trim().toLowerCase();
        const tipo = $("pcTipo").value;
        let cs = (dados.cargas || []).filter(c =>
          (!placa || (c.placa || "").toLowerCase().includes(placa)) &&
          (!tipo || c.tipo === tipo));
        if (ordem.k) {
          const col = COLS.find(x => x.k === ordem.k);
          const val = col.val || (c => c[ordem.k] ?? 0);
          cs = cs.slice().sort((a, b) => {
            const va = val(a), vb = val(b);
            if (va < vb) return -ordem.dir;
            if (va > vb) return ordem.dir;
            return 0;
          });
        }
        return cs;
      }

      function renderKpis(cs) {
        const t = dados.totais || {};
        const card = (rot, val) => `<div class="kpi"><span class="kpi-rot">${rot}</span><span class="kpi-num">${val}</span></div>`;
        // Recalcula produtividade média sobre o que está filtrado (mais intuitivo).
        const somaPeso = cs.reduce((s, c) => s + Number(c.peso || 0), 0);
        const somaHH = cs.reduce((s, c) => s + (c.horasHomemSeg || 0), 0);
        const prod = somaHH > 0 ? (somaPeso * 3600) / somaHH : 0;
        $("pcKpis").innerHTML = `<div class="kpi-row">
          ${card("Cargas", T.fmtInt.format(cs.length))}
          ${card("Janela mediana", T.fmtDuracao(t.janelaMedianaSeg))}
          ${card("Efetivo mediano", T.fmtDuracao(t.efetivoMedianaSeg))}
          ${card("Horas-homem (total)", T.fmtDuracao(t.horasHomemTotalSeg))}
          ${card("Produtividade", T.fmtPeso.format(prod) + " kg/h-h")}
        </div>`;
      }

      function detalhe(c) {
        const linhas = (c.atividades || []).map(a => {
          const parts = (a.participantes || []).map(p => `<tr>
            <td>${T.escapar(p.nome)}</td>
            <td>${fmtHora(p.entradaEm)}</td>
            <td>${p.saidaEm ? fmtHora(p.saidaEm) : '<span class="dica">em aberto</span>'}</td>
            <td class="num">${T.fmtDuracao(p.segundos)}</td>
          </tr>`).join("");
          return `<div class="atv-card" style="grid-column:1/-1">
            <div class="atv-cab"><span class="atv-tipo">Atividade #${a.idAtividade}</span>
              <span class="atv-meta">janela ${T.fmtDuracao(a.janelaSeg)}</span></div>
            <div class="tabela-wrap"><table class="tabela"><thead><tr>
              <th>Operador</th><th>Entrada</th><th>Saída</th><th class="num">Tempo</th>
            </tr></thead><tbody>${parts || '<tr><td colspan="4" class="dica">Sem participantes.</td></tr>'}</tbody></table></div>
          </div>`;
        }).join("");
        return `<td colspan="${COLS.length + 1}"><div class="atv-grid">${linhas || '<p class="dica">Sem atividades.</p>'}</div></td>`;
      }

      function render() {
        if (!dados) return;
        const cs = cargasFiltradas();
        renderKpis(cs);
        if (!cs.length) {
          $("pcSaida").innerHTML = '<section class="painel"><div class="painel-corpo"><p class="vazio">Nenhuma carga descarregada no período/filtro.</p></div></section>';
          return;
        }
        const seta = k => ordem.k === k ? (ordem.dir === 1 ? " ▲" : " ▼") : "";
        const th = COLS.map(col => `<th class="${col.num ? "num" : ""} pc-th" data-k="${col.k}"${col.dica ? ` title="${T.escapar(col.dica)}"` : ""}>${col.rot}${seta(col.k)}</th>`).join("");
        const linhas = cs.map(c => {
          const aberta = abertas.has(c.idViagem);
          const tds = COLS.map(col => `<td class="${col.num ? "num" : ""}">${col.fmt ? col.fmt(c) : T.escapar(c[col.k])}</td>`).join("");
          const detrow = aberta ? `<tr class="pc-det">${detalhe(c)}</tr>` : "";
          return `<tr class="pc-row" data-id="${c.idViagem}" style="cursor:pointer">
            <td>${aberta ? "▾" : "▸"}</td>${tds}</tr>${detrow}`;
        }).join("");
        $("pcSaida").innerHTML = `<section class="painel"><div class="tabela-wrap">
          <table class="tabela"><thead><tr><th></th>${th}</tr></thead><tbody>${linhas}</tbody></table>
        </div></section>`;

        $("pcSaida").querySelectorAll(".pc-th").forEach(el => el.addEventListener("click", () => {
          const k = el.dataset.k;
          ordem = { k, dir: ordem.k === k ? -ordem.dir : 1 };
          render();
        }));
        $("pcSaida").querySelectorAll(".pc-row").forEach(el => el.addEventListener("click", () => {
          const id = Number(el.dataset.id);
          if (abertas.has(id)) abertas.delete(id); else abertas.add(id);
          render();
        }));
      }

      async function carregar() {
        const de = $("pcDe").value, ate = $("pcAte").value;
        $("pcSaida").innerHTML = '<p class="dica">Carregando…</p>';
        $("pcKpis").innerHTML = "";
        try {
          const qs = T.comFilial(`de=${de}&ate=${ate}`);
          dados = await T.api("/api/torre/relatorios/cargas" + qs);
          render();
          Torre.marcarAtualizado(new Date().toISOString());
        } catch (e) {
          if (e.message !== "sessao-expirada") Torre.erro("Falha ao gerar relatório: " + e.message);
        }
      }

      async function exportar() {
        const de = $("pcDe").value, ate = $("pcAte").value;
        const qs = new URLSearchParams(`de=${de}&ate=${ate}`);
        const fq = T.comFilial();
        if (fq) new URLSearchParams(fq.slice(1)).forEach((v, k) => qs.set(k, v));
        try {
          const r = await fetch("/api/torre/relatorios/cargas/export?" + qs.toString(), { headers: T.authHeaders() });
          if (!r.ok) throw new Error("HTTP " + r.status);
          const blob = await r.blob();
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url; a.download = `produtividade-cargas-${de}_a_${ate}.xlsx`;
          document.body.appendChild(a); a.click(); a.remove();
          URL.revokeObjectURL(url);
        } catch (e) { Torre.erro("Falha ao exportar: " + e.message); }
      }

      $("pcBtn").addEventListener("click", carregar);
      $("pcExport").addEventListener("click", exportar);
      $("pcPlaca").addEventListener("input", render);
      $("pcTipo").addEventListener("change", render);
      carregar();
      Torre.ligarBotaoAtualizar(carregar);
      return () => {};
    }
  });
})();
