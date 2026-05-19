# Phase 12: Criar dashboard financeiro operacional - Context

**Gathered:** 2026-05-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega um dashboard financeiro operacional no `salome-core`, integrado ao Contas a Pagar migrado. O dashboard deve dar visibilidade de aberto, vencido, a vencer, pago no periodo, recortes por filial/fornecedor/banco/plano de contas/status e inconsistencias de dados, usando o banco legado como fonte da verdade e sem criar regra financeira dentro da View Vaadin.

O dashboard e analitico e operacional, nao substitui as telas de `Documento Entrada`, `Gestao de Pagamentos` ou baixa. Acoes sensiveis continuam nas telas operacionais existentes; o dashboard pode navegar para elas com filtros aplicados. Toda metrica, filtro e query precisa ter origem pesquisada no legado antes do plano implementar.

</domain>

<decisions>
## Implementation Decisions

### Indicadores do painel
- **D-01:** O dashboard deve ser hibrido e progressivo: operacional primeiro, mas com recortes gerenciais desde a primeira versao.
- **D-02:** A base de indicadores deve incluir vencidas, vencendo hoje, a vencer, em aberto, pago no periodo e total por status usando dados ja migrados.
- **D-03:** Os recortes gerenciais por filial, fornecedor, banco e plano de contas devem existir desde a primeira versao, mas apresentados de forma densa e operacional, sem transformar o dashboard em relatorio solto.
- **D-04:** A janela padrao deve ser o mes atual, com periodo configuravel pelo usuario.
- **D-05:** Valor em aberto, vencido, a vencer e pago no periodo devem aparecer como KPIs separados, com resumo consolidado de posicao financeira.

### Fonte e confianca dos dados
- **D-06:** O dashboard nao deve exibir aviso visual de "paridade pendente" para o usuario. Ele deve buscar a fonte operacional correta, tratar o legado como fonte da verdade e apresentar os numeros corretos.
- **D-07:** A rastreabilidade tecnica continua obrigatoria internamente: cada indicador deve documentar classe/metodo/query/tabela de origem antes de ser implementado.
- **D-08:** `NotaCompraDuplicatas` e a fonte principal para status e pagamento, usando `datapagamento`, `valorpago` e `idExtrato`.
- **D-09:** Quando houver inconsistencia entre duplicata e vinculo de baixa/extrato, o dashboard deve mostrar uma secao propria de inconsistencias ou registros para revisar, sem esconder o titulo.
- **D-10:** O dashboard deve ter `DashboardFinanceiroService` proprio, reaproveitando conceitos/filtros de `GestaoPagamentos` quando fizer sentido, com queries agregadas especificas em adapter legado.
- **D-11:** O dashboard atualiza ao abrir e ao filtrar, com botao manual de atualizar. Nao deve ter auto-refresh nesta fase.

### Filtros e recortes operacionais
- **D-12:** Os filtros principais no topo devem ser completos: periodo, filial, fornecedor, banco, plano de contas e status.
- **D-13:** O comportamento dos filtros deve ser hibrido: periodo e status podem atualizar rapidamente; filtros mais pesados como fornecedor, banco e plano de contas devem ser aplicados com botao `Filtrar`.
- **D-14:** Todos os recortes principais tambem devem poder aparecer como blocos/resumos visiveis quando fizer sentido: filial, fornecedor, banco, plano de contas e status. A apresentacao deve priorizar leitura operacional e evitar poluicao visual por hierarquia/progressividade.
- **D-15:** Quando houver duvida de produto, preferir a opcao mais completa e recomendada tecnicamente, desde que ela continue dentro do escopo da fase 12 e nao crie uma nova capacidade.
- **D-16:** O planner deve pesquisar o legado antes de fechar qualquer filtro ou recorte, especialmente em `NotaCompra.formWindowOpened`, `NotaCompraDuplicatasController.lista(...)`, `EmitirCheques.atualizaTabela(true)`, `Extrato.getSaldo(...)` e queries sobre `notacompraduplicatas`, `notacompra`, `extrato`, `banco`, `fornecedor`, `filial` e `planocontascentrocusto`.

### Navegacao e profundidade
- **D-17:** O dashboard deve ser uma tela operacional de primeira vista, nao landing page. A primeira dobra deve mostrar KPIs, filtros e os principais blocos de leitura financeira.
- **D-18:** A navegacao natural deve sair dos indicadores para telas operacionais existentes com filtros aplicados: `GestaoPagamentosView`, `DocumentoEntradaView`, `DocumentoEntradaDetalhesView` e fluxos de baixa/central de pagamentos quando aplicavel.
- **D-19:** O dashboard nao deve executar baixa, reversao, edicao, exclusao ou salvamento. Ele pode oferecer links/acoes de navegacao para a tela responsavel pelo caso de uso.
- **D-20:** A profundidade recomendada e: KPIs no topo, blocos resumidos por recorte, listas curtas de registros criticos e acesso para grade operacional filtrada. Analises extensas ou relatorios completos ficam para fases futuras se necessario.
- **D-21:** A secao de inconsistencias deve permitir navegar para o registro operacional correspondente, mas a correcao acontece na tela do fluxo responsavel.

### the agent's Discretion
- O usuario autorizou o agente/planner a escolher as proximas decisoes pela combinacao "completo e recomendado", desde que pesquise o legado antes de implementar.
- O planner pode definir a composicao visual exata dos blocos, desde que respeite a experiencia Vaadin densa e operacional, sem cards decorativos ou marketing.
- O planner pode escolher nomes internos de DTOs/read models/repositories, preservando `ui -> application -> domain -> infrastructure` e mantendo SQL fora da View.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto e escopo
- `.planning/PROJECT.md` - contexto do produto, guardrails, correcao sobre `ContasPagar.*` como codigo morto e foco no fluxo real `NotaCompraDuplicatas`/`NotaCompra`.
- `.planning/REQUIREMENTS.md` - requisito `DASH-01` e pendencias de homologacao que influenciam fonte/confianca dos dados.
- `.planning/ROADMAP.md` - meta e criterios de sucesso da fase 12.
- `.planning/STATE.md` - estado atual: fase 11 encontrou bloqueios de homologacao e exige cuidado com dados operacionais.

### Decisoes anteriores
- `.planning/phases/09-migrar-notacompra-completa/09-CONTEXT.md` - decisoes sobre `Documento Entrada`, secoes da nota, bloqueios e fonte de duplicatas.
- `.planning/phases/10-migrar-baixa-completa-de-contas-a-pagar/10-CONTEXT.md` - decisoes sobre baixa, banco, extrato, cheque/lote, reversao e origem de pagamento.
- `.planning/phases/11-homologar-paridade-operacional-do-contas-a-pagar/11-CONTEXT.md` - paridade, codigo morto `ContasPagar.*`, fluxo real baseado em `NotaCompraDuplicatas` e politica de divergencias.

### Mapas do legado
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, campos, joins, queries de status/pagamento/saldo e riscos de inconsistencia.
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - classes, metodos, botoes, regras e origem de `NotaCompra`, duplicatas, baixa, extrato, banco, fornecedor, filial e plano de contas.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - usuario corrente, filial, banco/caixa e permissoes que podem afetar filtros/escopo.
- `.planning/codebase/ARCHITECTURE.md` - arquitetura observada no legado e acoplamentos que nao devem ser copiados.
- `.planning/codebase/STRUCTURE.md` - organizacao do legado e classes prioritarias para pesquisa.

