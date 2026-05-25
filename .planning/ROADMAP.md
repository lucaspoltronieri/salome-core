# Roadmap: Migracao Salome Legacy para Financeiro Web

**Created:** 2026-05-12
**Granularity:** Fine
**Mode:** YOLO

## Overview

Este roadmap segue a ordem obrigatoria do projeto: primeiro mapear, depois planejar, depois implementar. As fases de mapeamento ja estao completas e documentadas em `.planning/codebase/`, e o plano agora assume explicitamente que o objetivo v1 e migrar o Contas a Pagar completo, com equivalencia funcional ao legado. A tela somente leitura das fases 7 e 8 e baseline tecnico historico, nao entrega parcial aceita como fim do modulo nem barreira para planejar operacao completa.

## Phases

| # | Phase | Status | Goal | Requirements | UI Hint |
|---|-------|--------|------|--------------|---------|
| 1 | Mapear Contas a Pagar legado | Complete | Identificar tela, classes, botoes, listeners, DAOs, queries e tabelas principais do Contas a Pagar | MAP-01, MAP-02, MAP-03 | no |
| 2 | Mapear usuarios, login e permissoes do legado | Complete | Entender usuario logado, perfis e permissoes que protegem o fluxo financeiro | SEC-01, SEC-02, SEC-03 | no |
| 3 | Mapear tabelas, queries e regras relacionadas a NotaCompra | Complete | Documentar `NotaCompra*`, duplicatas, rateio, produtos, fornecedor, filial, plano de contas e dependencias relevantes | MAP-04, DATA-01, DATA-03 | no |
| 4 | Mapear baixa, extrato bancario e banco | Complete | Documentar regras de baixa, banco, extrato, cheque e impactos financeiros | DATA-02 | no |
| 5 | Propor arquitetura do salome-core | Complete | Definir arquitetura alvo com separacao de UI, application, domain, infrastructure e security | ARCH-01, ARCH-02, ARCH-03, ARCH-04 | no |
| 6 | Criar projeto Java 25 + Spring Boot 4 + Vaadin | Complete | Criar base tecnica do novo modulo sem regra financeira migrada ainda, preparada para espelhar o legado | ARCH-01, ARCH-02, ARCH-04 | yes |
| 7 | Criar tela Contas a Pagar somente leitura | Complete | Espelhar os dados do Contas a Pagar, `NotaCompra`, duplicatas e extrato em Vaadin via Services e Adapters/Repositories | READ-01, READ-02 | yes |
| 8 | Validar dados da tela web contra o legado | Next | Comparar o espelhamento web com o legado e documentar divergencias nas consultas prioritarias como apoio a homologacao completa | READ-03 | yes |
| 9 | Migrar NotaCompra completa | Planned | Implementar `NotaCompra` completa no web: cabecalho, fornecedor, filial, produtos, duplicatas, rateio, plano de contas, inclusao, edicao, salvamento, exclusao, auditoria e permissoes | FULL-01, FULL-02, FULL-03, FULL-04, FULL-06, FULL-07 | yes |
| 10 | Migrar baixa completa de Contas a Pagar | Complete | Implementar baixa/pagamento com banco, extrato, cheque, datas, bloqueios, transacao, permissao e regras equivalentes ao legado | FULL-03, FULL-05, FULL-06, FULL-07 | yes |
| 11 | Homologar paridade operacional do Contas a Pagar | Next | Validar que tudo que o usuario faz no Contas a Pagar legado existe e funciona no `salome-core`, com testes e divergencias resolvidas | FULL-06, FULL-07, FULL-08 | yes |
| 12 | Criar dashboard financeiro operacional | Complete | Adicionar visao analitica integrada ao comportamento operacional migrado | DASH-01 | yes |
| 13 | Criar fluxo de caixa previsto operacional | Next | Apresentar previsao financeira baseada nos dados mapeados e nas regras ja migradas | CASH-01 | yes |
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
4. Acesso ao banco legado e feito por adapters/repositories; operacoes de escrita ficam em Services transacionais com testes e rastreabilidade.

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

**Goal:** Confirmar que o espelhamento web reproduz dados esperados e usar essa validacao como apoio a migracao operacional completa.

**Requirements:** READ-03

**Success criteria:**
1. Amostras de dados batem entre web e legado.
2. Divergencias sao documentadas com origem provavel.
3. Ajustes necessarios sao planejados dentro da migracao operacional completa.

### Phase 9: Migrar NotaCompra completa

**Goal:** Migrar `NotaCompra` completa, sem recorte parcial.

**Requirements:** FULL-01, FULL-02, FULL-03, FULL-04, FULL-06, FULL-07

**Success criteria:**
1. Usuario autorizado consegue incluir, editar, salvar e excluir `NotaCompra` pelo `salome-core`.
2. Cabecalho, fornecedor, filial, produtos, duplicatas, rateio, plano de contas e auditoria estao no mesmo fluxo operacional.
3. Regras de edicao, salvamento, exclusao, produto, duplicata e rateio apontam origem no legado por classe, metodo, botao, DAO, query e tabela quando aplicavel.
4. Services implementam casos de uso completos; Vaadin Views nao contem SQL nem regra financeira pesada.
5. Regras financeiras criticas possuem testes antes da liberacao operacional.
6. Permissoes de incluir, editar, salvar e excluir respeitam o mapeamento do legado ou adaptacao equivalente aprovada.

### Phase 10: Migrar baixa completa de Contas a Pagar

**Goal:** Migrar baixa/pagamento completa com equivalencia ao legado.

**Requirements:** FULL-03, FULL-05, FULL-06, FULL-07

**Success criteria:**
1. Usuario autorizado consegue baixar duplicatas pelo `salome-core` seguindo as mesmas regras do legado.
2. Baixa cria/atualiza `Extrato` e duplicata de forma transacional conforme origem mapeada.
3. Banco, cheque, datas, valor pago, bloqueios e mensagens de erro seguem o comportamento legado documentado.
4. Duplicata baixada fica protegida contra alteracoes/exclusoes proibidas.
5. Regras criticas de baixa possuem testes.

### Phase 11: Homologar paridade operacional do Contas a Pagar

**Goal:** Fechar o modulo Contas a Pagar somente quando a operacao web estiver 100% equivalente ao legado para o escopo mapeado.

**Requirements:** FULL-06, FULL-07, FULL-08

**Success criteria:**
1. Checklist de funcionalidades do legado para Contas a Pagar esta coberto no `salome-core`: consultar, filtrar, incluir, editar, salvar, excluir, produtos, duplicatas, rateio, baixa, banco, extrato, cheque, permissoes e auditoria.
2. Divergencias contra o legado estao resolvidas ou registradas como decisao explicita de produto.
3. Testes cobrem regras criticas financeiras e casos de permissao.
4. Homologacao do usuario confirma que o modulo web substitui o fluxo legado de Contas a Pagar.

### Phase 12: Criar dashboard financeiro operacional

**Goal:** Dar visibilidade financeira integrada ao modulo operacional migrado.

**Requirements:** DASH-01

**Success criteria:**
1. Dashboard apresenta indicadores definidos com origem de dados documentada.
2. Indicadores refletem dados criados, editados, excluidos e baixados pelo novo modulo quando essas operacoes estiverem disponiveis.

### Phase 13: Criar fluxo de caixa previsto operacional

**Goal:** Exibir previsao de fluxo de caixa usando dados financeiros mapeados e operacoes migradas.

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

- v1 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0

---
*Roadmap created: 2026-05-12*
*Last updated: 2026-05-14 after scope correction to complete Contas a Pagar migration*
