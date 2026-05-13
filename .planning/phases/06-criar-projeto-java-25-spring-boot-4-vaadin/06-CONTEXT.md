# Phase 6: Criar projeto Java 25 + Spring Boot 4 + Vaadin - Context

**Gathered:** 2026-05-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase cria a base tecnica do `salome-core` como aplicacao web Java 25 + Spring Boot 4 + Vaadin, pronta para abrir direto no fluxo principal de **Gestao de Pagamentos** e preparada para evoluir o espelhamento do legado. O escopo desta fase e fundacional: projeto Maven, estrutura de pacotes, shell web, menu lateral, conexao real de leitura com o MySQL legado, seguranca preparada e adaptadores de dominio iniciais. A fase nao libera gravacao funcional no legado, nao altera schema do MySQL legado e nao conclui ainda a migracao completa de `NotaCompra`, baixa, rateio, dashboard ou fluxo de caixa.

</domain>

<decisions>
## Implementation Decisions

### Estrutura inicial do modulo
- **D-01:** O `salome-core` comeca como um projeto Maven de modulo unico.
- **D-02:** A organizacao interna segue pacotes raiz por camada: `ui`, `application`, `domain`, `infrastructure` e `security`.
- **D-03:** Cada camada ja deve preparar subpacotes de `contaspagar` desde a fase 6, mesmo antes da funcionalidade completa da fase 7.
- **D-04:** Deve existir um `shared` pequeno e disciplinado, restrito a elementos realmente transversais; ele nao deve virar deposito generico.

### Baseline web e navegacao
- **D-05:** A aplicacao deve abrir direto no fluxo principal, cujo nome de produto passa a ser **Gestao de Pagamentos** em vez de `Contas a Pagar`.
- **D-06:** A fase 6 ja deve subir uma casca visual completa e interativa de `Gestao de Pagamentos`, inspirada em `references/ux-frontend`, sem copiar 100% da referencia.
- **D-07:** O shell inicial deve incluir menu lateral enxuto com visao de produto unificada; itens futuros ficam visiveis e clicaveis, abrindo paginas placeholder nesta fase.
- **D-08:** A tela inicial de `Gestao de Pagamentos` deve aproximar o modelo operacional desejado: filtro e acoes no topo, listagem de titulos/duplicatas no lado esquerdo, detalhes/produto no lado direito e area inferior preparada para a `Central de Pagamentos`.
- **D-09:** Os nomes-alvo aprovados para a experiencia futura sao: `Gestao de Pagamentos`, `Central de Pagamentos`, `Documento de Entrada`, `Painel Financeiro`, `Fluxo de Caixa` e `Movimento Financeiro`.

### Integracao com legado e configuracao
- **D-10:** A fase 6 deve conectar o novo modulo diretamente ao MySQL legado com leitura ativa usando dados reais.
- **D-11:** Embora a infraestrutura futura possa evoluir para escrita, a aplicacao desta fase deve bloquear gravacao funcional no legado.
- **D-12:** A configuracao deve usar profiles por ambiente e variaveis externas, evitando credenciais hardcoded.
- **D-13:** O Flyway deve estar instalado e configurado, mas nao pode gerenciar, migrar nem alterar o MySQL legado.
- **D-14:** Se no futuro alguma alteracao estrutural no legado for inevitavel, ela deve sair como SQL versionado, revisado e executado manualmente pelo time responsavel fora da aplicacao; nenhuma mudanca pode quebrar o codigo ou as telas legadas em producao.
- **D-15:** O acesso ao legado deve nascer como adapters/repositories orientados ao dominio, nao como camada tecnica generica solta.
- **D-16:** O primeiro recorte de leitura real deve cobrir apenas o necessario para a tela inicial de `Gestao de Pagamentos`; a abrangencia total do produto continua no roadmap, mas a implementacao segue por partes.

### Seguranca e usuario logado
- **D-17:** A aplicacao deve subir sem login real obrigatorio nesta fase, mas ja com a estrutura de Spring Security preparada.
- **D-18:** O usuario corrente inicial deve ser um usuario tecnico fixo de desenvolvimento, para manter desde o inicio o conceito de sessao/usuario logado.
- **D-19:** A fundacao deve preparar papeis/perfis basicos, sem ainda bloquear telas ou acoes por permissao real.
- **D-20:** Deve ser criado desde ja um contrato/interface para o adapter futuro do usuario legado, mas sem integrar autenticacao legada na fase 6.

### the agent's Discretion
- O nome final de subpacotes internos de `contaspagar` pode ser refinado no planejamento, desde que a divisao por camada e a preparacao da feature sejam mantidas.
- O nivel exato de placeholder dos menus futuros pode ser decidido no planejamento, desde que eles fiquem visiveis e clicaveis sem fingir funcionalidade pronta.
- A modelagem tecnica do usuario fixo de desenvolvimento e dos papeis basicos pode ser definida no planejamento, desde que preserve a futura substituicao pelo adapter do usuario legado.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto e arquitetura
- `.planning/PROJECT.md` - contexto do produto, restricoes da migracao e direcao da fase atual.
- `.planning/REQUIREMENTS.md` - requisitos `ARCH-*` e a ordem de entrega que mantem leitura antes de mutacoes.
- `.planning/ROADMAP.md` - meta, criterios de sucesso e encaixe da fase 6 no roadmap completo.
- `.planning/STATE.md` - estado atual do projeto e ponto de retomada apos a fase 5.
- `docs/architecture/salome-core-architecture.md` - proposta de arquitetura aprovada na fase 5 que a fundacao tecnica deve respeitar.
- `.planning/phases/05-propor-arquitetura-do-salome-core/05-CONTEXT.md` - decisoes travadas na fase anterior sobre camadas, seguranca e acesso ao legado.

### Mapas do legado e integracao
- `.planning/codebase/STACK.md` - stack observada no legado e superficie JDBC/MySQL existente.
- `.planning/codebase/ARCHITECTURE.md` - padroes do legado e fronteiras que o novo modulo precisa substituir.
- `.planning/codebase/CONVENTIONS.md` - convencoes observadas de UI, persistencia e operacao no legado.
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - classes, botoes e fluxo legado ligados a Contas a Pagar.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, queries e relacionamento de dados que a leitura inicial deve respeitar.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - base para o futuro adapter de usuario legado e reaproveitamento de identidade.

### Referencias de UX e produto
- `references/ux-frontend/screens/contas-pagar.jsx` - referencia visual e estrutural mais proxima para a tela inicial de `Gestao de Pagamentos`.
- `references/ux-frontend/uploads/PRD.md` - vocabulario de produto, nomenclaturas e referencias de experiencia que inspiram a consolidacao futura das telas.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `docs/architecture/salome-core-architecture.md`: define a separacao entre `ui`, `application`, `domain`, `infrastructure` e `security` que deve aparecer ja no bootstrap do projeto.
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`: ajuda a escolher o menor recorte de leitura real para a tela inicial de `Gestao de Pagamentos`.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`: serve como base para os primeiros repositories/adapters orientados ao dominio.
- `references/ux-frontend/screens/contas-pagar.jsx`: mostra uma composicao operacional densa, com toolbar, lista, detalhe, produtos e area inferior, que deve orientar o shell Vaadin inicial.

### Established Patterns
- O legado concentra SQL e eventos de tela em Swing; a nova base precisa separar isso desde o primeiro commit para nao repetir o acoplamento.
- A arquitetura aprovada na fase 5 ja travou `Views -> Services -> Repositories/Adapters`; a fase 6 deve expressar essa cadeia no codigo inicial.
- A leitura vem antes da escrita em toda a migracao; portanto a conexao real ao legado nesta fase existe para leitura e validacao estrutural, nao para mutacao operacional.
- O sistema final sera amplo, mas o roadmap exige recorte incremental; a base deve ser preparada para crescer sem tentar entregar tudo de uma vez.

### Integration Points
- O datasource do MySQL legado e o principal ponto de integracao tecnica imediato.
- O fluxo principal inicial conecta a nova tela de `Gestao de Pagamentos` ao recorte legado de titulos/duplicatas.
- O futuro adapter de usuario legado deve se apoiar no mapeamento de identidade existente sem acoplar Swing ao novo modulo.
- O menu lateral precisa preparar os futuros modulos `Documento de Entrada`, `Painel Financeiro`, `Fluxo de Caixa` e `Movimento Financeiro` como parte da narrativa de produto.

</code_context>

<specifics>
## Specific Ideas

- O usuario quer reduzir varias telas legadas em experiencias web mais unificadas, especialmente concentrando listagem, detalhe e operacao de pagamento em uma experiencia principal de `Gestao de Pagamentos`.
- A futura `Central de Pagamentos` deve ocupar a area inferior da experiencia principal e comportar baixa manual, selecao de banco e operacoes equivalentes ao legado de baixa/cheque.
- O futuro `Documento de Entrada` deve consolidar a visao de `NotaCompra`, produtos, duplicatas e rateio em uma experiencia unica mais moderna.
- O projeto final continua completo no roadmap; esta fase apenas fixa a fundacao e o primeiro recorte para avancar com seguranca.

</specifics>

<deferred>
## Deferred Ideas

- Consolidar plenamente tres telas legadas em uma experiencia unica de `Gestao de Pagamentos` com operacao completa de baixa permanece para fases posteriores, apos a fundacao.
- Implementar `Documento de Entrada` com listagem propria, abertura de detalhe em 3 paineis, CRUD de produtos, plano de contas, tributos, duplicatas e rateio fica para fases futuras.
- Implementar `Central de Pagamentos` funcional no rodape, incluindo baixa manual, banco, caixa e operacao multi-titulos, fica para fases futuras.
- Construir `Painel Financeiro`, `Fluxo de Caixa` e `Movimento Financeiro` com dados de contas a pagar e contas a receber permanece no roadmap posterior.
- Integrar autenticacao real e usuario legado no fluxo web fica para fase futura de seguranca/integracao.

</deferred>

---

*Phase: 6-Criar projeto Java 25 + Spring Boot 4 + Vaadin*
*Context gathered: 2026-05-13*
