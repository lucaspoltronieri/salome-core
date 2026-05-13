# Phase 6: Criar projeto Java 25 + Spring Boot 4 + Vaadin - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-13
**Phase:** 6-Criar projeto Java 25 + Spring Boot 4 + Vaadin
**Areas discussed:** Estrutura inicial do modulo, Baseline web e navegacao, Integracao com banco legado e configuracao, Seguranca e usuario logado na fundacao

---

## Estrutura inicial do modulo

| Option | Description | Selected |
|--------|-------------|----------|
| Modulo unico Maven | Base mais simples para levantar a stack e organizar a evolucao inicial | ✓ |
| Multi-modulo desde o inicio | Ja separar artefatos desde a fundacao | |
| Um modulo agora com extracao futura | Manter build simples, mas pensando em separacao posterior | |

**User's choice:** Modulo unico Maven.
**Notes:** O usuario tambem travou pacotes raiz por camada, subpacotes de `contaspagar` ja preparados dentro de cada camada e um `shared` pequeno e disciplinado.

---

## Baseline web e navegacao

| Option | Description | Selected |
|--------|-------------|----------|
| Home tecnica minima | Validar stack sem se aproximar ainda da tela final | |
| Casca completa de Contas a Pagar com interacao basica | Ja subir uma experiencia proxima do modelo desejado | ✓ |
| Aplicacao sem tela util | Apenas confirmar que a stack sobe | |

**User's choice:** Casca completa e interativa, ja conectada ao legado, usando `references/ux-frontend` como modelo proximo.
**Notes:** O usuario ajustou o nome principal para `Gestao de Pagamentos`, pediu abertura direta nessa tela, menu lateral enxuto e paginas placeholder clicaveis para modulos futuros. Tambem descreveu a visao futura de unificar varias telas legadas em `Gestao de Pagamentos`, `Documento de Entrada`, `Central de Pagamentos`, `Painel Financeiro`, `Fluxo de Caixa` e `Movimento Financeiro`.

---

## Integracao com banco legado e configuracao

| Option | Description | Selected |
|--------|-------------|----------|
| Conexao direta ao MySQL legado em leitura | Dados reais desde o inicio com escrita bloqueada | ✓ |
| Camada mockavel lendo do legado por baixo | Mais estrutura inicial antes do uso direto | |
| Contrato sem conexao real ainda | Base tecnica sem integracao viva | |

**User's choice:** Conexao real com o MySQL legado, operando em leitura ativa e com escrita bloqueada na aplicacao nesta fase.
**Notes:** O usuario reforcou que nao se pode alterar o banco MySQL legado nem quebrar o codigo ou as telas legadas; se algum ajuste futuro for necessario, deve sair como SQL versionado e ser executado manualmente pelo time responsavel. Tambem travou configuracao por profiles de ambiente com variaveis externas, Flyway instalado sem atuar no legado, adapters/repositories orientados ao dominio e recorte inicial somente para `Gestao de Pagamentos`.

---

## Seguranca e usuario logado na fundacao

| Option | Description | Selected |
|--------|-------------|----------|
| Estrutura de seguranca preparada sem login real | Spring Security encaixado, mas sem autenticacao legada ativa | ✓ |
| Integracao real com o usuario legado ja na fase 6 | Aproximacao mais rapida ao cenario final | |
| Sem seguranca por enquanto | Simplificar a fundacao e adiar seguranca | |

**User's choice:** Preparar a estrutura de seguranca agora, sem login real obrigatorio nesta fase.
**Notes:** O usuario aprovou uso de um usuario tecnico fixo de desenvolvimento, preparacao de papeis/perfis basicos sem bloqueio de tela ainda e criacao do contrato/interface do futuro adapter de usuario legado.

---

## the agent's Discretion

- Refinar os nomes exatos de subpacotes internos no planejamento, respeitando as decisoes travadas.
- Definir a profundidade dos placeholders de navegacao futura sem fingir funcionalidades prontas.
- Modelar tecnicamente o usuario fixo de desenvolvimento e os papeis basicos de forma consistente com a futura integracao legada.

## Deferred Ideas

- Consolidacao completa da `Central de Pagamentos` com baixa, banco, caixa e operacao multi-titulos.
- `Documento de Entrada` completo com detalhe em 3 paineis, produtos, duplicatas e rateio.
- `Painel Financeiro`, `Fluxo de Caixa` e `Movimento Financeiro` com dados mais amplos do financeiro e faturamento.
