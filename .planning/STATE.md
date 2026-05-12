# State: Migracao Salome Legacy para Financeiro Web

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-12)

**Core value:** Modernizar o Contas a Pagar com seguranca, preservando as regras financeiras existentes e mantendo o legado operando sem interrupcao.
**Current focus:** Phase 1 - Mapear Contas a Pagar legado

## Current Phase

- **Phase:** 1
- **Name:** Mapear Contas a Pagar legado
- **Status:** Not started
- **Recommended next command:** `$gsd-map-codebase`

## Guardrails

- Nao alterar `salome-legacy` sem autorizacao explicita.
- Nao alterar banco de producao.
- Nao criar projeto `salome-financeiro-web` ainda.
- Nao criar tabelas, campos ou migrations ainda.
- Nao implementar telas ainda.
- Mapear e documentar regras antes de migrar comportamento.

## Recent Activity

- 2026-05-12: Inicializado planejamento GSD do projeto.
- 2026-05-12: `AGENTS.md` lido antes da criacao dos artefatos.
- 2026-05-12: Git inicializado em `C:\dev\salome-core`.

## Next Steps

1. Executar `$gsd-map-codebase` para mapear o codebase existente, com foco em `salome-legacy`.
2. Executar `$gsd-discuss-phase 1` para consolidar perguntas e criterios do mapeamento de Contas a Pagar.
3. Executar `$gsd-plan-phase 1` depois que o contexto da fase estiver claro.

---
*Last updated: 2026-05-12 after initialization*
