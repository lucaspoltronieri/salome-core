# Migracao Salome Legacy para Financeiro Web

## What This Is

Este projeto organiza a migracao incremental do sistema legado da Expresso Salome para um modulo web financeiro moderno. O foco inicial e o dominio de Contas a Pagar: primeiro mapear o comportamento existente no legado Java 8 MVC Swing, depois criar uma aplicacao Vaadin que leia os mesmos dados e respeite as mesmas regras antes de permitir qualquer gravacao.

O legado `salome-legacy` continua funcionando em producao durante toda a migracao. O novo sistema planejado e `salome-financeiro-web`, com Java 25, Spring Boot 4, Vaadin, MySQL, Maven, Flyway e acesso inicial somente leitura via `JdbcTemplate` ao banco legado.

## Core Value

Modernizar o Contas a Pagar com seguranca, preservando as regras financeiras existentes e mantendo o legado operando sem interrupcao.

## Requirements

### Validated

(None yet - ship to validate)

### Active

- [ ] Mapear classes, telas, botoes, listeners, DAOs, queries, tabelas e regras de negocio do Contas a Pagar legado.
- [ ] Documentar origem de cada regra encontrada com classe, metodo, botao, DAO, query e tabela quando aplicavel.
- [ ] Mapear usuarios, login, perfil de usuario e permissoes usados pelo fluxo financeiro.
- [ ] Mapear entidades e conceitos relacionados a `NotaCompra*`, rateio, produto, plano de contas, filial, fornecedor, banco, extrato, baixa, `ContaPagar`, `ContasPagar` e contas a receber relevantes para faturamento/CT-e.
- [ ] Propor arquitetura do `salome-financeiro-web` antes de criar o projeto.
- [ ] Criar uma primeira versao web Vaadin somente leitura antes de qualquer fluxo de gravacao.
- [ ] Validar dados da tela web contra o comportamento e dados do legado.
- [ ] Migrar gradualmente edicao, salvamento, exclusao controlada e baixa somente apos documentacao e testes das regras criticas.
- [ ] Evoluir depois para dashboard financeiro, fluxo de caixa previsto, importacao XML, associacao produto fiscal x produto sistema, portal de pagamentos, integracao Banco do Brasil, comprovantes e auditoria.

### Out of Scope

- Alterar qualquer codigo em `salome-legacy` nesta fase - o objetivo atual e planejamento e preparacao para mapeamento.
- Criar o projeto `salome-financeiro-web` nesta fase - isso so acontece depois do mapeamento e da proposta de arquitetura.
- Refatorar codigo existente nesta fase - antes disso e preciso mapear e documentar comportamento.
- Alterar banco de dados ou producao - nenhuma tabela, campo ou dado deve ser alterado agora.
- Implementar telas Vaadin nesta fase - primeiro mapear, depois planejar, depois implementar.
- Liberar gravacao antes de existir versao somente leitura validada - regra obrigatoria do projeto.

## Context

O sistema atual da Expresso Salome esta em Java 8, MVC e Swing, roda no desktop dos usuarios e conecta em um banco MySQL hospedado em VPS Hostinger. Ele esta em producao e deve continuar funcionando durante toda a migracao.

O primeiro dominio e Contas a Pagar. O legado possui regras de negocio possivelmente distribuidadas entre telas Swing, botoes, listeners, classes internas, DAOs e queries. A migracao precisa identificar essas regras antes de traduzi-las para services, repositories/adapters e views Vaadin.

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
| Inicializar apenas planejamento GSD agora | O pedido atual e preparar o projeto para mapeamento, sem criar app novo ou mexer no legado | Pending |
| Usar `salome-legacy` como sistema legado e `salome-financeiro-web` como novo sistema alvo | Mantem fronteira clara entre codigo existente e modulo web futuro | Pending |
| Comecar pelo Contas a Pagar | E o primeiro dominio de negocio definido para migracao incremental | Pending |
| Usar Java 25, Spring Boot 4, Vaadin, MySQL, Maven, Flyway e `JdbcTemplate` inicialmente | Stack definida pelo projeto e adequada para leitura incremental do banco legado | Pending |
| Criar versao web inicialmente somente leitura | Reduz risco financeiro e permite comparar dados com o legado antes de gravacao | Pending |
| Preferir granularidade fina no GSD | Migracao financeira exige fases pequenas, revisaveis e documentadas | Pending |
| Commitar documentos de planejamento | Mantem historico de decisoes e facilita auditoria da migracao | Pending |

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
*Last updated: 2026-05-12 after initialization*
