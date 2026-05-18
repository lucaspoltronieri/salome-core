# Phase 11: Homologar paridade operacional do Contas a Pagar - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase fecha a migracao operacional do Contas a Pagar somente quando o `salome-core` demonstrar paridade com o fluxo realmente ativo no legado para o escopo mapeado. A homologacao deve validar operacoes completas sobre `NotaCompra`, `NotaCompraDuplicatas`, produtos, rateio, baixa, banco, extrato, cheque/lote, reversao, permissoes e auditoria, com evidencias suficientes para substituir o uso legado no dia a dia.

Achado central da discussao: a familia `ContasPagar.java` / `ContasPagarController` / `ContasPagarData` / `ContasPagarBean` / `ContasPagarTable` deve ser tratada como codigo morto ate prova contraria, porque a tabela `contaspagar` nao existe no banco de producao analisado e o DAO usa exclusivamente essa tabela. O "Contas a Pagar" real do sistema e o fluxo baseado em `NotaCompraDuplicatas` com `NotaCompra`.

</domain>

<decisions>
## Implementation Decisions

### Base de paridade
- **D-01:** A homologacao deve usar checklist por fluxo operacional, nao por copia visual de tela Swing: incluir, editar, salvar secoes, excluir, operar produtos, duplicatas, rateio, baixa manual, lote/cheque, reversao, permissoes, auditoria e validacao de dados.
- **D-02:** O escopo de paridade da fase 11 deve considerar `NotaCompra` e `NotaCompraDuplicatas` como fonte real de Contas a Pagar do legado.
- **D-03:** A familia `ContasPagar.*` nao deve ser migrada como requisito de paridade operacional nesta fase, salvo se pesquisa adicional provar que existe tabela/menu ativo em algum banco usado pela operacao. Se aparecer como menu historico, a decisao esperada e registrar como legado morto ou comportamento nao suportado, nao construir tabela nova.
- **D-04:** `ContasPagarFornecedorFilialBean` e excecao ao nome confuso: ele deve continuar sendo tratado como artefato ativo porque agrupa duplicatas por fornecedor/filial via `NotaCompraDuplicatasController`, apesar de nao representar a tela morta `ContasPagar.java`.

### Evidencias de homologacao
- **D-05:** A fase deve produzir uma evidencia versionada de homologacao, preferencialmente `11-HOMOLOGATION.md`, com checklist, responsavel/validador, ambiente usado, data, resultado por fluxo, divergencias e decisao final.
- **D-06:** O roteiro de homologacao deve ser executavel por usuario operacional ou com validacao direta do usuario: a evidencia so fecha quando o fluxo web permite substituir o fluxo legado real, nao apenas quando testes automatizados passam.
- **D-07:** A homologacao deve incluir ao menos cenarios representativos com dados reais ou base segura de homologacao: nota com produtos, nota com duplicatas abertas, duplicata baixada, nota com rateio, baixa manual, baixa em lote/cheque quando aplicavel, reversao, usuario sem permissao de caixa/banco, exclusao bloqueada e XML/vinculo apos pagamento quando aplicavel.
- **D-08:** A fase deve rodar os testes automatizados existentes e adicionar testes de cobertura faltante para regras criticas antes de considerar a paridade aprovada. `mvn test` deve ser gate minimo quando o ambiente local permitir.

### Politica de divergencias
- **D-09:** Divergencias que envolvem valor financeiro, data, status de baixa, vinculo com `Extrato`, permissao, auditoria, transacao/rollback, exclusao ou bloqueio de duplicata paga sao bloqueadoras ate serem corrigidas ou virarem decisao explicita de produto.
- **D-10:** Divergencias de experiencia visual podem ser aceitas quando preservarem a regra de negocio e melhorarem a operacao web, desde que sejam registradas como decisao explicita de produto.
- **D-11:** O achado de codigo morto em `ContasPagar.*` deve virar divergencia resolvida por decisao: nao ha paridade a homologar contra uma tabela inexistente; a paridade exigida e contra o fluxo ativo `NotaCompraDuplicatasConsulta` / `NotaCompraDuplicatas`.
- **D-12:** Nenhuma divergencia pode ser escondida em relatorio generico. Cada item deve indicar esperado legado, encontrado no `salome-core`, severidade, origem/rastro tecnico e decisao: corrigir, aceitar como produto, excluir como codigo morto ou adiar para fase futura.

