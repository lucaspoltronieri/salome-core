/* Bootstrap do app web da Torre: login (mesma infra do mapa/painel), topbar,
   seletor de filial (ADMIN) e ligação com o router. */
(() => {
  const T = Torre;
  const $ = id => document.getElementById(id);
  let recarregarView = () => {};

  // ---- Helpers expostos às views ----
  T.marcarAtualizado = iso => {
    $("atualizado").textContent = iso ? "atualizado " + new Date(iso).toLocaleTimeString("pt-BR") : "";
    $("appErro").hidden = true;
  };
  T.erro = msg => { $("appErro").textContent = msg; $("appErro").hidden = false; };
  T.ligarBotaoAtualizar = fn => { recarregarView = fn; };

  // ---- Sessão ----
  T.aoPerderSessao(mostrarLogin);

  function aplicarUsuario(u) {
    T.state.usuario = u;
    if (!T.state.filial && T.isAdmin()) T.state.filial = String(u.idFilial);
    $("usuarioInfo").textContent = `${u.nome}` + (u.perfil === "ADMIN" ? " · ADMIN" : "");
    // Mostra a seção admin da sidebar só para ADMIN.
    document.querySelectorAll("[data-admin-only]").forEach(el => { el.hidden = !T.isAdmin(); });
    configurarFilial(u);
  }

  async function configurarFilial(u) {
    const sel = $("filialSel"), txt = $("filialTxt");
    if (T.isAdmin()) {
      sel.hidden = false; txt.hidden = true;
      try {
        const filiais = await T.api("/api/torre/admin/filiais");
        sel.innerHTML = filiais.map(f =>
          `<option value="${f.idFilial}">${T.escapar(f.nome)} (${f.idFilial})</option>`).join("");
        sel.value = T.state.filial || String(u.idFilial);
      } catch (e) { /* mantém vazio */ }
      sel.onchange = () => { T.state.filial = sel.value; T.recarregarRota(); };
    } else {
      sel.hidden = true; txt.hidden = false;
      txt.textContent = "filial " + u.idFilial;
    }
  }

  function entrar(u) {
    aplicarUsuario(u);
    $("login").hidden = true;
    $("appShell").hidden = false;
    T.iniciarRouter();
  }

  function mostrarLogin() {
    T.desmontarAtual && T.desmontarAtual();
    $("appShell").hidden = true;
    $("usuarioInfo").textContent = "";
    $("login").hidden = false;
    $("loginUsuario").focus();
  }

  async function iniciarSessao() {
    const token = T.getToken();
    if (token) {
      try {
        const r = await fetch("/api/torre/auth/me", { headers: T.authHeaders() });
        if (r.ok) return entrar(await r.json());
      } catch (e) { /* tenta credencial */ }
    }
    const cred = T.lerCred();
    if (cred) {
      try { return entrar(await T.autenticar(cred.login, cred.senha)); } catch (e) { /* mostra login */ }
    }
    mostrarLogin();
  }

  // ---- Listeners de topo ----
  $("loginForm").addEventListener("submit", async ev => {
    ev.preventDefault();
    const btn = $("loginBtn"), erro = $("loginErro");
    const login = $("loginUsuario").value.trim(), senha = $("loginSenha").value, manter = $("loginManter").checked;
    btn.disabled = true; erro.hidden = true;
    try {
      const u = await T.autenticar(login, senha);
      if (manter) T.salvarCred(login, senha); else T.limparCred();
      $("loginSenha").value = "";
      entrar(u);
    } catch (e) { erro.textContent = e.message; erro.hidden = false; }
    finally { btn.disabled = false; }
  });
  $("sairBtn").addEventListener("click", () => { T.limparSessao(); T.limparCred(); location.reload(); });
  $("atualizarBtn").addEventListener("click", () => recarregarView());

  fetch("/api/versao").then(r => r.ok ? r.json() : null)
    .then(d => { if (d?.versao) $("versao").textContent = "v" + d.versao; })
    .catch(() => {});

  iniciarSessao();
})();
