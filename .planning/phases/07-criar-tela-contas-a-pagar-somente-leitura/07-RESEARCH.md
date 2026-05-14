# Phase 7 Research: Criar tela Contas a Pagar somente leitura

## RESEARCH COMPLETE

## Question

What do I need to know to plan Phase 7 well?

Phase 7 must turn the current Vaadin shell for **Gestao de Pagamentos** into the first useful read-only Contas a Pagar screen. The screen must center the user workflow on `NotaCompraDuplicatas` rows linked to `NotaCompra`, keep all SQL in the legacy adapter, preserve `View -> Service -> Repository/Adapter`, and keep every payment, baixa, edit, delete, rateio mutation and database write unavailable.

## User Constraints from CONTEXT.md

- D-01: the central listing is a queue of duplicatas + `NotaCompra`, not a direct Swing clone and not primarily a note list.
- D-02: each row must show fornecedor, documento/nota, parcela, vencimento, valor, status and filial.
- D-04: initial load must show duplicatas vencendo hoje.
- D-05: overdue duplicatas must not be mixed into initial state; they are available through their own filter/shortcut.
- D-06 and D-07: quick shortcuts are `Hoje`, `Vencidas`, `Proximos dias`, `Todas`, plus manual date/period filter.
- D-08 through D-11: the screen has central list, right details/products, and a future Central de Pagamentos area that remains read-only.
- READ-01: user can open the Vaadin Contas a Pagar read-only screen.
- READ-02: the read-only screen displays relevant legacy Contas a Pagar data.

## Current Code State

### Existing assets to reuse

- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java`
  - Already opens at the root route.
  - Already calls `GestaoPagamentosService`.
  - Already has a title grid, detail panel, product grid, parcel grid, rateio grid and disabled Central de Pagamentos actions.
  - Needs real filters/shortcuts, a clearer three-part layout, and labels aligned to the Phase 7 decisions.

- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosService.java`
  - Already wraps reads in `@Transactional(readOnly = true)`.
  - Already resolves selected title and loads detail/products/parcelas/rateios.
  - Needs to accept the active `GestaoPagamentosFiltro` instead of always using `GestaoPagamentosFiltro.padrao()` from the View.

- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosRepository.java`
  - Already exposes read-only methods only.
  - Must stay read-only in Phase 7.

- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/LegacyGestaoPagamentosRepository.java`
  - Already centralizes SQL outside the View using `JdbcTemplate`.
  - Already reads `notacompra`, `notacompraduplicatas`, `fornecedor`, `filial`, `extrato`, products and rateio tables.
  - Needs to make `NotaCompraDuplicatas` the primary table in the title query, filter by due date range/status, order operationally, and avoid duplicating rows through rateio joins.

- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/InMemoryGestaoPagamentosRepository.java`
  - Useful fallback for local development.
  - Needs sample data matching the Phase 7 defaults, especially one item due today.

- `src/test/java/br/com/salome/core/application/contaspagar/GestaoPagamentosServiceTest.java`
  - Already protects the read-only repository contract and service snapshot flow.
  - Should gain tests for default filter and selection/filter propagation.

## Legacy Origins to Preserve

### Title queue and status

- `salome-legacy/view/NotaCompra.java::formWindowOpened`
  - Loads the main note grid with calculated values for products, duplicatas, paid amount, amount payable and status.
  - Relevant for understanding how note-level totals and status appear, but Phase 7 should not make notes the primary row.

- `salome-legacy/view/NotaCompraDuplicatas.java::notaCompraDuplicatas`
  - Loads duplicatas/parcelas for a `NotaCompra`.
  - Origin for duplicate number, vencimento, valor, datapagamento, valorpago, meioPagamento and extrato linkage.

- `salome-legacy/view/EmitirCheques.java::atualizaTabela(true)`
  - Lists unpaid `notacompraduplicatas` joined to `notacompra` and `filial`, filtered by filial, fornecedor and vencimento interval.
  - Important because Phase 7's operational queue is closer to "what can be paid" than to the note-maintenance screen.
  - For Phase 7, only the read logic is relevant; cheque emission and baixa remain out of scope.

### Detail panel

- `salome-legacy/view/NotaCompraProdutos.java::construtor`
  - Origin for product list linked to the selected note.

- `salome-legacy/view/NotaCompraRateio.java::construtor`
  - Origin for rateio display by filial, centro de custo and plano de contas.

- `salome-legacy/view/NotaCompraDuplicatas.java::construtor`
  - Origin for the parcel list and payment/extrato read fields.

### Tables

Minimum Phase 7 title query tables:

- `notacompraduplicatas` as the primary queue table.
- `notacompra` for document, serie, dates, note value, filial and fornecedor linkage.
- `fornecedor` for fornecedor name/document.
- `filial` for filial label.
- `extrato` for read-only payment linkage where present.

Supporting detail tables:

- `notacompraprodutos`.
- `notacomprarateio`.
- `planocontascentrocusto`.
- `planocontas`.
- `centrocusto`.

## Recommended Plan Shape

Use one executable plan with four small slices:

1. Model and service filters.
   - Expand `GestaoPagamentosFiltro` to carry quick range/status intent and date range.
   - Add factory methods for `hoje`, `vencidas`, `proximosDias`, `todas` and manual period.
   - Make the View keep the active filter and pass it to the service.

2. Legacy adapter query alignment.
   - Rewrite `SQL_TITULOS` so `notacompraduplicatas` is the driving table.
   - Filter by due date and quick mode in SQL with parameters.
   - Select row data required by D-02: fornecedor, document/nota, parcela, vencimento, value, status and filial.
   - Prevent duplicate rows from `notacomprarateio` by using a correlated subquery or grouped/min description for centro/plano summary instead of joining rateio directly in the queue.

3. Vaadin screen refinement.
   - Add quick shortcut buttons/tabs and manual period inputs.
   - Default to `Hoje`.
   - Keep mutating commands disabled.
   - Keep dense operational layout: central queue, right products/details, bottom Central de Pagamentos shell.

4. Tests and guardrails.
   - Add focused unit tests for filter factory behavior and service propagation.
   - Add repository contract guard against write method names.
   - Add static guardrail checks in verification: no SQL/JdbcTemplate in `ui`, no `INSERT/UPDATE/DELETE/executeUpdate` in `infrastructure/legacy/contaspagar`, no tracked legacy modifications.

## Implementation Risks

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| Title query duplicates rows when joining rateio | A note can have multiple rateio rows; the title queue must show one row per duplicata | Use `notacompraduplicatas` as the base and derive centro/plano summary by subquery or grouped aggregation |
| Default filter accidentally shows overdue items | D-04 and D-05 explicitly separate "Hoje" from "Vencidas" | Use `vencimento = CURRENT_DATE` for default quick filter and test fallback data |
| Paid items appear as actionable | Payment actions remain future scope | Display status only; keep baixa/confirmar/excluir disabled |
| View accumulates business logic | Existing View already formats and switches tabs; adding filters can tempt SQL/status rules into UI | Keep only UI event handling in View; compute query/status in service/repository/read model |
| Live MySQL is unavailable in development | Existing local profile can use in-memory fallback | Keep `InMemoryGestaoPagamentosRepository` aligned to the same filter semantics |
| Environment cannot run Java 25/Maven | Phase 6 summary noted local tooling may be missing | Include verification commands, and if unavailable, record that as execution-time blocker in the future summary |

## Recommended Verification

- `mvn test`
- `rg -n "SELECT|JdbcTemplate|INSERT|UPDATE|DELETE|executeUpdate" src/main/java/br/com/salome/core/ui` should return no matches.
- `rg -n "INSERT|UPDATE|DELETE|executeUpdate" src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar` should return no mutating operations for this phase.
- `rg -n "notacompraduplicatas|vencimento|datapagamento|CURRENT_DATE|GestaoPagamentosFiltro" src/main/java/br/com/salome/core`
- `git status --short --untracked-files=no salome-legacy` should show no tracked modifications.

## Open Questions for Execution

- The exact "Proximos dias" window is at executor discretion per CONTEXT.md; recommended default is 7 days after today.
- The status values can remain simple (`PAGO`, `ATRASADO`, `PENDENTE`) unless execution discovers a documented legacy status label that should be preserved.
- Phase 8 will validate values against the legacy behavior; Phase 7 should be conservative and keep the query readable and traceable rather than over-optimizing.

## Sources Read

- `AGENTS.md`
- `.planning/phases/07-criar-tela-contas-a-pagar-somente-leitura/07-CONTEXT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md`
- `.planning/phases/05-propor-arquitetura-do-salome-core/05-CONTEXT.md`
- `.planning/phases/06-criar-projeto-java-25-spring-boot-4-vaadin/06-CONTEXT.md`
- `.planning/phases/06-criar-projeto-java-25-spring-boot-4-vaadin/06-01-SUMMARY.md`
- `docs/architecture/salome-core-architecture.md`
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`
- `references/ux-frontend/screens/contas-pagar.jsx`
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java`
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosService.java`
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosRepository.java`
- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/LegacyGestaoPagamentosRepository.java`
- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/InMemoryGestaoPagamentosRepository.java`
- `src/main/java/br/com/salome/core/domain/contaspagar/GestaoPagamentosFiltro.java`
- `src/main/java/br/com/salome/core/domain/contaspagar/TituloResumo.java`
- `src/main/java/br/com/salome/core/domain/contaspagar/TituloDetalhe.java`
- `src/test/java/br/com/salome/core/application/contaspagar/GestaoPagamentosServiceTest.java`
