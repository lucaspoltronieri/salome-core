# Migracao Salome Legacy para Financeiro Web

## What This Is

Este projeto organiza a migracao incremental do sistema legado da Expresso Salome para um modulo web financeiro moderno. O foco inicial e o dominio de Contas a Pagar, e o mapeamento das fases 1 a 4 ja confirmou que o fluxo passa por `ContasPagar`, `NotaCompra`, `NotaCompraDuplicatas`, `NotaCompraRateio`, `NotaCompraProdutos`, `Extrato`, `Banco`, `Fornecedor`, `Filial` e `PlanoContas*`.

O legado `salome-legacy` continua funcionando durante toda a migracao. O novo sistema planejado e `salome-core`, com Java 25, Spring Boot 4, Vaadin, MySQL, Maven e Flyway. A estrategia agora e usar o mapeamento como fonte de verdade para espelhar primeiro o comportamento existente em leitura, manter o Contas a Pagar funcionando na nova stack e so depois evoluir para novas features.

## Core Value

Modernizar o Contas a Pagar com seguranca, preservando as regras financeiras existentes, espelhando primeiro o legado na nova stack e mantendo o legado operando sem interrupcao.

## Requirements

### Validated

- [x] O inventario tecnico do Contas a Pagar legado esta documentado em `.planning/codebase/`.
- [x] As origens das principais regras financeiras foram rastreadas ate classe, metodo, botao, DAO, query e tabela quando aplicavel.
- [x] As consultas prioritarias para a primeira tela web somente leitura ja estao identificadas.
- [x] O mapeamento das fases 1 a 4 virou base oficial para as proximas fases de arquitetura e implementacao.
- [x] A arquitetura alvo do `salome-core` foi proposta em `docs/architecture/salome-core-architecture.md`, com separacao entre `ui`, `application`, `domain`, `infrastructure` e `security`.

### Active

- [ ] Criar a primeira versao web Vaadin somente leitura para Contas a Pagar, espelhando o comportamento e os dados do legado com Services e Adapters/Repositories por baixo.
- [ ] Reproduzir as consultas prioritarias de `ContasPagar`, `NotaCompra`, `NotaCompraDuplicatas` e `Extrato` na camada de leitura.
- [ ] Validar os dados exibidos na web contra o comportamento e os dados do legado.
- [ ] Migrar edicao, salvamento, exclusao controlada, rateio e baixa apenas depois que as regras criticas estiverem documentadas e testadas.
- [ ] Evoluir depois para dashboard financeiro, fluxo de caixa previsto, importacao XML, associacao produto fiscal x produto sistema, portal de pagamentos, integracao Banco do Brasil, comprovantes e auditoria.

### Out of Scope

- Alterar qualquer codigo em `salome-legacy` sem autorizacao explicita - o legado precisa continuar funcionando durante a migracao.
- Alterar banco de dados de producao - qualquer evolucao de schema futura exige script SQL versionado.
- Criar campos ou tabelas sem script SQL rastreavel - o novo modulo precisa manter governanca de schema.
- Reescrever a tela Swing como arquitetura nova - a meta e migrar comportamento, nao copiar a UI.
- Colocar regra de negocio ou SQL dentro da View Vaadin - a UI deve chamar Services.
- Liberar gravacao antes de existir uma versao somente leitura validada - a leitura vem primeiro por seguranca financeira.
- Migrar baixa, exclusao, rateio, fornecedor, produto, plano de contas, filial ou usuario logado sem documentacao previa - sao regras criticas.
- Desacoplar o usuario logado do legado antes de existir substituicao equivalente no novo modulo - a estrutura atual ainda e a referencia.

## Context

O sistema atual da Expresso Salome esta em Java 8, MVC e Swing, roda no desktop dos usuarios e conecta em um banco MySQL hospedado em VPS Hostinger. Ele esta em producao e deve continuar funcionando durante toda a migracao.

