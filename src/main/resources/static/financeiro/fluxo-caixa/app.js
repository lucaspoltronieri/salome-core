const state = { snapshot: null };

const fmtMoney = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const fmtDate = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" });

document.addEventListener("DOMContentLoaded", () => {
    const form = document.querySelector("#filters");
    const today = new Date();
    const first = new Date(today.getFullYear(), today.getMonth(), 1);
    const last = new Date(today.getFullYear(), today.getMonth() + 2, 0);
    form.inicio.value = iso(first);
    form.fim.value = iso(last);
    form.addEventListener("submit", event => {
        event.preventDefault();
        load();
    });
    document.querySelector("#closeDrawer").addEventListener("click", () => {
        document.querySelector("#drawer").classList.remove("open");
    });
    load();
});

async function load() {
    const form = document.querySelector("#filters");
    const params = new URLSearchParams(new FormData(form));
    const response = await fetch(`/api/financeiro/fluxo-caixa?${params.toString()}`);
    if (!response.ok) {
        throw new Error(`Falha ao carregar financeiro: ${response.status}`);
    }
    state.snapshot = await response.json();
    render(state.snapshot);
}

function render(snapshot) {
    document.querySelector("#periodLabel").textContent = `${date(snapshot.inicio)} a ${date(snapshot.fim)}`;
    document.querySelector("#dataMode").textContent = snapshot.demonstrativo
        ? "Modo demonstrativo: dados de exemplo, sem conexao com o MySQL legado."
        : "Dados reais do legado em modo read-only.";
    renderAlerts(snapshot.alertas);
    renderKpis(snapshot.kpis);
    renderSummaries(snapshot.resumos || []);
    renderChart(snapshot.serie);
    renderRanks("#costCenterList", snapshot.porCentroCusto);
    renderBankBalances(snapshot.saldosBancarios || []);
    renderDrillTrees();
    renderMovements(snapshot.movimentos);
}

function renderAlerts(alerts) {
    document.querySelector("#alerts").innerHTML = alerts.map(alert => `<div class="alert">${escapeHtml(alert)}</div>`).join("");
}

function renderKpis(kpis) {
    document.querySelector("#kpis").innerHTML = kpis.map(kpi => `
        <article class="kpi ${kpi.tom}">
            <small>${escapeHtml(kpi.titulo)}</small>
            <strong>${fmtMoney.format(kpi.valor)}</strong>
            <small>${escapeHtml(kpi.detalhe)}</small>
        </article>
    `).join("");
}

function renderSummaries(summaries) {
    document.querySelector("#summaryCards").innerHTML = summaries.map(summary => `
        <article class="summary-card ${summary.tom}">
            <div class="summary-head">
                <h2>${escapeHtml(summary.titulo)}</h2>
                <span>${escapeHtml(summary.detalhe)}</span>
            </div>
            <div class="summary-values">
                <div><small>Previsto</small><strong>${fmtMoney.format(summary.previsto)}</strong></div>
                <div><small>Realizado</small><strong>${fmtMoney.format(summary.realizado)}</strong></div>
                <div><small>Diferenca</small><strong>${fmtMoney.format(summary.diferenca)}</strong></div>
            </div>
        </article>
    `).join("");
}

function renderChart(serie) {
    const max = Math.max(1, ...serie.map(point => Math.max(
        Math.abs(point.saldoPrevisto || 0),
        Math.abs(point.saldoRealizado || 0)
    )));
    const slice = serie.length > 64 ? serie.filter((_, index) => index % Math.ceil(serie.length / 64) === 0) : serie;
    document.querySelector("#cashChart").innerHTML = slice.map(point => {
        const realHeight = Math.max(2, Math.round(Math.abs(point.saldoRealizado || 0) / max * 100));
        const plannedHeight = Math.max(2, Math.round(Math.abs(point.saldoPrevisto || 0) / max * 100));
        return `
            <div class="bar-day" title="${date(point.data)} | Realizado ${fmtMoney.format(point.saldoRealizado)} | Previsto ${fmtMoney.format(point.saldoPrevisto)}">
                <span class="bar realizado" style="height:${realHeight}%"></span>
                <span class="bar previsto" style="height:${plannedHeight}%"></span>
            </div>
        `;
    }).join("");
}

