# Phase 8 Research: Validar dados da tela web contra o legado

**Date:** 2026-05-14
**Phase:** 08-validar-dados-da-tela-web-contra-o-legado
**Requirement:** READ-03
**Status:** Complete

## Research Question

What do we need to know to plan a safe validation phase for confirming that the Vaadin `Gestao de Pagamentos` read-only screen matches the legacy MySQL data and mapped Swing behavior before any write capability is introduced?

## Sources Read

- `AGENTS.md`
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md`
- `.planning/phases/08-validar-dados-da-tela-web-contra-o-legado/08-CONTEXT.md`
- `.planning/phases/07-criar-tela-contas-a-pagar-somente-leitura/07-VERIFICATION.md`
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/TESTING.md`
- `docs/architecture/salome-core-architecture.md`
- `docs/setup/salome-core-local.md`
- `src/main/java/br/com/salome/core/domain/contaspagar/**`
- `src/main/java/br/com/salome/core/application/contaspagar/**`
- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/**`
- `src/test/java/br/com/salome/core/application/contaspagar/GestaoPagamentosServiceTest.java`

## Existing Implementation Shape

The Phase 7 screen already follows the intended dependency flow:

- `GestaoPagamentosView` calls `GestaoPagamentosService`.
- `GestaoPagamentosService` is `@Transactional(readOnly = true)` and calls `GestaoPagamentosRepository`.
- `GestaoPagamentosRepository` exposes read-only methods for title list, selected detail, products, parcels and rateios.
- `LegacyGestaoPagamentosRepository` is the current JDBC adapter over `notacompraduplicatas`, `notacompra`, `fornecedor`, `filial`, `extrato`, `notacompraprodutos` and `notacomprarateio`.
- `InMemoryGestaoPagamentosRepository` is the fallback when the legacy datasource is not enabled.
- Legacy DB activation is controlled by `SALOME_LEGACY_DB_ENABLED=true`, `SALOME_LEGACY_DB_URL`, `SALOME_LEGACY_DB_USERNAME` and `SALOME_LEGACY_DB_PASSWORD`.

This means Phase 8 should not validate by scraping Vaadin UI state. It should validate the same application read path that the UI uses, through services/adapters, and produce a versioned Markdown report.

## Critical Validation Scope

The phase context locks these comparison areas:

- Filters: `Hoje`, `Vencidas`, `Proximos dias`, `Todas` and manual period.
- Main grid: one row per `NotaCompraDuplicatas` record included by the active filter.
- Selected detail: note/duplicata detail, fornecedor, filial, fiscal document, dates, values, payment fields and extrato context.
- Detail collections: products, parcels and rateio for the selected note.
- Evidence classes: pending, overdue, paid/baixada, with/without products, with/without rateio and with/without extrato when such data exists in the database.

The comparison rule is strict: textual values, monetary values, dates, status, links, counts, ordering and detail fields must match exactly or be reported as divergences.

## Legacy Sources of Truth

The canonical legacy references for validation are:

- `salome-legacy/view/NotaCompra.java::formWindowOpened` for note list semantics and aggregate/status context.
- `salome-legacy/view/NotaCompraDuplicatas.java::notaCompraDuplicatas` for parcel/duplicata rows.
- `salome-legacy/view/NotaCompraProdutos.java::construtor` for products.
- `salome-legacy/view/NotaCompraRateio.java::construtor` for rateio.
- `salome-legacy/view/Extrato.java` and `salome-legacy/view/ExtratoPagarLancamentos.java` for extrato/payment linkage.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` for the mapped table and query surface.

The validator should cite these origins in mismatch messages or in a source map so every field has a traceable expected origin.

## Recommended Technical Approach

Create a validation subsystem outside the Vaadin UI:

- A small local runner, for example a Spring `ApplicationRunner` gated by a profile/property, or a service callable from a focused integration test/command.
- A validation service under `application/contaspagar/validation` or equivalent.
- A comparator engine that accepts two snapshots: expected legacy rows and actual `GestaoPagamentosService` snapshots.
- An expected-data adapter that reads the legacy MySQL queries mapped from the legacy behavior.
- A report writer that generates `.planning/phases/08-validar-dados-da-tela-web-contra-o-legado/08-VALIDATION.md`.
- Fixture-based unit tests for the comparator engine, independent of a live MySQL database.

The live validator must fail clearly when the legacy datasource is not configured. It should not silently fall back to in-memory demo data or declare success without MySQL.

## Comparison Model

Use explicit comparison keys:

- Title key: `notaCompraId + duplicataId`.
- Detail key: `notaCompraId + duplicataId`.
- Products key: `notaCompraId + product row id/code/order`.
- Parcel key: `notaCompraId + duplicataId`.
- Rateio key: `notaCompraId + rateio row id/order`.

Recommended comparison fields:

- Titles: fornecedor, documento/nota, parcela, data emissao, data entrada, vencimento, valor, valor pago, status, filial, centro/plano summary and ordering.
- Detail: fornecedor document, serie fiscal, filial, centro/plano summary, dates, valor da nota, valor pago, meio pagamento, extrato historico and observacao.
- Products: codigo, descricao, quantidade, valor unitario, total liquido, ICMS and IPI.
- Parcels: numero, vencimento, pagamento, valor, valor pago, meio pagamento and status.
- Rateios: filial, centro de custo, plano de contas and valor.

Represent every mismatch as:

- filter/scenario
- record key
- field/path
- expected from legacy/MySQL
- actual from `salome-core`
- origin reference
- severity or status (`divergence`, `pending-origin`, `environment`)

## Known Risk Found During Research

`LegacyGestaoPagamentosRepository.SQL_TITULOS` selects `ncd.valor AS valorTotal`, but `tituloRowMapper()` reads `rs.getBigDecimal("valorNota")` for the `TituloResumo.valorTotal` constructor argument. This likely causes the live title query to fail with a missing column or map the wrong field, depending on driver behavior.

Phase 8 should capture this through the validator and/or include a focused task to fix validation blockers discovered by strict comparison. The phase context says this phase documents divergences and plans correction rather than becoming a broad correction phase, so any fix should be limited to enabling truthful validation and recorded in `08-VALIDATION.md`.

## Boundaries

Do not:

- Alter `salome-legacy`.
- Write to the legacy database.
- Add Flyway schema changes.
- Expose validation as a Vaadin button.
- Add SQL or financial rules to `GestaoPagamentosView`.
- Enable baixa, edit, delete, payment confirmation, mutable rateio or any write path.

Do:

- Keep validation outside UI.
- Use services/repositories/adapters.
- Compare against MySQL legacy when configured.
- Fail loudly on missing DB config.
- Keep unit tests independent of the real database.
- Version the validation report in the phase directory.

## Planning Recommendation

Use one executable plan with these waves:

1. Add validation model, comparator and fixture tests.
2. Add expected legacy query adapter and actual service snapshot collector.
3. Add local validation runner and Markdown report writer.
4. Execute safe checks, generate `08-VALIDATION.md` when credentials are available, and document environment blockers when they are not.

## RESEARCH COMPLETE
