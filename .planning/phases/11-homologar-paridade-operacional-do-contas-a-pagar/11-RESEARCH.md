# Phase 11 Research: Homologar paridade operacional do Contas a Pagar

## RESEARCH COMPLETE

## Objective

Planejar a homologacao final do Contas a Pagar no `salome-core`, garantindo que o fluxo web possa substituir o fluxo legado ativo para o escopo mapeado, com evidencias versionadas, testes de regras criticas, rastreabilidade por origem e decisao explicita para divergencias.

## Sources Read

- `.planning/phases/11-homologar-paridade-operacional-do-contas-a-pagar/11-CONTEXT.md`
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md`
- `.planning/phases/09-migrar-notacompra-completa/09-CONTEXT.md`
- `.planning/phases/10-migrar-baixa-completa-de-contas-a-pagar/10-CONTEXT.md`
- `.planning/phases/10-migrar-baixa-completa-de-contas-a-pagar/10-RESEARCH.md`
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`
- `.planning/codebase/USUARIO-ACESSO-MAPA.md`
- `docs/architecture/salome-core-architecture.md`
- `docs/baixa-contas-pagar-regras-migradas.md`
- Current `salome-core` source and tests under `src/main/java` and `src/test/java`

## Core Finding

The operational Contas a Pagar parity target is not the legacy `ContasPagar.*` family. For Phase 11, parity must be validated against the active `NotaCompra` and `NotaCompraDuplicatas` flow:

- `NotaCompraDuplicatasConsulta.java` is the visible operational consultation/payment queue for Contas a Pagar.
- `NotaCompra.java`, `NotaCompraDuplicatas.java`, `NotaCompraRateio.java`, `NotaCompraProdutos` and related controllers/data classes define the editable purchase document flow.
- `NotaCompraDuplicataBaixa.java`, `EmitirCheques.java`, `Extrato.java` and `PagamentoCaixa.java` define baixa, cheque/lote, reversal, banco/saldo and caixa permission behavior.
- `ContasPagar.java`, `ContasPagarController`, `ContasPagarData`, `ContasPagarBean` and `ContasPagarTable` should remain classified as dead/historical code unless a safe target database proves the `contaspagar` table and menu path are actively used.

This matters because homologation should not invent a new `contaspagar` schema or adapter. The evidence should explicitly resolve that legacy surface as `Nao aplicavel - codigo morto` or `Aceito por decisao de produto`, depending on the database/menu proof gathered during execution.

## Current salome-core Fit

Reusable implemented assets:

- `DocumentoEntradaView` and `DocumentoEntradaDetalhesView` expose Documento Entrada, cabecalho, produtos, duplicatas, rateio, baixa manual, reversal and XML-after-payment entry points.
- `GestaoPagamentosView` exposes the payment queue, filters, detail panels, Central de Pagamentos shell and lote/cheque actions.
- `DocumentoEntradaService`, `ProdutoNotaCompraService`, `DuplicataNotaCompraService` and `RateioNotaCompraService` are the application layer for `NotaCompra`.
- `BaixaNotaCompraService`, `FinanceiroPermissionService`, `FinanceiroRuleValidator` and `FinanceiroRepository` are the application layer for baixa, lote/cheque, saldo, caixa and reversal.
- `GestaoPagamentosValidationService`, `GestaoPagamentosStrictComparator` and report writer from Phase 8 can be reused for live read parity evidence.
- Tests already cover parts of validation and permission behavior in `NotaCompraFoundationTest`, `BaixaNotaCompraServiceTest`, `GestaoPagamentosStrictComparatorTest` and `DevelopmentCurrentUserContextTest`.

Visible risks and gaps:

- Some UI actions in `DocumentoEntradaDetalhesView` still use demo/minimal commands (`salvarProdutoDemo`, `salvarDuplicataDemo`, `salvarRateioDemo`) and need either operational completion or explicit homologation blockage.
- `GestaoPagamentosView` has active lote/cheque and reversal buttons, but the first toolbar buttons still show Phase 7 read-only tooltips. Homologation must verify whether those are intentionally superseded by the Central de Pagamentos/Documento Entrada paths or whether they confuse the operator.
- Phase 8 validation report covers read parity, not full operational parity. Phase 11 needs a broader `11-HOMOLOGATION.md` with checklist rows, status, evidence, origins and final user acceptance.
- Requirement status in `.planning/REQUIREMENTS.md` still lists `FULL-01`, `FULL-02` and `FULL-04` as pending even though Phase 11 depends on their behavior being complete or explicitly blocked. The phase must not quietly mark `FULL-08` complete while those remain unresolved.

