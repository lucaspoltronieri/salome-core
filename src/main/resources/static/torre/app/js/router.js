/* Roteamento por hash, sem build. Cada view registra-se em Torre.routes com
   { titulo, eyebrow, admin?, mount(root) -> cleanup }. mount devolve uma função
   de limpeza (encerra polling) chamada ao sair da rota. */
(() => {
  const routes = {};
  const DEFAULT = "/dashboard";
  let limparAtual = null;

  function registrar(path, view) { routes[path] = view; }

  function rotaAtual() {
    const h = (location.hash || "").replace(/^#/, "");
    return h && routes[h] ? h : DEFAULT;
  }

  function navegar() {
    const path = rotaAtual();
    const view = routes[path];

    // Bloqueia rotas admin para operador.
    if (view.admin && !Torre.isAdmin()) {
      location.hash = "#" + DEFAULT;
      return;
    }

    if (typeof limparAtual === "function") { try { limparAtual(); } catch (e) {} }
    limparAtual = null;

    // Marca link ativo na sidebar.
    document.querySelectorAll(".sidebar a[data-route]").forEach(a => {
      a.classList.toggle("active", a.getAttribute("data-route") === path);
    });

    // Atualiza topbar.
    document.getElementById("viewEyebrow").textContent = view.eyebrow || "";
    document.getElementById("viewTitulo").textContent = view.titulo || "";

    const root = document.getElementById("view");
    root.innerHTML = "";
    limparAtual = view.mount(root) || null;
  }

  Torre.routes = routes;
  Torre.registrar = registrar;
  Torre.iniciarRouter = () => {
    window.addEventListener("hashchange", navegar);
    if (!location.hash) location.hash = "#" + DEFAULT;
    navegar();
  };
  Torre.recarregarRota = navegar;
  Torre.desmontarAtual = () => { if (typeof limparAtual === "function") { try { limparAtual(); } catch (e) {} } limparAtual = null; };
})();
