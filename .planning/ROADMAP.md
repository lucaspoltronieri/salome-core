# Roadmap: Migracao Salome Legacy para Financeiro Web

**Created:** 2026-05-12
**Granularity:** Fine
**Mode:** YOLO

## Overview

Este roadmap segue a ordem obrigatoria do projeto: primeiro mapear, depois planejar, depois implementar. As fases de mapeamento ja estao completas e documentadas em `.planning/codebase/`, e o restante do plano parte desse conhecimento para chegar na primeira tela Vaadin somente leitura, espelhando o legado antes de criar novas features.

## Phases

| # | Phase | Status | Goal | Requirements | UI Hint |
|---|-------|--------|------|--------------|---------|
| 1 | Mapear Contas a Pagar legado | Complete | Identificar tela, classes, botoes, listeners, DAOs, queries e tabelas principais do Contas a Pagar | MAP-01, MAP-02, MAP-03 | no |
| 2 | Mapear usuarios, login e permissoes do legado | Complete | Entender usuario logado, perfis e permissoes que protegem o fluxo financeiro | SEC-01, SEC-02, SEC-03 | no |
| 3 | Mapear tabelas, queries e regras relacionadas a NotaCompra | Complete | Documentar `NotaCompra*`, duplicatas, rateio, produtos, fornecedor, filial, plano de contas e dependencias relevantes | MAP-04, DATA-01, DATA-03 | no |
| 4 | Mapear baixa, extrato bancario e banco | Complete | Documentar regras de baixa, banco, extrato, cheque e impactos financeiros | DATA-02 | no |
| 5 | Propor arquitetura do salome-core | Complete | Definir arquitetura alvo com separacao de UI, application, domain, infrastructure e security | ARCH-01, ARCH-02, ARCH-03, ARCH-04 | no |
| 6 | Criar projeto Java 25 + Spring Boot 4 + Vaadin | Next | Criar base tecnica do novo modulo sem regra financeira migrada ainda, preparada para espelhar o legado | ARCH-01, ARCH-02, ARCH-04 | yes |
| 7 | Criar tela Contas a Pagar somente leitura | Planned | Espelhar os dados do Contas a Pagar, `NotaCompra`, duplicatas e extrato em Vaadin via Services e Adapters/Repositories | READ-01, READ-02 | yes |
| 8 | Validar dados da tela web contra o legado | Planned | Comparar o espelhamento web com o legado e documentar divergencias nas consultas prioritarias | READ-03 | yes |
| 9 | Migrar edicao e salvamento de NotaCompra | Planned | Liberar escrita controlada de NotaCompra apos regras documentadas e testadas | WRITE-01 | yes |
| 10 | Migrar exclusao controlada | Planned | Implementar exclusao conforme permissoes e regras criticas documentadas | WRITE-02 | yes |
| 11 | Migrar baixa de Contas a Pagar | Planned | Implementar baixa com testes e rastreabilidade de regras financeiras | WRITE-03 | yes |
| 12 | Criar dashboard financeiro somente leitura | Planned | Adicionar visao analitica inicial sem gravacao | DASH-01 | yes |
| 13 | Criar fluxo de caixa previsto somente leitura | Planned | Apresentar previsao financeira baseada nos dados mapeados | CASH-01 | yes |
| 14 | Criar portal de importacao XML | Planned | Receber XML fiscal em fluxo web controlado | XML-01 | yes |
| 15 | Criar associacao produto fiscal x produto sistema | Planned | Permitir conciliacao entre produto fiscal e cadastro interno | PROD-01 | yes |
| 16 | Criar portal de pagamentos | Planned | Preparar operacao web de pagamentos | PAY-01 | yes |
| 17 | Integrar Banco do Brasil | Planned | Integrar fluxo bancario conforme requisitos tecnicos e seguranca | BANK-01 | yes |
| 18 | Salvar comprovantes e auditoria | Planned | Persistir comprovantes e trilha auditavel de operacoes financeiras | AUD-01 | yes |

## Phase Details

### Phase 1: Mapear Contas a Pagar legado

**Goal:** Identificar a superficie real do Contas a Pagar no legado antes de qualquer implementacao.

**Requirements:** MAP-01, MAP-02, MAP-03

**Discovery artifacts:** `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`, `.planning/codebase/CONTAS-PAGAR-LEGADO.md`

**Success criteria:**
1. Inventario aponta arquivos/classes do legado ligados a Contas a Pagar.
2. Inventario aponta botoes, listeners e metodos que executam regras financeiras.
3. DAOs, queries e tabelas usadas pela tela principal estao listados.
4. Nenhum arquivo de `salome-legacy` foi alterado.

**Recommended command:** `$gsd-map-codebase`

### Phase 2: Mapear usuarios, login e permissoes do legado

**Goal:** Entender como o legado identifica usuario, perfil e permissao para preservar controle de acesso no modulo web.

**Requirements:** SEC-01, SEC-02, SEC-03

**Discovery artifacts:** `.planning/codebase/USUARIO-ACESSO-MAPA.md`

**Success criteria:**
1. Fluxo de login e usuario logado esta documentado.
2. Permissoes relacionadas a Contas a Pagar estao mapeadas.
3. Existe recomendacao de reaproveitamento sem acoplar Swing ao novo modulo.

### Phase 3: Mapear tabelas, queries e regras relacionadas a NotaCompra

**Goal:** Documentar dados e regras de `NotaCompra*` que sustentam Contas a Pagar.

**Requirements:** MAP-04, DATA-01, DATA-03

**Discovery artifacts:** `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`, `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`

