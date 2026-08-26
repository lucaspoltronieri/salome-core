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

No catalogo atual do ArpaSuite, `Carga especial/dedicada` esta cadastrado com
o erro de digitacao `Carga epecial/dedicada`. O adaptador aceita esse alias,
mas a regra de negocio e as anotacoes mantem a grafia correta.

`naoAprovacaoQualidade` e `naoAprovacaoSemMotivo` sao campos antigos e ficam
fora da automacao.

## Idempotencia

Cada cliente e unico por CNPJ e cada cotacao e unica por `idCotacao`. O Hub
guarda IDs externos, hash do snapshot e eventos com chave unica no schema
`salome_hub_crm`. Reinicios e repeticoes do polling nao podem criar novamente
uma organizacao, pessoa, card, timeline ou mensagem ja confirmada.

O vinculo entre as pontas e persistido em `hub_crm_quote` imediatamente depois
de localizar ou criar o card: `legacy_quote_id` (unico) aponta para `deal_id`.
Alteracoes posteriores da mesma cotacao sempre atualizam esse card e so geram
uma nova timeline quando o hash dos dados mudou. Antes de criar um card, o Hub
tambem procura o `idCotacao` no campo personalizado `Base de Cotacao`, para
recuperar o vinculo caso uma chamada externa tenha concluido antes da gravacao
local. Um novo card de cotacao so pode ser criado para outro `idCotacao`. Na
ausencia de vinculo pelo ID, a busca por CNPJ pode reaproveitar o card aberto
mais recente em qualquer estagio do funil (Carteira, Lead, Contato,
Diagnostico, Negociacao ou outro). O card nao sera reaproveitado quando ja
estiver amarrado no banco do Hub a outra cotacao.

Ao marcar ganho ou perda, o Hub envia o `status` e, na perda, o motivo. A versao
atual da API rejeita `winDate` e `lostDate` com erro HTTP 422 mesmo nos formatos
descritos na documentacao; por isso esses campos nao sao enviados. O ArpaSuite
grava sua data tecnica no momento da integracao, enquanto a data e hora reais
do legado permanecem registradas na anotacao da timeline.

As transicoes de ganho e perda sao idempotentes: depois que o evento de status
fica `PROCESSADO`, o polling nao envia novamente a mesma alteracao ao
ArpaSuite. Isso evita que o historico do card receba uma linha a cada 30
segundos.

A observacao detalhada da cotacao somente e criada quando `totalFrete` for
maior que zero. Salvar a cotacao antes de calcular o frete pode atualizar o
controle interno e o card, mas nao gera timeline sem valores. Quando o frete
for calculado e salvo, a mudanca do snapshot cria a observacao completa.

A chave idempotente da observacao usa apenas o conteudo visivel da propria
timeline. Alteracoes de status, data de aprovacao ou outros campos tecnicos que
nao mudem esse texto nao criam uma segunda observacao. O snapshot tecnico do
card e o evento de ganho/perda continuam separados. Registros anteriores a
essa regra sao indexados no primeiro polling sem republicar a observacao.