### Arquitetura e codigo atual
- `docs/architecture/salome-core-architecture.md` - contrato `View -> Service -> Repository/Adapter`, governanca de schema e nota de correcao sobre `ContasPagar.*`.
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java` - grade operacional atual e alvo natural para drill-down filtrado.
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosService.java` - padrao de service de leitura operacional.
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosRepository.java` - porta de repository existente para gestao de pagamentos.
- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/LegacyGestaoPagamentosRepository.java` - exemplo de adapter JDBC isolando SQL.
- `src/main/java/br/com/salome/core/ui/notacompra/DocumentoEntradaView.java` - destino operacional para documentos filtrados.
- `src/main/java/br/com/salome/core/ui/notacompra/DocumentoEntradaDetalhesView.java` - destino operacional para detalhe/correcao do registro.
- `src/main/java/br/com/salome/core/application/notacompra/DocumentoEntradaService.java` - service de documento de entrada.
- `src/main/java/br/com/salome/core/application/notacompra/DuplicataNotaCompraService.java` - service de duplicatas e bloqueios.
- `src/main/java/br/com/salome/core/application/financeiro/BaixaNotaCompraService.java` - service de baixa/reversao; dashboard deve navegar, nao duplicar acao.
- `src/main/java/br/com/salome/core/security/CurrentUserContext.java` - fronteira de usuario corrente para escopo/filial/permissao.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GestaoPagamentosView` e `GestaoPagamentosService`: podem orientar filtros, status e navegacao para titulos/duplicatas.
- `LegacyGestaoPagamentosRepository`: padrao atual de query JDBC em `infrastructure.legacy`, util para criar `LegacyDashboardFinanceiroRepository`.
- `DocumentoEntradaView` e `DocumentoEntradaDetalhesView`: destinos naturais quando o usuario clicar em um indicador, bloco ou inconsistencia.
- `BaixaNotaCompraService`: referencia para status de pagamento/baixa, sem ser chamado pela View do dashboard para mutacao.
- `CurrentUserContext`: base para escopo por usuario/filial se a regra do legado exigir.

### Established Patterns
- Views Vaadin chamam Services; Services chamam Repositories/Adapters; SQL fica em `infrastructure.legacy`.
- Read models devem ser separados de commands de escrita.
- Regras financeiras criticas exigem origem documentada e teste quando virarem comportamento.
- O fluxo real de Contas a Pagar e `NotaCompraDuplicatas` com `NotaCompra`; `ContasPagar.*` e codigo morto ate prova contraria.
- Banco legado segue como fonte da verdade; nenhuma mudanca de schema sem Flyway versionado.

### Integration Points
- Criar `DashboardFinanceiroView` em `ui.financeiro` ou pacote equivalente do financeiro operacional.
- Criar `DashboardFinanceiroService` em `application.financeiro` ou `application.contaspagar`, mantendo a decisao clara no plano.
- Criar porta `DashboardFinanceiroRepository` e adapter `LegacyDashboardFinanceiroRepository` para agregacoes de `notacompraduplicatas`, `notacompra`, `extrato`, `banco`, `fornecedor`, `filial` e `planocontascentrocusto`.
- Reaproveitar filtros/read models de `GestaoPagamentos` quando nao duplicar regra, mas criar queries agregadas proprias para os KPIs.
- Integrar clique/drill-down com rotas existentes de `GestaoPagamentosView`, `DocumentoEntradaView` e `DocumentoEntradaDetalhesView`.

</code_context>

<specifics>
## Specific Ideas

- O usuario quer sempre a versao completa quando houver escolha razoavel, combinada com a recomendacao tecnica do agente.
- O planner deve lembrar sempre de pesquisar o legado antes de implementar metrica, filtro ou recorte.
- O dashboard deve esconder complexidade tecnica de paridade do usuario final, mas preservar rastreabilidade para planejamento, auditoria e manutencao.
- Inconsistencias devem aparecer como trabalho operacional revisavel, nao como falha generica do painel.

</specifics>

<deferred>
## Deferred Ideas

- Relatorios analiticos completos, fluxo de caixa previsto e previsao/provisionamento financeiro pertencem a fases futuras, especialmente fase 13 ou fases novas se ampliarem o escopo.
- Acoes de pagamento, baixa, reversao, edicao e exclusao permanecem nas telas operacionais ja planejadas/implementadas; o dashboard navega para elas.

</deferred>

---

*Phase: 12-Criar dashboard financeiro operacional*
*Context gathered: 2026-05-19*