**Success criteria:**
1. Tabelas e relacionamentos de NotaCompra, duplicata, rateio, fornecedor, filial e plano de contas estao documentados.
2. Regras de produto e rateio apontam origem no legado.
3. Dependencias com contas a receber, faturamento e CT-e estao registradas quando impactarem o financeiro.

### Phase 4: Mapear baixa, extrato bancario e banco

**Goal:** Documentar regras criticas antes de qualquer migracao de baixa.

**Requirements:** DATA-02

**Discovery artifacts:** `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`

**Success criteria:**
1. Fluxos de baixa, banco, extrato e cheque estao rastreados ate origem no legado.
2. Regras criticas e casos de erro estao documentados.
3. Riscos e testes obrigatorios para baixa estao listados.

### Phase 5: Propor arquitetura do salome-core

**Goal:** Definir a arquitetura alvo antes de criar o novo projeto.

**Requirements:** ARCH-01, ARCH-02, ARCH-03, ARCH-04

**Success criteria:**
1. Proposta separa `ui`, `application`, `domain`, `infrastructure` e `security`.
2. Vaadin Views chamam Services, sem SQL e sem regra pesada.
3. Services chamam Repositories/Adapters.
4. Acesso ao banco legado e feito por adapters/repositories, inicialmente somente leitura.

### Phase 6: Criar projeto Java 25 + Spring Boot 4 + Vaadin

**Goal:** Criar a base tecnica do novo modulo web.

**Requirements:** ARCH-01, ARCH-02, ARCH-04

**Success criteria:**
1. Projeto Maven compila com Java 25, Spring Boot 4 e Vaadin.
2. Estrutura de pacotes segue a arquitetura proposta.
3. Flyway esta configurado sem alterar producao.

### Phase 7: Criar tela Contas a Pagar somente leitura

**Goal:** Espelhar Contas a Pagar em web sem gravacao.

**Requirements:** READ-01, READ-02

**Success criteria:**
1. Usuario acessa tela Vaadin de Contas a Pagar.
2. Dados sao carregados por Service e Repository/Adapter com base nas consultas mapeadas do legado.
3. Tela nao contem SQL nem regra financeira pesada.

### Phase 8: Validar dados da tela web contra o legado

**Goal:** Confirmar que o espelhamento web reproduz dados esperados antes de evoluir para escrita.

**Requirements:** READ-03

**Success criteria:**
1. Amostras de dados batem entre web e legado.
2. Divergencias sao documentadas com origem provavel.
3. Ajustes necessarios sao planejados antes da escrita.

### Phase 9: Migrar edicao e salvamento de NotaCompra

**Goal:** Liberar escrita controlada com regras documentadas e testadas.

**Requirements:** WRITE-01

**Success criteria:**
1. Regras de edicao e salvamento apontam origem no legado.
2. Services implementam casos de uso testados.
3. Operacao respeita permissoes mapeadas.

### Phase 10: Migrar exclusao controlada

**Goal:** Implementar exclusao com governanca financeira.

**Requirements:** WRITE-02

**Success criteria:**
1. Regras de exclusao estao documentadas e testadas.
2. Exclusao exige usuario autorizado.
3. Operacao mantem rastreabilidade suficiente.

### Phase 11: Migrar baixa de Contas a Pagar

**Goal:** Migrar baixa somente apos entendimento completo das regras criticas.

**Requirements:** WRITE-03

**Success criteria:**
1. Baixa segue regras mapeadas de banco, extrato e cheque.
2. Testes cobrem casos financeiros criticos.
3. Resultado pode ser comparado com comportamento esperado do legado.

### Phase 12: Criar dashboard financeiro somente leitura

**Goal:** Dar visibilidade financeira sem risco de mutacao.

**Requirements:** DASH-01

**Success criteria:**
1. Dashboard apresenta indicadores definidos com origem de dados documentada.
2. Nenhuma operacao de escrita e exposta.

### Phase 13: Criar fluxo de caixa previsto somente leitura

**Goal:** Exibir previsao de fluxo de caixa usando dados financeiros mapeados.

**Requirements:** CASH-01

**Success criteria:**
1. Previsao usa regras documentadas.
2. Usuario consegue filtrar e revisar valores previstos.

### Phase 14: Criar portal de importacao XML

**Goal:** Iniciar entrada web de documentos fiscais.

**Requirements:** XML-01

**Success criteria:**
1. Usuario consegue importar XML em fluxo controlado.
2. Validacoes fiscais e erros ficam documentados.

### Phase 15: Criar associacao produto fiscal x produto sistema

**Goal:** Resolver conciliacao de produtos entre XML fiscal e cadastro interno.

**Requirements:** PROD-01

**Success criteria:**
1. Usuario consegue associar produto fiscal a produto do sistema.
2. Regras de produto existentes sao respeitadas.

### Phase 16: Criar portal de pagamentos

**Goal:** Preparar operacao web de pagamentos.

**Requirements:** PAY-01

**Success criteria:**
1. Portal segue permissoes e regras financeiras.
2. Acoes sensiveis ficam auditaveis.

### Phase 17: Integrar Banco do Brasil

**Goal:** Integrar com servicos bancarios de forma segura.

**Requirements:** BANK-01

**Success criteria:**
1. Credenciais e ambientes sao tratados com seguranca.
2. Integracao respeita fluxo financeiro aprovado.

### Phase 18: Salvar comprovantes e auditoria

**Goal:** Registrar evidencias e auditoria das operacoes financeiras.

**Requirements:** AUD-01

**Success criteria:**
1. Comprovantes ficam associados as operacoes corretas.
2. Auditoria permite rastrear usuario, acao, data e dados relevantes.

## Coverage

- v1 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0

---
*Roadmap created: 2026-05-12*
*Last updated: 2026-05-13 after codemap-driven migration update*
