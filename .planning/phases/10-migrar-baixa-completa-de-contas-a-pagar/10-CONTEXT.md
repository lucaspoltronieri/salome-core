# Phase 10: Migrar baixa completa de Contas a Pagar - Context

**Gathered:** 2026-05-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega a migracao completa da baixa/pagamento de duplicatas de `NotaCompra` no `salome-core`, com equivalencia ao legado. O usuario autorizado deve conseguir baixar duplicatas manualmente, baixar uma ou mais duplicatas pela `Central de Pagamentos`, operar cheque quando aplicavel, gravar `Extrato`, atualizar `NotaCompraDuplicatas`, respeitar banco/caixa/conciliacao, aplicar permissoes e desfazer baixa conforme o comportamento legado.

A fase deve preservar as regras financeiras mapeadas e melhorar a experiencia Vaadin sem copiar a arquitetura Swing. A View Vaadin chama Services; Services chamam Repositories/Adapters; Repositories/Adapters acessam o MySQL legado. Toda regra migrada deve apontar origem por classe, metodo, botao, DAO/query e tabela quando aplicavel.

Fica fora desta fase reabrir o escopo da `NotaCompra` completa da fase 9 ou criar portal bancario externo. A fase 10 usa a `NotaCompra`/duplicatas existentes como base operacional e foca baixa, banco, extrato, cheque, caixa, reversao, bloqueios, permissoes e testes financeiros criticos.

</domain>

<decisions>
## Implementation Decisions

### Baixa manual
- **D-01:** A baixa manual deve acontecer em `Documento Entrada Detalhes`, na aba de duplicatas.
- **D-02:** A baixa manual grava imediatamente ao confirmar o modal/formulario de baixa, igual ao legado.
- **D-03:** Valor pago diferente do valor da duplicata deve ser permitido, seguindo o legado, desde que grave `NotaCompraDuplicatas.valorpago` e documente a diferenca no comportamento/testes.
- **D-04:** Os campos de baixa manual devem seguir o legado sempre que possivel: data de pagamento, valor pago, banco, operacao, numero de cheque e historico.
- **D-05:** A baixa manual deve ser transacional: criar `Extrato` e depois atualizar a duplicata com `datapagamento`, `valorpago` e `idExtrato`, com rollback se qualquer parte falhar.
- **D-06:** O planner deve confirmar a regra exata em `NotaCompraDuplicataBaixa.btnBaixarActionPerformed(...)`, `ExtratoController.incluir(...)`, `NotaCompraDuplicatasController.salvar(...)`, `ExtratoData.incluir(...)` e `NotaCompraDuplicatasData.salvar(...)`.

### Central de Pagamentos, cheque e lote
- **D-07:** A baixa em lote/cheque deve ficar na **Central de Pagamentos**, selecionando uma ou mais duplicatas da fila operacional.
- **D-08:** A `Central de Pagamentos` deve ficar junto da experiencia operacional principal de pagamentos, nao como fluxo isolado sem contexto.
- **D-09:** A regra de permitir ou restringir mistura de fornecedores, notas e filiais deve seguir exatamente o legado apos confirmar a origem em `EmitirCheques`.
- **D-10:** Esta fase deve migrar o fluxo completo de cheque/lote quando aplicavel: cheque, `Extrato`, saida/entrada de caixa e vinculo das duplicatas.
- **D-11:** Se uma duplicata do lote falhar, a operacao deve fazer rollback total: nenhuma duplicata do lote fica baixada.
- **D-12:** O planner deve confirmar a ordem e os efeitos em `EmitirCheques.btnLancarActionPerformed(...)`, especialmente gravacao de `Extrato`, movimentacao de caixa, vinculo de `NotaCompraDuplicatas` e regras de cheque.

### Banco, caixa e conciliacao
- **D-13:** O usuario escolhe manualmente o banco em cada baixa/lote, igual ao legado.
- **D-14:** Conta caixa deve replicar exatamente o legado, incluindo `Extrato.dataConciliacao` e lancamento de caixa quando aplicavel.
- **D-15:** A UI deve mostrar saldo do banco selecionado usando a mesma origem do legado, como `v_saldobancariotalao`.
- **D-16:** Permissao de caixa entra nesta fase com bloqueio real da operacao quando o usuario nao puder alterar caixa/banco conforme o legado.
- **D-17:** O planner deve confirmar regras ligadas a `Banco.contaCaixa`, `Banco.idFilialCaixa`, `Banco.modeloCheque`, `UsuarioController.getIdBancoCaixa(...)` e `PagamentoCaixa.verificaPermissaoAlteraCaixa(...)`.

### Reversao e bloqueios
- **D-18:** Desfazer baixa deve seguir o legado: excluir o `Extrato` vinculado e limpar `NotaCompraDuplicatas.datapagamento`, `NotaCompraDuplicatas.valorpago` e `NotaCompraDuplicatas.idExtrato`.
- **D-19:** A reversao deve aparecer em `Documento Entrada Detalhes` e na `Central de Pagamentos`/consulta de baixas, usando o mesmo Service.
- **D-20:** Duplicata baixada deve ser protegida seguindo exatamente o legado, documentando quais campos ficam bloqueados.
- **D-21:** Mensagens de erro/bloqueio devem ser operacionais e curtas, informando motivo e origem funcional: paga, vinculada a extrato, sem permissao, data invalida etc. Quando houver mensagem equivalente no legado, ela deve orientar o texto.
- **D-22:** O planner deve confirmar reversao e bloqueios em `Extrato.btnExcluirActionPerformed(...)`, `ExtratoData.excluir(...)`, `NotaCompraDuplicatas.tabelaClick(...)`, `NotaCompraDuplicatas.btnExcluirActionPerformed(...)` e `NotaCompraDuplicatasData.salvar(...)`.

### Testes obrigatorios
- **D-23:** Regras criticas de baixa devem ter teste antes de liberar escrita: data de pagamento menor que entrada deve falhar; baixa cria `Extrato` e atualiza duplicata atomicamente; banco caixa define conciliacao/lancamento de caixa conforme legado; usuario sem permissao de caixa e bloqueado; lote falha com rollback total; reversao limpa duplicata ao excluir `Extrato`.

### the agent's Discretion
- O planner pode definir a composicao visual exata entre modal, drawer ou painel lateral, desde que baixa manual fique em `Documento Entrada Detalhes`/aba de duplicatas e lote/cheque fique na `Central de Pagamentos`.
- O planner pode escolher nomes internos de Services, Commands, DTOs e Repositories, desde que preserve `ui -> application -> domain -> infrastructure` e mantenha SQL fora da View Vaadin.
- O planner pode decidir como apresentar saldo, historico e mensagens no layout Vaadin, desde que use a origem legada correta e preserve a clareza operacional.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto, roadmap e decisoes anteriores
- `.planning/PROJECT.md` - contexto do produto, restricoes da migracao, MySQL legado como base operacional e equivalencia funcional completa.
- `.planning/REQUIREMENTS.md` - requisitos `FULL-03`, `FULL-05`, `FULL-06` e `FULL-07`.
- `.planning/ROADMAP.md` - meta e criterios de sucesso da fase 10.
- `.planning/STATE.md` - estado atual do projeto e fase anterior em execucao.
- `.planning/phases/09-migrar-notacompra-completa/09-CONTEXT.md` - decisoes sobre `Documento Entrada`, bloqueio de duplicata baixada, excecao de XML apos pagamento e fronteira entre fase 9 e fase 10.

### Mapas do legado
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - classes, botoes, metodos e regras criticas de baixa, cheque, banco, caixa, permissao e reversao.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, campos, queries e riscos de consistencia para `NotaCompraDuplicatas`, `Extrato`, `Banco`, `Caixa`, `Operaca` e `v_saldobancariotalao`.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - base para permissao de caixa, banco do usuario, filial e contexto de usuario legado.
- `.planning/codebase/TESTING.md` - convencoes e foco de testes do projeto.

