# Phase 12: Criar dashboard financeiro operacional - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 12-criar-dashboard-financeiro-operacional
**Areas discussed:** Indicadores do painel, Fonte e confianca dos dados, Filtros e recortes operacionais, Navegacao e profundidade

---

## Indicadores do painel

| Option | Description | Selected |
|--------|-------------|----------|
| Operacional | Vencidas, hoje, proximos dias, em aberto, pago no periodo e total por status usando dados ja migrados. | |
| Gerencial | Recortes agregados por filial, fornecedor, banco e plano de contas desde a primeira versao. | |
| Minimo | KPIs essenciais para validar a integracao antes de expandir. | |
| Hibrido | Mesclar operacional, gerencial e minimo em dashboard progressivo. | yes |

**User's choice:** Mesclar operacional, gerencial e minimo: dashboard hibrido e progressivo.
**Notes:** O usuario preferiu uma base completa com prioridade operacional. A janela padrao ficou como mes atual configuravel. Aberto, vencido, a vencer e pago no periodo ficam separados, com resumo consolidado.

---

## Fonte e confianca dos dados

| Option | Description | Selected |
|--------|-------------|----------|
| Aviso explicito no painel | Mostrar para o usuario que ainda ha paridade pendente. | |
| Indicador por origem/status | Mostrar origem/confianca por indicador. | |
| Ambos | Combinar aviso e origem/status. | |
| Sem aviso de paridade | Buscar a fonte correta e mostrar o numero certo para o usuario. | yes |

**User's choice:** Nao exibir aviso de paridade pendente; buscar na fonte operacional correta e mostrar correto.
**Notes:** `NotaCompraDuplicatas` foi escolhida como fonte principal para status e pagamento, usando `datapagamento`, `valorpago` e `idExtrato`. Inconsistencias devem aparecer em secao propria. O dashboard deve ter service proprio e queries agregadas em adapter legado.

---

## Filtros e recortes operacionais

| Option | Description | Selected |
|--------|-------------|----------|
| Periodo + Filial + Status | Topo enxuto e operacional. | |
| Periodo + Filial + Fornecedor + Banco + Status | Completo para rotina financeira sem entrar em telas profundas. | |
| Periodo + Filial + Fornecedor + Banco + Plano de contas + Status | Visao gerencial desde a primeira versao, com topo mais carregado. | yes |

**User's choice:** Periodo, filial, fornecedor, banco, plano de contas e status.
**Notes:** Depois, o usuario decidiu que as proximas escolhas devem seguir sempre o completo e recomendado. O comportamento dos filtros ficou hibrido: periodo/status podem atualizar rapido; fornecedor/banco/plano de contas usam botao `Filtrar`. Todos os recortes principais podem virar blocos/resumos, desde que com hierarquia visual.

---

## Navegacao e profundidade

| Option | Description | Selected |
|--------|-------------|----------|
| Dashboard isolado | Apenas KPIs e resumo, sem profundidade operacional. | |
| Dashboard com drill-down | KPIs e blocos navegam para grades/telas operacionais filtradas. | yes |
| Dashboard com acoes diretas | Fazer baixa/edicao/reversao direto no painel. | |

**User's choice:** Completo e recomendado pelo agente: dashboard com drill-down para telas operacionais, sem executar mutacoes no painel.
**Notes:** A decisao foi inferida da orientacao do usuario para seguir o completo/recomendado. Acoes sensiveis continuam nos services/telas operacionais existentes; o dashboard navega para `GestaoPagamentosView`, `DocumentoEntradaView`, `DocumentoEntradaDetalhesView` e fluxos de baixa quando aplicavel.

---

## the agent's Discretion

- O usuario delegou as proximas escolhas ao agente/planner, com a regra: escolher o completo e recomendado.
- O usuario reforcou que o planner deve sempre pesquisar o legado antes de implementar.
- O agente decidiu registrar a obrigacao de rastrear origem por classe/metodo/query/tabela antes de cada indicador ou filtro.

## Deferred Ideas

- Relatorios analiticos completos e fluxo de caixa previsto ficam fora desta fase.
- Acoes de pagamento, baixa, reversao, edicao e exclusao ficam nas telas operacionais responsaveis, nao no dashboard.
