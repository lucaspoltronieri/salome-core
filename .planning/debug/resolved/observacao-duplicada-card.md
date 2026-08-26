---
status: resolved
trigger: "Alguns cards registram duas observacoes identicas"
created: 2026-08-26
updated: 2026-08-26
---

## Symptoms

- expected: A mesma composicao da cotacao deve gerar uma unica observacao.
- actual: Card da cotacao 15676 possuia duas observacoes visualmente identicas.
- errors: Sem erro; duplicacao funcional na timeline.
- timeline: Identificado em 2026-08-26 no card Papel Ecologico Industria.
- reproduction: Salvar cotacao aberta e depois aprovar sem alterar seus valores.

## Current Focus

- hypothesis: Confirmada e corrigida.
- test: Validar chave content da cotacao 15676 e contagem de anotacoes enviadas.
- expecting: Conteudo indexado sem terceira anotacao.
- next_action: Nenhuma.
- reasoning_checkpoint: Snapshot tecnico, conteudo visivel e status exigem chaves idempotentes separadas.
- tdd_checkpoint: Testes de status sem duplicacao e compatibilidade retroativa aprovados.

## Evidence

- timestamp: 2026-08-26T15:45:00-03:00
  finding: Cotacao 15676 possuia snapshots 527e... e 2f66..., ambos com observacao criada.
- timestamp: 2026-08-26T15:45:00-03:00
  finding: O segundo snapshot foi seguido pelo evento GANHO da aprovacao em 26/08/2026 15:30.
- timestamp: 2026-08-26T15:45:00-03:00
  finding: quoteHash incluia status e statusAt, mas quoteAnnotation nao apresentava esses campos.
- timestamp: 2026-08-26T15:52:00-03:00
  finding: Producao criou content:a42c... como conteudo existente indexado sem nova anotacao; total enviado permaneceu 2.

## Eliminated

- hypothesis: O ArpaSuite duplicava sozinho a mesma requisicao.
  reason: O banco do Hub comprovou dois hashes tecnicos distintos e duas chamadas registradas.

## Resolution

- root_cause: A aprovacao alterava o hash tecnico por status/statusAt, embora o texto detalhado permanecesse identico, criando outra observacao.
- fix: Usar hash exclusivo do texto da observacao, separado do snapshot e do evento de status, com indexacao compativel dos registros antigos.
- verification: Testes passaram; deploy 0546613 ativo; cotacao 15676 indexada sem terceira anotacao.
- files_changed: HubCrmQuoteSyncService.java, HubCrmStore.java, HubCrmQuoteSyncServiceTest.java, regra-integracao-arpa.md.