### Arquitetura e codigo atual
- `docs/architecture/salome-core-architecture.md` - contrato `View -> Service -> Repository/Adapter`, governanca de schema e testes financeiros.
- `docs/setup/salome-core-local.md` - configuracao local de conexao ao banco legado.
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java` - tela atual de gestao/fila de pagamentos e ponto natural da `Central de Pagamentos`.
- `src/main/java/br/com/salome/core/ui/notacompra/DocumentoEntradaDetalhesView.java` - tela onde a baixa manual deve aparecer na aba de duplicatas.
- `src/main/java/br/com/salome/core/application/notacompra/DuplicataNotaCompraService.java` - service atual de duplicatas que ja conhece bloqueios de duplicata paga/vinculada.
- `src/main/java/br/com/salome/core/infrastructure/legacy/notacompra/LegacyNotaCompraRepository.java` - adapter atual para `notacompraduplicatas`, ponto de extensao para baixa/reversao ou referencia para novo adapter financeiro.
- `src/main/java/br/com/salome/core/security/CurrentUserContext.java` - fronteira atual para usuario corrente, necessaria para permissoes de caixa/banco.

### UX e referencias visuais
- `references/ux-frontend/screens/contas-pagar.jsx` - referencia operacional densa para `Gestao de Pagamentos` e `Central de Pagamentos`.
- `references/ux-frontend/screens/pagamentos.jsx` - referencia visual relacionada ao fluxo de pagamentos.
- `references/ux-frontend/uploads/autorização de pagamento.png` - referencia de autorizacao/operacao de pagamento, se ainda aplicavel ao desenho Vaadin.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DocumentoEntradaDetalhesView`: ja exibe duplicatas na tela de detalhe da nota; deve receber a acao de baixa manual por duplicata, chamando Service.
- `GestaoPagamentosView`: ja representa a experiencia principal de gestao/fila; deve abrigar a `Central de Pagamentos` para selecionar uma ou mais duplicatas e executar lote/cheque.
- `DuplicataNotaCompraService`: ja centraliza operacoes de duplicata e valida bloqueio de duplicata paga/vinculada; pode ser estendido ou servir de fronteira para um novo `BaixaNotaCompraService`.
- `LegacyNotaCompraRepository`: ja le e grava `notacompraduplicatas`; a fase 10 deve adicionar adapters financeiros para `Extrato`, `Banco`, `Operaca`, `Caixa` e saldo, sem jogar SQL na View.
- `CurrentUserContext` e security atual: base para resolver usuario corrente e aplicar permissao de caixa/banco.

### Established Patterns
- Views Vaadin chamam Services; Services chamam Repositories/Adapters; adapters acessam MySQL legado.
- Operacoes financeiras que alteram multiplas tabelas devem ser transacionais.
- Regras criticas precisam apontar origem no legado e ter teste.
- Modelos de leitura e commands de escrita devem ficar separados.
- SQL, JDBC, procedures e views legadas ficam em `infrastructure.legacy`.

### Integration Points
- Criar/estender service de aplicacao para baixa manual de duplicata: valida permissao, valida datas/valores, cria `Extrato`, atualiza `NotaCompraDuplicatas` e retorna resultado operacional.
- Criar service de baixa em lote/cheque para a `Central de Pagamentos`, seguindo `EmitirCheques` e garantindo rollback total.
- Criar adapters/repositories para `Extrato`, `Banco`, `Operaca`, `Caixa` e saldo por `v_saldobancariotalao`.
- Conectar permissao de caixa/banco ao `CurrentUserContext` e a adapters legados de usuario/permissao.
- Implementar reversao por Service unico usado por `Documento Entrada Detalhes` e `Central de Pagamentos`.

</code_context>

<specifics>
## Specific Ideas

- Baixa manual fica junto da duplicata em `Documento Entrada Detalhes`.
- A `Central de Pagamentos` seleciona uma ou mais duplicatas e fica junto da experiencia operacional principal de pagamentos.
- O usuario quer comportamento "igual legado" para baixa manual, cheque/lote, banco/caixa, rollback e reversao.
- A UI deve ser operacional: saldo do banco, campos necessarios e mensagens curtas que expliquem o bloqueio sem despejar detalhe tecnico.

</specifics>

<deferred>
## Deferred Ideas

None - discussion stayed within phase scope.

</deferred>

---

*Phase: 10-Migrar baixa completa de Contas a Pagar*
*Context gathered: 2026-05-15*
