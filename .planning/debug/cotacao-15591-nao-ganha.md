---
status: fixing
trigger: "Cotacao 15591 aprovada no legado nao foi marcada como ganha no ArpaSuite"
created: 2026-08-18
updated: 2026-08-18
---

## Symptoms

- expected: Cotacao aprovada no legado deve marcar o card vinculado como ganho no ArpaSuite.
- actual: Cotacao 15591 esta aprovada no legado, mas o card continua aberto.
- errors: API retornou 422: The winDate field must be a datetime value.
- timeline: Detectado em 2026-08-18 apos a integracao da cotacao 15591.
- reproduction: Salvar a cotacao, depois aprovar no legado e aguardar o polling de 30 segundos.

## Current Focus

- hypothesis: A versao atual da API rejeita o proprio campo winDate; a transicao deve omiti-lo.
- test: Enviar somente status=won e verificar o card 2232585.
- expecting: Card ganho, com data tecnica definida pelo ArpaSuite.
- next_action: Omitir winDate/lostDate, testar, publicar e reprocessar 15591.
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- timestamp: 2026-08-18T15:10:00-03:00
  finding: hub_crm_quote 15591 esta APROVADA/ERRO, vinculada ao deal 2232585.
- timestamp: 2026-08-18T15:10:00-03:00
  finding: API rejeitou winDate com offset e respondeu HTTP 422.
- timestamp: 2026-08-18T15:14:00-03:00
  finding: API tambem rejeitou ISO local, UTC Z, formato SQL e formato brasileiro.

## Eliminated

## Resolution

- root_cause: A versao atual da API rejeita o campo winDate com HTTP 422 em todos os formatos documentados; enviar esse campo impede a transicao.
- fix: Omitir winDate/lostDate, enviar status e motivo, e preservar a data real do legado na timeline.
- verification:
- files_changed:
