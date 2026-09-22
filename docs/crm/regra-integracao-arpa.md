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

**Telefones do card da Carteira (v1.18.0).** O card tem dois telefones: o da
pessoa (contato) e o campo personalizado **Telefone** da negociacao (320838),
que leva o telefone da empresa. Sem telefone da empresa, o campo repete o do
contato; sem contato, a pessoa recebe o da empresa. O celular `11965728450` (do
Erick, gravado em contatos de varios clientes, as vezes com o nome de outro
funcionario) nunca e usado, em nenhum campo; outro numero do mesmo contato vale.
Em 21/09/2026 os 335 cards abertos em Nao Pagantes foram preenchidos uma vez, com
as fontes nesta ordem: legado, Receita (BrasilAPI) e Google. 4 ficaram sem
telefone. Nos 6 cards ligados as pessoas ADRIANO LIMA DA SILVA e LARISSA
(compartilhadas entre empresas e com o numero do Erick) foi criada uma pessoa por
card.

**Observacao de transportes (v1.9.0).** A Carteira aparece no ArpaSuite como o
estagio **Nao Pagantes** (320394): clientes que recebem mercadoria com o frete
pago pelo remetente e que estao em prospeccao. Desde a v1.9.1, **so os cards que
estao no estagio Nao Pagantes** no momento da verificacao recebem a observacao,
por pedido do Lucas. Card que o comercial ja moveu para outro estagio fica com o
evento `FORA_DO_ESTAGIO` e nao recebe nada. Cada card recebe a observacao uma
vez, na mesma ideia dos cards de Pagantes:

- total de CT-es recebidos sem pagar frete desde 2020, com o peso, o valor da NF
  e o frete pago pelo remetente;
- o ultimo transporte: data, CT-e, origem, destino, volumes, peso, valor da NF,
  frete e tipo de pagamento;
- o link assinado (365 dias) do PDF com a relacao de todos os CT-es recebidos
  (`/api/hub-crm/public/nao-pagantes/{idCliente}/pdf`). O PDF e gerado na hora,
  entao traz tambem os CT-es emitidos depois da observacao.

Os CT-es sao os mesmos da regra do cadastro: o cliente e o destinatario e o
pagamento nao e Destinatario (FOB). Tambem valem os filtros do PDF de inativos:
CT-e autorizado, nao cancelado, sem cortesia e com situacao Finalizada, Em
Viagem ou Armazem. O Hub grava 25 cards por ciclo e registra o evento
`client:<cnpj>:transportes`, que impede a observacao de ser repetida. Uma falha da
API fica como `ERRO` e volta a ser tentada depois de um dia. Se o card tiver sido
apagado, o Hub marca o cliente como `REMOVIDO`.

Quando o card gravado no Hub nao existe mais no ArpaSuite (apagado na mao, a API
responde 404), o Hub **respeita a exclusao**: grava `sync_status=REMOVIDO`, limpa
o `deal_id`, registra o evento `CARD_REMOVIDO` e nao recria. Cadastro ou cotacao
nesse estado sai do fluxo — nao e reprocessado nem fica repetindo o 404. Quem
apagou decidiu que o card nao deve existir. Vale para os dois lados: cadastro
(carteira) e cotacao.

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

A partir da v1.6.0 a aprovacao da cotacao no legado pode vir do proprio Hub:
quando o CT-e correspondente e emitido, `HubCrmCteApprovalService` aprova a
cotacao com o usuario `crm_api` (regras em `regra-amarracao-cotacao-cte.md`).
Para o ArpaSuite nada muda: o Hub ve a cotacao `APROVADA` e marca o card como
ganho, com a anotacao "aprovada automaticamente pelo CT-e X". A nao aprovada de
Fernanda/Jaci que recebe CT-e depois volta para ganho do mesmo jeito.

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

### Baixa automatica sem tratativa do comercial (v1.19.0, decisao do Lucas em 22/09/2026)

`HubCrmSemTratativaService` roda uma vez por hora e baixa a proposta que ninguem
tratou. A proposta precisa cumprir todas as condicoes abaixo:

- cotacao da Fernanda ou da Jaci, ABERTA no legado e com card **aberto** no
  ArpaSuite;
- **10 dias ou mais** desde a data da cotacao;
- **nenhuma atividade no card** (ligacao, WhatsApp, reuniao, tarefa ou nota, nao
  cancelada) criada a partir da data da cotacao.

A atividade no card e o sinal de "falei com o cliente, aguardando aprovacao". A
anotacao do card nao serve para isso, porque a API do ArpaSuite so cria anotacoes
e nao deixa le-las.

O que o Hub faz:

