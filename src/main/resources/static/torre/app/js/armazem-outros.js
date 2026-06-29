/* CT-es em outros armazéns: só listagem (saber onde a carga está). Fonte:
   /api/torre/mapa/snapshot (seção outrosArmazens). Export reaproveita o XLSX
   do mapa (/api/torre/mapa/export). */
(() => {
  const POLL_MS = 30000;
  const T = Torre;

  Torre.registrar("/armazem/outros", {
    titulo: "Outros armazéns", eyebrow: "Onde a carga está fora desta filial",
    mount(root) {
      let snap = null, filtro = "", timer = null;
      root.innerHTML = `
        <div class="barra-filtros">
          <input id="aoBusca" class="filtro" placeholder="Buscar CT-e, destinatário, cidade, armazém…">
          <span class="filtro-info" id="aoInfo"></span>
          <button id="aoExport" class="btn btn-export">Exportar Excel</button>
        </div>
        <section class="painel">
          <div class="tabela-wrap">
            <table class="tabela"><thead><tr>
              <th>Data emissão</th><th>CT-e</th><th>Armazém atual</th><th>Destinatário</th><th>Cidade</th>
              <th class="num">Volumes</th><th class="num">Peso (kg)</th><th>Situação</th>
            </tr></thead><tbody id="aoBody"></tbody></table>
            <p class="vazio" id="aoVazio" hidden>Nenhum CT-e em outros armazéns.</p>
          </div>
        </section>`;

      const $ = id => document.getElementById(id);
      $("aoBusca").addEventListener("input", () => { filtro = $("aoBusca").value.trim().toLowerCase(); render(); });
      $("aoExport").addEventListener("click", exportar);

      function casa(c) {
        if (!filtro) return true;
        return [c.cte, c.destinatario, c.cidadeDestinatario, c.armazemAtual, c.notasFiscais]
          .some(v => (v ?? "").toString().toLowerCase().includes(filtro));
      }
      function render() {
        if (!snap) return;
        const ctes = (snap.outrosArmazens || []).filter(casa);
        $("aoBody").innerHTML = ctes.map(c => `<tr>
          <td>${T.fmtData(c.dataEmissao) || "—"}</td>
          <td>${T.escapar(c.cte) || "—"}</td>
          <td>${T.escapar(c.armazemAtual)}</td>
          <td>${T.escapar(c.destinatario)}</td>
          <td>${T.escapar(c.cidadeDestinatario)}</td>
          <td class="num">${T.fmtInt.format(+c.volumes || 0)}</td>
          <td class="num">${T.fmtPeso.format(+c.peso || 0)}</td>
          <td>${T.escapar(c.situacaoCte)}</td>
        </tr>`).join("");
        $("aoVazio").hidden = ctes.length > 0;
        $("aoInfo").textContent = `${ctes.length} CT-e(s)`;
      }

      async function exportar() {
        const qs = new URLSearchParams();
        const fq = T.comFilial();
        if (fq) new URLSearchParams(fq.slice(1)).forEach((v, k) => qs.set(k, v));
        if (filtro) qs.set("texto", filtro);
        try {
          const r = await fetch("/api/torre/mapa/export?" + qs.toString(), { headers: T.authHeaders() });
          if (!r.ok) throw new Error("HTTP " + r.status);
          const blob = await r.blob();
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url; a.download = "mapa-armazem.xlsx";
          document.body.appendChild(a); a.click(); a.remove();
          URL.revokeObjectURL(url);
        } catch (e) { Torre.erro("Falha ao exportar: " + e.message); }
      }

      async function carregar() {
        try {
          snap = await T.api("/api/torre/mapa/snapshot" + T.comFilial());
          render();
          Torre.marcarAtualizado(snap.atualizadoEm);
        } catch (e) {
          if (e.message !== "sessao-expirada") Torre.erro("Falha ao carregar outros armazéns: " + e.message);
        }
      }
      carregar();
      timer = setInterval(carregar, POLL_MS);
      Torre.ligarBotaoAtualizar(carregar);
      return () => clearInterval(timer);
    }
  });
})();
