# Phase 13: Criar fluxo de caixa previsto operacional - Context

**Gathered:** 2026-05-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega a tela Vaadin de fluxo de caixa previsto do `salome-core`, com leitura operacional dos compromissos futuros ja mapeados no legado. O foco e mostrar valores projetados a partir de `NotaCompraDuplicatas`, `Extrato` e bancos/caixas ja migrados, com recorte por periodo e contexto operacional, sem criar regra de negocio pesada na View e sem liberar escrita, baixa ou reversao nesta fase.

O fluxo de caixa desta fase e um painel de previsao operacional, nao um modulo bancario novo. Ele deve reaproveitar os dados financeiros ja migrados, respeitar a origem legada de cada numero e permitir drill-down para as telas operacionais existentes quando o usuario precisar agir.

</domain>

<decisions>
## Implementation Decisions

### Escopo da previsao
- **D-01:** A fase 13 vai modelar previsao de caixa com base principal em `NotaCompraDuplicatas` e nos eventos de baixa ja representados por `Extrato`.
- **D-02:** O painel vai tratar o saldo bancario inicial como contexto operacional, usando `v_saldobancariotalao` quando precisar de referencia de abertura, mas nao vai depender disso como unica fonte da previsao.
- **D-03:** O resultado principal sera uma linha do tempo operacional com saldos projetados, titulos previstos e titulos ja realizados no periodo.

### Experiencia e navegacao
- **D-04:** A tela deve substituir o placeholder atual `fluxo-caixa` e manter o padrao Vaadin denso e operacional, sem aspecto de landing page.
- **D-05:** A fase nao vai executar baixa, reversao, inclusao ou pagamento. Qualquer acao sensivel deve navegar para a tela operacional responsavel.
- **D-06:** O usuario deve conseguir abrir o detalhe de itens previstos e ir para `GestaoPagamentosView`, `DocumentoEntradaView` ou `DocumentoEntradaDetalhesView` conforme o caso.

### Filtros e horizonte
- **D-07:** O periodo padrao sera o mes atual, com horizonte de previsao configuravel pelo usuario para revisar o curto prazo.
- **D-08:** Os filtros que fazem sentido reaproveitam a semantica do painel financeiro ja existente: periodo, filial, fornecedor, banco e plano de contas, com atualizacao manual e sem auto-refresh.
- **D-09:** O painel deve usar o contexto do usuario corrente quando ele existir para sugerir o recorte inicial, sem esconder a opcao de ver "Todos".

### Ordenacao e leitura
- **D-10:** A visualizacao principal deve priorizar a leitura por vencimento/data futura, com destaque para compromissos mais proximos e saldos projetados.
- **D-11:** A secao de previsao deve diferenciar o que ja foi pago do que ainda esta previsto, mas sem duplicar a logica de baixa; o status e derivado dos dados existentes.
- **D-12:** O painel deve continuar sem auto-refresh para evitar ruido em leitura operacional.

### the agent's Discretion
- O usuario pediu para seguir direto e usar a recomendacao tecnica, entao a decisao padrao e a versao mais completa e operacional dentro do escopo da fase.
- A composicao visual exata da linha do tempo, cards ou grade fica para o planner, desde que permanece densa, legivel e sem adornos de marketing.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Projeto e escopo
- `.planning/PROJECT.md` - contexto do produto, guardrails e foco no fluxo real `NotaCompraDuplicatas`/`NotaCompra`.
- `.planning/REQUIREMENTS.md` - requisito `CASH-01` e a trilha de paridade que sustenta a previsao.
- `.planning/ROADMAP.md` - meta e criterios de sucesso da fase 13.
- `.planning/STATE.md` - estado atual do projeto e historico recente das fases 11 e 12.

### Decisoes anteriores
- `.planning/phases/10-migrar-baixa-completa-de-contas-a-pagar/10-CONTEXT.md` - decisoes sobre baixa, banco, extrato, cheque/lote, reversao e origem de pagamento.
- `.planning/phases/11-homologar-paridade-operacional-do-contas-a-pagar/11-CONTEXT.md` - paridade, codigo morto `ContasPagar.*` e fluxo real baseado em `NotaCompraDuplicatas`.
- `.planning/phases/12-criar-dashboard-financeiro-operacional/12-CONTEXT.md` - padrao operacional de indicadores, filtros e drill-down reaproveitavel aqui.

