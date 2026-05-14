# Phase 8: Validar dados da tela web contra o legado - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase valida que a tela Vaadin read-only de `Gestao de Pagamentos` le e exibe corretamente os dados do MySQL legado para Contas a Pagar antes de qualquer liberacao de escrita. A entrega deve criar uma validacao local reutilizavel, comparar a leitura do novo adapter com as consultas/comportamento legado, gerar um relatorio versionado de divergencias e testar o motor de comparacao com fixtures.

O MySQL legado e a fonte oficial de verdade do negocio e sera a base futura do CRUD completo no `salome-core`. Nesta fase, porem, a operacao continua somente leitura: nao liberar inclusao, edicao, exclusao, baixa, rateio mutavel ou qualquer gravacao.

</domain>

<decisions>
## Implementation Decisions

### Recorte de comparacao
- **D-01:** A validacao deve cobrir todos os filtros principais expostos na tela: `Hoje`, `Vencidas`, `Proximos dias`, `Todas` e periodo manual.
- **D-02:** A comparacao obrigatoria cobre a grade principal e o detalhe completo: lista de duplicatas, detalhe selecionado, produtos, parcelas e rateio.
- **D-03:** A regua de paridade e estrita: tudo deve bater exatamente. Qualquer diferenca textual, monetaria, data, status, vinculo, contagem, ordenacao ou detalhe vira divergencia registrada.
- **D-04:** A fonte de verdade da comparacao sao as queries legadas mapeadas e o comportamento da tela Swing quando houver duvida.

### Dados e evidencias
- **D-05:** A validacao deve puxar o conjunto completo do MySQL legado para o recorte da fase, nao apenas mocks ou amostras artificiais.
- **D-06:** Para cada filtro, o validador deve processar todos os registros retornados. O relatorio deve resumir totais, divergencias e exemplos rastreaveis, sem listar o banco inteiro quando o volume for alto.
- **D-07:** As evidencias representativas devem cobrir status e vinculos importantes quando existirem: pendentes, vencidas, pagas/baixadas, com e sem produtos, com e sem rateio, com e sem extrato.

### Divergencias
- **D-08:** Divergencias devem aparecer como lista simples, com esperado do legado/MySQL, encontrado no novo modulo e local da diferenca.
- **D-09:** A fase 8 documenta divergencias e planeja a correcao; ela nao deve virar uma fase de corrigir tudo imediatamente.
- **D-10:** O relatorio final da validacao deve ser Markdown versionado no diretorio da fase, por exemplo `08-VALIDATION.md`.
- **D-11:** Campo que nao puder ser comparado por falta de origem/regra clara deve ser registrado como divergencia pendente, com campo, origem faltante e pergunta tecnica para investigacao.

### Automacao
- **D-12:** A fase deve criar um validador reutilizavel mais relatorio: rotina/servico de validacao que le o MySQL legado, compara com a leitura do adapter novo e gera `08-VALIDATION.md`.
- **D-13:** O validador deve rodar como comando/procedimento local de validacao, fora da UI Vaadin.
- **D-14:** Se o banco legado nao estiver configurado, o validador deve falhar com orientacao clara, sem fingir sucesso, explicando quais variaveis/configs faltam.
- **D-15:** Alem do relatorio, a fase deve incluir teste automatizado do motor de comparacao com fixtures, sem depender do banco real.

### the agent's Discretion
- O planner pode escolher o formato interno do comando local e do servico de validacao, desde que ele nao seja exposto na UI Vaadin e consiga gerar o relatorio Markdown versionado.
- O planner pode definir a estrutura exata do motor de comparacao e das fixtures, desde que cubra esperado vs encontrado e seja testavel sem MySQL real.
- O planner pode decidir como paginar/processar volume internamente, desde que a validacao compare todos os registros do recorte e o relatorio nao esconda divergencias.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto, roadmap e decisoes anteriores
- `.planning/PROJECT.md` - contexto do produto, restricoes da migracao, MySQL legado como base operacional e leitura antes de mutacoes.
- `.planning/REQUIREMENTS.md` - requisito `READ-03` e rastreabilidade da fase 8.
- `.planning/ROADMAP.md` - meta e criterios de sucesso da fase 8.
- `.planning/STATE.md` - estado atual do projeto e ponto de retomada.
- `.planning/phases/05-propor-arquitetura-do-salome-core/05-CONTEXT.md` - decisoes travadas sobre camadas, repositories/adapters e read-only first.
- `.planning/phases/06-criar-projeto-java-25-spring-boot-4-vaadin/06-CONTEXT.md` - decisoes sobre `Gestao de Pagamentos`, conexao MySQL legado e fundacao Vaadin.
- `.planning/phases/07-criar-tela-contas-a-pagar-somente-leitura/07-CONTEXT.md` - decisoes da tela read-only que a fase 8 deve validar.
- `.planning/phases/07-criar-tela-contas-a-pagar-somente-leitura/07-VERIFICATION.md` - evidencia da implementacao anterior, lacuna de `mvn test` e necessidade de validar valores reais contra o legado.

### Mapas do legado e validacao
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, queries prioritarias, campos de status/baixa, produtos, parcelas, rateio e extrato.
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - origens em classes, botoes, metodos e recomendacoes para Contas a Pagar.
- `.planning/codebase/TESTING.md` - recomendacao de focar leitura e consistencia dos dados de Contas a Pagar.
- `.planning/codebase/ARCHITECTURE.md` - padrao legado e fronteira de extracao para nao recolocar SQL/regra na View.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - contexto de usuario/filial quando a validacao precisar explicar filtros ou origem de sessao.

### Arquitetura e codigo atual
- `docs/architecture/salome-core-architecture.md` - contrato arquitetural `View -> Service -> Repository/Adapter`.
- `docs/setup/salome-core-local.md` - configuracao local esperada para conectar ao banco legado.
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java` - tela Vaadin read-only que consome os dados validados.
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosService.java` - service de aplicacao que monta o snapshot da tela.
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosRepository.java` - porta de leitura usada pelo service.
- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/LegacyGestaoPagamentosRepository.java` - adapter JDBC atual, principal alvo de validacao.
- `src/main/java/br/com/salome/core/domain/contaspagar/GestaoPagamentosFiltro.java` - filtros que definem o recorte obrigatorio da validacao.
- `src/test/java/br/com/salome/core/application/contaspagar/GestaoPagamentosServiceTest.java` - padrao inicial de teste da camada de aplicacao.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GestaoPagamentosService`: ja centraliza a carga da tela em transacao read-only e pode ser usado como uma das leituras "encontradas" pelo validador.
- `GestaoPagamentosRepository`: porta existente para listagem, detalhe, produtos, parcelas e rateios; o motor de comparacao deve orbitar essa fronteira, nao a View.
- `LegacyGestaoPagamentosRepository`: adapter JDBC atual com as queries que precisam ser conferidas contra o legado mapeado.
- `GestaoPagamentosFiltro`: ja codifica `Hoje`, `Vencidas`, `Proximos dias`, `Todas` e periodo manual, que sao o recorte obrigatorio da validacao.
- Testes em `src/test/java`: ja existe estrutura de teste; a fase 8 deve adicionar fixtures para o motor de comparacao sem depender do MySQL real.

### Established Patterns
- Views Vaadin chamam Services; Services chamam Repositories/Adapters; somente adapters acessam banco.
- A tela permanece read-only ate a validacao de dados passar; CRUD completo no MySQL legado fica para fases futuras.
- Relatorios e evidencias da migracao ficam versionados em `.planning/phases/`.
- Falhas de ambiente devem ser explicitas; nao mascarar ausencia de banco legado como sucesso.

### Integration Points
- O validador deve ler o MySQL legado configurado localmente e comparar com a leitura do adapter novo para `notacompraduplicatas`, `notacompra`, `fornecedor`, `filial`, `extrato`, `notacompraprodutos` e `notacomprarateio`.
- O relatorio `08-VALIDATION.md` deve registrar totais processados, divergencias simples, pendencias de origem/regra e evidencias representativas.
- O scout encontrou risco tecnico que a fase deve verificar: `TituloResumo` recebe `valorNota`, mas a query principal de titulos em `LegacyGestaoPagamentosRepository` precisa ser conferida para garantir que todos os campos do read model estao selecionados corretamente.

</code_context>

<specifics>
## Specific Ideas

- O usuario confirmou que a finalidade da fase e garantir que a tela nova mostre corretamente os dados do MySQL legado.
- O usuario reforcou que o novo `salome-core` vai conectar no banco MySQL do legado, que sera a fonte oficial de verdade, e que no futuro o CRUD completo deve acontecer nesse caminho.
- Para a fase 8, essa direcao maior deve ser respeitada sem antecipar escrita: validar leitura completa agora, preparar correcoes, e deixar CRUD para as fases de escrita do roadmap.

</specifics>

<deferred>
## Deferred Ideas

- CRUD completo no `salome-core` sobre o MySQL legado fica para as fases de escrita do roadmap, depois da validacao read-only da fase 8.
- Expor validacao como botao na UI Vaadin nao entra nesta fase; a validacao deve rodar como comando/procedimento local.

</deferred>

---

*Phase: 8-Validar dados da tela web contra o legado*
*Context gathered: 2026-05-14*
