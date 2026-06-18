const state = { snapshot: null, expanded: new Set() };

// Ordem fixa do demonstrativo: secoes do plano de contas intercaladas com os marcos DRE.
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
    document.querySelector("#closeDrawer").addEventListener("click", () => {
        document.querySelector("#drawer").classList.remove("open");
    });
    document.querySelector("#expandAll").addEventListener("click", () => toggleAll(true));
    document.querySelector("#collapseAll").addEventListener("click", () => toggleAll(false));
    load();
});

async function load() {
    const form = document.querySelector("#filters");
    const button = form.querySelector("button[type='submit']");
    const params = new URLSearchParams(new FormData(form));
    if (!params.get("filial")) {
        params.delete("filial");
    }
    const regime = params.get("regime") || "caixa";
    params.delete("regime");
    params.set("_", Date.now().toString());

    const endpoint = regime === "competencia"
        ? "/api/financeiro/dre-gerencial-competencia"
        : "/api/financeiro/dre-gerencial";
    state.regime = regime;

    button.disabled = true;
    button.textContent = "Atualizando";
    try {
        const response = await fetch(`${endpoint}?${params.toString()}`, { cache: "no-store" });
        if (!response.ok) {
            throw new Error(`Falha ao carregar DRE: ${response.status}`);
        }
        state.snapshot = await response.json();
        seedExpanded(state.snapshot);
        render(state.snapshot);
    } catch (error) {
        renderAlerts([error.message || "Nao foi possivel atualizar o DRE."]);
    } finally {
        button.disabled = false;
        button.textContent = "Atualizar";
    }
}

// Por padrao secoes e nivel 1 ja vem expandidos; contas analiticas ficam recolhidas.
function seedExpanded(snapshot) {
    state.expanded = new Set();
    (snapshot.secoes || []).forEach(secao => {
        state.expanded.add(`sec:${secao.codigo}`);
        (secao.contas || []).forEach(conta => marcarSinteticasNivel1(secao.codigo, conta));
    });
}

function marcarSinteticasNivel1(secaoCodigo, conta) {
    if (conta.sintetica && conta.nivel <= 1) {
        state.expanded.add(nodeId(secaoCodigo, conta));
    }
}

function render(snapshot) {
    document.querySelector("#periodLabel").textContent = `${date(snapshot.inicio)} a ${date(snapshot.fim)}`;
    document.querySelector("#dataMode").textContent = snapshot.demonstrativo
        ? "Modo demonstrativo: dados de exemplo, sem conexao com o MySQL legado."
        : "Dados reais do legado em modo read-only.";
    document.querySelector("#excludedLabel").textContent =
        `${snapshot.receitasExcluidasQuantidade || 0} receitas excluidas`;
    renderAlerts(snapshot.alertas || []);
    renderResumos(snapshot.resumos || []);
    renderDemonstrativo(snapshot);
    renderRanks("#planoList", snapshot.porPlano || []);
    renderRanks("#centroList", snapshot.porCentroCusto || []);
    renderCustoPeso(snapshot);
}

function renderCustoPeso(snapshot) {
    const target = document.querySelector("#custoPesoCard");
    if (!target) {
        return;
    }
    const custo = Number(snapshot.custoTotalPeriodo || 0);
    const toneladas = Number(snapshot.toneladasTransportadas || 0);
    const custoTon = Number(snapshot.custoPorTonelada || 0);
    const competencia = state.regime === "competencia";
    const cards = [
        { rotulo: competencia ? "Custo total (competencia)" : "Custo total (regime caixa)", valor: fmtMoney.format(custo),
          nota: competencia ? "Despesas por entrada/emissao no periodo" : "Despesas pagas no periodo" },
        { rotulo: "Toneladas transportadas", valor: `${fmtPeso.format(toneladas)} t`, nota: "Por data de emissao do CT-e" },
        { rotulo: "Custo por tonelada", valor: `${fmtMoney.format(custoTon)}/t`, nota: "Custo &divide; toneladas", destaque: true }
    ];
    target.innerHTML = cards.map(card => `
        <div class="custo-peso-item${card.destaque ? " destaque" : ""}">
            <span class="custo-peso-rotulo">${escapeHtml(card.rotulo)}</span>
            <strong class="custo-peso-valor">${escapeHtml(card.valor)}</strong>
            <span class="custo-peso-nota">${card.nota}</span>
        </div>
    `).join("");
}

function renderAlerts(alerts) {
    document.querySelector("#alerts").innerHTML = alerts.map(alert => `<div class="alert">${escapeHtml(alert)}</div>`).join("");
}

function renderResumos(resumos) {
    document.querySelector("#resumos").innerHTML = resumos.map(item => `
        <article class="kpi ${item.tom}">
            <small>${escapeHtml(item.titulo)}</small>
            <strong>${fmtMoney.format(item.valor || 0)}</strong>
            <small>${escapeHtml(item.detalhe || "")}</small>
        </article>
    `).join("");
}

function renderDemonstrativo(snapshot) {
    const host = document.querySelector("#demonstrativo");
    host.innerHTML = "";
    const secoes = new Map((snapshot.secoes || []).map(secao => [secao.codigo, secao]));
    const marcos = new Map((snapshot.linhas || []).map(linha => [linha.codigo, linha]));

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
    const expandable = conta.sintetica ? (conta.filhos || []).length > 0 : (conta.origens || []).length > 0;
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

    if (!open) {
        return;
    }
    if (conta.sintetica) {
        const box = document.createElement("div");
        box.className = "dre-children";
        (conta.filhos || []).forEach(filho => renderConta(box, secaoCodigo, filho));
        host.appendChild(box);
    } else {
        host.appendChild(detalheConta(secaoCodigo, conta));
    }
}

function detalheConta(secaoCodigo, conta) {
    const wrap = document.createElement("div");
    wrap.className = "conta-detalhe";
    wrap.style.paddingLeft = `${20 + conta.nivel * 18}px`;

    const origens = document.createElement("div");
    origens.className = "origens";
    origens.innerHTML = `<div class="origens-titulo">Origem da despesa</div>`;
    (conta.origens || []).forEach(origem => {
        const item = document.createElement("div");
        item.className = "origem-item";
        item.innerHTML = `
            <span class="origem-tag" data-origem="${escapeHtml(origem.origemTipo)}">${escapeHtml(origem.label)}</span>
            <span class="origem-qtd">${origem.quantidade} mov.</span>
            <strong class="${(origem.valor || 0) >= 0 ? "positive" : "negative"}">${fmtMoney.format(origem.valor || 0)}</strong>
        `;
        origens.appendChild(item);
    });
    wrap.appendChild(origens);

    const documentos = movimentosDaConta(secaoCodigo, conta);
    if (documentos.length) {
        const lista = document.createElement("div");
        lista.className = "doc-list";
        documentos.slice(0, 30).forEach(movimento => {
            const fatura = movimento.origemTipo === "FATURA_BAIXA" || movimento.origemTipo === "FATURA_ABERTA";
            const wrap = document.createElement("div");
            wrap.className = "doc-wrap";

            const doc = document.createElement("button");
            doc.type = "button";
            doc.className = "doc-item";
            doc.innerHTML = `
                <span class="doc-info">
                    <strong>${fatura ? `<span class="cte-caret">+</span> ` : ""}${escapeHtml(movimento.documento)}</strong>
                    <small>${escapeHtml(labelOrigem(movimento.origemTipo))} - ${escapeHtml(movimento.clienteFornecedor)} - ${escapeHtml(movimento.filial)}</small>
                </span>
                <strong class="${signedValue(movimento) >= 0 ? "positive" : "negative"}">${fmtMoney.format(signedValue(movimento))}</strong>
            `;
            doc.addEventListener("click", () => openDrawer(movimento));
            wrap.appendChild(doc);

            if (fatura) {
                const cteBox = document.createElement("div");
                cteBox.className = "cte-list";
                cteBox.style.display = "none";
                wrap.appendChild(cteBox);
                const caret = doc.querySelector(".cte-caret");
                let loaded = false;
                caret.addEventListener("click", event => {
                    event.stopPropagation();
                    const open = cteBox.style.display === "none";
                    cteBox.style.display = open ? "block" : "none";
                    caret.textContent = open ? "−" : "+";
                    if (open && !loaded) {
                        loaded = true;
                        carregarCtes(movimento, cteBox);
                    }
                });
            }
            lista.appendChild(wrap);
        });
        if (documentos.length > 30) {
            const mais = document.createElement("div");
            mais.className = "doc-mais";
            mais.textContent = `+ ${documentos.length - 30} documento(s) - refine pela busca`;
            lista.appendChild(mais);
        }
        wrap.appendChild(lista);
    }
    return wrap;
}

