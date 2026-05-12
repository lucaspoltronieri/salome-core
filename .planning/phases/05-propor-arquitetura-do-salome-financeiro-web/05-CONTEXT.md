# Phase 5: Propor arquitetura do salome-financeiro-web - Context

**Gathered:** 2026-05-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase define a arquitetura alvo do `salome-financeiro-web`: separacao de camadas, identidade e autorizacao, acesso a dados e limites entre UI e regras de negocio. A entrega desta fase nao implementa o CRUD completo no banco legado; ela trava as decisoes para que as proximas fases possam criar a primeira versao web com seguranca e sem SQL na View.

</domain>

<decisions>
## Implementation Decisions

### Identidade e seguranca
- **D-01:** Reaproveitar a identidade legada como fonte de verdade para o usuario corrente, mas centralizar autenticacao e autorizacao em Spring Security no novo modulo.
- **D-02:** Criar um adapter para mapear o legado (`Conecta.getUsuario()`, `UsuarioController`, tabelas `usuario`/`usuarioalerta`) para `Principal`, authorities e contexto de request/session no novo sistema.

### Estrutura de pacotes
- **D-03:** Usar arquitetura em camadas no topo (`ui`, `application`, `domain`, `infrastructure`, `security`) com subpacotes por funcionalidade dentro de cada layer para evitar mega-pacotes e manter crescimento controlado.

### Acesso a dados
- **D-04:** Implementar repositories/adapters em `infrastructure` com JDBC/JdbcTemplate sobre MySQL, porque o legado ja expõe SQL claro e o schema existente precisa ser lido com controle direto.
- **D-05:** Projetar esses repositories como CRUD-capable desde o inicio, mas manter a primeira entrega do fluxo em leitura somente; escrita, delete e baixa continuam para as fases posteriores e testadas.

### Ordem de entrega
- **D-06:** A arquitetura deve suportar CRUD completo no futuro, mas a fase atual preserva a ordem do roadmap: primeiro arquitetura, depois leitura web validada, depois mutacoes.

### the agent's Discretion
- O detalhamento exato dos subpacotes por bounded context fica para o planejamento da fase, desde que a divisao por camada seja mantida.
- O mapeamento fino de authorities no Spring Security pode ser refinado durante o planejamento, mas a centralizacao ja fica travada aqui.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project context
- `.planning/PROJECT.md` - product description, constraints, current phase framing, and architecture guardrails.
- `.planning/REQUIREMENTS.md` - phase coverage, ARCH/READ/WRITE requirements, and current traceability.
- `.planning/ROADMAP.md` - phase order, phase 5 goal, and downstream delivery boundaries.

### Legacy mapping
- `.planning/codebase/STACK.md` - observed legacy stack and persistence style.
- `.planning/codebase/ARCHITECTURE.md` - Swing/MVC layering and extraction boundaries.
- `.planning/codebase/INTEGRATIONS.md` - DB, reporting, logging, and component integration points.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - legacy identity, user context, and permission patterns.
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - Contas a Pagar class, button, DAO, and rule origins.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - MySQL tables, queries, and data relationships that the new architecture must respect.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ContasPagarController`: thin controller facade that can inform the shape of application services.
- `ContasPagarData`: concrete example of the current JDBC persistence surface that repositories must replace.
- `ContasPagarBean`: shows update-by-flags behavior that must not be copied into the new domain model.
- `NotaCompraRateioController`: useful reference for read models and legacy totalization rules.
- `UsuarioAlertaController`: evidence that user context exists in the legacy database and can be adapted for Spring Security.

### Established Patterns
- `view` owns SQL and button events in the legacy, which confirms the need to move queries out of Vaadin views.
- `controller` is a thin pass-through layer, so application services should own use cases instead of just forwarding calls.
- `model.data` performs JDBC CRUD directly, which is the clearest boundary for building repositories/adapters.
- Beans use `...Gravar` flags for partial updates, which is a persistence pattern to replace with explicit command objects.

### Integration Points
- `Conecta.getCon()` and `BancoDados` are the legacy database entry points that the new adapters must ultimately replace.
- `Conecta.getUsuario()` and `UsuarioController` are the current sources of user identity, filial, and bank context.
- `Conecta.getDataSource()` is used by lookup components and confirms the legacy relies on shared database access.
- MySQL tables `usuario`, `usuarioalerta`, `ContasPagar`, `NotaCompra`, `NotaCompraDuplicatas`, `NotaCompraRateio`, `NotaCompraProdutos`, `Extrato`, `Banco`, `Filial`, `Fornecedor`, and `PlanoContas*` are the primary integration surface for the new module.

</code_context>

<specifics>
## Specific Ideas

- The user wants to reuse the legacy identity model, but with authentication and authorization centralized in Spring Security.
- The user prefers an architecture that does not block CRUD in MySQL later, even though the roadmap still keeps the first delivery read-only.
- The recommended package layout is layered at the top and feature-oriented inside each layer, so finance modules can grow without becoming a monolith.

</specifics>

<deferred>
## Deferred Ideas

- Full CRUD across the web module is deferred to later implementation phases; Phase 5 only defines the architecture that will support it.
- The first Vaadin slice stays read-only in the roadmap even though the repositories are designed to support writes later.

</deferred>

---

*Phase: 5-Propor arquitetura do salome-financeiro-web*
*Context gathered: 2026-05-12*
