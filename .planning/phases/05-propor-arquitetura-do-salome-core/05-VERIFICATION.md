---
phase: 05-propor-arquitetura-do-salome-core
status: passed
verified: 2026-05-13
source:
  - 05-01-PLAN.md
  - 05-01-SUMMARY.md
---

# Phase 05 Verification: Propor arquitetura do salome-core

## Result

Status: passed

Phase goal verified: `salome-core` now has a reviewable architecture proposal
before project creation, with clear separation between `ui`, `application`,
`domain`, `infrastructure` and `security`.

## Requirement Coverage

| Requirement | Status | Evidence |
| --- | --- | --- |
| ARCH-01 | passed | `docs/architecture/salome-core-architecture.md` exists as the architecture proposal before project creation. |
| ARCH-02 | passed | The proposal defines responsibilities for `ui`, `application`, `domain`, `infrastructure` and `security`. |
| ARCH-03 | passed | The proposal states that Vaadin Views must not contain SQL or heavy financial rules. |
| ARCH-04 | passed | The proposal states that Services call Repositories/Adapters, and Repositories/Adapters access legacy MySQL. |

## Must-Haves

| Must-have | Status | Evidence |
| --- | --- | --- |
| Decision D-01 covered | passed | Spring Security centralization with legacy identity source is documented. |
| Decision D-02 covered | passed | `Conecta.getUsuario()`, `UsuarioController`, `usuario` and `usuarioalerta` adapter mapping is documented. |
| Decision D-03 covered | passed | Layered top-level packages and feature subpackages are documented. |
| Decision D-04 covered | passed | JDBC/JdbcTemplate over legacy MySQL is documented for adapters. |
| Decision D-05 covered | passed | CRUD-capable future repositories with read-only first exposure are documented. |
| Decision D-06 covered | passed | Roadmap order is preserved: architecture, validated read-only web, then mutations. |
| Architecture proposal exists | passed | `docs/architecture/salome-core-architecture.md` exists. |
| Layer separation documented | passed | `## Camadas e responsabilidades` and package structure sections cover all layers. |
| Vaadin View boundary documented | passed | The exact phrase `Views Vaadin chamam Services` is present. |
| Service to repository boundary documented | passed | The exact phrase `Services chamam Repositories/Adapters` is present. |
| Legacy database access boundary documented | passed | The exact phrase `Repositories/Adapters acessam MySQL legado` is present. |
| Spring Security integration documented | passed | `Spring Security`, `Conecta.getUsuario()`, `UsuarioController`, `usuario` and `usuarioalerta` are present. |

## Automated Checks

- `Test-Path docs/architecture/salome-core-architecture.md` returned true.
- Required architecture headings were found.
- Dependency-direction phrases were found.
- Adapter families and canonical references were found.
- Security, Flyway and critical financial-rule references were found.
- `pom.xml`, `src/main/java`, and `src/main/resources/db/migration` do not exist.

## Scope Check

No Spring Boot project, Vaadin View, Java source, Flyway migration or database
change was created in this phase.

`salome-legacy/` appears as an untracked directory in the workspace, but this
phase did not modify tracked legacy code and did not stage anything under it.

## Gaps

None.

---
*Verification completed: 2026-05-13*
