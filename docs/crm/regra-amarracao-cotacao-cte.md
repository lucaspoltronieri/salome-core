# Regra de amarração entre cotação e CT-e

## Regra principal

Para relacionar uma cotação do legado a um CT-e, considerar inicialmente:

1. CNPJ do remetente igual;
2. CNPJ do destinatário igual;
3. CT-e emitido no mesmo dia ou depois da data da cotação e não cancelado;
4. proximidade de data, valor do frete, natureza, volumes, peso e valor das
   notas fiscais.

Peso, quantidade, valor da NF, frete e filial podem ser alterados entre a
cotação e a emissão. Por isso não são critérios obrigatórios quando as partes
coincidem.

## Aprovação automática da cotação pelo CT-e (a partir da v1.6.0)

O Hub deixa de depender do clique em "Aprovar" no legado. A cada polling (30s)
`HubCrmCteApprovalService` lê os CT-es emitidos nos últimos 3 dias e, quando um
deles casa com uma cotação, aprova a cotação **no legado** com o usuário MySQL
`crm_api` (única escrita do Hub no legado). O ganho no ArpaSuite continua saindo
pelo fluxo normal de cotações, no mesmo ciclo.

Critérios (`CteQuoteMatcher`), decididos com o Lucas em 10/09/2026:

1. **Pagador igual** — obrigatório. No CT-e, pagador é o destinatário quando
   `tipoPagamento` contém DESTINAT e FOB; senão o emitente. Remetente e
   destinatário podem ser diferentes dos da cotação (ficam só como divergência).
2. CT-e emitido **no dia da cotação ou até 30 dias depois**, não cancelado
   (`cteCancelado` vazio e `situacao` diferente de Cancelada/Inutilizada).
3. **Peso** (soma de `conhecimentonotasfiscais.pesoNf`) igual ao da cotação.
4. **Valor da NF** (soma de `valorNF`) igual ao da cotação.
5. **Frete** (`conhecimento.valorTotal`) igual ao `totalFrete` da cotação.
   Exceção: se a cotação tem cubagem e o frete do CT-e é **menor** (cubagem
   esquecida na emissão), aprova mesmo assim quando peso e NF batem.

"Igual" aceita diferença de até 1% ou 1 unidade (kg / R$), o que for maior.
Volumes e natureza não são critério.

Quais cotações podem ser aprovadas:

| Responsável | Status | O que acontece |
|---|---|---|
| Fernanda/Jaci | ABERTA | aprova no legado; card vira ganho no ArpaSuite |
| Fernanda/Jaci | NÃO APROVADA | reverte para APROVADA; card sai de perdido para ganho |
| Carlos e demais | ABERTA | aprova só no legado; nada vai ao ArpaSuite |

A gravação replica a tela `CotacaoAprovacao`: `status='APROVADA'`, `statusData`,
`statusHora`, `tipoAprovacao='Outro'`, `contatoAprovacao='HUB CRM - CT-e <número>/<série>'` e uma linha no
`log` no formato do legado com o usuário `crm_api`. O `UPDATE` só vale se o status
ainda for o lido (`AND status=?`); se alguém mexeu no meio, registra
`CONCORRENCIA` e não sobrescreve.

Um CT-e aprova no máximo uma cotação e uma cotação é aprovada por no máximo um
CT-e. Havendo mais de uma candidata, vence a de data mais próxima da emissão e
depois a de frete mais parecido; empate exato fica `AMBIGUO` para conferência.
Cancelamento do CT-e depois da aprovação não desfaz nada.

O resultado fica em `salome_hub_crm.hub_crm_cte_match` e no `hub_crm_event`
(`entity_type='CTE'`), e aparece na tela do Hub nas abas **Aprovação por CT-e**
e **Logs**. Liga/desliga por `SALOME_HUB_CRM_AUTO_APPROVAL_ENABLED`; senha do
`crm_api` em `SALOME_HUB_CRM_LEGACY_WRITE_PASSWORD`.

### Lote de limpeza (v1.8.0, pedido do Lucas em 11/09/2026)

Na aba **Lote** da tela do Hub, `HubCrmBatchService` roda uma vez sobre todo o
histórico:

1. aprova toda cotação ABERTA (ou NÃO APROVADA da Fernanda/Jaci) que tenha CT-e
   correspondente pelas mesmas regras acima, procurando CT-es desde 01/01/2021;
2. toda cotação **ABERTA criada antes de 31/08/2026 sem CT-e** vira **NÃO
   APROVADA** com o motivo **Preço** (`naoAprovacaoPreco='Sim'`, descrição "Sem
   CT-e emitido para a cotação (lote Hub CRM dd/mm/aaaa)", linha do `crm_api` no
   `log`). As da Fernanda/Jaci viram **Perdido — Preço alto** no ArpaSuite nos
   ciclos seguintes.

**ArpaSuite só é atualizado, nunca contaminado (v1.8.1):** quando o status foi
alterado pelo próprio Hub (lote ou aprovação pelo CT-e), a cotação só mexe no
ArpaSuite se **já tiver card**: ganho ou perdido no card existente. Sem card, o
Hub não cria organização, pessoa nem card; registra `SEM_CARD_ARPA` e segue. O
lote também pode ser disparado uma única vez na inicialização definindo
`SALOME_HUB_CRM_BATCH_TOKEN` (o token executado fica em
`hub_crm_checkpoint('lote_token')` e não roda de novo).

Cotações empatadas no mesmo CT-e (AMBIGUO) não são aprovadas nem marcadas como
perdidas. Cotações a partir de 31/08/2026 sem CT-e ficam como estão. O botão
**Simular** mostra a lista sem gravar nada; **Executar** grava.

## Fallback com destinatário divergente

Quando não houver candidato com os dois CNPJs, permitir uma segunda busca por:

- mesmo remetente;
- mesmo peso ou volume, com tolerância operacional;
- mesmo valor total das notas fiscais ou valor muito próximo;
- mesma natureza quando disponível;
   - emissão no mesmo dia ou posterior à cotação;
- CT-e não cancelado.

Esse vínculo nunca deve ser tratado como correspondência perfeita. Registrar o
status `ASSOCIADO_COM_ALERTA_DESTINATARIO_DIVERGENTE`, guardar o destinatário
da cotação e o destinatário do CT-e e encaminhar para validação. O candidato
mais próximo deve ser escolhido pela pontuação, mas a divergência deve ficar
visível.

## Casos confirmados pelo usuário

| Cotação | CT-e | Observação |
|---:|---:|---|
| 15265 | 296083 | Destinatário divergente; usar remetente e dados físicos/valor da NF como evidência. |
| 15299 | 296156 | Destinatário divergente; dados principais coincidem. |
| 15294 | 83864 | CT-e informado pelo usuário; validar por remetente/destinatário e dados da carga. |
| 15281 | 317921 | Destinatário divergente; volumes, peso, valor da NF e frete coincidem. |
| 15296 | — | Não existe CT-e localizado. |

Os números acima devem ser validados também por data, série, chave e demais
campos antes da persistência definitiva no Hub CRM.

## Casos especiais de correção na timeline do Cubo

- Se houver uma mensagem posterior corrigindo o número da cotação, usar o
  número corrigido mais recente.
- `15261` corrigida para `15281`.
- `25359` corrigida operacionalmente para `15359`.

## Idempotência e auditoria

O Hub CRM deve guardar o identificador da cotação, `idConhecimento`, número,
série, chave do CT-e, pontuação, critérios usados, divergências e status da
validação. O legado permanece somente leitura.

## Relatório operacional de comissão

O relatório deverá receber como parâmetros:

- período inicial e final;
- usuário/vendedor do Cubo.

Antes de montar qualquer aba ou totalizador, filtrar os cards pelos dois
critérios abaixo, em conjunto:

- a data do status deve estar dentro do período solicitado (para `Ganho`,
  usar a data de ganho/aprovação; para `Perdido`, a data de perda; para cards
  abertos, a última movimentação). As datas inicial e final são inclusivas;
  tecnicamente, consultar até o início do dia seguinte ao final informado;
- o estágio atual do card deve ser exatamente `Proposta Enviada`.

Cards de outros estágios não entram na relação, nas abas de revisão ou nos
totalizadores, mesmo que estejam dentro do período ou tenham status de ganho
no Cubo.

