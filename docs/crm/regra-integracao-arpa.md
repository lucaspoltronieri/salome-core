# Regra de integracao Legado x ArpaSuite

## Clientes

A consulta de candidatos e executada por
`LegacyHubCrmRepository.findEligibleClients`, somente leitura, sobre as tabelas
`cliente`, `cidade`, `estado`, `conhecimento`, `cnae`, `clientecontato` e
`clientesetor`. O corte e `cliente.idCliente >= 32001`.

A data historica enviada para a timeline e sempre `MIN(conhecimento.cteEmissao)`
dos CT-es em que o cliente aparece como destinatario sem pagar o frete. A data
de cadastro do cliente nao e usada pelo Hub CRM.

## Cotacoes

Origem funcional observada no legado:

- tela e botoes: `view/Cotacao.java`;
- aprovacao: `view/CotacaoAprovacao.java`;
- nao aprovacao: versao implantada da tela `CotacaoNaoAprovacao`;
- controller: `controller/CotacaoController.java`;
- DAO/query: `model/data/CotacaoData.java`;
- tabela: `cotacao`.

O workspace contem uma versao anterior da tela de nao aprovacao, mas o schema
de producao foi conferido em leitura e possui as colunas usadas abaixo. O Hub
nao altera o codigo nem o schema legado.

O pagador e o remetente quando `tipoPagamento` representa CIF e o destinatario
quando contem simultaneamente `DESTINAT` e `FOB`.

## Motivos de perda

Somente os dez campos ativos sao considerados. Deve existir exatamente um com
valor `Sim`; zero ou mais de um enviam a cotacao para revisao.

| Coluna legado | Motivo ArpaSuite |
|---|---|
| `naoAprovacaoPreco` | Preço alto |
| `naoAprovacaoPrazo` | Prazo e janela ruins |
| `naoAprovacaoCargaEspecial` | Carga especial/dedicada |
| `naoAprovacaoForaPerfil` | Fora do perfil ideal |
| `naoAprovacaoSemCredito` | Sem crédito |
| `naoAprovacaoSemSeguro` | Sem Seguro |
| `naoAprovacaoArrependimentoFrete` | Arrependimento do frete |
| `naoAprovacaoProblemaColeta` | Problema na coleta |
| `naoAprovacaoInadimplencia` | Inadimplência |
| `naoAprovacaoConcorrente` | Perda para concorrente |

`naoAprovacaoQualidade` e `naoAprovacaoSemMotivo` sao campos antigos e ficam
fora da automacao.

## Idempotencia

Cada cliente e unico por CNPJ e cada cotacao e unica por `idCotacao`. O Hub
guarda IDs externos, hash do snapshot e eventos com chave unica no schema
`salome_hub_crm`. Reinicios e repeticoes do polling nao podem criar novamente
uma organizacao, pessoa, card, timeline ou mensagem ja confirmada.