### Mapas do legado
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md` - tabelas, campos, joins, queries de status/pagamento/saldo e riscos de inconsistencia.
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md` - classes, metodos, botoes, regras e origem de `NotaCompra`, duplicatas, baixa, extrato, banco, fornecedor, filial e plano de contas.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md` - usuario corrente, filial, banco/caixa e permissoes que podem afetar filtros/escopo.
- `.planning/codebase/ARCHITECTURE.md` - arquitetura observada no legado e acoplamentos que nao devem ser copiados.

### Regras e arquitetura do `salome-core`
- `docs/architecture/salome-core-architecture.md` - contrato `View -> Service -> Repository/Adapter`, governanca de schema e nota de correcao sobre `ContasPagar.*`.
- `docs/baixa-contas-pagar-regras-migradas.md` - regras ja migradas na fase 10 para baixa, lote/cheque, banco/saldo/caixa, reversao e governanca.
- `src/main/java/br/com/salome/core/application/financeiro/FinanceiroRuleOrigins.java` - catalogo atual de origens das regras financeiras.
- `src/main/java/br/com/salome/core/security/CurrentUserContext.java` - fronteira de usuario corrente para escopo/filial/permissao.
- `src/main/java/br/com/salome/core/security/CurrentUser.java` - modelo atual do usuario logado no `salome-core`.

### Codigo atual e pontos de integracao
- `src/main/java/br/com/salome/core/ui/placeholder/FluxoCaixaView.java` - placeholder que esta na rota e deve ser substituido.
- `src/main/java/br/com/salome/core/ui/financeiro/DashboardFinanceiroView.java` - referencia de layout, densidade visual e navegacao operacional.
- `src/main/java/br/com/salome/core/application/financeiro/DashboardFinanceiroService.java` - padrao de service de leitura operacional com origem legada.
- `src/main/java/br/com/salome/core/application/financeiro/DashboardFinanceiroRepository.java` - porta de repository para consultas legadas agregadas.
- `src/main/java/br/com/salome/core/infrastructure/legacy/financeiro/LegacyDashboardFinanceiroRepository.java` - exemplo de adapter JDBC isolando SQL.
- `src/main/java/br/com/salome/core/ui/contaspagar/GestaoPagamentosView.java` - destino natural de drill-down.
- `src/main/java/br/com/salome/core/ui/notacompra/DocumentoEntradaView.java` - destino operacional para documentos filtrados.
- `src/main/java/br/com/salome/core/ui/notacompra/DocumentoEntradaDetalhesView.java` - destino operacional para detalhe e correcao do registro.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `FluxoCaixaView`: ja existe como rota placeholder e e o ponto natural para substituir a experiencia.
- `DashboardFinanceiroView` e `DashboardFinanceiroService`: fornecem o padrao atual de leitura operacional, filtros e drill-down.
- `LegacyDashboardFinanceiroRepository`: mostra como isolar SQL/JDBC em `infrastructure.legacy`.
- `DashboardFinanceiroFiltro`, `DashboardFinanceiroStatus` e `DashboardFinanceiroRecorteTipo`: podem orientar a modelagem dos filtros e buckets da previsao.
- `FinanceiroRuleOrigins`: centraliza a referencia tecnica das regras financeiras ja catalogadas.
- `CurrentUserContext` e `CurrentUser`: base para recorte inicial por usuario, filial e caixa.

### Established Patterns
- Views Vaadin chamam Services; Services chamam Repositories/Adapters; SQL fica em `infrastructure.legacy`.
- Read models devem ser separados de commands de escrita.
- Regras financeiras criticas exigem origem documentada e teste quando virarem comportamento.
- O fluxo real do Contas a Pagar e `NotaCompraDuplicatas` com `NotaCompra`; `ContasPagar.*` e codigo morto ate prova contraria.
- Banco legado segue como fonte da verdade; nenhuma mudanca de schema sem Flyway versionado.

### Integration Points
- Criar a nova View em `ui.financeiro` ou reaproveitar a rota existente `fluxo-caixa`.
- Criar um service dedicado em `application.financeiro` para consolidar a previsao de caixa.
- Criar uma porta de repository e um adapter legado em `infrastructure.legacy.financeiro` para agregacoes sobre `notacompraduplicatas`, `notacompra`, `extrato`, `banco`, `fornecedor`, `filial`, `planocontascentrocusto` e `v_saldobancariotalao`.
- Integrar o drill-down com `GestaoPagamentosView`, `DocumentoEntradaView` e `DocumentoEntradaDetalhesView`.

</code_context>

<specifics>
## Specific Ideas

- O fluxo de caixa previsto deve ser payables-driven e operacional, nao uma suite completa de tesouraria.
- A experiencia deve continuar densa e acionavel, sem virar relatorio decorativo.
- O periodo padrao deve ser o mes atual, com possibilidade de ampliar o horizonte quando necessario para revisar o curto prazo.

</specifics>

<deferred>
## Deferred Ideas

- Influxo completo de caixa a partir de `contas a receber` e `faturamento` pertence a fases futuras.
- Integracoes bancarias, portal de pagamentos e acoes de mutacao continuam nas fases proprias e nas telas operacionais.

</deferred>

---

*Phase: 13-Criar fluxo de caixa previsto operacional*
*Context gathered: 2026-05-25*
