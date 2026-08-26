---
status: fixing
trigger: "Alguns cards registram duas observacoes identicas"
created: 2026-08-26
updated: 2026-08-26
---

## Symptoms

- expected: A mesma composicao da cotacao deve gerar uma unica observacao.
- actual: Card da cotacao 15676 possui duas observacoes visualmente identicas.
- errors: Sem erro; duplicacao funcional na timeline.
- timeline: Identificado em 2026-08-26 no card Papel Ecologico Industria.
- reproduction: Salvar cotacao aberta e depois aprovar sem alterar seus valores.

## Current Focus

- hypothesis: status e statusAt participam do hash do snapshot, mas nao do texto da observacao.
- test: Comparar eventos de snapshot e evento de ganho da cotacao 15676.
- expecting: Dois hashes de snapshot e textos iguais, sendo o segundo provocado pela aprovacao.
- next_action: Separar hash do conteudo da observacao e indexar eventos antigos sem republicacao.
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- timestamp: 2026-08-26T15:45:00-03:00
  finding: Cotacao 15676 possui snapshots 527e... e 2f66..., ambos com observacao criada.
- timestamp: 2026-08-26T15:45:00-03:00
  finding: O segundo snapshot foi seguido pelo evento GANHO da aprovacao em 26/08/2026 15:30.
- timestamp: 2026-08-26T15:45:00-03:00
  finding: quoteHash inclui status e statusAt, mas quoteAnnotation nao apresenta esses campos.

## Eliminated

## Resolution

- root_cause: A aprovacao alterava o hash tecnico por status/statusAt, embora o texto detalhado permanecesse identico, criando outra observacao.
- fix: Usar hash exclusivo do texto da observacao, separado do snapshot e do evento de status, com indexacao compativel dos registros antigos.
- verification:
- files_changed:
