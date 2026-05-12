# Requirements: Migracao Salome Legacy para Financeiro Web

**Defined:** 2026-05-12
**Core Value:** Modernizar o Contas a Pagar com seguranca, preservando as regras financeiras existentes e mantendo o legado operando sem interrupcao.

## v1 Requirements

### Mapeamento Legado

- [ ] **MAP-01**: Equipe pode localizar as classes, telas, botoes, listeners e classes internas do Contas a Pagar legado.
- [ ] **MAP-02**: Equipe pode localizar os DAOs, queries e tabelas usados pelo Contas a Pagar legado.
- [ ] **MAP-03**: Equipe pode consultar um inventario de regras de negocio com origem documentada por classe, metodo, botao, DAO, query e tabela quando aplicavel.
- [ ] **MAP-04**: Equipe pode identificar dependencias de `NotaCompra*`, rateio, produto, plano de contas, filial, fornecedor, banco, extrato e baixa.

### Seguranca e Permissoes

- [ ] **SEC-01**: Equipe pode entender como usuario, login, perfil de usuario e permissao funcionam no legado.
- [ ] **SEC-02**: Equipe pode mapear quais permissoes protegem visualizacao, edicao, exclusao, salvamento e baixa no fluxo financeiro.
- [ ] **SEC-03**: Equipe pode propor reaproveitamento da estrutura de usuario logado sem acoplar Swing ao novo modulo.

### Dados Financeiros

- [ ] **DATA-01**: Equipe pode mapear tabelas e relacionamentos de `NotaCompra`, duplicatas, rateio, fornecedores, filial e plano de contas.
- [ ] **DATA-02**: Equipe pode mapear regras e dados de baixa, extrato bancario, banco e cheque.
- [ ] **DATA-03**: Equipe pode mapear dependencias com contas a receber, faturamento e CT-e emitidos com status diferente de cancelado ou inutilizado quando impactarem o financeiro.

### Arquitetura

- [ ] **ARCH-01**: Equipe pode revisar uma proposta de arquitetura para `salome-financeiro-web` antes da criacao do projeto.
- [ ] **ARCH-02**: Arquitetura proposta separa `ui`, `application`, `domain`, `infrastructure` e `security`.
- [ ] **ARCH-03**: Arquitetura proposta impede regra pesada e SQL dentro de Views Vaadin.
- [ ] **ARCH-04**: Arquitetura proposta usa Services para casos de uso e Repositories/Adapters para acesso ao banco legado.

### Leitura Web Validada

- [ ] **READ-01**: Usuario pode abrir uma tela Vaadin de Contas a Pagar somente leitura.
- [ ] **READ-02**: Tela web somente leitura exibe os mesmos dados relevantes do legado para Contas a Pagar.
- [ ] **READ-03**: Equipe pode validar dados da tela web contra consultas e comportamento do legado.

### Escrita Financeira Controlada

- [ ] **WRITE-01**: Usuario autorizado pode editar e salvar dados de NotaCompra apenas apos regras documentadas e testadas.
- [ ] **WRITE-02**: Usuario autorizado pode executar exclusao controlada apenas apos regras documentadas e testadas.
- [ ] **WRITE-03**: Usuario autorizado pode executar baixa de Contas a Pagar apenas apos regras documentadas e testadas.

## v2 Requirements

### Analitico Financeiro

- **DASH-01**: Usuario pode consultar dashboard financeiro somente leitura.
- **CASH-01**: Usuario pode consultar fluxo de caixa previsto somente leitura.

### XML e Produtos

- **XML-01**: Usuario pode importar XML fiscal por portal web.
- **PROD-01**: Usuario pode associar produto fiscal a produto do sistema.

### Pagamentos e Bancos

- **PAY-01**: Usuario pode usar portal de pagamentos.
- **BANK-01**: Sistema pode integrar com Banco do Brasil.
- **AUD-01**: Sistema pode salvar comprovantes e manter auditoria de operacoes financeiras.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Alterar `salome-legacy` na fase de inicializacao | Esta fase e somente planejamento e preparacao para mapeamento |
| Criar `salome-financeiro-web` na fase de inicializacao | Projeto novo depende do mapeamento e da proposta de arquitetura |
| Alterar banco ou schema agora | Banco de producao nao deve ser modificado; mudancas futuras exigem script versionado |
| Implementar tela Vaadin agora | Primeiro mapear e planejar comportamento legado |
| Liberar gravacao antes da versao somente leitura | Regra obrigatoria de seguranca financeira |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| MAP-01 | Phase 1 | Pending |
| MAP-02 | Phase 1 | Pending |
| MAP-03 | Phase 1 | Pending |
| MAP-04 | Phase 3 | Pending |
| SEC-01 | Phase 2 | Pending |
| SEC-02 | Phase 2 | Pending |
| SEC-03 | Phase 2 | Pending |
| DATA-01 | Phase 3 | Pending |
| DATA-02 | Phase 4 | Pending |
| DATA-03 | Phase 3 | Pending |
| ARCH-01 | Phase 5 | Pending |
| ARCH-02 | Phase 5 | Pending |
| ARCH-03 | Phase 5 | Pending |
| ARCH-04 | Phase 5 | Pending |
| READ-01 | Phase 7 | Pending |
| READ-02 | Phase 7 | Pending |
| READ-03 | Phase 8 | Pending |
| WRITE-01 | Phase 9 | Pending |
| WRITE-02 | Phase 10 | Pending |
| WRITE-03 | Phase 11 | Pending |

**Coverage:**
- v1 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0

---
*Requirements defined: 2026-05-12*
*Last updated: 2026-05-12 after initial definition*
