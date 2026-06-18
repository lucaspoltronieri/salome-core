const state = { snapshot: null };

const fmtMoney = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const fmtMoneyShort = new Intl.NumberFormat("pt-BR", { notation: "compact", style: "currency", currency: "BRL", maximumFractionDigits: 1 });
const fmtDate = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" });
const fmtDayMonth = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC", day: "2-digit", month: "2-digit" });

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
    document.querySelector("#dataMode").textContent = snapshot.demonstrativo
        ? "Modo demonstrativo: dados de exemplo, sem conexao com o MySQL legado."
        : "Dados reais do legado em modo read-only.";
    renderAlerts(snapshot.alertas);
    renderKpis(snapshot.kpis);
    renderConciliacao(snapshot);
    renderProjecao(snapshot.projecao || []);
    renderHorizonteCards("#aPagar", "#aPagarDetail", snapshot.aPagar || []);
    renderHorizonteCards("#aReceber", "#aReceberDetail", snapshot.aReceber || []);
    renderRetrospectivo(snapshot.retrospectivo || []);
    renderBankBalances(snapshot.saldosBancarios || []);
    renderRanks("#costCenterList", snapshot.porCentroCusto);
    renderMovements(snapshot.movimentos || []);
}

function renderAlerts(alerts) {
    document.querySelector("#alerts").innerHTML = (alerts || [])
        .map(alert => `<div class="alert">${escapeHtml(alert)}</div>`).join("");
}

function renderKpis(kpis) {
    document.querySelector("#kpis").innerHTML = (kpis || []).map(kpi => `
        <article class="kpi ${kpi.tom}">
            <small>${escapeHtml(kpi.titulo)}</small>
            <strong>${fmtMoney.format(kpi.valor)}</strong>
            <small>${escapeHtml(kpi.detalhe)}</small>
        </article>
    `).join("");
}

function renderConciliacao(snapshot) {
    const aReceberMes = horizonteValor(snapshot.aReceber, "MES");
    const aPagarMes = horizonteValor(snapshot.aPagar, "MES");
    const atrasoReceber = horizonteValor(snapshot.aReceber, "ATRASO");
    const atrasoPagar = horizonteValor(snapshot.aPagar, "ATRASO");
    const projecao = snapshot.projecao || [];
    const projetado = projecao.length ? projecao[projecao.length - 1].saldoProjetado : snapshot.saldoBancarioAtual;
    const rows = [
        ["Saldo bancario hoje", snapshot.saldoBancarioAtual, "base"],
        ["+ A receber (mes)", aReceberMes, "entrada"],
        ["+ A receber em atraso", atrasoReceber, "entrada muted"],
        ["- A pagar (mes)", -Math.abs(aPagarMes), "saida"],
        ["- A pagar em atraso", -Math.abs(atrasoPagar), "saida muted"],
        ["= Saldo projetado (fim do mes)", projetado, projetado >= 0 ? "total positivo" : "total negativo"]
    ];
    document.querySelector("#conciliacao").innerHTML = rows.map(([label, value, cls]) => `
        <div class="concil-row ${cls}">
            <span>${escapeHtml(label)}</span>
            <strong>${fmtMoney.format(value || 0)}</strong>
        </div>
    `).join("");
}

function renderProjecao(serie) {
    const host = document.querySelector("#projecaoChart");
    const range = document.querySelector("#projecaoRange");
    if (!serie.length) {
        host.innerHTML = `<div class="empty-state">Sem previsao de movimentos para o periodo.</div>`;
        range.textContent = "";
        return;
    }
    range.textContent = `${date(serie[0].data)} a ${date(serie[serie.length - 1].data)}`;

    const width = 720;
    const height = 240;
    const padX = 44;
    const padY = 24;
    const values = serie.map(point => point.saldoProjetado);
    const max = Math.max(...values, 0);
    const min = Math.min(...values, 0);
    const span = max - min || 1;
    const stepX = serie.length > 1 ? (width - padX * 2) / (serie.length - 1) : 0;
    const x = index => padX + index * stepX;
    const y = value => padY + (height - padY * 2) * (1 - (value - min) / span);
    const zeroY = y(0);

    const points = serie.map((point, index) => `${x(index).toFixed(1)},${y(point.saldoProjetado).toFixed(1)}`).join(" ");
    const areaPoints = `${x(0).toFixed(1)},${zeroY.toFixed(1)} ${points} ${x(serie.length - 1).toFixed(1)},${zeroY.toFixed(1)}`;
    const negative = values.some(value => value < 0);

    const labelStep = Math.max(1, Math.ceil(serie.length / 8));
    const xLabels = serie.map((point, index) => index % labelStep === 0 || index === serie.length - 1
        ? `<text class="axis" x="${x(index).toFixed(1)}" y="${(height - 4).toFixed(1)}" text-anchor="middle">${dayMonth(point.data)}</text>`
        : "").join("");

    const dots = serie.map((point, index) => point.saldoProjetado < 0
        ? `<circle cx="${x(index).toFixed(1)}" cy="${y(point.saldoProjetado).toFixed(1)}" r="2.6" class="dot-neg"></circle>`
        : "").join("");

    host.innerHTML = `
        <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" class="proj-svg">
            <polygon class="proj-area ${negative ? "has-neg" : ""}" points="${areaPoints}"></polygon>
            <line class="proj-zero" x1="${padX}" y1="${zeroY.toFixed(1)}" x2="${width - padX}" y2="${zeroY.toFixed(1)}"></line>
            <polyline class="proj-line" points="${points}"></polyline>
            ${dots}
            <text class="axis" x="${padX}" y="${(padY - 8).toFixed(1)}" text-anchor="start">${fmtMoneyShort.format(max)}</text>
            <text class="axis" x="${padX}" y="${(height - padY + 14).toFixed(1)}" text-anchor="start">${fmtMoneyShort.format(min)}</text>
            ${xLabels}
        </svg>`;
}