- No legado, a cotacao vira NAO APROVADA com o motivo **Preco** e a descricao
  "Sem tratativa do comercial, baixado pelo legado". O legado nao tem um motivo
  proprio para isso.
- No ArpaSuite, o card fica perdido com o motivo **317833** "Sem tratativa do
  comercial, baixado pelo legado", e nao com "Preço alto". O evento
  `quote:<id>:sem-tratativa` faz o sync usar esse motivo e registrar uma anotacao.

A regra liga e desliga por `SALOME_HUB_CRM_SEM_TRATATIVA_ENABLED`. O prazo fica em
`SALOME_HUB_CRM_SEM_TRATATIVA_DAYS` (padrao 10) e o motivo em
`SALOME_HUB_CRM_SEM_TRATATIVA_LOST_REASON_ID` (padrao 317833).

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

## Campos personalizados do card de cotacao (v1.16.0)

Alem de CNPJ, Base de Cotacao, Rota, Tipo de Carga e Volume, o card da cotacao
recebe os dados do tomador do frete (destinatario no FOB, remetente nos demais):

- Segmento, Cidade e Estado: do cadastro do cliente no legado (`cliente` pelo
  CNPJ, segmento = descricao do CNAE). Sem cadastro, Cidade e Estado caem para
  os da propria cotacao; Segmento fica vazio.
- Telefone e E-mail: primeiro os da cotacao; sem eles, os do cadastro. Do
  e-mail vai o primeiro endereco do campo.
- Data de fechamento prevista (campo e `expectClosingDate`): `previsaoFechamento`
  da cotacao, somente quando informada.
- Origem nao e preenchida (lista sem opcoes no ArpaSuite).

Esses dados entram no hash da cotacao: a publicacao da v1.16.0 reprocessa uma
vez as cotacoes acompanhadas e preenche os cards ja existentes. O reprocesso
nao repete observacao, ganho, perda nem WhatsApp, e nao apaga o status final do
WhatsApp (ENVIADO, SEM_CONVERSA, ERRO).

**Pagador trocado depois do card criado** (ex.: cotacao salva CIF e depois
mudada para FOB): quando o CNPJ do card difere do pagador atual, o Hub liga o
card a empresa/pessoa do pagador novo em vez de renomear as do antigo.

**CNPJ do pagador invalido** (diferente de 14 digitos) **nao bloqueia** (v1.17.0,
regra do Lucas em 21/09/2026): a cotacao sobe com o CNPJ como esta, fica INTEGRADO e
recebe um aviso na coluna de erro (evento `CNPJ_INVALIDO`) com quantos digitos veio
e, se houver um unico cliente com a mesma razao social, o CNPJ do cadastro. Com
CNPJ invalido o Hub nao procura card aberto do cliente por CNPJ; cria o card da
cotacao. Corrigido o CNPJ no legado, o hash muda e o card e atualizado (e, se
for outra empresa, religado a ela) no proximo polling.

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

## PDF da cotacao pelo WhatsApp (v1.11.0)

Regra definida pelo Lucas em 17/09/2026 e implementada em `HubCrmQuoteWhatsappService`. O PDF
**so e enviado quando ja existe uma conversa aberta** com o cliente. **Nenhum template e
enviado.** Essa regra substitui a v1.10.0, que mandava o template "Atendimento Salome" quando a
janela estava fechada.

Conversa aberta e uma conversa do canal cujo `lastInboundAt` esta dentro das ultimas 24h, que e a
janela da Meta. A conversa vale para a cotacao quando cumpre uma destas condicoes, nesta ordem de
preferencia:

1. mesma pessoa do card (`peopleId`);
2. mesmo telefone do pagador, inclusive com ou sem o nono digito;
3. pessoa da mesma organizacao do card no ArpaSuite;
4. pessoa com o mesmo nome da empresa (razao social ou nome curto). Isso cobre o cliente que fala
   de outro numero.

O PDF e enviado dentro dessa conversa (`POST /api/messages/send` com `conversationId`). O Hub
consulta as conversas uma vez por minuto, com cache.

- **Sem conversa aberta:** a cotacao fica `AGUARDANDO_CONVERSA`, e o Hub procura de novo a cada
  ciclo. Se nada aparecer ate `wait-days` (3) dias depois da data da cotacao, o envio e encerrado
  sem o PDF (`SEM_CONVERSA`).
- **Janela fechada durante o envio (`window_closed`):** o Hub tenta de novo no ciclo seguinte.
- **Outro erro 4xx:** o envio e encerrado (`ERRO`).
- **Eventos:** `quote:<id>:whatsapp` (enviado) e `quote:<id>:whatsapp-encerrado`. Nada e
  reenviado.
- **Corte:** so entram cotacoes a partir de `first-quote-id` (15841).

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
