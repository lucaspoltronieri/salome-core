/* Ocorrências (avarias): lista com filtros + drawer de detalhe com fotos, CT-es,
   itens e o ciclo de tratamento (análise → pagamento → resolvida → finalizada).
   Consome /api/torre/ocorrencias (GET lista/detalhe, PATCH tratamento). */
(() => {
  const T = Torre;

  const STATUS = {
    REGISTRADA:           { rotulo: "Registrada",           badge: "st-avaria" },
    EM_ANALISE:           { rotulo: "Em análise",           badge: "st-analise" },
    AGUARDANDO_PAGAMENTO: { rotulo: "Aguardando pagamento",  badge: "st-aguardando" },
    RESOLVIDA:            { rotulo: "Resolvida",             badge: "st-pronto" },
    FINALIZADA:           { rotulo: "Finalizada",            badge: "st-pronto" },
    CANCELADA:            { rotulo: "Cancelada",             badge: "st-cancelada" },
  };
  // Transições oferecidas como botões, por status atual.
  const ACOES = {
    REGISTRADA:           [["EM_ANALISE", "Iniciar análise"], ["CANCELADA", "Cancelar"]],
    EM_ANALISE:           [["AGUARDANDO_PAGAMENTO", "Aguardando pagamento"], ["RESOLVIDA", "Resolver"], ["CANCELADA", "Cancelar"]],
    AGUARDANDO_PAGAMENTO: [["RESOLVIDA", "Resolver"], ["FINALIZADA", "Finalizar"], ["CANCELADA", "Cancelar"]],
    RESOLVIDA:            [["FINALIZADA", "Finalizar"], ["CANCELADA", "Cancelar"]],
    FINALIZADA:           [],
    CANCELADA:            [],
  };
  const CULPA = { CARREGAMENTO: "Carregamento", VIAGEM: "Viagem (motorista)", DESCARREGAMENTO: "Descarregamento" };
  const fmtBRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

  function badge(status) {
    const s = STATUS[status] || { rotulo: status, badge: "" };
    return `<span class="status-badge ${s.badge}">${T.escapar(s.rotulo)}</span>`;
  }
  function dataHoraIso(iso) { return iso ? new Date(iso).toLocaleString("pt-BR") : "—"; }
  function valor(v) { return v == null ? "—" : fmtBRL.format(Number(v)); }

  let filtroStatus = "";
  let objectUrls = [];

  async function carregar() {
    const qs = new URLSearchParams(T.comFilial());
    if (filtroStatus) qs.set("status", filtroStatus);
    const s = qs.toString();
    const lista = await T.api("/api/torre/ocorrencias" + (s ? "?" + s : ""));
    const tb = document.getElementById("tbOcorr");
    tb.innerHTML = lista.map(o => `<tr data-id="${o.id}">
      <td>${dataHoraIso(o.criadoEm)}</td>
      <td>${T.escapar(o.tipo)}</td>
      <td>${badge(o.status)}</td>
      <td>${T.escapar(o.placaVeiculo || "—")}${o.motorista ? " · " + T.escapar(o.motorista) : ""}</td>
      <td class="num">${valor(o.valorTotal)}</td>
      <td>${o.culpa ? T.escapar(CULPA[o.culpa] || o.culpa) : "—"}</td>
      <td><button class="btn btn-ghost btn-sm" data-abrir="${o.id}">Ver</button></td>
    </tr>`).join("");
    document.getElementById("vazioOcorr").hidden = lista.length > 0;
    T.marcarAtualizado(new Date().toISOString());
  }

  function liberarImagens() {
    objectUrls.forEach(u => URL.revokeObjectURL(u));
    objectUrls = [];
  }

  async function abrirDetalhe(id) {
    const d = await T.api("/api/torre/ocorrencias/" + id + T.comFilial());
    const o = d.cabecalho;
    const overlay = document.getElementById("drawerOverlay");
    const box = document.getElementById("drawerBox");
    const acoes = (ACOES[o.status] || []).map(([st, txt]) =>
      `<button class="btn ${st === "CANCELADA" ? "btn-cancel" : ""}" data-acao="${st}">${txt}</button>`).join("");

    box.innerHTML = `
      <div class="drawer-cab">
        <div>
          <p class="eyebrow">Ocorrência #${o.id} · ${T.escapar(o.tipo)}</p>
          <h2>${badge(o.status)} ${valor(o.valorTotal)}</h2>
        </div>
        <button class="btn btn-ghost btn-sm" id="fecharDrawer">fechar</button>
      </div>

      <div class="drawer-grid">
        <div><span>Placa</span><strong>${T.escapar(o.placaVeiculo || "—")}</strong></div>
        <div><span>Motorista</span><strong>${T.escapar(o.motorista || "—")}</strong></div>
        <div><span>Identificada</span><strong>${dataHoraIso(o.dataIdentificacao)}</strong></div>
        <div><span>Tempo de registro</span><strong>${o.duracaoSegundos != null ? T.fmtDuracao(o.duracaoSegundos) : "—"}</strong></div>
        <div><span>Atividade</span><strong>${o.idAtividade ?? "—"}</strong></div>
        <div><span>Registrada em</span><strong>${dataHoraIso(o.criadoEm)}</strong></div>
      </div>

      ${o.descricao ? `<p class="drawer-desc">${T.escapar(o.descricao)}</p>` : ""}

      <h3>CT-es (${d.ctes.length})</h3>
      ${d.ctes.length ? `<ul class="lista-simples">${d.ctes.map(c =>
        `<li><strong>CT-e ${c.numeroCte ?? "—"}</strong> ${c.remetente ? "· " + T.escapar(c.remetente) : ""} ${c.destinatario ? "→ " + T.escapar(c.destinatario) : ""}</li>`).join("")}</ul>` : `<p class="vazio">Nenhum CT-e.</p>`}

      <h3>Itens avariados (${d.itens.length})</h3>
      ${d.itens.length ? `<table class="tabela"><thead><tr><th>Código</th><th>Produto</th><th class="num">Qtd</th></tr></thead>
        <tbody>${d.itens.map(it => `<tr><td>${T.escapar(it.codigo || "—")}</td><td>${T.escapar(it.nome)}</td><td class="num">${T.fmtPeso(it.quantidade)}</td></tr>`).join("")}</tbody></table>`
        : `<p class="vazio">Nenhum item.</p>`}

      <h3>Fotos (${d.fotos.length})</h3>
      <div class="thumbs" id="thumbs">${d.fotos.map(f =>
        `<figure><img data-foto="${f.id}" alt="${T.escapar(f.categoria)}"><figcaption>${f.categoria === "NOTA_FISCAL" ? "Nota fiscal" : "Avaria"}</figcaption></figure>`).join("") || '<p class="vazio">Sem fotos.</p>'}</div>

      <h3>Tratamento</h3>
      <form id="formTratar" class="form-card">
        <div class="form-grid">
          <label>Culpa<select name="culpa">
            <option value="">—</option>
            ${Object.entries(CULPA).map(([k, v]) => `<option value="${k}" ${o.culpa === k ? "selected" : ""}>${v}</option>`).join("")}
          </select></label>
          <label>Quem causou<input name="quemCausou" maxlength="160" value="${T.escapar(o.quemCausou || "")}"></label>
          <label>Pagar para<input name="responsavelPagamento" maxlength="160" value="${T.escapar(o.responsavelPagamento || "")}"></label>
          <label>Valor total (R$)<input name="valorTotal" type="number" step="0.01" min="0" value="${o.valorTotal ?? ""}"></label>
        </div>
        <label>Resolução<textarea name="resolucao" maxlength="1000" rows="2">${T.escapar(o.resolucao || "")}</textarea></label>
        <button class="btn">Salvar tratamento</button>
        <span id="msgTratar" class="form-msg" hidden></span>
      </form>

      <div class="drawer-acoes">${acoes || '<span class="vazio">Ocorrência encerrada.</span>'}</div>`;

    overlay.hidden = false;

    // Fotos protegidas (bearer) → objectURL.
    liberarImagens();
    box.querySelectorAll("img[data-foto]").forEach(async img => {
      try {
        const url = await T.imagem(`/api/torre/ocorrencias/${id}/fotos/${img.dataset.foto}` + T.comFilial());
        objectUrls.push(url);
        img.src = url;
      } catch (e) { img.alt = "falha ao carregar"; }
    });

    document.getElementById("fecharDrawer").onclick = fechar;
    document.getElementById("formTratar").addEventListener("submit", ev => salvarTratamento(ev, id));
    box.querySelectorAll("[data-acao]").forEach(b =>
      b.addEventListener("click", () => mudarStatus(id, b.dataset.acao)));
  }

  function fechar() {
    document.getElementById("drawerOverlay").hidden = true;
    liberarImagens();
  }

  function msgTratar(ok, txt) {
    const el = document.getElementById("msgTratar");
    if (!el) return;
    el.className = "form-msg " + (ok ? "ok" : "erro");
    el.textContent = txt; el.hidden = false;
  }

  async function enviarPatch(id, body) {
    const r = await T.patch("/api/torre/ocorrencias/" + id + T.comFilial(), body);
    if (!r.ok) {
      let m = "HTTP " + r.status;
      try { const j = await r.json(); m = j.message || j.erro || j.mensagem || m; } catch (e) {}
      throw new Error(m);
    }
  }

  async function salvarTratamento(ev, id) {
    ev.preventDefault();
    const f = ev.target;
    const body = {
      culpa: f.culpa.value || null,
      quemCausou: f.quemCausou.value.trim() || null,
      responsavelPagamento: f.responsavelPagamento.value.trim() || null,
      valorTotal: f.valorTotal.value === "" ? null : Number(f.valorTotal.value),
      resolucao: f.resolucao.value.trim() || null,
    };
    try { await enviarPatch(id, body); msgTratar(true, "Tratamento salvo."); await carregar(); }
    catch (e) { msgTratar(false, e.message); }
  }

  async function mudarStatus(id, status) {
    if (status === "CANCELADA" && !confirm("Cancelar esta ocorrência?")) return;
    try { await enviarPatch(id, { status }); fechar(); await carregar(); }
    catch (e) { alert("Falha: " + e.message); }
  }

  Torre.registrar("/ocorrencias", {
    titulo: "Ocorrências", eyebrow: "Avarias · registro e tratamento",
    mount(root) {
      const opcoes = ["", ...Object.keys(STATUS)]
        .map(s => `<option value="${s}">${s ? STATUS[s].rotulo : "Todos os status"}</option>`).join("");
      root.innerHTML = `
        <div class="filtros-linha">
          <label>Status <select id="filtroStatus">${opcoes}</select></label>
        </div>
        <section class="painel"><div class="tabela-wrap">
          <table class="tabela"><thead><tr>
            <th>Registrada</th><th>Tipo</th><th>Status</th><th>Placa / motorista</th>
            <th class="num">Valor</th><th>Culpa</th><th></th>
          </tr></thead><tbody id="tbOcorr"></tbody></table>
          <p class="vazio" id="vazioOcorr" hidden>Nenhuma ocorrência.</p>
        </div></section>

        <div id="drawerOverlay" class="drawer-overlay" hidden>
          <div class="drawer-backdrop"></div>
          <aside class="drawer" id="drawerBox"></aside>
        </div>`;

      document.getElementById("filtroStatus").addEventListener("change", ev => {
        filtroStatus = ev.target.value; carregar();
      });
      document.getElementById("tbOcorr").addEventListener("click", ev => {
        const b = ev.target.closest("[data-abrir]");
        if (b) abrirDetalhe(b.dataset.abrir).catch(e => { if (e.message !== "sessao-expirada") T.erro("Falha: " + e.message); });
      });
      document.querySelector("#drawerOverlay .drawer-backdrop").addEventListener("click", fechar);

      T.ligarBotaoAtualizar(carregar);
      carregar().catch(e => { if (e.message !== "sessao-expirada") T.erro("Falha: " + e.message); });
      return () => { fechar(); };
    }
  });
})();
