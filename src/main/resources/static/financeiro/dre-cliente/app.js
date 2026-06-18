const state = { snapshot: null, params: null, detalhe: null, expanded: new Set() };

// Ordem do demonstrativo do cliente: secoes do plano intercaladas com os marcos do DRE.
const LAYOUT = [
    { type: "secao", codigo: "RECEITA" },
    { type: "secao", codigo: "DEDUCOES" },
    { type: "marco", codigo: "RECEITA_LIQUIDA" },
    { type: "secao", codigo: "CUSTOS_SERVICOS" },
    { type: "marco", codigo: "MARGEM_BRUTA" },
    { type: "secao", codigo: "DESPESAS_COMERCIAIS" },
    { type: "secao", codigo: "DESPESAS_ADMINISTRATIVAS" },
    { type: "marco", codigo: "EBITDA" },
    { type: "secao", codigo: "DEPRECIACAO_AMORTIZACAO" },
    { type: "marco", codigo: "EBIT" },
    { type: "secao", codigo: "RESULTADO_FINANCEIRO" },
    { type: "secao", codigo: "IMPOSTOS" },
    { type: "marco", codigo: "RESULTADO_LIQUIDO" },
    { type: "secao", codigo: "TRANSFERENCIA" }
];

const DRIVER_LABEL = { PESO: "Peso (tonelada)", FRETE: "Valor de frete", CTES: "Numero de CT-es" };
const REGIME_LABEL = { COMPETENCIA: "Competencia", CAIXA: "Caixa" };

const fmtMoney = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const fmtDate = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" });
const fmtInt = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 0 });
const fmtPeso = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

document.addEventListener("DOMContentLoaded", () => {
    const form = document.querySelector("#filters");
    const today = new Date();
    form.inicio.value = iso(new Date(today.getFullYear(), today.getMonth(), 1));
    form.fim.value = iso(new Date(today.getFullYear(), today.getMonth() + 1, 0));
    form.addEventListener("submit", event => {
        event.preventDefault();
        load();
    });
    document.querySelector("#closeDrawer").addEventListener("click", closeDrawer);
    load();
});

function paramsAtuais() {
    const form = document.querySelector("#filters");
    const params = new URLSearchParams(new FormData(form));
    if (!params.get("filial")) {
        params.delete("filial");
    }
    return params;
}

async function load() {
    const form = document.querySelector("#filters");
    const button = form.querySelector("button[type='submit']");
    const params = paramsAtuais();
    state.params = params;
    params.set("_", Date.now().toString());

    button.disabled = true;
    button.textContent = "Atualizando";
    try {
        const response = await fetch(`/api/financeiro/dre-cliente?${params.toString()}`, { cache: "no-store" });
        if (!response.ok) {
            throw new Error(`Falha ao carregar DRE por cliente: ${response.status}`);
        }
        state.snapshot = await response.json();
        render(state.snapshot);
        closeDrawer();
    } catch (error) {
        renderAlerts([error.message || "Nao foi possivel atualizar o DRE por cliente."]);
    } finally {
        button.disabled = false;
        button.textContent = "Atualizar";
    }
}

function render(snapshot) {
    document.querySelector("#periodLabel").textContent = `${date(snapshot.inicio)} a ${date(snapshot.fim)}`;
    document.querySelector("#driverLabel").textContent =
        `${REGIME_LABEL[snapshot.regime] || snapshot.regime} · rateio por ${DRIVER_LABEL[snapshot.driver] || snapshot.driver}`;
    document.querySelector("#dataMode").textContent = snapshot.demonstrativo
        ? "Modo demonstrativo: dados de exemplo, sem conexao com o MySQL legado."
        : "Dados reais do legado em modo read-only.";
    renderAlerts(snapshot.alertas || []);
    renderResumos(snapshot);
    renderRanking(snapshot);
}

function renderResumos(snapshot) {
    const cards = [
        { titulo: "Receita realizada", valor: snapshot.receitaTotal, detalhe: `${snapshot.clientes || 0} cliente(s) no periodo`,
          tom: tom(snapshot.receitaTotal) },
        { titulo: "Despesa (bolo rateado)", valor: snapshot.despesaTotal,
          detalhe: `${fmtMoney.format(Number(snapshot.custoPorTonelada || 0))}/t · igual ao DRE gerencial`,
          tom: "negativo" },
        { titulo: "Resultado", valor: snapshot.resultadoTotal, detalhe: "Receita menos despesas no periodo",
          tom: tom(snapshot.resultadoTotal) },
        { titulo: "Margem media", valor: null, texto: `${Number(snapshot.margemMediaPct || 0).toFixed(2)}%`,
          detalhe: `${fmtPeso.format(Number(snapshot.toneladasTotal || 0))} t - ${fmtInt.format(snapshot.qtdCtesTotal || 0)} CT-es`,
          tom: tom(snapshot.resultadoTotal) }
    ];
    document.querySelector("#resumos").innerHTML = cards.map(card => `
        <article class="kpi ${card.tom}">
            <small>${escapeHtml(card.titulo)}</small>
            <strong>${card.texto != null ? escapeHtml(card.texto) : fmtMoney.format(card.valor || 0)}</strong>
            <small>${escapeHtml(card.detalhe || "")}</small>
        </article>
    `).join("");
}

