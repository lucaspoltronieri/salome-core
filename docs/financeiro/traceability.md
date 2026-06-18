# Rastreabilidade - Financeiro fluxo de caixa

| Area | Tela/botao | Controller | Data/query | Table enum | Tabelas |
| --- | --- | --- | --- | --- | --- |
| Nota compra | `NotaCompra.java`, `NotaCompraDuplicatas.java`, `NotaCompraDuplicataBaixa.java` | `NotaCompraController`, `NotaCompraDuplicatasController`, `NotaCompraRateioController` | `NotaCompraData`, `NotaCompraDuplicatasData`, `NotaCompraRateioData` | `NotaCompraTable`, `NotaCompraDuplicatasTable`, `NotaCompraRateioTable` | `notacompra`, `notacompraduplicatas`, `notacomprarateio` |
| Pagamento/caixa | `PagamentoCaixa.java`, `AprovacaoCaixa.java`, tela 227 informada pelo usuario | `PagamentoCaixaController`, `CaixaController` | `PagamentoCaixaData`, `CaixaData` | `PagamentoCaixaTable`, `CaixaTable` | `pagamentocaixa`, `caixa` |
| Extrato bancario | `Extrato.java` | `ExtratoController` | `ExtratoData` | `ExtratoTable` | `extrato`, `banco` |
| Faturamento | `NotaServicoFatura.java`, `NotaServicoFaturaBaixa.java`, `NotaServicoFaturaRetorno.java` | `FaturaController`, `FaturaBaixaController`, `FaturaRetornoController` | `FaturaData`, `FaturaBaixaData`, `FaturaRetornoData` | `FaturaTable`, `FaturaBaixaTable`, `FaturaRetornoTable` | `fatura`, `faturabaixa`, `faturaretorno`, `extrato` |
| CT-e aberto | `Conhecimento.java`, telas CTe relacionadas | `ConhecimentoController`, `CteSpedController`, `CteinutilizacaoController` | `ConhecimentoData` | `ConhecimentoTable` | `conhecimento`, `cliente` |
| Classificacao | `PlanoContas.java`, `PlanoContasCentroCusto.java`, `CentroCusto.java` | `PlanoContasController`, `PlanoContasCentroCustoController`, `CentroCustoController` | `PlanoContasData`, `PlanoContasCentroCustoData`, `CentroCustoData` | `PlanoContasTable`, `PlanoContasCentroCustoTable`, `CentroCustoTable` | `planocontas`, `planocontascentrocusto`, `centrocusto` |

## Regras de exclusao

- `ContasPagar*`: fora do escopo por decisao de negocio.
- Banco 34: excluir de receita realizada como perdas e danos.
- Tomador Expresso Salome: excluir de receita prevista e realizada apos resolver o tomador pela regra do faturamento; no `salome-core`, a consulta read-only marca o cadastro por `cliente.razaoSocial`/`cliente.fantasia` e compara tambem por `cliente.idCliente` e CNPJ normalizado.

## Regra anti-duplicidade

- `caixa` x `pagamentocaixa`: a baixa em dinheiro gravada pela tela `PagamentoCaixa.java` cria `caixa.idPagamentoCaixa` com `tipoMovimento = Saida`; o dashboard considera esse registro como realizado em caixa e exclui o mesmo `pagamentocaixa` da consulta complementar para nao somar duas vezes.

## Bancos considerados no fluxo

- Bancos reais de movimento bancario: `41 - Caixa Economica Fed.`, `36 - Sicredi`, `15 - Brasil (5256-6)`, `40 - Banco Santander`, `18 - ITAU S/A`, `37 - ITAU S/A - 41430`.
- Bancos de desconto/cessao fora do fluxo: `BMA Capital` e `OPERA/Operacao`; quando aparecem em faturas abertas, a receita prevista continua existindo, mas nao classifica o fluxo como saldo/movimento desses bancos.
- Contas de caixa em especie entram somente como despesa lancada no `caixa`: Caixa Motorista, Caixa Bau, Caixa Cam, Caixa Diversos, Caixa Rib e Caixa SPO.
- `34 - Perdas e Danos` continua sendo lido apenas para marcar receita excluida e nao entra como recebimento.
