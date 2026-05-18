# Migracao Salome Legacy para Financeiro Web

## What This Is

Este projeto organiza a migracao completa do sistema legado da Expresso Salome para um modulo web financeiro moderno. O foco inicial e migrar o dominio de Contas a Pagar inteiro, com equivalencia funcional ao fluxo legado realmente ativo: `NotaCompra`, `NotaCompraDuplicatas`, `NotaCompraRateio`, `NotaCompraProdutos`, baixa, extrato, banco, fornecedor, produto, filial, plano de contas, usuario logado e permissoes.

Nota de correcao da fase 11: a familia `ContasPagar.java` / `ContasPagarController` / `ContasPagarData` / `ContasPagarBean` / `ContasPagarTable` foi reclassificada como codigo morto ate prova contraria. A tabela `contaspagar` nao existe no banco de producao analisado e o DAO legado usa exclusivamente essa tabela. O "Contas a Pagar" real do sistema deve ser tratado como o fluxo baseado em `NotaCompraDuplicatas` e `NotaCompra`.

O legado `salome-legacy` continua funcionando durante toda a migracao. O novo sistema planejado e `salome-core`, com Java 25, Spring Boot 4, Vaadin, MySQL, Maven e Flyway. A estrategia e usar o mapeamento como fonte de verdade para entregar o Contas a Pagar web com todas as funcionalidades operacionais equivalentes ao legado. A etapa somente leitura das fases 7 e 8 fica registrada como baseline tecnico historico; daqui para frente o escopo aprovado e operacional completo, nao leitura parcial.

## Core Value

Modernizar o Contas a Pagar com seguranca, preservando as regras financeiras existentes e entregando no `salome-core` um modulo web funcionalmente equivalente ao legado, mantendo o legado operando sem interrupcao durante a migracao.

## Requirements

### Validated

- [x] O inventario tecnico do Contas a Pagar legado esta documentado em `.planning/codebase/`.
- [x] As origens das principais regras financeiras foram rastreadas ate classe, metodo, botao, DAO, query e tabela quando aplicavel.
- [x] As consultas prioritarias para a primeira tela web somente leitura ja estao identificadas.
- [x] O mapeamento das fases 1 a 4 virou base oficial para as proximas fases de arquitetura e implementacao.
- [x] A arquitetura alvo do `salome-core` foi proposta em `docs/architecture/salome-core-architecture.md`, com separacao entre `ui`, `application`, `domain`, `infrastructure` e `security`.

### Active

- [x] Criar a primeira versao web Vaadin somente leitura para Contas a Pagar, espelhando o comportamento e os dados do legado com Services e Adapters/Repositories por baixo.
- [x] Reproduzir as consultas prioritarias de `NotaCompra`, `NotaCompraDuplicatas`, produtos, rateio e `Extrato` na camada de leitura, sem depender da tabela morta `contaspagar`.
- [ ] Validar os dados exibidos na web contra o comportamento e os dados do legado como apoio a homologacao da migracao completa.
- [ ] Migrar `NotaCompra` completa, sem recorte parcial: cabecalho, produtos, duplicatas, rateio, fornecedor, filial, plano de contas, edicao, inclusao, salvamento, exclusao e auditoria conforme o legado.
- [ ] Migrar baixa de Contas a Pagar completa: pagamento, extrato, banco, cheque, datas, bloqueios, permissoes e transacoes equivalentes ao legado.
- [ ] Homologar paridade operacional do Contas a Pagar: tudo que o usuario faz no legado para o dominio precisa existir no `salome-core`, com regras rastreadas e testes nas regras financeiras criticas.
- [ ] Evoluir depois para dashboard financeiro, fluxo de caixa previsto, importacao XML, associacao produto fiscal x produto sistema, portal de pagamentos, integracao Banco do Brasil, comprovantes e auditoria.

### Out of Scope

- Alterar qualquer codigo em `salome-legacy` sem autorizacao explicita - o legado precisa continuar funcionando durante a migracao.
- Alterar banco de dados de producao - qualquer evolucao de schema futura exige script SQL versionado.
- Criar campos ou tabelas sem script SQL rastreavel - o novo modulo precisa manter governanca de schema.
- Reescrever a tela Swing como arquitetura nova - a meta e migrar comportamento, nao copiar a UI.
- Colocar regra de negocio ou SQL dentro da View Vaadin - a UI deve chamar Services.
- Tratar a versao somente leitura como entrega final do Contas a Pagar - ela foi apenas baseline tecnico historico; a meta aprovada e migracao completa.
- Migrar baixa, exclusao, rateio, fornecedor, produto, plano de contas, filial ou usuario logado sem documentacao previa - sao regras criticas.
- Desacoplar o usuario logado do legado antes de existir substituicao equivalente no novo modulo - a estrutura atual ainda e a referencia.

## Context

O sistema atual da Expresso Salome esta em Java 8, MVC e Swing, roda no desktop dos usuarios e conecta em um banco MySQL hospedado em VPS Hostinger. Ele esta em producao e deve continuar funcionando durante toda a migracao.

O mapeamento tecnico inicial tratava `ContasPagar.java` como candidato de migracao, mas a fase 11 corrigiu esse entendimento: essa familia aponta para uma tabela inexistente no banco analisado e deve ser preservada apenas como referencia historica/codigo morto. O dominio financeiro ativo esta distribuido principalmente em `NotaCompra`, `NotaCompraDuplicatas`, `NotaCompraProdutos`, `NotaCompraRateio`, `Extrato`, banco/caixa e permissoes. Esses mapas sao a referencia para construir a nova stack sem inventar comportamento novo antes de espelhar o legado.

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
- **Complete Contas a Pagar**: O escopo aprovado e migrar o modulo inteiro com equivalencia funcional ao legado, nao entregar apenas leitura ou recortes parciais.
- **Operational completeness**: Da fase 9 em diante, fases de implementacao devem entregar fluxo operacional completo do recorte aprovado, com escrita, validacoes, permissoes, auditoria e testes quando o legado possuir essas funcoes.
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
| Tratar a tela somente leitura como baseline historico, nao como destino | A leitura das fases 7 e 8 ajuda a validar dados, mas nao atende o objetivo de migrar o modulo completo | Superseded |
| Nao planejar recortes parciais para funcoes operacionais do Contas a Pagar | O usuario reafirmou que a migracao deve cobrir as funcoes completas do legado dentro do dominio | Accepted |
| Migrar Contas a Pagar completo, 100% equivalente ao legado | O objetivo do projeto nao e um modulo parcial: `NotaCompra`, produtos, duplicatas, rateio, baixa, exclusao, edicao, salvamento, permissoes e auditoria precisam funcionar no `salome-core` como funcionam no legado | Accepted |
| Fazer a fase 9 como `NotaCompra` completa | O menor recorte aprovado para escrita e a nota completa: cabecalho, produtos, duplicatas e rateio no mesmo fluxo, com testes e rastreabilidade | Accepted |
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
*Last updated: 2026-05-14 after scope correction to complete Contas a Pagar migration*