// Drill-down de uma fatura: busca sob demanda os CTes que a compoem e renderiza linhas
// clicaveis (cada CTe abre os detalhes no painel direito, igual aos demais documentos).
async function carregarCtes(fatura, container) {
    container.innerHTML = `<div class="cte-empty">Carregando CTes...</div>`;
    try {
        const resp = await fetch(`/api/financeiro/dre-gerencial/faturas/${fatura.origemId}/ctes`, { cache: "no-store" });
        if (!resp.ok) {
            throw new Error(`HTTP ${resp.status}`);
        }
        const ctes = await resp.json();
        if (!ctes.length) {
            container.innerHTML = `<div class="cte-empty">Sem CTes para esta fatura.</div>`;
            return;
        }
        container.innerHTML = "";
        const head = document.createElement("div");
        head.className = "cte-head";
        head.innerHTML = `
            <span>Numero Cte</span>
            <span>Remetente</span>
            <span>Destinatario</span>
            <span class="num">Volume</span>
            <span class="num">Peso (kg)</span>
            <span class="num">Total NF</span>
            <span class="num">Total Frete</span>
        `;
        container.appendChild(head);
        ctes.forEach(cte => {
            const item = document.createElement("button");
            item.type = "button";
            item.className = "cte-item";
            item.innerHTML = `
                <strong class="cte-num">${escapeHtml(cte.cte ?? "-")}</strong>
                <span class="cte-text" title="${escapeHtml(cte.remetente ?? "-")}">${escapeHtml(cte.remetente ?? "-")}</span>
                <span class="cte-text" title="${escapeHtml(cte.destinatario ?? "-")}">${escapeHtml(cte.destinatario ?? "-")}</span>
                <span class="num">${fmtInt.format(cte.volume || 0)}</span>
                <span class="num">${fmtPeso.format(cte.peso || 0)}</span>
                <span class="num">${fmtMoney.format(cte.valorNota || 0)}</span>
                <span class="num cte-frete ${(cte.valorFrete || 0) >= 0 ? "positive" : "negative"}">${fmtMoney.format(cte.valorFrete || 0)}</span>
            `;
            item.addEventListener("click", () => openCteDrawer(cte, fatura));
            container.appendChild(item);
        });
    } catch (error) {
        container.innerHTML = `<div class="cte-empty">Falha ao carregar CTes.</div>`;
    }
}

// Detalhe de um CT-e no painel direito, reusando o mesmo drawer dos movimentos.
function openCteDrawer(cte, fatura) {
    document.querySelector(".drawer .eyebrow").textContent = "Conhecimento de transporte";
    document.querySelector("#drawerTitle").textContent = `CTe ${cte.cte ?? "-"}`;
    const pairs = [
        ["CTe", cte.cte],
        ["Remetente", cte.remetente],
        ["Destinatario", cte.destinatario],
        ["Volume", fmtInt.format(cte.volume || 0)],
        ["Peso (kg)", fmtPeso.format(cte.peso || 0)],
        ["Valor nota", fmtMoney.format(cte.valorNota || 0)],
        ["Valor frete", fmtMoney.format(cte.valorFrete || 0)],
        ["Fatura", fatura.documento]
    ];
    document.querySelector("#drawerDetails").innerHTML = pairs.map(([key, value]) => `
        <dt>${escapeHtml(key)}</dt><dd>${escapeHtml(value ?? "Nao informado")}</dd>
    `).join("");
    document.querySelector("#drawer").classList.add("open");
}

// Documentos de uma conta analitica: casa pela classificacao real ou pelas regras das contas especiais.
function movimentosDaConta(secaoCodigo, conta) {
    const movimentos = (state.snapshot?.movimentos || []).filter(m => m.natureza === "DESPESA" || secaoCodigo === "RECEITA" || secaoCodigo === "DEDUCOES");
    if (conta.classificacao) {
        return movimentos.filter(m => classificacao(m) === conta.classificacao);
    }
    if (conta.descricao === "Despesas Bancarias Extrato") {
        return movimentos.filter(m => semClassificacao(m) && m.origemTipo === "EXTRATO_AVULSO");
    }
    if (conta.descricao === "Sem centro de custo") {
        return movimentos.filter(m => semClassificacao(m) && m.origemTipo !== "EXTRATO_AVULSO");
    }
    return [];
}