### Checklist recomendado
- **D-13:** A fase deve consolidar um inventario final de cobertura cruzando roadmap, requisitos, mapas do legado, codigo implementado e docs de regras migradas.
- **D-14:** O checklist deve marcar cada fluxo como `Coberto`, `Bloqueado`, `Aceito com diferenca documentada`, `Nao aplicavel - codigo morto` ou `Fora de fase`.
- **D-15:** O planner deve priorizar lacunas ja visiveis nos requisitos: `FULL-01`, `FULL-02`, `FULL-04` e `FULL-08` ainda estavam pendentes em `REQUIREMENTS.md`; a fase 11 deve provar que foram cobertos ou registrar o bloqueio.

### the agent's Discretion
- O usuario delegou a definicao detalhada da homologacao por paridade. O planner pode escolher o formato exato de checklist, relatorio e roteiro, desde que preserve as decisoes acima e trate `ContasPagar.*` como codigo morto ate confirmacao contraria.
- O planner pode decidir se implementa um comando/servico de auditoria de paridade alem do Markdown, desde que a evidencia final seja versionada e revisavel.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto e escopo
- `.planning/PROJECT.md` - contexto do produto, restricoes da migracao, equivalencia funcional completa e guardrails contra alterar legado/banco de producao.
- `.planning/REQUIREMENTS.md` - requisitos `FULL-01`, `FULL-02`, `FULL-04` e `FULL-08` pendentes e `FULL-03`, `FULL-05`, `FULL-06`, `FULL-07` ja marcados como completos.
- `.planning/ROADMAP.md` - meta e criterios de sucesso da fase 11.
- `.planning/STATE.md` - foco atual e historico recente de fases 9 e 10.

### Decisoes anteriores
- `.planning/phases/08-validar-dados-da-tela-web-contra-o-legado/08-CONTEXT.md` - validacao estrita contra MySQL legado e politica de divergencias de dados.
- `.planning/phases/09-migrar-notacompra-completa/09-CONTEXT.md` - decisoes sobre `Documento Entrada`, secoes, bloqueios, XML apos pagamento e escopo completo de `NotaCompra`.
- `.planning/phases/10-migrar-baixa-completa-de-contas-a-pagar/10-CONTEXT.md` - decisoes sobre baixa manual, lote/cheque, banco/caixa, reversao, bloqueios e testes obrigatorios.

### Mapas e achado de codigo morto
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - classes, metodos, botoes, regras criticas e lista historica que ainda inclui `ContasPagar.*`; deve ser relido com o achado de tabela inexistente em mente.
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, queries e relacoes; downstream deve confirmar que `notacompraduplicatas` e `notacompra` sao a base real e que `contaspagar` nao existe no banco alvo.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - permissoes, usuario corrente, banco/caixa e auditoria.
- `docs/baixa-contas-pagar-regras-migradas.md` - regras ja migradas na fase 10 para baixa, lote/cheque, banco/saldo/caixa, reversao e governanca.
- `docs/architecture/salome-core-architecture.md` - contrato arquitetural e regras financeiras que exigem teste.

