# Phase 7: Criar tela Contas a Pagar somente leitura - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 7-Criar tela Contas a Pagar somente leitura
**Areas discussed:** Recorte da grade principal

---

## Recorte da grade principal

| Option | Description | Selected |
|--------|-------------|----------|
| Duplicatas a pagar | A grade vira uma fila operacional de parcelas/titulos, baseada em `NotaCompraDuplicatas` + `NotaCompra`, com foco em vencimento, status, fornecedor, valor e vinculo com extrato. | yes |
| Notas de compra recentes | A grade lista `NotaCompra` como documento principal, com duplicatas/produtos/rateio no detalhe. | |
| Espelho mais fiel da tela legada `ContasPagar` | A grade prioriza a tabela `ContasPagar` e suas colunas do Swing. | |

**User's choice:** `Duplicatas a pagar`.
**Notes:** O usuario definiu que a tela sera dividida em tres partes: no centro fica a listagem de duplicatas + `NotaCompra`; ao clicar em uma linha, o canto direito mostra produtos com valor e plano de contas para o pagador saber do que se refere; na area de baixa fica a futura `Central de Pagamentos`.

---

## Estado inicial da listagem

| Option | Description | Selected |
|--------|-------------|----------|
| Em aberto e vencidas primeiro | Mostra o que ainda precisa de acao, ordenado por vencimento. | |
| Vencendo hoje por padrao | Abre direto na rotina do dia, com vencidas disponiveis por filtro. | yes |
| Todas as duplicatas recentes | Inclui pagas, pendentes e vencidas para conferencia ampla. | |

**User's choice:** `Vencendo hoje por padrao`.
**Notes:** A tela deve abrir mostrando as duplicatas que estao vencendo hoje. Deve haver opcoes para ver vencidas, filtrar e consultar outras visoes.

---

## Controles de filtro

| Option | Description | Selected |
|--------|-------------|----------|
| Abas/status rapidos no topo | Atalhos como `Hoje`, `Vencidas`, `Proximos dias`, `Todas`. | yes |
| Campo de filtro tradicional | Usuario abre filtros e escolhe status/periodo/fornecedor/filial. | |
| Os dois | Atalhos rapidos para uso diario e filtros avancados para busca especifica. | partial |

**User's choice:** `Abas/status rapidos no topo`, com filtro por data adicional.
**Notes:** Alem dos atalhos, deve existir opcao de filtrar por data/periodo.

---

## Colunas da linha central

| Option | Description | Selected |
|--------|-------------|----------|
| Resumo operacional enxuto | Fornecedor, numero da nota/documento, parcela, vencimento, valor, status e filial. | yes |
| Resumo financeiro mais completo | Inclui plano de contas/centro de custo, valor pago, meio de pagamento e extrato na propria linha. | |
| Linha enxuta com destaque visual | Linha enxuta com cores/icones para status. | |

**User's choice:** `Resumo operacional enxuto`.
**Notes:** Produtos, valores detalhados e plano de contas devem ficar no painel direito ao selecionar a duplicata.

---

## the agent's Discretion

- Definir detalhe tecnico dos valores de status e da janela de `Proximos dias`.
- Ajustar a composicao visual exata em Vaadin, mantendo a divisao em tres partes definida pelo usuario.

## Deferred Ideas

- Baixa real pela `Central de Pagamentos`.
- Edicao, exclusao, rateio mutavel e qualquer escrita no banco legado.
- Validacao formal dos dados da tela web contra o legado, prevista para a fase 8.
