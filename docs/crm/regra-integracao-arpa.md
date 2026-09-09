# Regra de integracao Legado x ArpaSuite

## Clientes

A consulta de candidatos e executada por
`LegacyHubCrmRepository.findEligibleClients`, somente leitura, sobre as tabelas
`cliente`, `cidade`, `estado`, `conhecimento`, `cnae`, `clientecontato` e
`clientesetor`. O corte e `cliente.idCliente >= 32001`.

A data historica enviada para a timeline e sempre `MIN(conhecimento.cteEmissao)`
dos CT-es em que o cliente aparece como destinatario sem pagar o frete. A data
de cadastro do cliente nao e usada pelo Hub CRM.

Quando a razao social de um empresario individual comecar por uma inscricao
numerica de 8, 11 ou 14 digitos, com ou sem mascara, o Hub remove essa inscricao
do nome apresentado no ArpaSuite. A regra vale para organizacao, pessoa, titulo
do card e nomes exibidos na observacao da cotacao. Por exemplo,
`63.110.705 REYNALDO LUIZ CERQUEIRA DE SOUZA` passa a ser
`REYNALDO LUIZ CERQUEIRA DE SOUZA`. Numeros que fazem parte de uma marca, como
`3M DO BRASIL` e `1001 FESTAS`, sao preservados. O CNPJ continua sendo enviado
integralmente no campo personalizado proprio e nao e alterado por essa regra.
Cards ja existentes tambem recebem a correcao, inclusive quando o cadastro nao
possui contato pessoal valido; nesse caso o Hub atualiza organizacao e card sem
criar uma pessoa artificial com o nome da empresa.

**Todo cadastro elegivel vira card na Carteira**, tenha ou nao contato pessoal
no legado. Havendo contato valido, o Hub cria a pessoa e amarra ao card; sem
contato (ausente, so numerico ou o contato interno `ERICK`), o card e criado
com `peopleName` igual a razao social curta do cliente — a API do ArpaSuite
recusa a criacao sem esse campo (`422 peopleName e obrigatorio`), entao a pessoa
do card e a propria empresa. Atualizacoes posteriores desse card continuam
usando `peopleId: null`, sem criar pessoa a partir do contato. Ate 09/09/2026 a regra era outra — cadastro novo
sem contato ficava com `sync_status=SEM_CONTATO` e sem card — o que deixou 81
cadastros qualificados fora da Carteira.

Quando o card gravado no Hub nao existe mais no ArpaSuite (apagado na mao, a API
responde 404), o Hub recria o card reaproveitando a organizacao e a pessoa ja
conhecidas, em vez de repetir o erro a cada polling.

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

## Resiliencia do polling

O cliente HTTP do ArpaSuite tem timeout de conexao (15s) e de leitura (60s). Sem
esses limites o JDK espera para sempre: em 09/09/2026 uma resposta que nunca
chegou em `GET /api/channels` deixou a thread `scheduling-1` parada por quatro
dias, sem erro novo no log e com o servico marcado como ativo. O checkpoint
ficou em 15765 e nenhuma cotacao de 05/09 em diante subiu.

Cada cotacao e isolada no laco, inclusive no ramo de reprocesso das que ja estao
integradas. Uma falha ali conta como `failed`, registra evento e nao muda o
status da cotacao (segue `INTEGRADO`, tenta de novo no proximo polling), mas nao
interrompe as demais nem impede o checkpoint de avancar. A sincronizacao de
clientes e a de cotacoes tambem sao independentes: uma falhando nao impede a
outra.

A consulta do canal de WhatsApp e cacheada por 5 minutos, em vez de uma chamada
por cotacao a cada polling.

## Titulo do card

O titulo do card e a razao social completa do cliente (no card de cotacao, a do
pagador), sem a inscricao numerica que alguns cadastros trazem na frente do nome
— `63.110.705 REYNALDO LUIZ CERQUEIRA DE SOUZA` vira
`REYNALDO LUIZ CERQUEIRA DE SOUZA`. Ate a versao 1.4.3 o titulo usava o nome
curto (`shortName`, no maximo tres palavras e 28 caracteres), que cortava nomes
como `AGROCENTER AGROPECUARIA E PET SHOP`. O nome curto continua valendo apenas
para a pessoa, nao para o card.

## Estagio do card de cotacao

O card de cotacao vive em **Proposta Enviada**: criado, ganho ou perdido. A
criacao e as atualizacoes ja gravavam `stageId`, mas o ganho e a perda enviavam
so o `status` — entao um card arrastado a mao para Negociacao era fechado como
ganho fora do lugar (visto em 09/09/2026 no card 2268977). Agora `markWon` e
`markLost` tambem enviam o estagio de proposta e trazem o card de volta.

Cards criados manualmente pelo time nao tem o campo personalizado de CNPJ nem o
de `Base de Cotacao`, entao o Hub nao consegue encontra-los: a busca por card
aberto usa o CNPJ do campo personalizado. Quando alguem cria o card na mao para
uma cotacao que o Hub tambem vai integrar, o funil fica com dois cards do mesmo
cliente — um manual e um da automacao. Isso nao e duplicacao gerada pelo Hub,
que mantem um card por `idCotacao`.