Para o período e vendedor informados, consultar os cards do Cubo com status
`won` (Ganho) e usar a data `winDate` como data de aprovação no Cubo. Para cada
card, apresentar:

- título do card;
- ID do card;
- valor do Cubo;
- número da cotação informado na timeline, usando a correção mais recente;
- status do Cubo em português (`Ganho`, `Perdido` ou `Aberto`) e data do ganho;
- status da cotação no legado (`APROVADA`, `NÃO APROVADA` ou `ABERTA`);
- número, valor e data de emissão do CT-e, quando localizado;
- observações e divergências do cruzamento.

A ordem do relatório deve ser:

1. registros com CT-e localizado, ordenados pela data do ganho no Cubo;
2. registros sem CT-e localizado, também ordenados pela data do ganho.

O totalizador deve informar:

- quantidade de cotações ganhas no Cubo;
- valor total dos cards no Cubo;
- quantidade de CT-es localizados;
- valor total dos CT-es localizados;
- diferença entre o valor do Cubo e o valor dos CT-es.

Uma cotação ganha no Cubo com status `NÃO APROVADA` ou `ABERTA` no legado deve
ser destacada como pendência operacional para orientar o vendedor a concluir
ou corrigir a aprovação no legado. Esse status não deve ser alterado
automaticamente pelo Hub.

O relatório é somente leitura e serve como apoio à comissão: ganhar no Cubo
não significa, por si só, que o frete foi emitido. A comissão deve permitir a
separação entre ganho sem CT-e, ganho com CT-e e ganho com pendência de status
no legado.

## Auditoria complementar de cards nao ganhos

Para identificar vendas que podem ter sido fechadas sem que o vendedor tenha
marcado o card como `won` no Cubo, executar no mesmo periodo e vendedor uma
segunda consulta com todos os cards cujo status seja diferente de `won`
(`lost` ou `open`).

1. Extrair o numero da cotacao da timeline. Se houver mensagem posterior de
   correcao, usar o numero corrigido mais recente.
2. Consultar a cotacao no legado e registrar seu status (`APROVADA`,
   `NAO APROVADA`, `ABERTA`) sem alterar o legado.
3. Procurar CT-e nao cancelado, priorizando remetente e destinatario da
   cotacao, emitido na data ou depois da cotacao e com proximidade de valor,
   peso, volumes, natureza e valor de NF.
4. Se houver mesmo remetente e destinatario, mas o CT-e for anterior à data da
   cotação, não associar nem considerar o CT-e no relatório. O registro pode
   ser mencionado na observação como histórico não elegível.
5. Cards sem numero de cotacao ficam em relacao separada para revisao manual;
   nao devem gerar consulta ambigua no legado.

O resultado deve separar CT-e associado no dia ou após a cotação e ausência de
CT-e elegível. Essa auditoria não altera status no Cubo nem no legado e serve
para localizar fretes potencialmente fechados fora do fluxo formal de
aprovação.

Quando o valor do CT-e for diferente do valor da cotação, destacar a célula do
valor do CT-e com fundo de alerta no Excel e manter a divergência na coluna de
observação.

## Indicadores do pagador no relatorio de comissao

Na aba principal do relatorio de cotacoes ganhas, o pagador do frete e
determinado pelo `tipoPagamento` do CT-e: quando o valor contem
`DESTINATARIO` e `FOB`, usa-se `idClienteDestinatario`; nos demais casos,
usa-se `idClienteEmitente`.

Depois da coluna `Observação`, a aba principal deve apresentar as duas colunas
adicionais abaixo:

- `Tabela Preço`: preencher `sim` quando existir pelo menos um registro em
  `tabelapreco` com `idCliente` igual ao pagador. Caso contrario, preencher
  `nao`. Tabelas gerais, sem vinculo especifico ao cliente, nao contam como
  tabela comercial cadastrada para este indicador.
- `CTes Ultimos 6 meses`: contar CT-es nao cancelados do mesmo pagador, com
  `cteEmissao` dentro dos seis meses anteriores até a data final do relatório,
  inclusive. Para o relatório de 07/07/2026 a 31/07/2026, a janela usada foi
  de 31/01/2026 a 31/07/2026. Em novas consultas, recalcular a janela a partir
  da data final informada.

Esses indicadores sao somente leitura e nao alteram o cadastro do legado.
