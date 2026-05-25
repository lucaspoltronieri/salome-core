---
phase: 13-criar-fluxo-de-caixa-previsto-operacional
plan: 01
subsystem: financeiro
tags:
  - forecast
  - legacy-adapter
  - read-model
  - tests
requires:
  - CASH-01
provides:
  - FluxoCaixaPrevistoFiltro
  - FluxoCaixaPrevistoLancamento
  - FluxoCaixaPrevistoDia
  - FluxoCaixaPrevistoResumo
  - FluxoCaixaPrevistoSnapshot
affects:
  - src/main/java/br/com/salome/core/domain/financeiro
  - src/main/java/br/com/salome/core/application/financeiro
  - src/main/java/br/com/salome/core/infrastructure/legacy/financeiro
  - src/test/java/br/com/salome/core/application/financeiro
tech-stack:
  added:
    - Java 25
    - Spring Boot 4
    - Spring JDBC
  patterns:
    - read-model
    - legacy-adapter
    - service-boundary
    - deterministic-tests
key-files:
  created:
    - src/main/java/br/com/salome/core/domain/financeiro/FluxoCaixaPrevistoStatus.java
    - src/main/java/br/com/salome/core/domain/financeiro/FluxoCaixaPrevistoFiltro.java
    - src/main/java/br/com/salome/core/domain/financeiro/FluxoCaixaPrevistoLancamento.java
    - src/main/java/br/com/salome/core/domain/financeiro/FluxoCaixaPrevistoDia.java
    - src/main/java/br/com/salome/core/domain/financeiro/FluxoCaixaPrevistoResumo.java
    - src/main/java/br/com/salome/core/domain/financeiro/FluxoCaixaPrevistoSnapshot.java
    - src/main/java/br/com/salome/core/application/financeiro/FluxoCaixaPrevistoRepository.java
    - src/main/java/br/com/salome/core/application/financeiro/FluxoCaixaPrevistoService.java
    - src/main/java/br/com/salome/core/infrastructure/legacy/financeiro/LegacyFluxoCaixaPrevistoRepository.java
    - src/test/java/br/com/salome/core/application/financeiro/FluxoCaixaPrevistoServiceTest.java
  modified: []
key-decisions:
  - Build the forecast as a dedicated read model instead of extending the dashboard aggregate.
  - Keep SQL only in `infrastructure.legacy` and keep projection logic inside the service boundary.
  - Seed filial and banco from `CurrentUserContext` when available, but keep the user able to clear back to "Todos".
  - Model the timeline as daily rows with separate projected and realized balances so the UI can distinguish paid items from future titles.
  - Keep the forecast payables-driven using `notacompraduplicatas` as the source of truth and `v_saldobancariotalao` as opening balance context.
duration: 12m
completed: 2026-05-25T14:42:23.1718134-03:00
---
# Phase 13 Plan 01: Forecast Domain and Service Boundary Summary

The first wave of phase 13 now has a dedicated operational cash-flow forecast read model that is isolated from Vaadin and JDBC concerns.

What changed:
- Added a new forecast filter with current-month defaults, configurable horizon, and the same operational filters used by the finance screens.
- Added immutable read models for raw forecast rows, daily timeline rows, summary totals, and the final snapshot.
- Added a `FluxoCaixaPrevistoService` that computes the timeline, opening balance, projected totals, realized totals, and cumulative balances.
- Added a legacy JDBC adapter that reads the forecast inputs from `notacompraduplicatas`, `notacompra`, `extrato`, `banco`, `filial`, `fornecedor`, `planocontascentrocusto`, `planocontas`, and `v_saldobancariotalao`.
- Added database-free service tests covering current-month defaults, manual refresh behavior through repeated calls, deterministic projection math, and filter propagation.

Verification:
- `javac` compilation succeeded for the new domain/application/infrastructure/test files using the local cache of jars.
- JUnit Platform execution of `FluxoCaixaPrevistoServiceTest` passed with 4 tests successful.
- `rg -n "SELECT|INSERT|UPDATE|DELETE|JdbcTemplate" src/main/java/br/com/salome/core/domain` returned no matches.
- `rg -n "[^\\x00-\\x7F]"` over the new forecast files returned no matches.

Issues encountered:
- `mvn` is not installed in this workspace PATH, so I could not run the exact Maven command from the plan.
- To compensate, I validated the implementation with direct `javac` compilation and a local JUnit Platform runner built from the cached dependencies.

Next step:
- Ready for phase 13 plan 02, which will replace the `fluxo-caixa` placeholder route with the operational Vaadin forecast screen and wire the drill-down navigation.
