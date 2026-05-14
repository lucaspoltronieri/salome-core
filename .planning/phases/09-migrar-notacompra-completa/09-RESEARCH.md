# Phase 9: Migrar NotaCompra completa - Research

**Researched:** 2026-05-14
**Status:** Ready for planning

## Research Question

What do we need to know to plan Phase 9 well?

Phase 9 is the first write-enabled financial migration for `NotaCompra`. The plan must preserve legacy behavior, keep `salome-legacy` untouched, write only through application services and legacy adapters, and make the Vaadin UI operational without turning Swing event handlers into the new architecture.

## Sources Read

- `AGENTS.md`
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md`
- `.planning/phases/09-migrar-notacompra-completa/09-CONTEXT.md`
- `.planning/phases/07-criar-tela-contas-a-pagar-somente-leitura/07-01-PLAN.md`
- `.planning/phases/08-validar-dados-da-tela-web-contra-o-legado/08-01-PLAN.md`
- `docs/architecture/salome-core-architecture.md`
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`
- `.planning/codebase/USUARIO-ACESSO-MAPA.md`
- `.planning/codebase/TESTING.md`
- `src/main/java/br/com/salome/core/**`
- `salome-legacy/view/NotaCompra.java`
- `salome-legacy/view/NotaCompraDuplicatas.java`
- `salome-legacy/view/NotaCompraProdutos.java`
- `salome-legacy/view/NotaCompraRateio.java`
- `salome-legacy/controller/NotaCompraController.java`

## Current salome-core State

- The current operational screen is `GestaoPagamentosView`, routed at `/`, and it is still read-only.
- `DocumentoEntradaView` exists only as a placeholder route at `/documento-entrada`.
- Existing code already follows the desired boundary for read paths:
  - `ui.contaspagar.GestaoPagamentosView`
  - `application.contaspagar.GestaoPagamentosService`
  - `application.contaspagar.GestaoPagamentosRepository`
  - `infrastructure.legacy.contaspagar.LegacyGestaoPagamentosRepository`
- Security has a minimal `CurrentUserContext`, `CurrentUser`, and roles: `ROLE_FINANCEIRO_LEITURA`, `ROLE_FINANCEIRO_OPERACAO`, `ROLE_ADMIN`.
- Phase 8 validation code exists under `application.contaspagar.validation`, which should be reused conceptually for final parity checks, but Phase 9 should introduce `notacompra` packages instead of expanding `contaspagar` into write use cases.

## Legacy Origins That Must Shape the Plan

### NotaCompra header

Origin:
- `salome-legacy/view/NotaCompra.java`
- `NotaCompra.btnSalvarActionPerformed(...)`
- `NotaCompra.btnExcluirActionPerformed(...)`
- `NotaCompra.formWindowOpened(...)`
- `NotaCompra.chkFiltrar1ActionPerformed(...)`
- `NotaCompra.importaNFe(...)`
- `NotaCompra.importaCTe(...)`
- `salome-legacy/controller/NotaCompraController.java`
- `NotaCompraController.existeNotafiscal(...)`
- `salome-legacy/model/data/NotaCompraData.java`
- Table: `notacompra`

Observed behavior:
- Header save validates required Swing labels, then builds `NotaCompraBean`.
- Required fields include fornecedor, data emissao, data entrada, data escrituracao, modelo NF, serie NF, nota fiscal, tipo pagamento, tipo frete, filial, rateio, valor nota and related NF-e fields where applicable.
- For models `55`, `57`, `62`, `66`, chave de acesso is required; chave shorter than 44 digits is rejected.
- Protocolo shorter than 15 digits is rejected when present.
- Chave/protocolo are valid only for models `55`, `57`, `62`, `66`.
- Duplicate note is blocked by `NotaCompraController.existeNotafiscal(idAtual, fornecedor, serieNF, numeroNF, modeloNF)`.
- New note inclusion is blocked when `dataEntrada` is before company `fechamentoPagar`.
- Include/edit commits through `BancoDados.commit()` and rolls back on failure.
- Delete requires selected note and confirmation, then calls `NotaCompraController.excluir(idNotaCompra)`.

Planning implication:
- Header save should be a separate application command with explicit validation and tests.
- The plan must add a repository method equivalent to `existeNotafiscal(...)`.
- Delete cannot simply mirror `NotaCompra.btnExcluirActionPerformed(...)`; Phase 9 context requires a stricter guard against paid/extrato-linked duplicatas before deleting the full note.

