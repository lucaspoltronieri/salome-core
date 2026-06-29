/* Administração (somente ADMIN): cadastro de operadores das filiais (usuários do
   app), locais/boxes, filiais e auditoria. Consome /api/torre/admin/* e
   /api/torre/auditoria — todas já existentes no backend. */
(() => {
  const T = Torre;
  const TIPOS_LOCAL = ["DOCA", "BOX", "AREA", "PENDENCIA", "AVARIA", "QUIMICA", "BOX_DEFINITIVO"];
  const ABAS = [
    ["usuarios", "Usuários"], ["locais", "Locais"], ["filiais", "Filiais"], ["auditoria", "Auditoria"],
  ];

  async function falhaMsg(r) {
    let extra = "";
    try { const j = await r.json(); extra = j.message || j.erro || ""; } catch (e) {}
    if (r.status === 409) return extra || "Registro duplicado.";
    if (r.status === 400) return extra || "Dados inválidos.";
    if (r.status === 403) return "Sem permissão.";
    return extra || ("HTTP " + r.status);
  }

  function feedback(id, ok, msg) {
    const el = document.getElementById(id);
    if (!el) return;
    el.className = "form-msg " + (ok ? "ok" : "erro");
    el.textContent = msg;
    el.hidden = false;
  }

  // ---------- Usuários ----------
  async function renderUsuarios(box) {
    box.innerHTML = `
      <form id="formUsuario" class="form-card">
        <h3>Novo operador</h3>
        <div class="form-grid">
          <label>Login<input name="login" required maxlength="60"></label>
          <label>Nome<input name="nome" required maxlength="120"></label>
          <label>Senha<input name="senha" type="password" required minlength="6"></label>
          <label>Perfil<select name="perfil">
            <option value="OPERADOR">Operador</option><option value="ADMIN">Admin</option></select></label>
          <label>Filial (id)<input name="idFilial" type="number" required value="${filialPadrao()}"></label>
        </div>
        <button class="btn">Cadastrar</button>
        <span id="msgUsuario" class="form-msg" hidden></span>
      </form>
      <section class="painel"><div class="tabela-wrap">
        <table class="tabela"><thead><tr>
          <th>Nome</th><th>Login</th><th>Perfil</th><th class="num">Filial</th><th>Status</th><th></th>
        </tr></thead><tbody id="tbUsuarios"></tbody></table>
        <p class="vazio" id="vazioUsuarios" hidden>Nenhum usuário cadastrado.</p>
      </div></section>`;

    document.getElementById("formUsuario").addEventListener("submit", async ev => {
      ev.preventDefault();
      const f = ev.target;
      const body = {
        login: f.login.value.trim(), nome: f.nome.value.trim(), senha: f.senha.value,
        idFilial: Number(f.idFilial.value), perfil: f.perfil.value,
      };
      const r = await T.post("/api/torre/admin/usuarios", body);
      if (r.ok) { feedback("msgUsuario", true, "Usuário criado."); f.reset(); f.idFilial.value = filialPadrao(); listarUsuarios(); }
      else feedback("msgUsuario", false, await falhaMsg(r));
    });
    document.getElementById("tbUsuarios").addEventListener("click", async ev => {
      const btn = ev.target.closest("[data-toggle]");
      if (!btn) return;
      const id = btn.getAttribute("data-id"), ativo = btn.getAttribute("data-toggle") === "1";
      const r = await T.post(`/api/torre/admin/usuarios/${id}/ativo?ativo=${ativo}`);
      if (r.ok) listarUsuarios(); else feedback("msgUsuario", false, await falhaMsg(r));
    });
    listarUsuarios();
  }
  async function listarUsuarios() {
    const lista = await T.api("/api/torre/admin/usuarios" + T.comFilial());
    const tb = document.getElementById("tbUsuarios");
    tb.innerHTML = lista.map(u => `<tr>
      <td>${T.escapar(u.nome)}</td><td>${T.escapar(u.login)}</td>
      <td>${u.perfil === "ADMIN" ? '<span class="status-badge st-pronto">ADMIN</span>' : "Operador"}</td>
      <td class="num">${u.idFilial}</td>
      <td>${u.ativo ? '<span class="status-badge st-pronto">Ativo</span>' : '<span class="status-badge st-avaria">Inativo</span>'}</td>
      <td><button class="btn btn-ghost btn-sm" data-toggle="${u.ativo ? 0 : 1}" data-id="${u.id}">${u.ativo ? "Desativar" : "Ativar"}</button></td>
    </tr>`).join("");
    document.getElementById("vazioUsuarios").hidden = lista.length > 0;
  }

  // ---------- Locais ----------
  async function renderLocais(box) {
    box.innerHTML = `
      <form id="formLocal" class="form-card">
        <h3>Novo local / box</h3>
        <div class="form-grid">
          <label>Código<input name="codigo" required maxlength="40" placeholder="DOCA_A"></label>
          <label>Nome<input name="nome" required maxlength="120" placeholder="Doca A"></label>
          <label>Tipo<select name="tipo">${TIPOS_LOCAL.map(t => `<option>${t}</option>`).join("")}</select></label>
        </div>
        <button class="btn">Cadastrar</button>
        <span id="msgLocal" class="form-msg" hidden></span>
      </form>
      <section class="painel"><div class="tabela-wrap">
        <table class="tabela"><thead><tr>
          <th>Código</th><th>Nome</th><th>Tipo</th><th>Status</th><th></th>
        </tr></thead><tbody id="tbLocais"></tbody></table>
        <p class="vazio" id="vazioLocais" hidden>Nenhum local cadastrado.</p>
      </div></section>`;
    document.getElementById("formLocal").addEventListener("submit", async ev => {
      ev.preventDefault();
      const f = ev.target;
      const body = { codigo: f.codigo.value.trim(), nome: f.nome.value.trim(), tipo: f.tipo.value };
      const r = await T.post("/api/torre/admin/locais" + T.comFilial(), body);
      if (r.ok) { feedback("msgLocal", true, "Local criado."); f.reset(); listarLocais(); }
      else feedback("msgLocal", false, await falhaMsg(r));
    });
    document.getElementById("tbLocais").addEventListener("click", async ev => {
      const btn = ev.target.closest("[data-toggle]");
      if (!btn) return;
      const id = btn.getAttribute("data-id"), ativo = btn.getAttribute("data-toggle") === "1";
      const sep = T.comFilial() ? "&" : "?";
      const r = await T.post(`/api/torre/admin/locais/${id}/ativo${T.comFilial()}${sep}ativo=${ativo}`);
      if (r.ok) listarLocais(); else feedback("msgLocal", false, await falhaMsg(r));
    });
    listarLocais();
  }
  async function listarLocais() {
    const lista = await T.api("/api/torre/admin/locais" + T.comFilial());
    document.getElementById("tbLocais").innerHTML = lista.map(l => `<tr>
      <td><strong>${T.escapar(l.codigo)}</strong></td><td>${T.escapar(l.nome)}</td><td>${T.escapar(l.tipo)}</td>
      <td>${l.ativo ? '<span class="status-badge st-pronto">Ativo</span>' : '<span class="status-badge st-avaria">Inativo</span>'}</td>
      <td><button class="btn btn-ghost btn-sm" data-toggle="${l.ativo ? 0 : 1}" data-id="${l.id}">${l.ativo ? "Desativar" : "Ativar"}</button></td>
    </tr>`).join("");
    document.getElementById("vazioLocais").hidden = lista.length > 0;
  }

  // ---------- Filiais ----------
  async function renderFiliais(box) {
    box.innerHTML = `
      <form id="formFilial" class="form-card">
        <h3>Cadastrar / atualizar filial</h3>
        <div class="form-grid">
          <label>Filial (id legado)<input name="idFilial" type="number" required></label>
          <label>Nome<input name="nome" required maxlength="120"></label>
          <label>Corte de viagem<input name="dataCorteViagem" type="date" required></label>
          <label class="check">Ativa na Torre<input name="ativa" type="checkbox" checked></label>
        </div>
        <button class="btn">Salvar</button>
        <span id="msgFilial" class="form-msg" hidden></span>
      </form>
      <section class="painel"><div class="tabela-wrap">
        <table class="tabela"><thead><tr>
          <th class="num">Id</th><th>Nome</th><th>Corte viagem</th><th>Status</th>
        </tr></thead><tbody id="tbFiliais"></tbody></table>
        <p class="vazio" id="vazioFiliais" hidden>Nenhuma filial cadastrada.</p>
      </div></section>`;
    document.getElementById("formFilial").addEventListener("submit", async ev => {
      ev.preventDefault();
      const f = ev.target;
      const body = {
        idFilial: Number(f.idFilial.value), nome: f.nome.value.trim(),
        dataCorteViagem: f.dataCorteViagem.value, ativa: f.ativa.checked,
      };
      const r = await T.post("/api/torre/admin/filiais", body);
      if (r.ok) { feedback("msgFilial", true, "Filial salva."); listarFiliais(); }
      else feedback("msgFilial", false, await falhaMsg(r));
    });
    document.getElementById("tbFiliais").addEventListener("click", ev => {
      const tr = ev.target.closest("tr[data-fil]");
      if (!tr) return;
      const f = document.getElementById("formFilial");
      f.idFilial.value = tr.getAttribute("data-id");
      f.nome.value = tr.getAttribute("data-nome");
      f.dataCorteViagem.value = tr.getAttribute("data-corte") || "";
      f.ativa.checked = tr.getAttribute("data-ativa") === "1";
    });
    listarFiliais();
  }
  async function listarFiliais() {
    const lista = await T.api("/api/torre/admin/filiais");
    document.getElementById("tbFiliais").innerHTML = lista.map(f => `<tr data-fil data-id="${f.idFilial}"
        data-nome="${T.escapar(f.nome)}" data-corte="${f.dataCorteViagem || ""}" data-ativa="${f.ativa ? 1 : 0}">
      <td class="num">${f.idFilial}</td><td>${T.escapar(f.nome)}</td><td>${T.fmtData(f.dataCorteViagem)}</td>
      <td>${f.ativa ? '<span class="status-badge st-pronto">Ativa</span>' : '<span class="status-badge st-avaria">Inativa</span>'}</td>
    </tr>`).join("");
    document.getElementById("vazioFiliais").hidden = lista.length > 0;
  }

  // ---------- Auditoria ----------
  async function renderAuditoria(box) {
    box.innerHTML = `<section class="painel"><div class="tabela-wrap">
      <table class="tabela"><thead><tr>
        <th>Quando</th><th>Ação</th><th>Entidade</th><th class="num">Id</th><th class="num">Usuário</th><th>Detalhe</th>
      </tr></thead><tbody id="tbAuditoria"></tbody></table>
      <p class="vazio" id="vazioAuditoria" hidden>Nenhum evento.</p>
    </div></section>`;
    const lista = await T.api("/api/torre/auditoria" + T.comFilial());
    document.getElementById("tbAuditoria").innerHTML = lista.map(e => `<tr>
      <td>${new Date(e.ocorridoEm).toLocaleString("pt-BR")}</td>
      <td>${T.escapar(e.acao)}</td><td>${T.escapar(e.entidade)}</td>
      <td class="num">${e.idEntidade ?? "—"}</td><td class="num">${e.idUsuario ?? "—"}</td>
      <td>${T.escapar(e.detalhe)}</td>
    </tr>`).join("");
    document.getElementById("vazioAuditoria").hidden = lista.length > 0;
  }

  function filialPadrao() { return T.state.filial || (T.state.usuario && T.state.usuario.idFilial) || ""; }

  const RENDER = { usuarios: renderUsuarios, locais: renderLocais, filiais: renderFiliais, auditoria: renderAuditoria };

  Torre.registrar("/admin/usuarios", {
    titulo: "Administração", eyebrow: "Cadastros · somente ADMIN", admin: true,
    mount(root) {
      let aba = "usuarios";
      root.innerHTML = `
        <div class="tabs" id="adminTabs">${ABAS.map(([k, r]) =>
          `<button class="tab ${k === aba ? "ativo" : ""}" data-aba="${k}">${r}</button>`).join("")}</div>
        <div id="adminBox"></div>`;
      const box = document.getElementById("adminBox");
      async function abrir(k) {
        aba = k;
        document.querySelectorAll("#adminTabs .tab").forEach(t => t.classList.toggle("ativo", t.dataset.aba === k));
        try { await RENDER[k](box); } catch (e) { if (e.message !== "sessao-expirada") Torre.erro("Falha: " + e.message); }
      }
      document.getElementById("adminTabs").addEventListener("click", ev => {
        const t = ev.target.closest("[data-aba]"); if (t) abrir(t.dataset.aba);
      });
      Torre.ligarBotaoAtualizar(() => abrir(aba));
      abrir(aba);
      return () => {};
    }
  });
})();