function renderRanks(selector, groups) {
    const target = document.querySelector(selector);
    if (!target) {
        return;
    }
    groups = groups || [];
    const max = Math.max(1, ...groups.map(group => Math.abs(group.saldo || 0)));
    target.innerHTML = groups.map(group => {
        const width = Math.max(4, Math.round(Math.abs(group.saldo || 0) / max * 100));
        return `
            <div class="rank-item">
                <div class="rank-row">
                    <strong>${escapeHtml(group.chave)}</strong>
                    <span>${fmtMoney.format(group.saldo)}</span>
                </div>
                <div class="meter"><span style="width:${width}%"></span></div>
            </div>
        `;
    }).join("");
}

function renderBankBalances(balances) {
    const target = document.querySelector("#bankBalanceList");
    if (!balances.length) {
        target.innerHTML = `<div class="empty-state">Saldo bancario indisponivel neste modo.</div>`;
        return;
    }
    target.innerHTML = balances.map(balance => `
        <div class="balance-item">
            <div>
                <strong>${escapeHtml(balance.banco || "Banco nao informado")}</strong>
                <small>${balance.contaCaixa ? "Conta caixa" : "Conta bancaria"}</small>
            </div>
            <div>
                <span>${fmtMoney.format(balance.saldoBancario || 0)}</span>
                <small>Limite ${fmtMoney.format(balance.limite || 0)}</small>
            </div>
        </div>
    `).join("");
}

function renderMovements(movements) {
    document.querySelector("#movementCount").textContent = `${movements.length} movimentos`;
    document.querySelector("#movements").innerHTML = movements.map((movement, index) => `
        <tr data-index="${index}">
            <td>${date(flowDate(movement))}</td>
            <td><span class="badge ${movement.natureza.toLowerCase()}">${movement.natureza}</span></td>
            <td>${movement.status}</td>
            <td>${escapeHtml(movement.documento)}</td>
            <td>${escapeHtml(movement.clienteFornecedor)}</td>
            <td>${escapeHtml(movement.planoContas)}</td>
            <td>${escapeHtml(movement.centroCusto)}</td>
            <td>${escapeHtml(movement.filial)}</td>
            <td>${fmtMoney.format(movement.valor)}</td>
        </tr>
    `).join("");
    document.querySelectorAll("#movements tr").forEach(row => {
        row.addEventListener("click", () => openDrawer(movements[Number(row.dataset.index)]));
    });
}

function openDrawer(movement) {
    document.querySelector("#drawerTitle").textContent = movement.documento;
    const origin = movement.origin || {};
    const pairs = [
        ["Natureza", movement.natureza],
        ["Status", movement.status],
        ["Origem", movement.origemTipo],
        ["ID legado", movement.origemId],
        ["Valor", fmtMoney.format(movement.valor)],
        ["Data fluxo", date(flowDate(movement))],
        ["Competencia", date(movement.dataCompetencia)],
        ["Vencimento", date(movement.dataVencimento)],
        ["Baixa", date(movement.dataBaixa)],
        ["Banco/Caixa", movement.banco],
        ["Pessoa", movement.clienteFornecedor],
        ["Centro custo", movement.centroCusto],
        ["Filial", movement.filial],
        ["Plano contas", movement.planoContas],
        ["Classificacao", movement.classificacao],
        ["DMR", movement.dmr],
        ["Historico", movement.historico],
        ["Classe", origin.classe],
        ["Metodo/Botao", origin.metodoOuBotao],
        ["DAO/Query", origin.daoOuQuery],
        ["Tabela", origin.tabela]
    ];
    document.querySelector("#drawerDetails").innerHTML = pairs.map(([key, value]) => `
        <dt>${escapeHtml(key)}</dt><dd>${escapeHtml(value ?? "Nao informado")}</dd>
    `).join("");
    document.querySelector("#drawer").classList.add("open");
}

