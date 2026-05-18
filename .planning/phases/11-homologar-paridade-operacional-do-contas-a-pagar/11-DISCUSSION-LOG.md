# Phase 11: Homologar paridade operacional do Contas a Pagar - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 11-Homologar paridade operacional do Contas a Pagar
**Areas discussed:** Checklist de paridade, evidencias de homologacao, politica de divergencias, achado de codigo morto

---

## Selecao de areas

| Option | Description | Selected |
|--------|-------------|----------|
| Todas | Discutir checklist de paridade, evidencias de homologacao e politica de divergencias antes de gerar o contexto. | yes |
| So homologacao | Focar em quem valida, quais cenarios o usuario percorre e quais evidencias confirmam substituicao do legado. | |
| So divergencias | Focar em quando uma diferenca bloqueia a liberacao, quando vira decisao explicita e como registrar excecoes. | |

**User's choice:** `1` - Todas.
**Notes:** O usuario depois delegou as decisoes de homologacao por paridade ao agente, pedindo para seguir a recomendacao sem prolongar a discussao.

---

## Checklist de paridade

| Option | Description | Selected |
|--------|-------------|----------|
| Checklist por fluxo operacional | Incluir, editar, salvar secoes, excluir, operar produtos, duplicatas, rateio, baixa manual, lote/cheque, reversao, permissoes e auditoria. | yes |
| Checklist por tela do legado | Validar cada tela/dialogo Swing mapeado contra uma tela ou acao equivalente no Vaadin. | |
| Checklist por regra critica | Validar principalmente regras financeiras, bloqueios, transacoes, permissoes e rastreabilidade; equivalencia visual secundaria. | |

**User's choice:** Delegou para a recomendacao do agente.
**Notes:** A recomendacao escolhida foi checklist por fluxo operacional, porque e mais adequado para homologacao de usuario e permite anexar origem tecnica por tras.

---

## Achado de codigo morto

| Finding | Impact |
|---------|--------|
| A tabela `contaspagar` nao existe no banco analisado `bck.tronbr.com/salome2_rp`. | `ContasPagarData` nao conseguiria persistir dados reais porque usa exclusivamente essa tabela. |
| `ContasPagar.java` nao e instanciada diretamente por outras classes Java. | A tela parece depender apenas de menu por reflection, se existir configuracao no banco. |
| `ContasPagarFornecedorFilialBean` e usado, mas guarda `NotaCompraDuplicatasBean`. | O nome e confuso; o artefato ativo pertence ao fluxo real de duplicatas de nota de compra. |
| O "Contas a Pagar" real do sistema e baseado em `NotaCompraDuplicatasConsulta` / `NotaCompraDuplicatas`. | A fase 11 deve homologar paridade contra `notacompraduplicatas` e `notacompra`, nao contra `ContasPagar.*`. |

**User's choice:** Tratar o achado como potencial mudanca de rumo.
**Notes:** O contexto consolidou a decisao recomendada: `ContasPagar.*` e codigo morto ate prova contraria; a homologacao deve mirar o fluxo ativo de `NotaCompraDuplicatas`.

---

## the agent's Discretion

- Definir o formato detalhado da homologacao por paridade.
- Criar checklist, relatorio e politica de divergencias com base na recomendacao tecnica.
- Tratar `ContasPagar.*` como codigo morto ate pesquisa adicional provar uso operacional real.

## Deferred Ideas

- Lancamento avulso de contas a pagar fora de `NotaCompra` deve virar fase propria se o negocio quiser essa capacidade, porque exigiria decisao de produto e schema versionado.