function renderRanking(snapshot) {
    const tbody = document.querySelector("#ranking");
    const linhas = snapshot.linhas || [];
    if (!linhas.length) {
        tbody.innerHTML = `<tr><td colspan="8" class="empty-state">Sem clientes com receita no periodo.</td></tr>`;
        return;
    }
    tbody.innerHTML = linhas.map(linha => `
        <tr data-cliente="${linha.idCliente}" tabindex="0">
            <td class="cliente-nome">${escapeHtml(linha.nome)}</td>
            <td class="num">${fmtMoney.format(linha.receita || 0)}</td>
            <td class="num">${Number(linha.pesoRateioPct || 0).toFixed(2)}%</td>
            <td class="num">${fmtPeso.format(Number(linha.toneladas || 0))}</td>
            <td class="num">${fmtInt.format(linha.qtdCtes || 0)}</td>
            <td class="num negative">${fmtMoney.format(linha.despesaApropriada || 0)}</td>
            <td class="num ${(linha.resultado || 0) >= 0 ? "positive" : "negative"}">${fmtMoney.format(linha.resultado || 0)}</td>
            <td class="num ${(linha.margemPct || 0) >= 0 ? "positive" : "negative"}">${Number(linha.margemPct || 0).toFixed(2)}%</td>
        </tr>
    `).join("");
    tbody.querySelectorAll("tr[data-cliente]").forEach(row => {
        const id = row.getAttribute("data-cliente");
        row.addEventListener("click", () => abrirCliente(id));
        row.addEventListener("keydown", event => {
            if (event.key === "Enter") {
                abrirCliente(id);
            }
        });
    });
}

async function abrirCliente(idCliente) {
    const params = new URLSearchParams(state.params);
    params.delete("busca");
    params.set("_", Date.now().toString());
    const drawer = document.querySelector("#drawer");
    drawer.classList.add("open");
    document.querySelector("#drawerTitle").textContent = "Carregando...";
    document.querySelector("#drawerSub").textContent = "";
    document.querySelector("#drawerResumos").innerHTML = "";
    document.querySelector("#drawerDre").innerHTML = "";
    try {
        const resp = await fetch(`/api/financeiro/dre-cliente/${idCliente}?${params.toString()}`, { cache: "no-store" });
        if (!resp.ok) {
            throw new Error(`HTTP ${resp.status}`);
        }
        state.detalhe = await resp.json();
        seedExpanded(state.detalhe);
        renderDetalhe(state.detalhe);
    } catch (error) {
        document.querySelector("#drawerTitle").textContent = "Falha ao carregar o DRE do cliente.";
    }
}

function seedExpanded(detalhe) {
    state.expanded = new Set();
    (detalhe.secoes || []).forEach(secao => {
        state.expanded.add(`sec:${secao.codigo}`);
    });
}

function renderDetalhe(detalhe) {
    document.querySelector("#drawerTitle").textContent = detalhe.nome;
    document.querySelector("#drawerSub").textContent =
        `${date(detalhe.inicio)} a ${date(detalhe.fim)} - rateio por ${DRIVER_LABEL[detalhe.driver] || detalhe.driver}`
        + ` (${Number(detalhe.pesoRateioPct || 0).toFixed(2)}% do bolo)`;
    document.querySelector("#drawerResumos").innerHTML = (detalhe.resumos || []).map(item => `
        <article class="kpi ${item.tom}">
            <small>${escapeHtml(item.titulo)}</small>
            <strong>${fmtMoney.format(item.valor || 0)}</strong>
            <small>${escapeHtml(item.detalhe || "")}</small>
        </article>
    `).join("");
    renderDre(detalhe);
}