const API = "/api/financeiro/fluxo-caixa";

function periodParams() {
    const form = document.querySelector("#filters");
    return `inicio=${encodeURIComponent(form.inicio.value)}&fim=${encodeURIComponent(form.fim.value)}`;
}

function stripPrefix(id) {
    return String(id).replace(/^[A-Za-z]+/, "");
}

async function fetchNodes(url) {
    try {
        const response = await fetch(url);
        if (!response.ok) {
            return [];
        }
        return await response.json();
    } catch (error) {
        return [];
    }
}

// descend(node) -> null (leaf) | async () => [{ node, descend }]
function makeLevel(urlFn, childDescend) {
    return node => {
        if (!node.temFilhos) {
            return null;
        }
        return async () => {
            const kids = await fetchNodes(urlFn(node));
            return kids.map(kid => ({ node: kid, descend: childDescend }));
        };
    };
}

const ctesLevel = makeLevel(node => `${API}/faturas/${stripPrefix(node.id)}/ctes`, () => null);
const faturasLevel = makeLevel(node => `${API}/clientes/${node.id}/faturas?${periodParams()}`, ctesLevel);
const produtosLevel = makeLevel(node => `${API}/notas/${stripPrefix(node.id)}/produtos`, () => null);
const notasLevel = makeLevel(node => `${API}/fornecedores/${node.id}/notas?${periodParams()}`, produtosLevel);

function renderDrillTrees() {
    buildTree("#clientesTree", `${API}/clientes?${periodParams()}`, faturasLevel);
    buildTree("#fornecedoresTree", `${API}/fornecedores?${periodParams()}`, notasLevel);
}

function buildTree(selector, rootUrl, rootDescend) {
    const container = document.querySelector(selector);
    container.innerHTML = `<div class="drill-loading">Carregando...</div>`;
    fetchNodes(rootUrl).then(nodes => {
        container.innerHTML = "";
        if (!nodes.length) {
            container.innerHTML = `<div class="drill-empty">Sem dados no periodo.</div>`;
            return;
        }
        nodes.forEach(node => container.appendChild(renderDrillNode({ node, descend: rootDescend })));
    });
}

function renderDrillNode(item) {
    const { node, descend } = item;
    const loader = descend ? descend(node) : null;
    const wrap = document.createElement("div");

    const row = document.createElement("div");
    row.className = "drill-node" + (loader ? "" : " leaf");
    row.innerHTML = `
        <span class="drill-twisty">${loader ? "›" : ""}</span>
        <span class="drill-label">${escapeHtml(node.titulo)}<small>${escapeHtml(node.detalhe || "")}</small></span>
        <span class="drill-value">${fmtMoney.format(node.valor || 0)}</span>`;
    wrap.appendChild(row);

    if (!loader) {
        return wrap;
    }

    const childBox = document.createElement("div");
    childBox.className = "drill-children";
    childBox.style.paddingLeft = "16px";
    childBox.style.display = "none";
    wrap.appendChild(childBox);

    let loaded = false;
    row.addEventListener("click", async () => {
        const open = row.classList.toggle("open");
        childBox.style.display = open ? "grid" : "none";
        if (open && !loaded) {
            loaded = true;
            childBox.innerHTML = `<div class="drill-loading">Carregando...</div>`;
            const kids = await loader();
            childBox.innerHTML = "";
            if (!kids.length) {
                childBox.innerHTML = `<div class="drill-empty">Sem itens.</div>`;
                return;
            }
            kids.forEach(kid => childBox.appendChild(renderDrillNode(kid)));
        }
    });
    return wrap;
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

function flowDate(movement) {
    if (movement.status === "REALIZADO" && movement.dataBaixa) {
        return movement.dataBaixa;
    }
    return movement.dataVencimento || movement.dataCompetencia;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
