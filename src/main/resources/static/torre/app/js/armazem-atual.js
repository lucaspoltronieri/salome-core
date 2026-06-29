/* Armazém atual: o que está fisicamente no galpão pelos estados da Torre,
   agrupado por box. Fonte: /api/torre/armazem/por-box. */
(() => {
  const POLL_MS = 20000;
  const T = Torre;

  const STATUS = {
    NO_ARMAZEM: { rot: "Aguardando separação", cls: "st-aguardando" },
    EM_SEPARACAO: { rot: "Em separação", cls: "st-separacao" },
    SEPARADO_BOX: { rot: "Pronto", cls: "st-pronto" },
    EM_CARREGAMENTO: { rot: "Carregando", cls: "st-carregando" },
    AVARIA: { rot: "Avaria", cls: "st-avaria" },
    DIVERGENCIA: { rot: "Divergência", cls: "st-avaria" },
  };
  function badge(status) {
    const s = STATUS[status] || { rot: status, cls: "" };
    return `<span class="status-badge ${s.cls}">${s.rot}</span>`;
  }

  Torre.registrar("/armazem/atual", {
    titulo: "Armazém atual", eyebrow: "Estados próprios da Torre · por box",
    mount(root) {
      let snap = null, boxSel = "", filtro = "", timer = null;

      root.innerHTML = `
        <div class="barra-filtros">
          <input id="aaBusca" class="filtro" placeholder="Buscar CT-e, destinatário, cidade…">
          <span class="filtro-info" id="aaInfo"></span>
        </div>
        <div id="aaChips" class="chip-row"></div>
        <section class="painel">
          <div class="tabela-wrap">
            <table class="tabela">
              <thead><tr>
                <th>Data emissão</th><th>CT-e</th><th>Destinatário</th><th>Cidade</th>
                <th class="num">Volumes</th><th class="num">Peso (kg)</th><th>Box</th><th>Situação</th>
              </tr></thead>
              <tbody id="aaBody"></tbody>
            </table>
            <p class="vazio" id="aaVazio" hidden>Nenhum documento no armazém para este filtro.</p>
          </div>
        </section>`;

      const $ = id => document.getElementById(id);
      $("aaBusca").addEventListener("input", () => { filtro = $("aaBusca").value.trim().toLowerCase(); render(); });
      $("aaChips").addEventListener("click", ev => {
        const c = ev.target.closest("[data-box]");
        if (!c) return;
        boxSel = c.getAttribute("data-box");
        render();
      });

      function chips() {
        const boxes = (snap.boxes || []);
        const total = boxes.reduce((s, b) => s + b.total, 0);
        const all = `<button class="chip ${boxSel === "" ? "ativo" : ""}" data-box="">Todos <b>${total}</b></button>`;
        return all + boxes.map(b => {
          const id = b.idLocal == null ? "sembox" : String(b.idLocal);
          const ativo = boxSel === id ? "ativo" : "";
          return `<button class="chip ${ativo}" data-box="${id}">${T.escapar(b.codigo || b.nome)} <b>${b.total}</b></button>`;
        }).join("");
      }

      function casa(d) {
        if (!filtro) return true;
        return [d.numeroCte, d.destinatario, d.cidadeDestino, d.remetente]
          .some(v => (v ?? "").toString().toLowerCase().includes(filtro));
      }
      function boxId(d) { return d.idLocal == null ? "sembox" : String(d.idLocal); }

      function render() {
        if (!snap) return;
        document.getElementById("aaChips").innerHTML = chips();
        let docs = (snap.documentos || []).filter(casa);
        if (boxSel) docs = docs.filter(d => boxId(d) === boxSel);
        const body = document.getElementById("aaBody");
        body.innerHTML = docs.map(d => `<tr>
          <td>${T.fmtData(d.dataEmissao) || "—"}</td>
          <td>${d.numeroCte ? T.escapar(d.numeroCte) : `<span class="pre">NF (pré-CT-e)</span>`}</td>
          <td>${T.escapar(d.destinatario)}</td>
          <td>${T.escapar(d.cidadeDestino)}</td>
          <td class="num">${T.fmtInt.format(d.volumes || 0)}</td>
          <td class="num">${T.fmtPeso.format(+d.peso || 0)}</td>
          <td>${T.escapar(d.codigoLocal || d.nomeLocal || "—")}</td>
          <td>${badge(d.status)}</td>
        </tr>`).join("");
        document.getElementById("aaVazio").hidden = docs.length > 0;
        document.getElementById("aaInfo").textContent = `${docs.length} documento(s)`;
      }

      async function carregar() {
        try {
          snap = await T.api("/api/torre/armazem/por-box" + T.comFilial());
          render();
          Torre.marcarAtualizado(snap.atualizadoEm);
        } catch (e) {
          if (e.message !== "sessao-expirada") Torre.erro("Falha ao carregar armazém: " + e.message);
        }
      }
      carregar();
      timer = setInterval(carregar, POLL_MS);
      Torre.ligarBotaoAtualizar(carregar);
      return () => clearInterval(timer);
    }
  });
})();
