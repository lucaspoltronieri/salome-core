---
status: resolved
trigger: "Card ganho repete alteracao na timeline e cotacao zerada cria observacao"
created: 2026-08-20
updated: 2026-08-20
---

## Symptoms

- expected: Ganho deve ser enviado uma unica vez; timeline de cotacao somente com frete calculado e valor positivo.
- actual: Card ganho acumulou 1431 alteracoes; cotacao zerada criava observacao sem valores.
- errors: Sem erro visivel; comportamento repetitivo no historico do card.
- timeline: Identificado em 2026-08-20 no card 2234309, cotacao 15611.
- reproduction: Aprovar a cotacao e aguardar varios ciclos do polling; salvar cotacao antes de calcular o frete.

## Current Focus

- hypothesis: Confirmada e corrigida.
- test: Comparar updatedAt do card antes e depois de dois ciclos do polling.
- expecting: Horario inalterado e somente um evento GANHO processado.
- next_action: Nenhuma.
- reasoning_checkpoint: A idempotencia deve anteceder qualquer chamada externa mutavel.
- tdd_checkpoint: Testes de ganho unico e frete zerado aprovados.

## Evidence

- timestamp: 2026-08-20T14:10:00-03:00
  finding: Cotacao 15611 possuia somente um evento GANHO PROCESSADO no Hub.
- timestamp: 2026-08-20T14:10:00-03:00
  finding: O codigo chamava markWon antes de eventProcessed e diretamente no caminho de snapshot inalterado.
- timestamp: 2026-08-20T14:10:00-03:00
  finding: Cotacao 15611 possuia cinco eventos COTACAO_SALVA de snapshots sucessivos.
- timestamp: 2026-08-20T14:16:00-03:00
  finding: Apos dois ciclos, card 2234309 permaneceu won e updatedAt ficou em 20/08/2026 14:13:01.

## Eliminated

- hypothesis: O ArpaSuite criava sozinho alteracoes periodicas.
  reason: As alteracoes cessaram assim que a chamada repetida do polling foi bloqueada.

## Resolution

- root_cause: A idempotencia era verificada depois da chamada externa e o caminho de snapshot inalterado reenviava ganho a cada polling. A anotacao de snapshot nao validava totalFrete.
- fix: Consultar o evento antes de markWon/markLost e criar observacao de cotacao somente com totalFrete maior que zero.
- verification: Testes passaram; deploy ativo; updatedAt permaneceu inalterado por dois ciclos e existe um unico evento GANHO processado.
- files_changed: HubCrmQuoteSyncService.java, HubCrmQuoteSyncServiceTest.java, regra-integracao-arpa.md.