O mapeamento tecnico ja mostrou como o dominio financeiro esta distribuido: `ContasPagar.java` monta SQL na propria view, `ContasPagarData` faz CRUD JDBC direto, `ContasPagarController` e uma fachada fina, e `ContasPagarBean` carrega flags `...Gravar` para update parcial. Em `NotaCompra`, a tela mistura leitura, importacao XML, criacao de fornecedor, rateio, duplicatas e geracao de NF-e. Esses mapas sao a referencia para construir a nova stack sem inventar comportamento novo antes de espelhar o legado.

Os mapas tambem confirmaram regras sensiveis: duplicata baixada nao pode ser excluida, vencimento nao pode anteceder emissao, baixa cria `Extrato` e atualiza a duplicata, cheque baixa varias duplicatas na mesma transacao, rateio nao pode exceder o valor restante, e o usuario logado depende de `Conecta.getUsuario()` e de helpers de `UsuarioController`.

Termos prioritarios para investigacao no legado:

- `notacompra`
- `nota compra`
- `notacompraproduto`
- `notacomprarateio`
- `notacompraduplicata`
- `baixacheque`
- `rateio`
- `produto`
- `plano contas`
- `plano de contas`
- `filial`
- `fornecedor`
- `contas pagar`
- `conta pagar`
- `baixa`
- `banco`
- `extrato`
- `usuario`
- `login`
- `perfil usuario`
- `permissao`
- `centro de custo`
- `contasareceber`
- `faturamento`
- `cte`

## Constraints

- **Legacy safety**: Nao alterar `salome-legacy` sem autorizacao explicita - o legado permanece produtivo durante a migracao.
- **Production database**: Nao alterar banco de producao - qualquer evolucao futura de schema exige script SQL versionado.
- **Migration order**: Primeiro mapear, depois planejar, depois implementar - reduz risco em regras financeiras.
- **Read-only first**: Antes de liberar gravacao, criar versao somente leitura - validacao de dados vem antes de comandos mutaveis.
- **Architecture**: Views Vaadin chamam Services; Services chamam Repositories/Adapters; Repositories/Adapters acessam banco - sem regra pesada ou SQL na View.
- **Traceability**: Toda regra migrada deve apontar origem no legado - classe, metodo, botao, DAO, query e tabela quando existir.
- **Testing**: Regras criticas de financeiro exigem teste antes de migracao operacional.
- **Authentication**: Usuario logado deve aproveitar a estrutura existente do legado sempre que possivel, sem acoplar Swing ao novo modulo.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Inicializar apenas planejamento GSD agora | O pedido atual era preparar o projeto para mapeamento, sem criar app novo ou mexer no legado | Accepted |
| Usar `salome-legacy` como sistema legado e `salome-core` como novo sistema alvo | Mantem fronteira clara entre codigo existente e modulo web futuro | Accepted |
| Comecar pelo Contas a Pagar | E o primeiro dominio de negocio definido para migracao incremental | Accepted |
| Usar Java 25, Spring Boot 4, Vaadin, MySQL, Maven e Flyway no novo modulo | Stack definida pelo projeto e adequada para leitura incremental do banco legado | Accepted |
| Criar versao web inicialmente somente leitura | Reduz risco financeiro e permite comparar dados com o legado antes de gravacao | Accepted |
| Priorizar leitura de `ContasPagar`, `NotaCompra`, duplicatas e extrato antes de qualquer escrita | Segue a ordem real do legado e reduz o risco da primeira entrega web | Accepted |
| Reaproveitar a semantica de identidade legada por enquanto | `Conecta.getUsuario()` e `UsuarioController` ainda carregam filial, banco e permissoes do fluxo atual | Accepted |
| Usar arquitetura alvo documentada para `salome-core` antes de criar o projeto | Phase 5 consolidou camadas, fluxo de dependencia, adapters legados, Spring Security, Flyway e testes financeiros | Accepted |
| Preferir granularidade fina no GSD | Migracao financeira exige fases pequenas, revisaveis e documentadas | Accepted |
| Commitar documentos de planejamento | Mantem historico de decisoes e facilita auditoria da migracao | Accepted |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `$gsd-transition`):
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `$gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check - still the right priority?
3. Audit Out of Scope - reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-13 after phase 5 architecture proposal*
