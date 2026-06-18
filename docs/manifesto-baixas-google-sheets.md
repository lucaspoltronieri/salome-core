# Mapa Atual de CT-es SJP para Google Sheets

## Objetivo

Automacao somente leitura que consulta o MySQL legado a cada 15 minutos e
publica em uma Google Sheet o mapa atual dos CT-es cuja regiao de destino
pertence a Expresso Salome - Sao Jose do Rio Preto.

Por padrao, entram somente CT-es emitidos a partir de `01/05/2026`
(`conhecimento.cteEmissao >= '2026-05-01'`).

## Origem no legado

- Regra de baixa de manifesto: `salome-legacy/view/BaixaManifesto.java`,
  `btnSalvarActionPerformed`.
- CT-es em armazem: `salome-legacy/view/CteArmazem.java`, `atualizaTabela`.
- Selecao de CT-es para transferencia:
  `salome-legacy/view/ViagemTransferenciaConhecimentoSelecao.java`,
  `atualizaTabela`.
- Viagem de transferencia: `salome-legacy/view/ViagemTransferencia.java`,
  `btnSalvarActionPerformed`.
- Viagem de entrega: `salome-legacy/view/ViagemEntrega.java`,
  `btnSalvarActionPerformed`.
- Baixa de entrega: `salome-legacy/view/BaixaEntrega.java`,
  `btnBaixarEntregasActionPerformed`.

Tabelas principais lidas: `conhecimento`, `conhecimentonotasfiscais`,
`cliente`, `cidade`, `filial`, `clienteregiao`, `clienteregiaosetor`,
`clienteregiaosetorlocal`, `viagemtransferencia`,
`viagemtransferenciaconhecimento`, `viagementrega`, `viagem`, `veiculo`,
`motorista`, `fornecedor`.

A previsao de entrega vem de `conhecimento.dataPrevistaEntrega`, gravada na
emissao do CT-e.

## Regra de destino SJP

O destino do CT-e e sempre a cidade do destinatario. Para todos os cenarios, o
batch usa `conhecimento.idClienteDestinatario` para obter o CEP/cidade do
destinatario e localizar setor/regiao em `clienteregiaosetorlocal`.

Nao entram nessa regra `idClienteRegiaoSetorRetirada` nem
`idClienteRedespacho`, porque eles nao definem a cidade destino do CT-e para
este mapa.

Entra no mapa apenas quando:

- a filial da regiao encontrada para o destinatario for Sao Jose do Rio Preto;
- `conhecimento.cteEmissao` for maior ou igual a data de corte configurada.

## Abas publicadas

- `Armazém SJP`: CT-es em `Armazem` cujo armazem atual e SJP. Inclui as
  colunas `Data entrada armazém` e `Hora entrada armazém`, preenchidas com
  `viagemtransferencia.dataBaixa` e `viagemtransferencia.horaBaixa` do ultimo
  manifesto baixado vinculado ao CT-e com destino SJP.
- `Em rota Entrega`: CT-es em `Em Viagem` com ultima entrega ativa
  (`viagementrega.entregaRealizada = 'Não'`) e regiao destino SJP.
- `CTe outros armazém`: CT-es em `Armazem`, com destino SJP, mas ainda em
  armazem de outra filial.
- `CTe Viagem p SJP`: CT-es em manifesto de transferencia `Em Viagem` para
  SJP, incluindo filial origem, placa, motorista e previsoes de saida/chegada.

Todas as abas incluem `previsao_entrega`. A coluna recebe destaque visual:
previsao igual ao dia atual fica em amarelo, e previsao anterior ao dia atual
fica em vermelho claro.

As abas antigas `Baixas Manifesto` e `Em Viagem` sao renomeadas para
`Armazém SJP` e `Em rota Entrega`. A aba oculta `_controle_manifestos` continua
existindo apenas por compatibilidade tecnica.

## Sincronizacao

A cada ciclo, a automacao substitui as linhas das quatro abas operacionais pelo
estado atual do banco legado.

O campo `conhecimento.situacao` guia o estagio (`Armazem` ou `Em Viagem`), mas
a separacao entre entrega e transferencia usa `viagementrega` e
`viagemtransferencia`, pois o legado grava `Em Viagem` nas duas rotinas.

Fluxo esperado:

- CT-e emitido em armazem fora de SJP aparece em `CTe outros armazém`.
- Ao entrar em manifesto de transferencia para SJP, sai de `CTe outros armazém`
  e aparece em `CTe Viagem p SJP`.
- Quando o manifesto e baixado, aparece em `Armazém SJP`.
- Quando sai para entrega, aparece em `Em rota Entrega`.
- Quando a entrega e baixada, ou quando o CT-e e finalizado/cancelado, ele some
  das abas operacionais.

## Configuracao

Variaveis:

```powershell
$env:SALOME_MANIFESTO_EXPORT_ENABLED="true"
$env:SALOME_MANIFESTO_EXPORT_CRON="0 */15 * * * *"
$env:SALOME_MANIFESTO_EXPORT_SPREADSHEET_ID="id-da-google-sheet"
$env:SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH="C:\dev\salome-core\google-service-account.json"
$env:SALOME_MANIFESTO_EXPORT_FILIAL_DESTINO_ID="2"
$env:SALOME_MANIFESTO_EXPORT_BATCH_SIZE="500"
$env:SALOME_MANIFESTO_EXPORT_DATA_CORTE="2026-05-01"
```

O `SALOME_MANIFESTO_EXPORT_FILIAL_DESTINO_ID` e opcional. Se nao for
informado, o sistema tenta localizar a filial Salome em cidade contendo
`Rio Preto`.

O `SALOME_MANIFESTO_EXPORT_DATA_CORTE` tambem e opcional. Se nao for informado,
o sistema usa `2026-05-01`.

## Execucao

O agendador roda dentro do proprio `salome-core` (`ManifestoBaixaExportScheduler`,
`@Scheduled` a cada 15 min) sempre que `SALOME_MANIFESTO_EXPORT_ENABLED=true`. Em
homologacao (Hostinger) o app roda como servico systemd; basta definir as variaveis de
ambiente acima.

Gerar o jar:

```bash
mvn package -DskipTests
```

Rodar localmente (com as variaveis de ambiente exportadas):

```bash
java -jar target/salome-core-0.0.1-SNAPSHOT.jar
```

Para rodar somente o agendador, sem servidor web, defina
`SALOME_WEB_APPLICATION_TYPE=none`.

## Seguranca

- Nao altera o legado.
- Nao altera banco de producao.
- Usa apenas `SELECT` no MySQL legado.
- A automacao fica desligada por padrao.
