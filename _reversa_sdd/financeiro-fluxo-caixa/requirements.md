# Requisitos - Modulo financeiro fluxo de caixa

> Gerado em 2026-06-08. Escopo: mapeamento financeiro do legado e modulo web local no `salome-core`.

## Regras confirmadas pelo codigo

| Regra | Origem legado | Tabelas |
| --- | --- | --- |
| A baixa de despesa de nota compra fica em duplicata com `datapagamento`, `valorpago` e `idExtrato`. | `NotaCompraDuplicatas.java`, `NotaCompraDuplicataBaixa.java`, `NotaCompraDuplicatasData.java`, `NotaCompraDuplicatasTable.java` | `notacompra`, `notacompraduplicatas`, `extrato` |
| O rateio da nota compra carrega filial, centro de custo e plano de contas/centro de custo. | `NotaCompraRateioData.java`, `NotaCompraRateioTable.java` | `notacomprarateio`, `centrocusto`, `planocontascentrocusto`, `planocontas` |
| Lancamentos manuais/aprovados/baixados de caixa carregam banco, fornecedor, centro de custo, plano de contas e situacao. | `PagamentoCaixa.java`, `AprovacaoCaixa.java`, `PagamentoCaixaData.java`, `PagamentoCaixaTable.java` | `pagamentocaixa`, `caixa`, `extrato` |
| A baixa em dinheiro do pagamento caixa grava `CaixaBean` com `tipoMovimento = Saida` e `idPagamentoCaixa`, criando a origem efetiva no caixa. | `PagamentoCaixa.java` handlers de baixa, `CaixaData.java`, `CaixaTable.java` | `caixa`, `pagamentocaixa` |
| O extrato bancario possui valor, historico, data, debito/credito, banco, plano de contas/centro de custo, duplicata de nota compra e fatura. | `Extrato.java`, `ExtratoData.java`, `ExtratoTable.java` | `extrato`, `banco`, `planocontascentrocusto` |
| A fatura possui banco, cliente, emissao, data limite, vencimento, nosso numero e plano de contas/centro de custo. | `NotaServicoFatura.java`, `FaturaController.java`, `FaturaData.java`, `FaturaTable.java` | `fatura`, `conhecimento`, `extrato` |
| O tomador usado no faturamento e calculado por `tipoPagamento`: destinatario em FOB; caso contrario consignatario quando existir, senao emitente. | `NotaServicoFatura.java`, consultas de selecao e totalizacao nas linhas que usam `IF(notaservico.tipoPagamento = 'Destinatario (FOB)', ...)` | `conhecimento`/`notaservico`, `cliente` |
| Plano de contas contem classificacao, tipo e DMR para agrupamento gerencial. | `PlanoContasTable.java` | `planocontas`, `planocontascentrocusto` |

## Regras de negocio do modulo

| ID | Regra | Status |
| --- | --- | --- |
| FIN-01 | Despesas previstas sao duplicatas/lancamentos a pagar ainda sem baixa. | Confirmado por campos de baixa; criterios finos por situacao seguem dados reais. |
| FIN-02 | Despesas realizadas incluem tudo que foi pago/baixado, sem excecao, vindo de nota compra, pagamento caixa, caixa e extrato. | Confirmado por escopo do usuario; quando `caixa.idPagamentoCaixa` existe, o fluxo usa `caixa` como origem realizada e evita duplicar `pagamentocaixa`. |
| FIN-03 | Lancamentos avulsos do extrato sem vinculo com duplicata devem entrar como despesas quando forem debitos de juros, tarifa, IOF ou despesa bancaria solta. | Confirmado por escopo do usuario; extrato vinculado a `notacompraduplicatas.idExtrato` nao entra como avulso. |
| FIN-04 | `ContasPagar*` deve ser ignorado mesmo existindo no codigo. | Confirmado por escopo do usuario. |
| FIN-05 | Receitas realizadas sao baixas de faturas, excluindo banco 34 e tomador Expresso Salome. | Confirmado por escopo do usuario; identificacao do cliente feita em consulta read-only por cadastro `cliente`, comparando `idCliente`, razao/fantasia e CNPJ normalizado. |
| FIN-06 | Receitas previstas incluem faturas abertas e CT-es emitidos ainda nao faturados, excluindo cancelados, inutilizados, cortesia e tomador Expresso Salome. | Parcialmente confirmado por campos em `conhecimento`; lista final de situacoes deve ser validada por dados reais. |
| FIN-07 | CT-es emitidos de 01 a 15 vencem dia 30; de 16 a 31 vencem dia 15 do mes seguinte, salvo regras inferidas por historico de clientes principais. | Inferido pelo negocio; requer consulta historica. |
| FIN-08 | Para mes fechado, previsto e calculado por vencimento no periodo e realizado por data de baixa no periodo; a diferenca de recebimento indica juros/outros recebimentos quando positiva e aberto/abatimento quando negativa. | Confirmado por alinhamento do usuario em 2026-06-08. |
| FIN-09 | Saldo de banco/caixa deve aparecer como posicao financeira separada do fluxo do periodo, usando a mesma origem da tela de extrato. | Confirmado por `Extrato.java#getSaldo` e `v_saldobancariotalao`. |
| FIN-10 | Fluxo de caixa bancario considera apenas os bancos reais de movimento: 41 Caixa Economica Federal, 36 Sicredi, 15 Brasil 5256-6, 40 Santander, 18 Itau 00153-8 e 37 Itau 41430-4. | Confirmado por regra do usuario em 2026-06-09. |
| FIN-11 | BMA Capital e OPERA/Operacao sao bancos de desconto/cessao, nao bancos de fluxo; nao entram em saldo nem movimento bancario. | Confirmado por regra do usuario em 2026-06-09. |
| FIN-12 | Contas de caixa em dinheiro entram apenas pelas despesas lancadas em `caixa`: Caixa Motorista, Caixa Bau, Caixa Cam, Caixa Diversos, Caixa Rib e Caixa SPO. | Confirmado por regra do usuario em 2026-06-09. |
| FIN-13 | Banco/caixa `Nao informado` nao deve aparecer nos agrupamentos nem na tabela operacional do fluxo de caixa. | Confirmado por regra do usuario em 2026-06-09. |
| FIN-14 | TED, transferencia e saque nao devem entrar como despesa avulsa isolada; se houver transferencia entre contas, precisa tratar saida e entrada como par para nao distorcer o caixa. | Confirmado por regra do usuario em 2026-06-09. |

## Lacunas controladas

- CNPJ definitivo da Expresso Salome: a consulta atual descobre o cadastro por razao/fantasia e propaga por `idCliente`/CNPJ normalizado; validar o CNPJ oficial em amostra real.
- Lista exata de situacoes/flags de cortesia e inutilizacao no CT-e: mapear por amostras reais.
- Historico de acumulacao/vencimento por Anjo, Akzo, Dovac, Sherwin, Eucatex, Romabar e outros clientes principais.