### Duplicatas

Origin:
- `salome-legacy/view/NotaCompraDuplicatas.java`
- `NotaCompraDuplicatas.btnSalvarActionPerformed(...)`
- `NotaCompraDuplicatas.btnExcluirActionPerformed(...)`
- `NotaCompraDuplicatas.btnGerarDuplicatasActionPerformed(...)`
- `NotaCompraDuplicatas.btnSalvarRetencaoActionPerformed(...)`
- `salome-legacy/model/data/NotaCompraDuplicatasData.java`
- Tables: `notacompraduplicatas`, `extrato`
- Procedure: `sp_compraGerarDuplicatas`

Observed behavior:
- Duplicata save validates required fields and rejects `vencimento` before note `dataEmissao`.
- Duplicata delete is blocked when `txtDataPagamento` is present.
- Legacy baixa writes `datapagamento`, `valorpago` and `idExtrato`, but full baixa belongs to Phase 10.
- `btnGerarDuplicatasActionPerformed(...)` validates vencimentos, data base, data base not before emissao and nonzero compra value, then calls `CALL sp_compraGerarDuplicatas(...)`.
- Retencao can be saved separately for a selected duplicata.

Planning implication:
- Duplicatas need their own section save command and tests.
- Phase 9 may support manual duplicata create/edit/delete and possibly duplicata generation through the legacy procedure, but must not implement baixa.
- Any duplicata with `datapagamento` or `idExtrato` must lock normal note editing/deleting unless the explicit XML-after-payment exception applies.

### Produtos

Origin:
- `salome-legacy/view/NotaCompraProdutos.java`
- `NotaCompraProdutos.btnSalvarActionPerformed(...)`
- `NotaCompraProdutos.btnExcluirActionPerformed(...)`
- `NotaCompraProdutos.populaProduto()`
- `NotaCompraProdutos.setPlanoContasCentroCustoView(...)`
- `salome-legacy/model/data/NotaCompraProdutosData.java`
- Tables: `notacompraprodutos`, `compraproduto`, `fornecedorprodutos`, `filial`, `planocontascentrocusto`, `planocontas`

Observed behavior:
- Product save validates many required fiscal/product fields, saves `notacompraprodutos`, then upserts supplier-product association in `fornecedorprodutos`.
- Product lookup uses `CompraProduto` and supplier-product relationship to prefill codigo, NCM, EAN, descricao and unidade.
- Plano/centro de custo is tied to product and fiscal/accounting behavior.
- Product delete requires confirmation.

Planning implication:
- Product write must be a separate command and transaction because it touches both item and supplier-product mapping.
- The first implementation should prioritize fields needed by the legacy table and the operational UX; any not-yet-rendered fiscal fields must still be preserved in command/read model if they are required by save.
- Tests should cover product save/upsert and mandatory fields.

### Rateio

Origin:
- `salome-legacy/view/NotaCompraRateio.java`
- `NotaCompraRateio.btnSalvarActionPerformed(...)`
- `NotaCompraRateio.btnExcluirActionPerformed(...)`
- `NotaCompraRateioController.lerValorRateio(int idNotaCompra)`
- `salome-legacy/model/data/NotaCompraRateioData.java`
- Table: `notacomprarateio`

Observed behavior:
- Rateio save recalculates remaining amount before validation.
- When editing, old rateio value is added back to the remaining amount before comparing.
- Save rejects value greater than remaining amount.
- Required fields include filial, centro de custo, plano group/subgroup and value.
- Delete requires confirmation.
- Total rateado origin is `SELECT SUM(valor) FROM notacomprarateio WHERE idNotaCompra = ?`.

Planning implication:
- Rateio needs a tested domain/service rule: `novoValor <= valorNota - somaRateios + valorAntigoQuandoEdicao`.
- Rateio save is independent from header/products/duplicatas and must not be blocked by temporary mismatch in other sections.

### Security and current user

Origin:
- `salome-legacy/view/NotaCompra.java`: default filial from `UsuarioController.getIdFilial(Conecta.getUsuario())`.
- `.planning/codebase/USUARIO-ACESSO-MAPA.md`
- Current code: `CurrentUserContext`, `SecurityRoles`.

