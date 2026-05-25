---
phase: 13-criar-fluxo-de-caixa-previsto-operacional
plan: 02
subsystem: ui
tags:
  - forecast
  - vaadin
  - navigation
  - read-only
requirements-completed:
  - CASH-01
provides:
  - FluxoCaixaPrevistoView
  - FluxoCaixaView
affects:
  - src/main/java/br/com/salome/core/ui/financeiro/FluxoCaixaPrevistoView.java
  - src/main/java/br/com/salome/core/ui/placeholder/FluxoCaixaView.java
  - src/main/java/br/com/salome/core/ui/MainLayout.java
  - src/test/java/br/com/salome/core/ui/financeiro/FluxoCaixaPrevistoViewTest.java
tech-stack:
  added:
    - Vaadin
    - JUnit Platform
  patterns:
    - dense-operational-ui
    - read-only-drill-down
    - manual-refresh
    - service-driven-view
key-files:
  created:
    - src/main/java/br/com/salome/core/ui/financeiro/FluxoCaixaPrevistoView.java
    - src/test/java/br/com/salome/core/ui/financeiro/FluxoCaixaPrevistoViewTest.java
  modified:
    - src/main/java/br/com/salome/core/ui/placeholder/FluxoCaixaView.java
    - src/main/java/br/com/salome/core/ui/MainLayout.java
key-decisions:
  - Put the real forecast screen in `ui.financeiro` and keep the `fluxo-caixa` route pointing to it.
  - Keep the old placeholder class as a non-routed compatibility shim so the codebase can migrate cleanly.
  - Drive all screen content from `FluxoCaixaPrevistoService`; the view only renders and navigates to existing read-only screens.
  - Preserve manual refresh only, with no auto-refresh listeners or write actions in the forecast UI.
duration: 5m
completed: 2026-05-25T14:47:26.8202995-03:00
---
# Phase 13 Plan 02: Forecast UI and Navigation Summary

The forecast now has a real Vaadin screen and is reachable from the main navigation as a dense operational view.

What changed:
- Added `FluxoCaixaPrevistoView` as the operational forecast screen.
- Repointed the main drawer entry so "Fluxo de Caixa" opens the real forecast route.
- Converted the old placeholder class into a compatibility shim that no longer owns the route.
- Added a lightweight UI test that proves the screen can be constructed and rendered with a fake service.
- Kept every action read-only and routed drill-downs only to existing operational screens.

Verification:
- `javac` compilation succeeded for the new UI classes and UI test using the local cached dependencies.
- JUnit Platform execution of both `FluxoCaixaPrevistoServiceTest` and `FluxoCaixaPrevistoViewTest` passed with 5 tests successful.
- `rg -n "SELECT|INSERT|UPDATE|DELETE|JdbcTemplate" src/main/java/br/com/salome/core/ui` returned no matches.
- `rg -n "[^\\x00-\\x7F]"` over the new UI files returned no matches.

Issues encountered:
- `mvn` is not installed in this workspace PATH, so I could not run the exact Maven command from the plan.
- I validated the UI with direct `javac` compilation and a local JUnit Platform runner built from cached dependencies instead.

Next step:
- Phase 13 is now ready for the next workflow step, with both plans complete and the forecast screen wired into the app.
