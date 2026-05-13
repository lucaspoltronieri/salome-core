# Phase 5: Propor arquitetura do salome-core - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-12
**Phase:** 5-Propor arquitetura do salome-core
**Areas discussed:** Identidade e seguranca, Estrutura de pacotes, Acesso a dados, Leitura x escrita

---

## Identidade e seguranca

| Option | Description | Selected |
|--------|-------------|----------|
| Reaproveitar legado + centralizar no Spring Security | Reuse `Conecta.getUsuario()` / `UsuarioController` as the source of truth, but move auth and authorization into Spring Security via an adapter. | ✓ |
| Manter autenticacao espalhada no legado | Keep the current global-user approach and add only small bridges. | |
| Criar autenticacao nova sem reaproveitar o legado | Ignore the legacy identity model and start from scratch. | |

**User's choice:** Reaproveitar o legado e centralizar tudo no Spring Security, com adapter para identidade, filial, banco e permissoes.
**Notes:** O usuario quer preservar a estrutura existente do legado quando possivel, mas sem acoplar Swing ao modulo novo.

---

## Estrutura de pacotes

| Option | Description | Selected |
|--------|-------------|----------|
| Camadas no topo + subpacotes por funcionalidade | `ui`, `application`, `domain`, `infrastructure`, `security` no topo, com separacao por dominio dentro de cada camada. | ✓ |
| Apenas por camada, sem subdominios | Estrutura simples mas tende a concentrar tudo em pacotes muito grandes. | |
| Apenas por funcionalidade | Dominios primeiro, camadas misturadas em cada modulo. | |

**User's choice:** O agente recomendou a estrutura em camadas no topo com subpacotes por funcionalidade.
**Notes:** A forma exata dos subpacotes fica para o planejamento, mas a divisao por camada e o modelo de crescimento foram travados.

---

## Acesso a dados

| Option | Description | Selected |
|--------|-------------|----------|
| JDBC/JdbcTemplate em repositories/adapters | Repositories em `infrastructure` falam com MySQL diretamente e podem cobrir leitura e escrita futura. | ✓ |
| Adapter sobre os DAOs legados | Encapsular as classes `Data` do legado sem redesenhar a persistencia. | |
| JPA desde o inicio | Trocar a persistencia por ORM logo na arquitetura inicial. | |

**User's choice:** O agente recomendou JDBC/JdbcTemplate em repositories/adapters, com capacidade de CRUD.
**Notes:** O usuario aceitou que a arquitetura suporte CRUD em MySQL, mas isso nao muda a ordem de entrega da roadmap.

---

## Leitura x escrita

| Option | Description | Selected |
|--------|-------------|----------|
| Separar read model e command model desde ja | Estruturar a arquitetura para separar consulta e mutacao, mesmo que a primeira entrega exponha so leitura. | ✓ |
| Camada compartilhada no comeco | Reusar as mesmas estruturas para consulta e escrita ate a fase posterior. | |
| Deixar a escrita para depois sem preparar a separacao | Adiar qualquer desenho de command side. | |

**User's choice:** O agente recomendou separar read model e command model desde ja.
**Notes:** O usuario quer CRUD completo no futuro, mas a fase atual continua respeitando a estrategia read-first.

---

## the agent's Discretion

- Definir a organizacao exata dos subpacotes por bounded context.
- Definir o mapeamento fino das authorities no Spring Security.

## Deferred Ideas

- CRUD completo no modulo web agora - fica para fases posteriores de implementacao.
- Primeira entrega Vaadin com escrita habilitada - contradiz a sequencia read-first do roadmap.