function renderDre(detalhe) {
    const host = document.querySelector("#drawerDre");
    host.innerHTML = "";
    const secoes = new Map((detalhe.secoes || []).map(secao => [secao.codigo, secao]));
    const marcos = new Map((detalhe.linhas || []).map(linha => [linha.codigo, linha]));
    LAYOUT.forEach(entry => {
        if (entry.type === "marco") {
            const marco = marcos.get(entry.codigo);
            if (marco) {
                host.appendChild(marcoRow(marco));
            }
            return;
        }
        const secao = secoes.get(entry.codigo);
        if (secao) {
            renderSecao(host, secao);
        }
    });
}

function marcoRow(marco) {
    const row = document.createElement("div");
    row.className = "dre-row marco";
    row.appendChild(labelCell(marco.titulo, 0, null, false));
    row.appendChild(valueCell(marco.valor));
    row.appendChild(pctCell(marco.percentualReceitaLiquida));
    row.appendChild(textCell(""));
    return row;
}

function renderSecao(host, secao) {
    const id = `sec:${secao.codigo}`;
    const open = state.expanded.has(id);
    const header = document.createElement("div");
    header.className = `dre-row secao ${open ? "open" : ""}`;
    header.appendChild(labelCell(secao.titulo, 0, open, true));
    header.appendChild(valueCell(secao.valor));
    header.appendChild(pctCell(secao.percentualReceitaLiquida));
    header.appendChild(textCell(secao.quantidade));
    header.addEventListener("click", () => toggle(id));
    host.appendChild(header);
    if (!open) {
        return;
    }
    const box = document.createElement("div");
    box.className = "dre-children";
    (secao.contas || []).forEach(conta => renderConta(box, secao.codigo, conta));
    host.appendChild(box);
}

function renderConta(host, secaoCodigo, conta) {
    const id = nodeId(secaoCodigo, conta);
    const expandable = conta.sintetica && (conta.filhos || []).length > 0;
    const open = expandable && state.expanded.has(id);
    const row = document.createElement("div");
    row.className = `dre-row conta ${conta.sintetica ? "sintetica" : "analitica"} ${open ? "open" : ""}`;
    const titulo = conta.classificacao ? `${conta.classificacao} - ${conta.descricao}` : conta.descricao;
    row.appendChild(labelCell(titulo, conta.nivel, expandable ? open : null, conta.sintetica));
    row.appendChild(valueCell(conta.valor));
    row.appendChild(pctCell(conta.percentualReceitaLiquida));
    row.appendChild(textCell(conta.quantidade));
    if (expandable) {
        row.addEventListener("click", () => toggle(id));
    }
    host.appendChild(row);
    if (open) {
        const box = document.createElement("div");
        box.className = "dre-children";
        (conta.filhos || []).forEach(filho => renderConta(box, secaoCodigo, filho));
        host.appendChild(box);
    }
}

function toggle(id) {
    if (state.expanded.has(id)) {
        state.expanded.delete(id);
    } else {
        state.expanded.add(id);
    }
    renderDre(state.detalhe);
}

function nodeId(secaoCodigo, conta) {
    return `c:${secaoCodigo}:${conta.classificacao || conta.descricao}`;
}

function closeDrawer() {
    document.querySelector("#drawer").classList.remove("open");
}

function labelCell(titulo, nivel, openState, sintetica) {
    const cell = document.createElement("div");
    cell.className = "cell-label";
    cell.style.paddingLeft = `${nivel * 18}px`;
    const toggleIcon = openState === null
        ? `<span class="row-spacer"></span>`
        : `<span class="row-toggle ${sintetica ? "strong" : ""}">${openState ? "−" : "+"}</span>`;
    cell.innerHTML = `${toggleIcon}<span class="row-title">${escapeHtml(titulo)}</span>`;
    return cell;
}

function valueCell(valor) {
    const cell = document.createElement("div");
    cell.className = `cell-valor ${(valor || 0) >= 0 ? "positive" : "negative"}`;
    cell.textContent = fmtMoney.format(valor || 0);
    return cell;
}

function pctCell(pct) {
    const cell = document.createElement("div");
    cell.className = "cell-pct";
    cell.textContent = `${Number(pct || 0).toFixed(2)}%`;
    return cell;
}

function textCell(value) {
    const cell = document.createElement("div");
    cell.className = "cell-num";
    cell.textContent = value === 0 || value ? String(value) : "";
    return cell;
}

function renderAlerts(alerts) {
    document.querySelector("#alerts").innerHTML = (alerts || [])
        .map(alert => `<div class="alert">${escapeHtml(alert)}</div>`).join("");
}

function iso(dateValue) {
    return dateValue.toISOString().slice(0, 10);
}

function date(value) {
    if (!value) {
        return "-";
    }
    return fmtDate.format(new Date(`${value}T00:00:00Z`));
}

function tom(valor) {
    return Number(valor || 0) >= 0 ? "positivo" : "negativo";
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
