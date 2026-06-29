/* Dashboard de entrada: indicadores do dia, ocupação do armazém (por estágio e
   por box) e situação do pátio. Fonte: /api/torre/dashboard/snapshot. */
(() => {
  const POLL_MS = 30000;
  const T = Torre;

  // Estágios do ciclo dentro do galpão (ordem = pipeline), com cor e rótulo.
  const ESTAGIOS = [
    { key: "aguardandoSeparacao", rot: "Aguardando separação", cor: "var(--est-aguardando)" },
    { key: "emSeparacao", rot: "Em separação", cor: "var(--est-separacao)" },
    { key: "prontos", rot: "Pronto p/ carregar", cor: "var(--est-pronto)" },
    { key: "emCarregamento", rot: "Carregando", cor: "var(--est-carregando)" },
    { key: "avarias", rot: "Avaria / divergência", cor: "var(--est-avaria)" },
  ];

  function kpis(ind) {
    ind = ind || {};
    const cards = [
      ["Pessoas ativas", T.fmtInt.format(ind.pessoasAtivasAgora || 0)],
      ["Horas-homem hoje", T.fmtDuracao(ind.horasHomemHojeSeg)],
      ["Descargas finalizadas", T.fmtInt.format(ind.atividadesFinalizadasHoje || 0)],
      ["Tempo médio descarga", ind.tempoMedioDescargaSeg ? T.fmtDuracao(ind.tempoMedioDescargaSeg) : "—"],
      ["Ocorrências hoje", T.fmtInt.format(ind.ocorrenciasHoje || 0)],
    ];
    return `<div class="kpi-row">${cards.map(([rot, num]) => `
      <div class="kpi"><span class="kpi-num">${num}</span><span class="kpi-rot">${rot}</span></div>`).join("")}</div>`;
  }

  function barraOcupacao(s) {
    const total = s.totalNoArmazem || 0;
    const segs = ESTAGIOS.map(e => {
      const n = s[e.key] || 0;
      if (!n) return "";
      return `<div class="seg" style="width:${T.pct(n, total)}%;background:${e.cor}" title="${e.rot}: ${n}"></div>`;
    }).join("");
    const legenda = ESTAGIOS.map(e => {
      const n = s[e.key] || 0;
      return `<div class="leg">
        <span class="leg-dot" style="background:${e.cor}"></span>
        <span class="leg-rot">${e.rot}</span>
        <span class="leg-num">${T.fmtInt.format(n)}</span>
        <span class="leg-pct">${T.pct(n, total)}%</span>
      </div>`;
    }).join("");
    return `
      <section class="painel">
        <header class="painel-cab"><h2>Ocupação do armazém</h2><span class="contador">${T.fmtInt.format(total)}</span></header>
        <div class="painel-corpo">
          <div class="barra-stack">${segs || '<div class="seg vazia"></div>'}</div>
          <div class="legenda">${legenda}</div>
          ${boxes(s.boxes || [])}
        </div>
      </section>`;
  }

  function boxes(lista) {
    if (!lista.length) {
      return `<p class="dica">Sem documentos endereçados ainda. Os boxes preenchem conforme o app de armazém registra descarga, separação e carregamento.</p>`;
    }
    return `<div class="box-grid">${lista.map(b => `
      <div class="box-card">
        <div class="box-cab"><strong>${T.escapar(b.codigo || "—")}</strong><span>${T.escapar(b.nome || "Sem box")}</span></div>
        <div class="box-total">${T.fmtInt.format(b.total)}</div>
        <div class="box-bd">
          ${b.aguardandoSeparacao ? `<span title="Aguardando separação">⏳ ${b.aguardandoSeparacao}</span>` : ""}
          ${b.emSeparacao ? `<span title="Em separação">🔀 ${b.emSeparacao}</span>` : ""}
          ${b.prontos ? `<span title="Pronto">✅ ${b.prontos}</span>` : ""}
          ${b.emCarregamento ? `<span title="Carregando">🚚 ${b.emCarregamento}</span>` : ""}
          ${b.avarias ? `<span class="alerta" title="Avaria/divergência">⚠️ ${b.avarias}</span>` : ""}
        </div>
      </div>`).join("")}</div>`;
  }

  function patio(s) {
    const proxima = s.proximaChegadaData ? T.dataHora(s.proximaChegadaData, s.proximaChegadaHora) : "—";
    const tiles = [
      ["Em trânsito (chegando)", s.caminhoesEmTransito || 0, "próx. chegada " + proxima],
      ["Aguardando descarga", s.caminhoesAguardando || 0, "caminhões na doca"],
      ["Coletas em trânsito", s.coletasEmTransito || 0, "voltando p/ filial"],
      ["Descargas em andamento", s.descargasEmAndamento || 0, "operando agora"],
    ];
    return `
      <section class="painel">
        <header class="painel-cab"><h2>Pátio &amp; recebimento</h2></header>
        <div class="patio-grid">${tiles.map(([rot, num, sub]) => `
          <div class="patio-tile"><span class="patio-num">${T.fmtInt.format(num)}</span>
            <span class="patio-rot">${rot}</span><span class="patio-sub">${T.escapar(sub)}</span></div>`).join("")}</div>
      </section>`;
  }

  Torre.registrar("/dashboard", {
    titulo: "Dashboard", eyebrow: "Visão geral da operação",
    mount(root) {
      let timer = null;
      async function carregar() {
        try {
          const s = await T.api("/api/torre/dashboard/snapshot" + T.comFilial());
          root.innerHTML = kpis(s.indicadores) + barraOcupacao(s) + patio(s);
          Torre.marcarAtualizado(s.atualizadoEm);
        } catch (e) {
          if (e.message !== "sessao-expirada") Torre.erro("Falha ao carregar dashboard: " + e.message);
        }
      }
      carregar();
      timer = setInterval(carregar, POLL_MS);
      Torre.ligarBotaoAtualizar(carregar);
      return () => clearInterval(timer);
    }
  });
})();