function renderHorizonteCards(gridSelector, detailSelector, cards) {
    renderDrillCards(gridSelector, detailSelector, cards, horizonteMeta, "Nada previsto.");
}

function horizonteMeta(card) {
    return `${card.quantidade} conta(s) ${rangeLabel(card)}`;
}

function retrospectivoMeta(card) {
    return `${card.quantidade} lancamento(s) &middot; ${escapeHtml(card.detalhe || "")}`;
}

function renderDrillCards(gridSelector, detailSelector, cards, metaFn, vazio) {
    const grid = document.querySelector(gridSelector);
    const detail = document.querySelector(detailSelector);
    detail.innerHTML = "";
    detail.style.display = "none";
    if (!cards.length) {
        grid.innerHTML = `<div class="empty-state">${escapeHtml(vazio)}</div>`;
        return;
    }
    grid.innerHTML = "";
    cards.forEach(card => grid.appendChild(buildDrillCard(card, grid, detail, metaFn)));
}

function buildDrillCard(card, grid, detail, metaFn) {
    const wrap = document.createElement("article");
    wrap.className = `horizon-card ${card.tom}`;
    const hasDetail = (card.contas || []).length > 0;

    const head = document.createElement("button");
    head.type = "button";
    head.className = "horizon-head";
    head.innerHTML = `
        <div class="horizon-top">
            <span class="horizon-title">${escapeHtml(card.titulo)}</span>
            <span class="horizon-twisty">${hasDetail ? "▾" : ""}</span>
        </div>
        <strong class="horizon-value">${fmtMoney.format(card.valor || 0)}</strong>
        <span class="horizon-meta">${metaFn(card)}</span>`;
    wrap.appendChild(head);

    if (!hasDetail) {
        head.disabled = true;
        return wrap;
    }

    head.addEventListener("click", () => {
        const jaAtivo = wrap.classList.contains("active");
        grid.querySelectorAll(".horizon-card.active").forEach(card => card.classList.remove("active"));
        if (jaAtivo) {
            detail.style.display = "none";
            detail.innerHTML = "";
            return;
        }
        wrap.classList.add("active");
        renderDrillDetail(detail, card, metaFn);
        detail.scrollIntoView({ behavior: "smooth", block: "nearest" });
    });
    return wrap;
}

function renderDrillDetail(detail, card, metaFn) {
    detail.style.display = "block";
    detail.innerHTML = `
        <div class="detail-head">
            <strong>${escapeHtml(card.titulo)}</strong>
            <span>${metaFn(card)} &middot; ${fmtMoney.format(card.valor || 0)}</span>
        </div>
        <div class="conta-cols">
            <span>Plano de contas</span>
            <span class="col-qtd">Qtd</span>
            <span class="col-valor">Valor</span>
        </div>
        <div class="conta-tree wide"></div>`;
    const tree = detail.querySelector(".conta-tree");
    (card.contas || []).forEach(node => tree.appendChild(renderContaNo(node)));
}

function renderContaNo(node) {
    const wrap = document.createElement("div");
    wrap.className = "conta-branch";

    const expandable = (node.filhos || []).length > 0 || (node.documentos || []).length > 0;
    const row = document.createElement("div");
    row.className = `conta-node ${node.sintetica ? "sintetica" : "analitica"}`;
    row.style.paddingLeft = `${10 + (node.nivel - 1) * 18}px`;
    row.innerHTML = `
        <span class="conta-twisty">${expandable ? "▸" : ""}</span>
        <span class="conta-label">${escapeHtml(node.descricao)}</span>
        <span class="conta-qtd">${node.quantidade}</span>
        <span class="conta-value">${fmtMoney.format(node.valor || 0)}</span>`;
    wrap.appendChild(row);

    if (!expandable) {
        return wrap;
    }

    const children = document.createElement("div");
    children.className = "conta-children";
    children.style.display = "none";
    (node.filhos || []).forEach(child => children.appendChild(renderContaNo(child)));
    (node.documentos || []).forEach(doc => children.appendChild(renderContaDoc(doc, node.nivel)));
    wrap.appendChild(children);

    row.addEventListener("click", () => {
        const open = row.classList.toggle("open");
        children.style.display = open ? "block" : "none";
    });
    return wrap;
}

