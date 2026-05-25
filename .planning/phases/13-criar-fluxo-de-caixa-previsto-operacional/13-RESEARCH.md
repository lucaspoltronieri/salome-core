# Phase 13: Criar fluxo de caixa previsto operacional - Research

**Gathered:** 2026-05-25
**Status:** Ready for planning

## What this phase needs to know

Phase 13 is not a generic treasury module and it is not a copy of a legacy cash-flow screen. The available evidence points to a payables-driven forecast built from the already migrated Contas a Pagar data: `NotaCompraDuplicatas` for due and paid behavior, `Extrato` for realized movements, and `v_saldobancariotalao` for opening balance context when needed.

The legacy codebase does not expose a dedicated cash-flow forecast engine. That means the plan needs to introduce a new read model and projection service instead of trying to reuse an existing forecast API that does not exist.

## Reusable code already in the core

- `src/main/java/br/com/salome/core/ui/placeholder/FluxoCaixaView.java` is the current route placeholder and is the natural place to replace the forecast screen.
- `src/main/java/br/com/salome/core/ui/MainLayout.java` already contains the drawer entry for `FluxoCaixaView`.
- `src/main/java/br/com/salome/core/ui/financeiro/DashboardFinanceiroView.java` shows the preferred dense operational style for financial screens.
- `src/main/java/br/com/salome/core/application/financeiro/DashboardFinanceiroService.java` is the current read-model service pattern for finance.
- `src/main/java/br/com/salome/core/infrastructure/legacy/financeiro/LegacyDashboardFinanceiroRepository.java` demonstrates how to isolate SQL in the legacy infrastructure layer.
- `src/main/java/br/com/salome/core/security/CurrentUserContext.java` is the current boundary for user/filial/banco scoping.
- `src/main/java/br/com/salome/core/domain/notacompra/LegacyOrigin.java` is the standard traceability carrier for legacy source references.

## Legacy facts that matter

### Forecast inputs

- `notacompraduplicatas.datapagamento`
- `notacompraduplicatas.valorpago`
- `notacompraduplicatas.idExtrato`
- `notacompraduplicatas.vencimento`
- `notacompra.dataEmissao`
- `notacompra.dataEntrada`
- `extrato.data`
- `extrato.dataConciliacao`
- `v_saldobancariotalao.saldo`
- `v_saldobancariotalao.saldoBancario`

Origin:

- `salome-legacy/view/NotaCompraDuplicatas.java`
- `salome-legacy/view/NotaCompraDuplicataBaixa.java`
- `salome-legacy/view/EmitirCheques.java`
- `salome-legacy/view/Extrato.java`
- `salome-legacy/view/PagamentoCaixa.java`

### Legacy semantics that should not be lost

- Paid items are derived from `datapagamento` and `idExtrato`, not from a new status table.
- The forecast should remain operational and explainable, not a generic analytic report.
- The dashboard phase already established that `NotaCompraDuplicatas` is the source of truth for payment state.
- Bank/caixa permission rules belong to the operational flows, not to a forecast-only view.
- `Filial.previsaoColeta` appears in the legacy maps, but it is unrelated to the cash-flow forecast itself and should not become the forecast engine.

## Gaps the plan must close

1. There is no dedicated forecast read model yet.
2. There is no dedicated forecast service boundary yet.
3. There is no dedicated legacy repository/adaptor for projection queries yet.
4. The current `FluxoCaixaView` is only a placeholder.
5. The drawer label exists, but the actual forecast screen must be operational, read-only and wired to drill-down screens.

## Planning recommendation

- Build a dedicated `FluxoCaixaPrevisto` read model instead of extending the dashboard model.
- Make the forecast payables-driven and cumulative: opening balance plus expected/realized outflows by date.
- Keep the default window to the current month, with user-configurable forecast horizon.
- Reuse the same dense operational UI language used by the dashboard, but do not copy the dashboard service or repository directly.
- Keep all SQL in `infrastructure.legacy`.
- Do not modify `salome-legacy`.

## Source references used

- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md`
- `.planning/phases/13-criar-fluxo-de-caixa-previsto-operacional/13-CONTEXT.md`
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`
- `.planning/codebase/USUARIO-ACESSO-MAPA.md`
- `.planning/codebase/ARCHITECTURE.md`
- `docs/architecture/salome-core-architecture.md`
- `docs/baixa-contas-pagar-regras-migradas.md`
- `src/main/java/br/com/salome/core/ui/MainLayout.java`
- `src/main/java/br/com/salome/core/ui/placeholder/FluxoCaixaView.java`
- `src/main/java/br/com/salome/core/ui/financeiro/DashboardFinanceiroView.java`
- `src/main/java/br/com/salome/core/application/financeiro/DashboardFinanceiroService.java`
- `src/main/java/br/com/salome/core/infrastructure/legacy/financeiro/LegacyDashboardFinanceiroRepository.java`

## Recommended plan shape

- Wave 1: forecast domain contracts, service boundary, legacy adapter and service-level tests.
- Wave 2: replace the placeholder screen with the real forecast UI, wire navigation and add UI-facing compile/behavior checks.

---

*Phase: 13-Criar fluxo de caixa previsto operacional*
*Context gathered: 2026-05-25*
