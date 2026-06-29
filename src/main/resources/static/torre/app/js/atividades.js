/* Atividades & operadores: quem está logado/trabalhando no celular agora (por
   atividade) e correção pela web — cancelar uma atividade aberta por engano
   (ex.: descarga aberta errada, antes de bipar nada) ou finalizá-la.
   Fonte: /api/torre/atividades/abertas; ações: .../cancelar e .../finalizar.
   Escopo: ADMIN corrige qualquer filial (pelo seletor de filial); o operador
   comum só corrige a própria filial (o backend trava pelo perfil do token). */
(() => {
  const POLL_MS = 15000;
  const T = Torre;

  const TIPO_ROT = {
    DESCARGA_TRANSFERENCIA: "Descarga · transferência",
    DESCARGA_COLETA: "Descarga · coleta",
    SEPARACAO: "Separação",
    CARREGAMENTO: "Carregamento",
    OUTRAS: "Outras",
  };
  function tipoRot(a) {
    if (a.tipo === "OUTRAS" && a.subtipo) return "Outras · " + a.subtipo;
    return TIPO_ROT[a.tipo] || a.tipo;
  }
  function ativos(a) { return (a.participantes || []).filter(p => !p.saidaEm); }

  // Operadores logados agora (achatado das atividades abertas).
  function blocoOperadores(abertas) {
    const linhas = [];
    abertas.forEach(a => ativos(a).forEach(p => linhas.push({ p, a })));
    const corpo = linhas.map(({ p, a }) => `<tr>
      <td><strong>${T.escapar(p.nomeUsuario)}</strong></td>
      <td>${T.escapar(p.funcao) || "—"}</td>
      <td>${T.escapar(tipoRot(a))}</td>
      <td class="placa">${T.escapar(a.placaVeiculo) || (a.idViagemLegado ? "viagem " + a.idViagemLegado : "—")}</td>
      <td>${T.escapar(p.dispositivo) || "APP"}</td>
      <td class="num">${T.tempoDesde(p.entradaEm)}</td>
    </tr>`).join("");
    return `<section class="painel">
      <header class="painel-cab"><h2>Operadores ativos agora</h2><span class="contador">${linhas.length}</span></header>
      <div class="tabela-wrap"><table class="tabela"><thead><tr>
        <th>Operador</th><th>Função</th><th>Atividade</th><th>Placa / viagem</th><th>Origem</th><th class="num">Há</th>
      </tr></thead><tbody>${corpo}</tbody></table>
      ${linhas.length ? "" : '<p class="vazio">Ninguém logado/trabalhando agora.</p>'}</div>
    </section>`;
  }

  // Atividades abertas com ações de correção.
  function blocoAtividades(abertas) {
    const cards = abertas.map(a => {
      const at = ativos(a);
      const pessoas = at.length
        ? at.map(p => `<span class="chip-mini">${T.escapar(p.nomeUsuario)}${p.funcao ? " · " + T.escapar(p.funcao) : ""}</span>`).join("")
        : '<span class="dica">sem ninguém ativo</span>';
      return `<div class="atv-card">
        <div class="atv-cab">
          <span class="atv-tipo">${T.escapar(tipoRot(a))}</span>
          <span class="atv-meta">${T.escapar(a.placaVeiculo) || (a.idViagemLegado ? "viagem " + a.idViagemLegado : "")}</span>
        </div>
        <div class="atv-tempo">aberta há ${T.tempoDesde(a.iniciadaEm)}</div>
        <div class="atv-pessoas">${pessoas}</div>
        <div class="atv-acoes">
          <button class="btn btn-ghost btn-sm" data-fin="${a.id}">Finalizar</button>
          <button class="btn btn-cancel btn-sm" data-cancel="${a.id}">Cancelar</button>
        </div>
      </div>`;
    }).join("");
    return `<section class="painel">
      <header class="painel-cab"><h2>Atividades abertas</h2><span class="contador">${abertas.length}</span></header>
      <div class="painel-corpo">
        ${abertas.length ? `<div class="atv-grid">${cards}</div>` : '<p class="vazio">Nenhuma atividade aberta.</p>'}
        <p class="dica">Cancelar só é permitido enquanto nada foi bipado (corrige uma abertura errada). Depois disso, use Finalizar.</p>
      </div>
    </section>`;
  }

  Torre.registrar("/atividades", {
    titulo: "Atividades & operadores", eyebrow: "Quem está no celular · correções",
    mount(root) {
      let timer = null;

      async function carregar() {
        try {
          const abertas = await T.api("/api/torre/atividades/abertas" + T.comFilial());
          root.innerHTML = blocoOperadores(abertas) + blocoAtividades(abertas);
          Torre.marcarAtualizado(new Date().toISOString());
        } catch (e) {
          if (e.message !== "sessao-expirada") Torre.erro("Falha ao carregar atividades: " + e.message);
        }
      }

      root.addEventListener("click", async ev => {
        const cancel = ev.target.closest("[data-cancel]");
        const fin = ev.target.closest("[data-fin]");
        if (cancel) {
          const motivo = window.prompt("Motivo do cancelamento (ex.: descarga aberta errada):");
          if (motivo == null || !motivo.trim()) return;
          const r = await T.post(`/api/torre/atividades/${cancel.getAttribute("data-cancel")}/cancelar${T.comFilial()}`, { motivo: motivo.trim() });
          if (r.ok) carregar(); else Torre.erro(await msgErro(r));
        } else if (fin) {
          if (!window.confirm("Finalizar esta atividade? O tempo dos operadores será encerrado.")) return;
          const r = await T.post(`/api/torre/atividades/${fin.getAttribute("data-fin")}/finalizar${T.comFilial()}`);
          if (r.ok) carregar(); else Torre.erro(await msgErro(r));
        }
      });

      async function msgErro(r) {
        let m = "";
        try { const j = await r.json(); m = j.message || j.erro || ""; } catch (e) {}
        if (r.status === 409) return m || "Operação não permitida no estado atual.";
        if (r.status === 404) return "Atividade não encontrada na filial selecionada.";
        if (r.status === 403) return "Sem permissão para corrigir esta filial.";
        return m || ("Falha (HTTP " + r.status + ").");
      }

      carregar();
      timer = setInterval(carregar, POLL_MS);
      Torre.ligarBotaoAtualizar(carregar);
      return () => clearInterval(timer);
    }
  });
})();
