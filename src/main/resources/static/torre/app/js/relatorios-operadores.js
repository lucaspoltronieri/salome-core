/* Relatório de operadores: o que cada pessoa fez no período — tempo por atividade
   e relação de CT-es (peso, volumes, remetente, destinatário e tempo aprox. por CT-e,
   derivado do horário de cada marcação). Fonte: /api/torre/relatorios/operadores. */
(() => {
  const T = Torre;

  const TIPO_ROT = {
    DESCARGA_TRANSFERENCIA: "Descarga · transferência",
    DESCARGA_COLETA: "Descarga · coleta",
    SEPARACAO: "Separação",
    CARREGAMENTO: "Carregamento",
    OUTRAS: "Outras",
  };
  const STATUS_ROT = {
    AGUARDANDO_DESCARGA: "Aguardando descarga",
    EM_DESCARGA: "Em descarga",
    NO_ARMAZEM: "No armazém",
    EM_SEPARACAO: "Em separação",
    SEPARADO_BOX: "Pronto (box)",
    EM_CARREGAMENTO: "Em carregamento",
    CARREGADO: "Carregado",
    CROSS_DOCK_DIRETO: "Cross-dock",
    AVARIA: "Avaria",
    DIVERGENCIA: "Divergência",
  };
  function tipoRot(a) {
    if (a.tipo === "OUTRAS" && a.subtipo) return "Outras · " + a.subtipo;
    return TIPO_ROT[a.tipo] || a.tipo;
  }
  function hojeStr() {
    const d = new Date();
    const z = n => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())}`;
  }

  function tabelaCtes(ctes) {
    if (!ctes.length) return '<p class="dica">Nenhum CT-e marcado nesta atividade.</p>';
    const linhas = ctes.map(c => `<tr>
      <td>${c.numeroCte ?? "—"}</td>
      <td class="num">${T.fmtInt.format(c.volumes || 0)}</td>
      <td class="num">${T.fmtPeso.format(c.peso || 0)}</td>
      <td>${T.escapar(c.remetente) || "—"}</td>
      <td>${T.escapar(c.destinatario) || "—"}</td>
      <td>${T.escapar(c.cidadeDestino) || "—"}</td>
      <td class="num">${c.segundosNoCte == null ? "1º" : T.fmtDuracao(c.segundosNoCte)}</td>
      <td>${T.escapar(STATUS_ROT[c.status] || c.status)}</td>
    </tr>`).join("");
    return `<div class="tabela-wrap"><table class="tabela"><thead><tr>
        <th>CT-e</th><th class="num">Vol</th><th class="num">Peso (kg)</th>
        <th>Remetente</th><th>Destinatário</th><th>Cidade</th><th class="num">Tempo CT-e</th><th>Situação</th>
      </tr></thead><tbody>${linhas}</tbody></table></div>`;
  }

  function blocoOperador(op) {
    const atvs = op.atividades.map(a => `
      <div class="atv-card" style="grid-column:1/-1">
        <div class="atv-cab">
          <span class="atv-tipo">#${a.idAtividade} · ${T.escapar(tipoRot(a))}</span>
          <span class="atv-meta">${T.escapar(a.placa) || "—"} · tempo ${T.fmtDuracao(a.segundos)}</span>
        </div>
        ${tabelaCtes(a.ctes || [])}
      </div>`).join("");
    return `<section class="painel">
      <header class="painel-cab">
        <h2>${T.escapar(op.nome)}</h2>
        <span class="contador">${op.totalCtes} CT-es · ${T.fmtInt.format(op.totalVolumes)} vol · ${T.fmtPeso.format(op.totalPeso || 0)} kg · ${T.fmtDuracao(op.totalSegundos)}</span>
      </header>
      <div class="painel-corpo"><div class="atv-grid">${atvs || '<p class="vazio">Sem atividades no período.</p>'}</div></div>
    </section>`;
  }

  Torre.registrar("/relatorios/operadores", {
    titulo: "Relatório de operadores", eyebrow: "O que cada um fez · tempo e CT-es",
    mount(root) {
      const hoje = hojeStr();
      root.innerHTML = `
        <section class="painel">
          <div class="painel-corpo" style="display:flex;gap:12px;align-items:flex-end;flex-wrap:wrap">
            <label>De<br><input type="date" id="relDe" value="${hoje}"></label>
            <label>Até<br><input type="date" id="relAte" value="${hoje}"></label>
            <button class="btn" id="relBtn">Gerar</button>
            <p class="dica" style="margin:0">Tempo por CT-e é aproximado (intervalo entre marcações). No modo "marcar todos" os CT-es ficam no mesmo instante.</p>
          </div>
        </section>
        <div id="relSaida"></div>`;

      async function carregar() {
        const de = document.getElementById("relDe").value;
        const ate = document.getElementById("relAte").value;
        const saida = document.getElementById("relSaida");
        saida.innerHTML = '<p class="dica">Carregando…</p>';
        try {
          const qs = T.comFilial(`de=${de}&ate=${ate}`);
          const rel = await T.api("/api/torre/relatorios/operadores" + qs);
          saida.innerHTML = (rel.operadores || []).length
            ? rel.operadores.map(blocoOperador).join("")
            : '<section class="painel"><div class="painel-corpo"><p class="vazio">Nenhum operador com atividade no período.</p></div></section>';
          Torre.marcarAtualizado(new Date().toISOString());
        } catch (e) {
          if (e.message !== "sessao-expirada") Torre.erro("Falha ao gerar relatório: " + e.message);
        }
      }

      root.querySelector("#relBtn").addEventListener("click", carregar);
      carregar();
      Torre.ligarBotaoAtualizar(carregar);
      return () => {};
    }
  });
})();