## Homologation Evidence Model

Recommended evidence artifact: `.planning/phases/11-homologar-paridade-operacional-do-contas-a-pagar/11-HOMOLOGATION.md`.

Required sections:

- Environment: date, branch/commit, safe database or fake/test environment, operator/validator, app URL if manually tested.
- Scope decision: active flow is `NotaCompra`/`NotaCompraDuplicatas`; `ContasPagar.*` is dead/historical until database proof says otherwise.
- Checklist: one row per operational flow with status `Coberto`, `Bloqueado`, `Aceito com diferenca documentada`, `Nao aplicavel - codigo morto` or `Fora de fase`.
- Evidence: automated test command, validation report path, manual steps, screenshots/log notes if available.
- Divergences: expected legacy behavior, salome-core behavior, severity, origin, decision and owner.
- Final acceptance: user/operator confirmation that web replaces the mapped legacy flow, or explicit blockers.

The checklist should cover at least:

- Consultar e filtrar duplicatas: Hoje, Vencidas, Proximos dias, Todas, Periodo manual.
- Abrir Documento Entrada from the payment context or equivalent navigation.
- Cabecalho: incluir, editar, salvar, excluir and XML-after-payment path.
- Produtos: include/edit/delete/save with product origin.
- Duplicatas: include/edit/delete/generate and paid/extrato lock.
- Rateio: include/edit/delete with filial, centro de custo, plano de contas and remaining-value rule.
- Baixa manual: banco, operacao, historico, valor pago, data pagamento, extrato and duplicata update.
- Central de Pagamentos lote/cheque: selected duplicatas, operation 83 default, cheque fields and transfer operation 97 when applicable.
- Reversal: clear duplicatas and delete extrato atomically.
- Permissions: read-only user, finance operation user, caixa permission, XML-after-payment permission.
- Audit/origins: every critical rule cites class, method/button, DAO/query and table.

## Planning Recommendation

Plan Phase 11 in three waves:

1. Build the homologation evidence matrix and classify every legacy operational surface, including the `ContasPagar.*` dead-code decision.
2. Add/extend automated parity checks and tests for the highest-risk financial and permission scenarios, using service boundaries and no production database writes.
3. Fix or explicitly document operational blockers, run final verification, update requirement traceability and produce the final `11-HOMOLOGATION.md` acceptance record.

This split keeps the phase auditable. Wave 1 creates the truth table, Wave 2 proves the rules, Wave 3 closes or records gaps before claiming `FULL-08`.

## Risks and Mitigations

- Risk: Homologation becomes a vague manual checklist. Mitigation: require a row-level artifact with status, evidence, origin and decision for each flow.
- Risk: Dead legacy `ContasPagar.*` is accidentally rebuilt. Mitigation: require explicit database/menu proof before any `contaspagar` adapter/schema work; otherwise mark as `Nao aplicavel - codigo morto`.
- Risk: Existing UI demo shortcuts are mistaken for complete operational behavior. Mitigation: inspect each command path and make demo paths blockers unless replaced by real service-backed flows.
- Risk: A live database is unavailable. Mitigation: allow the homologation report to mark live validation as environment-blocked, but automated service tests and static architecture checks remain mandatory.
- Risk: Phase 11 tries to repair all missing Phase 9 behavior in one plan. Mitigation: classify blockers clearly; only make small, high-confidence corrections needed to prove parity, and defer product changes with explicit decisions.

## Test Priorities

- `mvn test` as the minimum local gate when JDK 25/Maven are available.
- Service-level tests for `NotaCompra` cabecalho/produtos/duplicatas/rateio locks and permissions.
- Service-level tests for baixa manual, lote/cheque, reversal, banco caixa and caixa permission.
- Comparator/report tests proving divergences include expected value, actual value, path and origin.
- Static checks proving no SQL/JdbcTemplate in `ui` and no tracked modifications in `salome-legacy`.

