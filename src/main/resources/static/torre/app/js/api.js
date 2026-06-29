/* Núcleo compartilhado do app web da Torre: estado de sessão, fetch autenticado
   e formatadores. Mesma infra de login do mapa/painel (token + credencial no
   localStorage), agora num app só. Exposto como window.Torre. */
window.Torre = (() => {
  const params = new URLSearchParams(location.search);
  const TOKEN_KEY = "torre_app_token";
  const CRED_KEY = "torre_app_cred";

  const state = {
    filial: params.get("filial"), // ADMIN pode forçar; operador usa a do token
    usuario: null,
  };

  // ---- Formatadores (alinhados ao mapa/painel) ----
  const fmtInt = new Intl.NumberFormat("pt-BR");
  const fmtPeso = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 0, maximumFractionDigits: 1 });

  function escapar(s) {
    return (s ?? "").toString().replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  }
  function fmtData(d) { return d ? d.split("-").reverse().join("/") : ""; }
  function dataHora(d, h) { return (fmtData(d) + " " + (h || "")).trim() || "—"; }
  function fmtDuracao(seg) {
    seg = Math.max(0, Math.round(seg || 0));
    if (seg < 60) return seg + "s";
    const h = Math.floor(seg / 3600), m = Math.floor((seg % 3600) / 60);
    return h > 0 ? `${h}h${String(m).padStart(2, "0")}` : `${m} min`;
  }
  function tempoDesde(iso) {
    if (!iso) return "—";
    return fmtDuracao((Date.now() - new Date(iso).getTime()) / 1000);
  }
  // Origem vem como "EXPRESSO SALOME - OSASCO"; mostra só a cidade.
  function limparOrigem(origem) {
    const o = (origem ?? "").trim();
    const partes = o.split(" - ");
    const cidade = partes.length > 1 ? partes[partes.length - 1].trim() : o;
    return cidade || o.replace(/^expresso\s+salome\b[\s-]*/i, "").trim() || o;
  }
  function pct(parte, total) { return total > 0 ? Math.round((parte / total) * 100) : 0; }

  // ---- Sessão ----
  function getToken() { return localStorage.getItem(TOKEN_KEY); }
  function authHeaders() { return { "Authorization": "Bearer " + getToken() }; }
  function limparSessao() { localStorage.removeItem(TOKEN_KEY); }
  function salvarCred(login, senha) {
    localStorage.setItem(CRED_KEY, btoa(unescape(encodeURIComponent(JSON.stringify({ login, senha })))));
  }
  function lerCred() {
    const v = localStorage.getItem(CRED_KEY);
    if (!v) return null;
    try { return JSON.parse(decodeURIComponent(escape(atob(v)))); } catch (e) { return null; }
  }
  function limparCred() { localStorage.removeItem(CRED_KEY); }

  async function autenticar(login, senha) {
    const r = await fetch("/api/torre/auth/login", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ login, senha })
    });
    if (!r.ok) throw new Error(r.status === 401 ? "Usuário ou senha inválidos." : "Falha no login (HTTP " + r.status + ").");
    const data = await r.json();
    localStorage.setItem(TOKEN_KEY, data.token);
    state.usuario = data.usuario;
    return data.usuario;
  }

  // Acrescenta filial (ADMIN) à querystring de qualquer endpoint.
  function comFilial(qs) {
    const p = new URLSearchParams(qs || "");
    if (state.filial) p.set("filial", state.filial);
    const s = p.toString();
    return s ? "?" + s : "";
  }

  let onAuthLost = () => {};
  function aoPerderSessao(fn) { onAuthLost = fn; }

  // GET autenticado com JSON; tenta re-login silencioso uma vez em 401.
  async function api(path, { reautenticar = true } = {}) {
    const r = await fetch(path, { cache: "no-store", headers: authHeaders() });
    if (r.status === 401) {
      if (reautenticar) {
        const cred = lerCred();
        if (cred) {
          try { await autenticar(cred.login, cred.senha); return api(path, { reautenticar: false }); }
          catch (e) { /* credencial velha */ }
        }
      }
      limparSessao();
      onAuthLost();
      throw new Error("sessao-expirada");
    }
    if (!r.ok) throw new Error("HTTP " + r.status);
    return r.json();
  }

  // POST autenticado (admin); devolve a resposta crua p/ tratar status.
  async function post(path, body) {
    return fetch(path, {
      method: "POST",
      headers: { ...authHeaders(), "Content-Type": "application/json" },
      body: body == null ? undefined : JSON.stringify(body),
    });
  }

  function isAdmin() { return state.usuario && state.usuario.perfil === "ADMIN"; }

  return {
    state, isAdmin,
    fmtInt, fmtPeso, escapar, fmtData, dataHora, fmtDuracao, tempoDesde, limparOrigem, pct,
    getToken, authHeaders, autenticar, limparSessao, salvarCred, lerCred, limparCred,
    comFilial, api, post, aoPerderSessao,
  };
})();