function classificacao(movimento) {
    return semClassificacao(movimento) ? "" : String(movimento.classificacao).trim();
}

function semClassificacao(movimento) {
    const c = movimento.classificacao;
    return !c || !String(c).trim() || String(c).trim().toLowerCase() === "nao informado";
}

function labelCell(titulo, nivel, openState, sintetica) {
    const cell = document.createElement("div");
    cell.className = "cell-label";
    cell.style.paddingLeft = `${nivel * 18}px`;
    const toggle = openState === null
        ? `<span class="row-spacer"></span>`
        : `<span class="row-toggle ${sintetica ? "strong" : ""}">${openState ? "−" : "+"}</span>`;
    cell.innerHTML = `${toggle}<span class="row-title">${escapeHtml(titulo)}</span>`;
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

function nodeId(secaoCodigo, conta) {
    return `c:${secaoCodigo}:${conta.classificacao || conta.descricao}`;
}

function toggle(id) {
    if (state.expanded.has(id)) {
        state.expanded.delete(id);
    } else {
        state.expanded.add(id);
    }
    renderDemonstrativo(state.snapshot);
}

function toggleAll(open) {
    state.expanded = new Set();
    if (open) {
        (state.snapshot?.secoes || []).forEach(secao => {
            state.expanded.add(`sec:${secao.codigo}`);
            (secao.contas || []).forEach(conta => marcarTodos(secao.codigo, conta));
        });
    } else {
        (state.snapshot?.secoes || []).forEach(secao => state.expanded.add(`sec:${secao.codigo}`));
    }
    renderDemonstrativo(state.snapshot);
}

function marcarTodos(secaoCodigo, conta) {
    if (conta.sintetica) {
        state.expanded.add(nodeId(secaoCodigo, conta));
        (conta.filhos || []).forEach(filho => marcarTodos(secaoCodigo, filho));
    }
}

function labelOrigem(value) {
    return {
        FATURA_BAIXA: "Fatura baixada",
        FATURA_ABERTA: "Fatura aberta",
        CTE_ABERTO: "CT-e aberto",
        NOTA_COMPRA_DUPLICATA: "Nota de compra",
        PAGAMENTO_CAIXA: "Pagamento caixa",
        CAIXA_DINHEIRO: "Caixa (dinheiro)",
        EXTRATO_AVULSO: "Extrato bancario"
    }[value] || value || "Origem nao informada";
}

function renderRanks(selector, groups) {
    const target = document.querySelector(selector);
    if (!groups.length) {
        target.innerHTML = `<div class="empty-state">Sem dados no periodo.</div>`;
        return;
    }
    const max = Math.max(1, ...groups.map(group => Math.abs(group.saldo || 0)));
    target.innerHTML = groups.map(group => {
        const width = Math.max(4, Math.round(Math.abs(group.saldo || 0) / max * 100));
        return `
            <div class="rank-item">
                <div class="rank-row">
                    <strong>${escapeHtml(group.chave)}</strong>
                    <span>${fmtMoney.format(group.saldo || 0)}</span>
                </div>
                <div class="meter"><span style="width:${width}%"></span></div>
            </div>
        `;
    }).join("");
}

function openDrawer(movement) {
    document.querySelector(".drawer .eyebrow").textContent = "Origem do movimento";
    document.querySelector("#drawerTitle").textContent = movement.documento;
    const origin = movement.origin || {};
    const pairs = [
        ["Natureza", movement.natureza],
        ["Origem", labelOrigem(movement.origemTipo)],
        ["ID legado", movement.origemId],
        ["Valor", fmtMoney.format(signedValue(movement))],
        ["Baixa", date(movement.dataBaixa)],
        ["Banco/Caixa", movement.banco],
        ["Pessoa", movement.clienteFornecedor],
        ["Filial", movement.filial],
        ["Centro custo", movement.centroCusto],
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

function signedValue(movement) {
    const value = Number(movement.valor || 0);
    return movement.natureza === "RECEITA" ? value : -value;
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

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
