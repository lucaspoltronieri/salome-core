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

- hypothesis: A chamada de ganho foi rejeitada pelo formato da data enviado a API.
- test: Consultar status, last_error e logs da cotacao 15591.
- expecting: Erro 422 relacionado ao campo winDate ou falha equivalente da API.
- next_action: Alterar winDate/lostDate para ISO local, testar, publicar e reprocessar 15591.
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- timestamp: 2026-08-18T15:10:00-03:00
  finding: hub_crm_quote 15591 esta APROVADA/ERRO, vinculada ao deal 2232585.
- timestamp: 2026-08-18T15:10:00-03:00
  finding: API rejeitou winDate com offset e respondeu HTTP 422.

## Eliminated

## Resolution

- root_cause: O Hub enviava LocalDateTime convertido para OffsetDateTime com sufixo -03:00; o validador do ArpaSuite rejeitou o campo.
- fix: Formatar winDate e lostDate como ISO_LOCAL_DATE_TIME, sem offset.
- verification:
- files_changed:
