# Phase 9: Migrar NotaCompra completa - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega a migracao completa de `NotaCompra` no `salome-core`, sem recorte parcial. O usuario autorizado deve conseguir incluir, editar, salvar e excluir documento de entrada/nota de compra pelo web, cobrindo cabecalho, fornecedor, filial, produtos, duplicatas/parcelas, rateio, plano de contas, importacao XML/chave, auditoria, permissoes e testes.

A fase deve preservar comportamento do legado e melhorar a experiencia Vaadin sem copiar a arquitetura Swing. A tela nova grava no MySQL legado via Services e Repositories/Adapters, nunca via View Vaadin. Baixa/pagamento completa, banco, extrato, cheque e portal de pagamentos ficam para a fase 10, mas a fase 9 deve respeitar bloqueios quando ja houver duplicata baixada ou vinculada a `Extrato`.

</domain>

<decisions>
## Implementation Decisions

### Recorte da edicao
- **D-01:** A fase 9 nao deve fazer recorte parcial. O escopo aprovado e `NotaCompra` completa com cabecalho, fornecedor, filial, produtos, duplicatas/parcelas, rateio, plano de contas, inclusao, edicao, salvamento, exclusao, auditoria, permissoes e testes.
- **D-02:** A migracao deve apontar a origem de cada regra relevante no legado por classe, metodo, botao, DAO/query e tabela quando aplicavel, antes de liberar comportamento financeiro.

### Salvamento por secoes
- **D-03:** A experiencia web fica em tela unica, mas preserva a logica operacional do legado por secoes: cabecalho, produtos, duplicatas/parcelas e rateio.
- **D-04:** Cada secao deve ter seu proprio botao de salvar. O botao de cabecalho salva cabecalho; o de produtos salva produtos; o de duplicatas salva duplicatas; o de rateio salva rateio.
- **D-05:** Salvamento manual e por secao. Ao salvar uma secao ja existente, o sistema grava somente aquela parte, com validacao da propria secao e dependencias minimas. Nao deve regravar produtos ao salvar duplicatas, nem regravar rateio ao salvar cabecalho.
- **D-06:** Divergencia temporaria entre `valorNota`, soma dos produtos, soma das duplicatas e soma do rateio e permitida durante o lancamento manual. O sistema nao deve bloquear a gravacao por secao somente porque o documento ainda esta incompleto.
- **D-07:** Quando a entrada vier por importacao XML/chave, a importacao salva tudo automaticamente no fluxo importado. Depois, se precisar corrigir, o usuario entra em cada secao e altera/salva a secao correspondente.

### Validacoes e bloqueios financeiros
- **D-08:** Quando a `NotaCompra` tiver duplicata paga/baixada ou vinculada a `Extrato`, a edicao normal fica bloqueada.
- **D-09:** Existe excecao para o caso real de despesa lancada antes da nota/XML, por exemplo boleto pago antes da chegada da nota fiscal. Usuario com permissao especifica deve poder importar ou vincular XML depois da baixa.
- **D-10:** Excluir nota so deve ser permitido se nao houver duplicata baixada ou vinculada a `Extrato`. O planner deve confirmar a regra exata no legado antes de implementar, especialmente em `NotaCompra.btnExcluirActionPerformed`, `NotaCompraData.excluir(...)`, `NotaCompraDuplicatas.btnExcluirActionPerformed` e campos `NotaCompraDuplicatas.datapagamento` / `idExtrato`.
- **D-11:** Validacoes de datas e valores devem seguir o legado mapeado, sem inventar rigidez nova nesta fase. Regras ja mapeadas: vencimento nao pode anteceder emissao; rateio nao pode exceder valor restante; duplicata baixada nao edita/exclui. Demais regras precisam ser confirmadas na origem antes de migrar.
- **D-12:** Permissoes devem seguir o legado quando existirem. Se alguma acao ainda nao estiver mapeada, usar permissao geral provisoria de `NotaCompra` ate mapear melhor. A excecao de importar/vincular XML apos pagamento precisa ficar destacada no plano.

### Experiencia Vaadin de Documento Entrada
- **D-13:** A entrada principal do usuario deve ser a tela **Documento Entrada**, uma listagem/grade de documentos de entrada.
- **D-14:** Ao clicar em uma linha da listagem, o usuario abre **Documento Entrada Detalhes**.
- **D-15:** A tela **Documento Entrada** tambem pode seguir composicao em 3 areas: listagem principal, painel direito com produtos do documento selecionado e rodape com parcelas/rateio.
- **D-16:** **Documento Entrada Detalhes** deve ser tela unica com secoes/abas para cabecalho, produtos, duplicatas/parcelas e rateio. O usuario alterna entre as secoes sem sair da nota.
- **D-17:** A UX deve seguir as orientacoes e mockups em `references/ux-frontend`, especialmente a experiencia densa em 3 paineis. Nao fazer landing page nem tela decorativa; a primeira tela deve ser operacional.
- **D-18:** A preferencia e colocar o botao de salvar de cada secao perto do conteudo correspondente, deixando claro que o salvamento e por secao. O planner pode adicionar toolbar global com atalhos se isso melhorar ergonomia, desde que nao esconda que cada secao salva separadamente.
- **D-19:** Quando uma nota estiver bloqueada por baixa/extrato, a tela deve abrir em modo somente leitura com aviso claro explicando o motivo do bloqueio e quais acoes ainda sao permitidas, como importar/vincular XML com permissao.

### the agent's Discretion
- O planner pode escolher a composicao exata entre abas, blocos e toolbar global, desde que preserve `Documento Entrada` como listagem, `Documento Entrada Detalhes` como edicao completa e salvamento por secao.
- O planner pode decidir a nomenclatura interna de Services, Commands, DTOs e Repositories, desde que respeite `ui -> application -> domain -> infrastructure` e mantenha SQL fora da View Vaadin.
- O planner deve decidir como destacar divergencias temporarias de totais sem bloquear o salvamento por secao, desde que o usuario consiga perceber que o documento ainda pode estar incompleto.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto, roadmap e decisoes anteriores
- `.planning/PROJECT.md` - contexto do produto, restricoes da migracao, MySQL legado como base operacional e equivalencia funcional completa.
- `.planning/REQUIREMENTS.md` - requisitos `FULL-01`, `FULL-02`, `FULL-03`, `FULL-04`, `FULL-06` e `FULL-07`.
- `.planning/ROADMAP.md` - meta e criterios de sucesso da fase 9.
- `.planning/STATE.md` - estado atual e decisao recente de nao planejar recortes parciais de escrita.
- `.planning/phases/06-criar-projeto-java-25-spring-boot-4-vaadin/06-CONTEXT.md` - decisoes sobre `Gestao de Pagamentos`, `Documento de Entrada`, conexao MySQL legado e camadas.
- `.planning/phases/07-criar-tela-contas-a-pagar-somente-leitura/07-CONTEXT.md` - layout operacional em tres partes, leitura de produtos/parcelas/rateio e filtro operacional.
- `.planning/phases/08-validar-dados-da-tela-web-contra-o-legado/08-CONTEXT.md` - validacao estrita de dados e MySQL legado como fonte oficial.

### Mapas do legado
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - classes, botoes, metodos, regras criticas, destino sugerido no novo modulo e origem de `NotaCompra`, produtos, duplicatas, rateio e usuario.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, relacionamentos, queries, campos de status/baixa/exclusao e riscos de inconsistencia.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - base para reaproveitar usuario logado, filial e permissoes sem acoplar Swing.
- `.planning/codebase/TESTING.md` - convencoes e foco de testes do projeto.

### Arquitetura e codigo atual
- `docs/architecture/salome-core-architecture.md` - contrato `View -> Service -> Repository/Adapter`, governanca de schema e testes financeiros.
- `docs/setup/salome-core-local.md` - configuracao local de conexao ao banco legado.
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java` - tela Vaadin atual e ponto de integracao/navegacao para documento de entrada.
- `src/main/java/br/com/salome/core/application/contaspagar/GestaoPagamentosService.java` - service atual de aplicacao.
- `src/main/java/br/com/salome/core/infrastructure/legacy/contaspagar/LegacyGestaoPagamentosRepository.java` - adapter JDBC atual e padrao de isolamento SQL fora da View.

### UX e referencias visuais
- `references/ux-frontend/screens/contas-pagar.jsx` - referencia operacional densa em tres paineis, toolbar, listagem, produtos, financeiro, impostos, XML, historico, rateio e anexos.
- `references/ux-frontend/uploads/PRD.md` - vocabulario de produto e direcao de experiencia, com ressalva de que o projeto atual e Java/Vaadin/MySQL legado.
- `references/ux-frontend/uploads/Central de Compras.png` - mockup visual de central/listagem.
- `references/ux-frontend/uploads/Central de compras detalhes.png` - mockup visual de detalhes em paineis.
- `references/ux-frontend/uploads/Central de compras detalhess.png` - mockup complementar de detalhes.
- `references/ux-frontend/uploads/importacaoxmlpedidofinanceiro.png` - referencia para importacao XML com impacto financeiro.
- `references/ux-frontend/uploads/importacaoxmlimpostos.png` - referencia para importacao XML/impostos.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GestaoPagamentosView`: ja estabelece uma experiencia Vaadin operacional com grid, detalhe, produtos, parcelas e rateio em leitura; pode orientar navegacao para `Documento Entrada`.
- `GestaoPagamentosService`: padrao atual de View chamando Service, que deve ser preservado para os novos casos de uso de `NotaCompra`.
- `LegacyGestaoPagamentosRepository`: exemplo de adapter JDBC isolando SQL na infraestrutura.
- `docs/architecture/salome-core-architecture.md`: ja define familias alvo como `LegacyNotaCompraRepository`, `LegacyNotaCompraDuplicataRepository`, `LegacyNotaCompraRateioRepository`, `LegacyNotaCompraProdutoRepository`, lookups e commands explicitos.

### Established Patterns
- Views Vaadin chamam Services; Services chamam Repositories/Adapters; adapters acessam MySQL legado.
- Leitura e escrita devem ter modelos separados: read models para grids/detalhes e commands para alteracoes.
- Regras financeiras criticas ficam no dominio ou em services testaveis, nunca na View.
- Escrita em banco legado deve ser transacional quando uma operacao envolver multiplas tabelas.
- SQL, JDBC e nomes fisicos de tabelas ficam em `infrastructure.legacy`.

### Integration Points
- Criar fluxo de `Documento Entrada` em `ui.notacompra` ou pacote equivalente, integrado ao shell/navegacao existente.
- Criar Services de aplicacao para carregar documento, salvar cabecalho, salvar produtos, salvar duplicatas, salvar rateio, excluir nota e importar/vincular XML.
- Criar Repositories/Adapters legado para `NotaCompra`, `NotaCompraProdutos`, `NotaCompraDuplicatas`, `NotaCompraRateio`, `Fornecedor`, `Filial`, `PlanoContasCentroCusto` e lookups relacionados.
- Confirmar no legado regras de exclusao e bloqueio antes de implementar delete e edicao de nota com duplicata baixada.
- Usar usuario atual/permissao via camada `security`/adapter legado quando disponivel; nao chamar `Conecta` ou Swing diretamente.

</code_context>

<specifics>
## Specific Ideas

- O usuario quer **Documento Entrada** como listagem e **Documento Entrada Detalhes** como tela de edicao.
- A listagem pode mostrar em tres partes: lista principal, produtos do documento selecionado no lado direito e parcelas/rateio no rodape.
- O detalhe deve concentrar cabecalho, produtos, duplicatas e rateio numa tela unica, mas com secoes independentes e botoes de salvar por secao.
- O lancamento manual pode ficar temporariamente incompleto; divergencias de totais nao devem bloquear o trabalho.
- Importacao XML/chave salva tudo automaticamente, mas correcoes posteriores acontecem por secao.
- Para despesa paga antes da chegada da nota, importar/vincular XML depois da baixa deve ser permitido apenas com permissao.

</specifics>

<deferred>
## Deferred Ideas

- Despesa prevista/provisionada: permitir criar despesas que afetam fluxo de caixa previsto, mas nao financeiro realizado, por exemplo energia provisionada mensalmente; quando a nota real chegar, importar/vincular e ajustar. Registrar como direcao futura ou fase propria, pois amplia o conceito de entrada de documento e previsao financeira.
- Baixa/pagamento completa, banco, extrato, cheque e reversoes ficam para a fase 10, embora a fase 9 precise respeitar bloqueios decorrentes de baixa/extrato existentes.
- Portal de pagamentos, integracao bancaria, DDA, comprovantes e fluxo de caixa analitico permanecem em fases posteriores do roadmap.

</deferred>

---

*Phase: 9-Migrar NotaCompra completa*
*Context gathered: 2026-05-14*
