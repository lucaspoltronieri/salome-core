# Phase 13: Criar fluxo de caixa previsto operacional - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-25
**Phase:** 13-criar-fluxo-de-caixa-previsto-operacional
**Areas discussed:** Escopo da previsao, Experiencia e navegacao, Filtros e horizonte, Ordenacao e leitura

---

## Escopo da previsao

| Option | Description | Selected |
|--------|-------------|----------|
| Dashboard analitico amplo | Mistura previsao, indicadores e relatorios mais gerais de tesouraria | |
| Previsao payables-driven | Usa duplicatas e baixa como base principal da previsao | ✓ |
| Tesouraria completa | Inclui inflows, outflows, bancos e operacoes bancarias novas | |

**User's choice:** Previsao payables-driven
**Notes:** A escolha seguiu a recomendacao tecnica para manter a fase dentro do escopo atual e reaproveitar os dados ja migrados.

---

## Experiencia e navegacao

| Option | Description | Selected |
|--------|-------------|----------|
| Landing page leve | Interface visual, com pouco detalhe operacional | |
| Painel operacional denso | Mostra linha do tempo, saldos projetados e drill-down | ✓ |
| Fluxo com acoes sensiveis | Permite baixa, reversao e pagamentos direto na tela | |

**User's choice:** Painel operacional denso
**Notes:** A experiencia precisa continuar acionavel, mas as acoes sensiveis ficam nas telas operacionais existentes.

---

## Filtros e horizonte

| Option | Description | Selected |
|--------|-------------|----------|
| Mes atual apenas | Janela fixa e simples, sem ajuste de horizonte | |
| Mes atual com horizonte configuravel | Mantem um padrao curto e permite ampliar para revisao | ✓ |
| Horizonte aberto por default | Mostra um periodo amplo sem foco inicial | |

**User's choice:** Mes atual com horizonte configuravel
**Notes:** O padrao permanece curto para leitura operacional, com flexibilidade para analisar o curto prazo.

---

## Ordenacao e leitura

| Option | Description | Selected |
|--------|-------------|----------|
| Listagem simples por vencimento | Apenas uma grade ordenada cronologicamente | |
| Linha do tempo com saldos projetados | Destaca proximidade, saldo e compromissos futuros | ✓ |
| Relatorio consolidado por status | Prioriza agregados acima do detalhamento | |

**User's choice:** Linha do tempo com saldos projetados
**Notes:** A leitura principal deve ser por vencimento/data futura, com destaque para o que esta mais proximo e para o saldo projetado.

---

## the agent's Discretion

- O usuario pediu para seguir direto e aceitar a recomendacao tecnica sem perguntas adicionais.
- A composicao visual exata da linha do tempo, cards ou grade ficou a criterio do agente, desde que permaneça densa, legivel e sem adornos de marketing.

## Deferred Ideas

- Influxo completo de caixa a partir de `contas a receber` e `faturamento` foi notado, mas pertence a fases futuras.
- Integracoes bancarias, portal de pagamentos e acoes de mutacao continuam fora da fase 13.