function renderContaDoc(doc, nivelPai) {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "conta-doc";
    item.style.paddingLeft = `${10 + (nivelPai + 1) * 18}px`;
    item.innerHTML = `
        <span class="doc-name">${escapeHtml(doc.documento)}</span>
        <span class="doc-person">${escapeHtml(doc.clienteFornecedor)}</span>
        <span class="doc-filial">${escapeHtml(doc.filial || "-")}</span>
        <span class="doc-venc">${date(doc.dataVencimento)}</span>
        <span class="doc-value">${fmtMoney.format(doc.valor || 0)}</span>`;
    item.addEventListener("click", () => openDocDrawer(doc));
    return item;
}

function renderRetrospectivo(cards) {
    renderDrillCards("#retrospectivo", "#retrospectivoDetail", cards, retrospectivoMeta, "Sem lancamentos no periodo.");
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
                <span class="${(balance.saldoBancario || 0) < 0 ? "neg" : "pos"}">${fmtMoney.format(balance.saldoBancario || 0)}</span>
                <small>Limite ${fmtMoney.format(balance.limite || 0)}</small>
            </div>
        </div>
    `).join("");
}

function renderRanks(selector, groups) {
    const target = document.querySelector(selector);
    if (!target) {
        return;
    }
    groups = groups || [];
    if (!groups.length) {
        target.innerHTML = `<div class="empty-state">Sem despesas no periodo.</div>`;
        return;
    }
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
    document.querySelector("#drawerEyebrow").textContent = "Origem do movimento";
    document.querySelector("#drawerItens").innerHTML = "";
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

function openDocDrawer(doc) {
    document.querySelector("#drawerEyebrow").textContent = "Detalhe da conta";
    document.querySelector("#drawerTitle").textContent = doc.documento;
    const pairs = [
        ["Pessoa", doc.clienteFornecedor],
        ["Filial", doc.filial],
        ["Vencimento", date(doc.dataVencimento)],
        ["Valor", fmtMoney.format(doc.valor || 0)],
        ["Origem", doc.origemTipo]
    ];
    document.querySelector("#drawerDetails").innerHTML = pairs.map(([key, value]) => `
        <dt>${escapeHtml(key)}</dt><dd>${escapeHtml(value ?? "Nao informado")}</dd>
    `).join("");
    const itens = document.querySelector("#drawerItens");
    itens.innerHTML = "";
    document.querySelector("#drawer").classList.add("open");
    carregarItensDoDoc(doc, itens);
}

// Drill ate produtos (nota de compra) ou CT-es (fatura) reaproveitando os endpoints existentes.
async function carregarItensDoDoc(doc, host) {
    let url = null;
    let titulo = "Itens";
    const nota = /^NC-(\d+)/.exec(doc.documento || "");
    const fatura = /^FAT-(\d+)/.exec(doc.documento || "");
    if (doc.origemTipo === "NOTA_COMPRA_DUPLICATA" && nota) {
        url = `/api/financeiro/fluxo-caixa/notas/${nota[1]}/produtos`;
        titulo = "Produtos da nota";
    } else if (doc.origemTipo === "FATURA_ABERTA" && fatura) {
        url = `/api/financeiro/fluxo-caixa/faturas/${fatura[1]}/ctes`;
        titulo = "CT-es da fatura";
    } else {
        return;
    }
    host.innerHTML = `<p class="itens-title">${escapeHtml(titulo)}</p><div class="drill-loading">Carregando...</div>`;
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const nodes = await response.json();
        if (!nodes.length) {
            host.innerHTML = `<p class="itens-title">${escapeHtml(titulo)}</p><div class="empty-state">Sem itens.</div>`;
            return;
        }
        host.innerHTML = `<p class="itens-title">${escapeHtml(titulo)}</p>` + nodes.map(node => `
            <div class="item-row">
                <span class="item-name">${escapeHtml(node.titulo)}<small>${escapeHtml(node.detalhe || "")}</small></span>
                <span class="item-value">${fmtMoney.format(node.valor || 0)}</span>
            </div>
        `).join("");
    } catch (error) {
        host.innerHTML = `<p class="itens-title">${escapeHtml(titulo)}</p>`
            + `<div class="empty-state">Falha ao carregar itens. <button type="button" class="itens-retry">Tentar novamente</button></div>`;
        host.querySelector(".itens-retry").addEventListener("click", () => carregarItensDoDoc(doc, host));
    }
}

function horizonteValor(cards, codigo) {
    const card = (cards || []).find(item => item.codigo === codigo);
    return card ? card.valor : 0;
}

function rangeLabel(card) {
    if (card.codigo === "ATRASO") {
        return "&middot; vencidas";
    }
    if (card.codigo === "HOJE") {
        return `&middot; vence hoje`;
    }
    if (card.codigo === "AMANHA") {
        return `&middot; ${date(card.de)}`;
    }
    return `&middot; ate ${date(card.ate)}`;
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

function dayMonth(value) {
    if (!value) {
        return "";
    }
    return fmtDayMonth.format(new Date(`${value}T00:00:00Z`));
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