Observed behavior:
- The legacy snapshot does not show a centralized permission gate for `NotaCompra` CRUD.
- Legacy uses global `Conecta.getUsuario()` plus `UsuarioController` for filial, banco, perfil and scattered permissions.
- Current `salome-core` has a provisional development user and generic finance roles.

Planning implication:
- Phase 9 should gate write commands through `ROLE_FINANCEIRO_OPERACAO` or `ROLE_ADMIN` as provisional equivalent, and document the gap.
- Filial default/filter should come from `CurrentUserContext.filialId`, not from Swing/`Conecta`.
- The XML-after-payment exception needs its own explicit permission method/role check in the service, even if implemented initially as `ROLE_ADMIN` or a named finance operation authority.

## Architecture Recommendations

Create a new feature family under `notacompra`:

- `ui.notacompra`
  - `DocumentoEntradaView`
  - `DocumentoEntradaDetalhesView` or a detail component composed by the main route
- `application.notacompra`
  - `DocumentoEntradaService`
  - `NotaCompraCommandService`
  - `ProdutoNotaCompraService`
  - `DuplicataNotaCompraService`
  - `RateioNotaCompraService`
  - `NotaCompraPermissionService`
- `domain.notacompra`
  - read models for list/detail/sections
  - commands for each section
  - validation results and lock status
  - value objects for totals and blocked state
- `infrastructure.legacy.notacompra`
  - `LegacyNotaCompraRepository`
  - `LegacyNotaCompraProdutoRepository`
  - `LegacyNotaCompraDuplicataRepository`
  - `LegacyNotaCompraRateioRepository`
  - lookup repositories for fornecedor, filial, produto, plano/centro

Keep command boundaries section-scoped:

- `salvarCabecalho`
- `salvarProduto`
- `excluirProduto`
- `salvarDuplicata`
- `gerarDuplicatas`
- `excluirDuplicata`
- `salvarRateio`
- `excluirRateio`
- `excluirNota`
- `importarOuVincularXml`

Use transactions at service level for each command. Do not put SQL or validation rules in Vaadin.

## UX Recommendations

- Replace the placeholder `/documento-entrada` with the operational first screen.
- Preserve a dense operational view:
  - main document list,
  - right/detail panel for selected document,
  - lower or tabbed sections for products, duplicatas and rateio.
- Provide section-local save buttons close to each section, matching decisions D-03 through D-06.
- Expose total divergence as informational status, not a blocker:
  - valor nota,
  - soma produtos,
  - soma duplicatas,
  - soma rateio.
- When locked by paid/extrato-linked duplicata, open read-only with a clear reason and show any permitted XML linkage action separately.

## Testing Strategy

Minimum Phase 9 tests:

- Header save rejects duplicate note by fornecedor/serie/numero/modelo.
- Header save rejects invalid chave/protocolo/model combinations.
- New note rejects `dataEntrada` before `fechamentoPagar`.
- Duplicata save rejects vencimento before emissao.
- Duplicata delete rejects paid/extrato-linked row.
- Note delete rejects any paid/extrato-linked duplicata.
- Rateio rejects value greater than remaining amount, including edit case where old value is added back.
- Product save creates/updates supplier-product mapping in the same transaction boundary.
- Section save does not touch unrelated sections.
- Permission tests for unauthorized write and XML-after-payment exception.
- UI/service boundary guard: no SQL/JDBC in `ui`.

## Risks

- `NotaCompra` legacy behavior is broad: XML, products, fiscal fields, duplicatas, rateio and supplier mapping intersect.
- Database schema is legacy MySQL and may contain nullable/zero values that must be handled conservatively.
- Current security is provisional; permission parity may require more mapping later.
- Product fiscal fields are numerous; an overly small command could lose data.
- Full XML import may be too large if treated as full fiscal parser work; plan should provide a narrow import/vincular shell if parser implementation is not yet feasible.
- Delete behavior in legacy header does not visibly check duplicata baixa; Phase 9 context requires stricter service guard before deleting.

## Planning Direction

Plan Phase 9 as multiple waves:

1. Build `notacompra` domain, repositories, command ports, lock/status rules and tests.
2. Replace `DocumentoEntradaView` placeholder with operational list/detail and header save.
3. Implement product, duplicata and rateio section commands with section-local saves and tests.
4. Implement delete, XML-after-payment exception, audit/log preservation, final UI states and safety verification.

This decomposition keeps the phase complete while making each wave reviewable.

## RESEARCH COMPLETE