### Codigo do salome-core
- `src/main/java/br/com/salome/core/ui/notacompra/DocumentoEntradaView.java` - entrada operacional de documentos.
- `src/main/java/br/com/salome/core/ui/notacompra/DocumentoEntradaDetalhesView.java` - edicao de cabecalho, produtos, duplicatas, rateio e baixa manual.
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java` - gestao/fila operacional e ponto natural para Central de Pagamentos.
- `src/main/java/br/com/salome/core/application/notacompra/DocumentoEntradaService.java` - caso de uso de documento de entrada.
- `src/main/java/br/com/salome/core/application/notacompra/DuplicataNotaCompraService.java` - regras e operacoes de duplicatas.
- `src/main/java/br/com/salome/core/application/notacompra/ProdutoNotaCompraService.java` - operacoes de produtos.
- `src/main/java/br/com/salome/core/application/notacompra/RateioNotaCompraService.java` - operacoes de rateio.
- `src/main/java/br/com/salome/core/application/financeiro/BaixaNotaCompraService.java` - baixa manual, lote e reversao.
- `src/main/java/br/com/salome/core/application/financeiro/FinanceiroRuleValidator.java` - validacoes financeiras criticas.
- `src/main/java/br/com/salome/core/security/CurrentUserContext.java` - fronteira de usuario corrente e permissoes.

### Testes existentes
- `src/test/java/br/com/salome/core/application/contaspagar/validation/GestaoPagamentosStrictComparatorTest.java` - base de validacao estrita.
- `src/test/java/br/com/salome/core/application/financeiro/BaixaNotaCompraServiceTest.java` - cobertura de baixa e reversao.
- `src/test/java/br/com/salome/core/application/notacompra/NotaCompraFoundationTest.java` - cobertura de regras de `NotaCompra`.
- `src/test/java/br/com/salome/core/security/DevelopmentCurrentUserContextTest.java` - cobertura de contexto de usuario.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GestaoPagamentosValidationService` e classes em `application/contaspagar/validation`: podem sustentar comparacoes de dados e relatorio de divergencias.
- `DocumentoEntradaService`, `ProdutoNotaCompraService`, `DuplicataNotaCompraService` e `RateioNotaCompraService`: fronteiras naturais para montar checklist de operacoes por secao.
- `BaixaNotaCompraService`: fronteira natural para baixa manual, lote e reversao.
- `NotaCompraRuleOrigins` e `FinanceiroRuleOrigins`: ativos importantes para rastrear origem das regras na evidencia de homologacao.
- `docs/baixa-contas-pagar-regras-migradas.md`: documento pronto para alimentar parte da matriz de paridade da baixa.

### Established Patterns
- Views Vaadin chamam Services; Services chamam Repositories/Adapters; SQL fica em `infrastructure.legacy`.
- Operacoes financeiras multi-tabela devem ser transacionais.
- Regras criticas financeiras devem ter origem documentada e teste.
- Divergencias contra o legado devem ser registradas explicitamente, nao suavizadas.
- Evidencias da migracao ficam versionadas em `.planning/phases/` e docs de suporte ficam em `docs/`.

### Integration Points
- Criar `11-HOMOLOGATION.md` ou equivalente no diretorio da fase para registrar checklist e resultado.
- Usar os services de `notacompra` e `financeiro` como superficie de validacao, evitando validar diretamente pela View.
- Confirmar no banco alvo seguro/homologacao a inexistencia de `contaspagar` e a presenca de `notacompraduplicatas`.
- Se necessario, criar teste ou verificacao que documente que `ContasPagar.*` nao entra no escopo operacional porque sua tabela nao existe.

</code_context>

<specifics>
## Specific Ideas

- O usuario delegou a estrategia de homologacao por paridade ao agente: usar a recomendacao tecnica e nao prolongar a discussao.
- O achado fornecido pelo usuario afirma que `contaspagar` nao existe em `bck.tronbr.com/salome2_rp`, que o DAO `ContasPagarData` usa exclusivamente essa tabela e que nenhuma classe Java instancia diretamente `ContasPagar.java`.
- O achado tambem afirma que a tela "Contas a Pagar (103)" visivel no menu e `NotaCompraDuplicatasConsulta.java`, baseada em `notacompraduplicatas` com `notacompra`.
- `ContasPagarFornecedorFilialBean` permanece relevante apesar do nome, porque guarda `NotaCompraDuplicatasBean` e e usado para agrupamento por fornecedor/filial.

</specifics>

<deferred>
## Deferred Ideas

- Criar uma feature nova de lancamento avulso de contas a pagar fora de `NotaCompra` seria nova capacidade de produto, porque a tabela `contaspagar` nao existe no banco analisado. Se o negocio quiser isso, deve virar fase propria com schema versionado e decisao explicita.

</deferred>

---

*Phase: 11-Homologar paridade operacional do Contas a Pagar*
*Context gathered: 2026-05-18*
