---
phase: 05-propor-arquitetura-do-salome-core
plan: 01
subsystem: architecture
tags: [java-25, spring-boot-4, vaadin, mysql, flyway, spring-security, jdbc]

requires:
  - phase: 01-04
    provides: "Legacy mapping for Contas a Pagar, usuarios, NotaCompra, baixa, banco and extrato"
provides:
  - "Reviewable architecture proposal for salome-core"
  - "Layer contracts for ui, application, domain, infrastructure and security"
  - "Legacy adapter and read-first migration guardrails"
affects: [phase-06, phase-07, architecture, security, legacy-adapters]

tech-stack:
  added: []
  patterns: [layered-architecture, read-model-command-model, legacy-adapter-boundary]

key-files:
  created:
    - docs/architecture/salome-core-architecture.md
  modified: []

key-decisions:
  - "salome-core architecture keeps Vaadin Views calling Services, Services calling Repositories/Adapters, and Repositories/Adapters accessing legacy MySQL."
  - "Spring Security centralizes authentication and authorization while legacy user semantics are adapted through a dedicated boundary."
  - "Phase 5 remains documentation-only; project scaffolding, Flyway migrations, writes and legacy changes stay out of scope."

patterns-established:
  - "Layered package structure with feature subpackages inside each layer."
  - "Read-first policy with future command services for financial mutations."
  - "Canonical references must be cited before migrating financial rules."

requirements-completed: [ARCH-01, ARCH-02, ARCH-03, ARCH-04]

duration: 2 min
completed: 2026-05-13
---

# Phase 05 Plan 01: salome-core Architecture Proposal Summary

**Reviewable salome-core architecture contract for read-first Vaadin migration over the legacy MySQL finance data**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-13T11:26:28.9043276-03:00
- **Completed:** 2026-05-13T11:28:27.1528140-03:00
- **Tasks:** 3 completed
- **Files modified:** 1

## Accomplishments

- Created `docs/architecture/salome-core-architecture.md` with the required architecture proposal sections.
- Documented layer responsibilities for `ui`, `application`, `domain`, `infrastructure` and `security`.
- Captured the read-first policy, future CRUD boundary, legacy MySQL adapter families, Spring Security integration, Flyway governance and critical financial test expectations.
- Preserved the phase boundary: no Java project, no migration, no production DB change and no legacy code change.

## Task Commits

1. **Task 1-3: Create architecture proposal, layer contracts, security/schema/test guardrails** - `07b8c5f` (docs)

**Plan metadata:** pending in metadata commit

## Files Created/Modified

- `docs/architecture/salome-core-architecture.md` - Architecture target document for `salome-core`.

## Decisions Made

- Followed the locked Phase 5 decisions from `05-CONTEXT.md` without changing scope.
- Used a documentation-only architecture proposal because Phase 6 owns project creation.
- Kept JDBC/JdbcTemplate as the first persistence recommendation for legacy MySQL adapters.
- Kept Spring Security as the boundary for current-user and permission integration.

## Deviations from Plan

None - plan executed exactly as written.

---

**Total deviations:** 0 auto-fixed.
**Impact on plan:** No scope change.

## Issues Encountered

- `salome-legacy/` is visible as an untracked directory in the workspace, but no files under it were modified by this plan.

## User Setup Required

None - no external service configuration required.

## Verification

- `Test-Path docs/architecture/salome-core-architecture.md` returned true.
- Required headings were found in `docs/architecture/salome-core-architecture.md`.
- Dependency-direction phrases were found: `Views Vaadin chamam Services`, `Services chamam Repositories/Adapters`, and `Repositories/Adapters acessam MySQL legado`.
- Adapter families and canonical mapping references were found.
- Security, Flyway, ARCH and critical finance-rule references were found.
- `pom.xml`, `src/main/java`, and `src/main/resources/db/migration` do not exist.

## Self-Check: PASSED

- [x] All tasks from `05-01-PLAN.md` completed.
- [x] All plan verification checks completed.
- [x] `docs/architecture/salome-core-architecture.md` exists and is substantive.
- [x] ARCH-01, ARCH-02, ARCH-03 and ARCH-04 are addressed.
- [x] No application code or database migration was created.

## Next Phase Readiness

Phase 6 can create the Java 25 + Spring Boot 4 + Vaadin base project using the documented package structure and boundaries. The architecture proposal should be treated as the contract for avoiding SQL and heavy financial rules in Vaadin Views.

---
*Phase: 05-propor-arquitetura-do-salome-core*
*Completed: 2026-05-13*
