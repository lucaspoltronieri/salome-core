/* Aguardando descarga, em 3 estágios do recebimento:
   1) Em trânsito (caminhões vindo de outras bases, com previsão de chegada),
   2) Chegou / baixado (na doca, pronto p/ descarregar),
   3) Coleta em trânsito (voltando p/ a própria filial).
   Fonte: /api/torre/mapa/snapshot + /api/torre/viagens/coletas-aguardando. */
(() => {
  const POLL_MS = 20000;
  const T = Torre;

  function blocoCaminhoes(titulo, lista, comChegada) {
    const linhas = (lista || []).map(c => `<tr>
      <td class="placa">${T.escapar(c.placa) || "—"}</td>
      <td>${T.escapar(T.limparOrigem(c.origem))}</td>
      <td>${T.escapar(c.motorista) || "—"}</td>
      ${comChegada ? `<td>${T.dataHora(c.dataPrevisaoChegada, c.horaPrevisaoChegada)}</td>` : ""}
      <td class="num">${T.fmtInt.format(c.qtdCtes || 0)}</td>
      <td class="num">${T.fmtInt.format(+c.volumes || 0)}</td>
      <td class="num">${T.fmtPeso.format(+c.peso || 0)}</td>
    </tr>`).join("");
    return painel(titulo, lista.length, `
      <table class="tabela"><thead><tr>
        <th>Placa</th><th>Origem</th><th>Motorista</th>
        ${comChegada ? "<th>Previsão chegada</th>" : ""}
        <th class="num">CT-es</th><th class="num">Volumes</th><th class="num">Peso (kg)</th>
      </tr></thead><tbody>${linhas}</tbody></table>
      ${lista.length ? "" : '<p class="vazio">Nada aqui agora.</p>'}`);
  }

  // Vários idViagemTransferencia (manifestos) do mesmo idViagem chegam no mesmo
  // caminhão: agrupamos por idViagem (fallback idViagemTransferencia quando nulo),
  // igual ao painel TV, para virar uma linha só com selo ×N. Veja painel/app.js.
  function agruparAguardando(viagens) {
    const grupos = new Map();
    for (const v of viagens || []) {
      const chave = v.idViagem != null ? "g" + v.idViagem : "t" + v.idViagemTransferencia;
      let g = grupos.get(chave);
      if (!g) {
        g = { placa: v.placa, origem: v.origem, dataBaixa: v.dataBaixa, horaBaixa: v.horaBaixa,
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

  function blocoAguardando(titulo, viagens) {
    const grupos = agruparAguardando(viagens);
    const linhas = grupos.map(g => {
      const selo = g.manifestos > 1
        ? ` <span class="badge-grupo" title="${g.manifestos} manifestos na mesma viagem">×${g.manifestos}</span>`
        : "";
      return `<tr>
      <td class="placa">${T.escapar(g.placa) || "—"}${selo}</td>
      <td>${T.escapar(T.limparOrigem(g.origem))}</td>
      <td>${T.dataHora(g.dataBaixa, g.horaBaixa)}</td>
      <td class="num">${T.fmtInt.format(g.qtdCtes || 0)}</td>
      <td class="num">${T.fmtInt.format(+g.volumes || 0)}</td>
      <td class="num">${T.fmtPeso.format(+g.peso || 0)}</td>
    </tr>`;
    }).join("");
    return painel(titulo, grupos.length, `
      <table class="tabela"><thead><tr>
        <th>Placa</th><th>Origem</th><th>Baixa</th>
        <th class="num">CT-es</th><th class="num">Volumes</th><th class="num">Peso (kg)</th>
      </tr></thead><tbody>${linhas}</tbody></table>
      ${grupos.length ? "" : '<p class="vazio">Nenhum caminhão aguardando descarga.</p>'}`);
  }

  function blocoDescarregando(titulo, atividades) {
    const linhas = (atividades || []).map(a => {
      const ativos = (a.participantes || []).filter(p => !p.saidaEm).length;
      return `<tr>
        <td>${T.escapar(a.idViagemLegado) || "—"}</td>
        <td class="placa">${T.escapar(a.placaVeiculo) || "—"}</td>
        <td class="num">${T.fmtInt.format(ativos)}</td>
        <td class="num">${T.tempoDesde(a.iniciadaEm)}</td>
      </tr>`;
    }).join("");
    return painel(titulo, atividades.length, `
      <table class="tabela"><thead><tr>
        <th>Viagem</th><th>Placa</th><th class="num">Pessoas</th><th class="num">Tempo</th>
      </tr></thead><tbody>${linhas}</tbody></table>
      ${atividades.length ? "" : '<p class="vazio">Nenhuma descarga em andamento.</p>'}`);
  }

  function painel(titulo, contador, corpo) {
    return `<section class="painel">
      <header class="painel-cab"><h2>${titulo}</h2><span class="contador">${T.fmtInt.format(contador)}</span></header>
      <div class="tabela-wrap">${corpo}</div>
    </section>`;
  }

  Torre.registrar("/armazem/aguardando", {
    titulo: "Aguardando descarga", eyebrow: "Recebimento · do pátio à doca",
    mount(root) {
      let timer = null;
      async function carregar() {
        try {
          const [mapa, coletas] = await Promise.all([
            T.api("/api/torre/mapa/snapshot" + T.comFilial()),
            T.api("/api/torre/viagens/coletas-aguardando" + T.comFilial()),
          ]);
          root.innerHTML =
            blocoCaminhoes("Em trânsito — chegando", mapa.vindoDeOutrasBases || [], true) +
            blocoAguardando("Chegou / baixado — na doca", mapa.aguardandoDescarga || []) +
            blocoDescarregando("Descarregando agora", mapa.descarregando || []) +
            blocoCaminhoes("Coleta em trânsito", coletas || [], false);
          Torre.marcarAtualizado(mapa.atualizadoEm);
        } catch (e) {
          if (e.message !== "sessao-expirada") Torre.erro("Falha ao carregar aguardando: " + e.message);
        }
      }
      carregar();
      timer = setInterval(carregar, POLL_MS);
      Torre.ligarBotaoAtualizar(carregar);
      return () => clearInterval(timer);
    }
  });
})();
