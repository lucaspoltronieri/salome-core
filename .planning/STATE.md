---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Phase 13 context gathered
last_updated: "2026-05-25T17:06:55.531Z"
progress:
  total_phases: 18
  completed_phases: 8
  total_plans: 17
  completed_plans: 17
  percent: 100
---

# State: Migracao Salome Legacy para Financeiro Web

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-14)

**Core value:** Modernizar o Contas a Pagar com seguranca, preservando as regras financeiras existentes, entregando equivalencia funcional completa no `salome-core` e mantendo o legado operando sem interrupcao.
**Current focus:** Phase 13 - criar-fluxo-de-caixa-previsto-operacional

## Current Phase

- **Phase:** 13
- **Name:** Criar fluxo de caixa previsto operacional
- **Status:** Ready to plan
- **Recommended next command:** `$gsd-plan-phase 13 ${GSD_WS}`

## Guardrails

- Nao alterar `salome-legacy` sem autorizacao explicita.
- Nao alterar banco de producao.
- Nao criar campo/tabela sem gerar script SQL versionado.
- Nao colocar regra de negocio dentro da View Vaadin.
- Nao colocar SQL dentro da View Vaadin.
- Mapear e documentar regras antes de migrar comportamento.
- A versao somente leitura das fases 7 e 8 e baseline tecnico historico, nao escopo final.
- Migrar o Contas a Pagar completo para o `salome-core`: `NotaCompra`, produtos, duplicatas, rateio, baixa, banco, extrato, cheque, permissoes, edicao, salvamento, exclusao e auditoria.
- Nao planejar recortes parciais de escrita para a fase 9; o recorte aprovado e `NotaCompra` completa.

## Recent Activity

- 2026-05-12: Inicializado planejamento GSD do projeto.
- 2026-05-12: `AGENTS.md` lido antes da criacao dos artefatos.
- 2026-05-12: Git inicializado em `C:\dev\salome-core`.
- 2026-05-12: Mapas de Contas a Pagar, banco/queries e usuario/acesso adicionados em `.planning/codebase/`.
- 2026-05-12: Fase de descoberta consolidada a partir dos mapas existentes.
- 2026-05-13: Mapeamento das fases 1 a 4 formalizado como baseline da migracao.
- 2026-05-13: Orientacao reforcada para espelhar o legado em leitura antes de novas features.
- 2026-05-13: Phase 5 concluida com proposta de arquitetura em `docs/architecture/salome-core-architecture.md`.
- 2026-05-14: Phase 7 planejada com pesquisa e plano executavel para tela Vaadin somente leitura de duplicatas/NotaCompra.
- 2026-05-14: Phase 7 executada com fila Vaadin read-only de duplicatas, filtros Hoje/Vencidas/Proximos/Todas e verificacao estatica documentada.
- 2026-05-14: Escopo corrigido por decisao do usuario: v1 deve migrar Contas a Pagar completo, 100% equivalente ao legado. Read-only permanece apenas como checkpoint; Phase 9 passa a ser `NotaCompra` completa.
- 2026-05-20: Phase 12 concluida com dashboard financeiro operacional.

## Session

Last session: 2026-05-25T17:06:55.516Z
Last Date: 2026-05-25T17:06:55.516Z
Stopped At: Phase 13 context gathered
Resume File: .planning/phases/13-criar-fluxo-de-caixa-previsto-operacional/13-CONTEXT.md

## Next Steps

1. Instalar/expor Maven ou adicionar Maven Wrapper e executar `mvn test`.
2. Configurar `SALOME_LEGACY_DB_*` para um MySQL legado seguro de desenvolvimento/homologacao.
3. Fechar os bloqueios de produtos e rateio registrados em `11-HOMOLOGATION.md`.
4. Rodar homologacao manual/operatoria e atualizar o aceite final.
5. Reexecutar verificacao da fase 11 antes de marcar `FULL-08` como completo.

---
*Last updated: 2026-05-14 after complete Contas a Pagar scope correction*
