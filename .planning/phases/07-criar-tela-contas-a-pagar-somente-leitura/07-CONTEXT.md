# Phase 7: Criar tela Contas a Pagar somente leitura - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase transforma a casca Vaadin de `Gestao de Pagamentos` em uma primeira tela web de Contas a Pagar realmente util em modo somente leitura. A entrega deve exibir uma fila operacional de duplicatas vinculadas a `NotaCompra`, carregada por `GestaoPagamentosService` e `GestaoPagamentosRepository`/adapter legado, com detalhe suficiente para o usuario entender o que esta pagando antes de qualquer baixa futura.

A fase nao libera baixa, edicao, exclusao, rateio mutavel, confirmacao de pagamento ou qualquer escrita no banco legado. A area de `Central de Pagamentos` pode existir como espaco preparado para fases posteriores, mas permanece bloqueada e sem efeito operacional.

</domain>

<decisions>
## Implementation Decisions

### Recorte da grade principal
- **D-01:** A listagem central da tela deve ser uma fila de **duplicatas + `NotaCompra`**, nao uma copia direta da tela Swing `ContasPagar` nem uma lista primaria de notas de compra.
- **D-02:** Cada linha representa uma duplicata/titulo a pagar com informacoes enxutas: fornecedor, documento/nota, parcela, vencimento, valor, status e filial.
- **D-03:** A grade principal deve priorizar o uso operacional de pagamento diario; detalhes ricos ficam fora da linha para manter a lista escaneavel.

### Estado inicial e filtros
- **D-04:** Ao abrir a tela, o filtro padrao deve mostrar as duplicatas **vencendo hoje**.
- **D-05:** As duplicatas vencidas nao devem vir misturadas no estado inicial; elas devem estar acessiveis por filtro/atalho proprio.
- **D-06:** A tela deve oferecer atalhos rapidos no topo da listagem: `Hoje`, `Vencidas`, `Proximos dias` e `Todas`.
- **D-07:** Alem dos atalhos rapidos, deve existir filtro por data/periodo para consulta manual.

### Divisao visual da tela
- **D-08:** A experiencia de `Gestao de Pagamentos` deve ser dividida em tres partes principais: listagem central, painel direito de detalhes e area de baixa/`Central de Pagamentos`.
- **D-09:** A area central concentra a listagem operacional de duplicatas vinculadas a `NotaCompra`.
- **D-10:** Ao clicar em uma duplicata, o painel direito deve exibir os produtos vinculados a nota, seus valores e o plano de contas, para quem for pagar saber do que a despesa se refere.
- **D-11:** A area de baixa deve preparar a futura `Central de Pagamentos`, mas nesta fase permanece somente leitura e sem acao de baixa habilitada.

### the agent's Discretion
- O planner pode definir o detalhe tecnico dos valores de `status` e da janela de `Proximos dias`, desde que `Hoje` seja o filtro inicial e `Vencidas`, `Proximos dias`, `Todas` e filtro por data estejam disponiveis.
- O planner pode ajustar nomes internos de classes/read models, desde que preserve a semantica de duplicata como item principal da grade e mantenha `View -> Service -> Repository/Adapter`.
- A apresentacao visual exata da divisao em tres partes pode seguir os componentes Vaadin existentes, desde que a grade central permaneca enxuta e o painel direito carregue produto, valor e plano de contas da nota selecionada.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto, roadmap e decisoes anteriores
- `.planning/PROJECT.md` - contexto do produto, restricoes da migracao e regra de leitura antes de mutacoes.
- `.planning/REQUIREMENTS.md` - requisitos `READ-01` e `READ-02`, alem da rastreabilidade da fase 7.
- `.planning/ROADMAP.md` - meta e criterios de sucesso da fase 7.
- `.planning/STATE.md` - estado atual do projeto e ponto de retomada recente.
- `.planning/phases/05-propor-arquitetura-do-salome-core/05-CONTEXT.md` - decisoes travadas sobre camadas, repositorios/adapters e read-only first.
- `.planning/phases/06-criar-projeto-java-25-spring-boot-4-vaadin/06-CONTEXT.md` - decisoes travadas sobre `Gestao de Pagamentos`, shell Vaadin, MySQL legado em leitura e nomes de produto.
- `docs/architecture/salome-core-architecture.md` - contrato arquitetural que proibe SQL/regra pesada na View Vaadin e define `View -> Service -> Repository/Adapter`.

### Mapas do legado
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - origem das classes, botoes, metodos e recomendacoes para migrar Contas a Pagar em leitura.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, relacionamentos, consultas prioritarias e campos de status/baixa para `NotaCompra`, `NotaCompraDuplicatas`, produtos, rateio e extrato.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - base futura para contexto de usuario/filial, sem acoplar Swing ao novo modulo.
- `.planning/codebase/STACK.md` - stack legada e superficie JDBC/MySQL observada.
- `.planning/codebase/ARCHITECTURE.md` - padroes legados a substituir e fronteiras de extracao.

### Codigo atual do salome-core
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java` - View Vaadin existente que deve evoluir a divisao em tres partes sem receber SQL.
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosService.java` - service de aplicacao existente para carregar o snapshot da tela.
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosRepository.java` - porta de leitura que deve expor a consulta da fila e detalhes.
- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/LegacyGestaoPagamentosRepository.java` - adapter JDBC atual que deve ser ajustado ao recorte aprovado.
- `src/main/java/br/com/salome/core/domain/contaspagar/GestaoPagamentosFiltro.java` - filtro atual que precisa evoluir para suportar atalhos e periodo.
- `src/main/java/br/com/salome/core/domain/contaspagar/TituloResumo.java` - read model da linha central.
- `src/main/java/br/com/salome/core/domain/contaspagar/TituloDetalhe.java` - read model de detalhe selecionado.

### UX e produto
- `references/ux-frontend/screens/contas-pagar.jsx` - referencia visual para a experiencia densa de gestao financeira.
- `references/ux-frontend/uploads/PRD.md` - vocabulario de produto e direcao futura, com a ressalva de que a fase 7 permanece somente leitura e alinhada ao roadmap Java/Vaadin atual.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GestaoPagamentosView`: ja possui toolbar, grid de titulos, painel de detalhe, grids de produtos/parcelas/rateio e shell de `Central de Pagamentos`.
- `GestaoPagamentosService`: ja centraliza a carga da tela em transacao read-only e resolve a selecao do titulo.
- `GestaoPagamentosRepository`: ja separa listagem, detalhe, produtos, parcelas e rateios da View.
- `LegacyGestaoPagamentosRepository`: ja usa `JdbcTemplate` e queries SQL fora da View, mas precisa ser alinhado ao filtro inicial de vencimento de hoje e ao recorte de duplicatas.
- `InMemoryGestaoPagamentosRepository`: pode continuar como fallback de desenvolvimento, desde que seus dados simulem a decisao de duplicatas por vencimento/status.

### Established Patterns
- A arquitetura aprovada exige que Vaadin Views chamem Services, Services chamem Repositories/Adapters e somente adapters acessem MySQL legado.
- A fase 6 ja escolheu o nome de produto `Gestao de Pagamentos` e preparou `Central de Pagamentos` como area futura, ainda bloqueada.
- O legado mistura SQL, evento de tela e regra financeira; a fase 7 deve reforcar a separacao e nao copiar listeners Swing.
- A primeira experiencia funcional deve ser somente leitura, com botoes de baixa/exclusao/rateio visivelmente bloqueados.

### Integration Points
- A grade central deve consultar `notacompraduplicatas` ligada a `notacompra`, `fornecedor` e `filial`.
- O detalhe direito deve carregar produtos de `notacompraprodutos` e plano/centro de contas via `planocontascentrocusto`/`planocontas` quando disponivel.
- A informacao de baixa/status deve respeitar campos mapeados como `NotaCompraDuplicatas.datapagamento`, `valorpago`, `idExtrato`, `meioPagamento` e o vinculo com `Extrato`.
- A futura baixa em `Central de Pagamentos` deve continuar fora desta fase, mas a tela pode reservar o espaco para manter a narrativa operacional.

</code_context>

<specifics>
## Specific Ideas

- O usuario definiu que a tela de `Gestao de Pagamentos` sera dividida em tres partes: listagem central, produtos/detalhes no canto direito e area de baixa/`Central de Pagamentos`.
- A listagem central deve abrir com duplicatas vencendo hoje, porque essa e a rotina principal de quem vai pagar.
- O painel direito deve ajudar o pagador a entender do que se refere a despesa antes de pagar, mostrando produtos, valores e plano de contas.
- As opcoes `Vencidas`, `Proximos dias`, `Todas` e filtro por data devem existir para sair do recorte do dia sem perder velocidade operacional.

</specifics>

<deferred>
## Deferred Ideas

- Executar baixa na `Central de Pagamentos` fica para fase posterior de migracao de baixa, com regras documentadas e testes financeiros.
- Edicao de duplicatas, produtos, rateio, plano de contas ou nota de compra continua fora da fase 7.
- Validacao formal dos dados exibidos contra o legado fica para a fase 8.

</deferred>

---

*Phase: 7-Criar tela Contas a Pagar somente leitura*
*Context gathered: 2026-05-14*
