---
status: fixing
trigger: "Card ganho repete alteracao na timeline e cotacao zerada cria observacao"
created: 2026-08-20
updated: 2026-08-20
---

## Symptoms

- expected: Ganho deve ser enviado uma unica vez; timeline de cotacao somente com frete calculado e valor positivo.
- actual: Card ganho acumulou 1431 alteracoes; cotacao zerada cria observacao sem valores.
- errors: Sem erro visivel; comportamento repetitivo no historico do card.
- timeline: Identificado em 2026-08-20 no card 2234309, cotacao 15611.
- reproduction: Aprovar a cotacao e aguardar varios ciclos do polling; salvar cotacao antes de calcular o frete.

## Current Focus

- hypothesis: markWon e chamado antes de consultar o evento idempotente, inclusive no caminho de snapshot inalterado.
- test: Conferir eventos da cotacao 15611 e os dois pontos de chamada de markWon.
- expecting: Um evento GANHO processado, mas diversas chamadas externas de status.
- next_action: Aplicar idempotencia antes da chamada externa e bloquear observacao com totalFrete zerado.
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- timestamp: 2026-08-20T10:00:00-03:00
  finding: Cotacao 15611 possui somente um evento GANHO PROCESSADO no Hub.
- timestamp: 2026-08-20T10:00:00-03:00
  finding: O codigo chamava markWon antes de eventProcessed e diretamente no caminho de snapshot inalterado.
- timestamp: 2026-08-20T10:00:00-03:00
  finding: Cotacao 15611 possui cinco eventos COTACAO_SALVA de snapshots sucessivos.

## Eliminated

## Resolution

- root_cause: A idempotencia era verificada depois da chamada externa e o caminho de snapshot inalterado reenviava ganho a cada polling. A anotacao de snapshot nao validava totalFrete.
- fix: Consultar o evento antes de markWon/markLost e criar observacao de cotacao somente com totalFrete maior que zero.
- verification:
- files_changed:
