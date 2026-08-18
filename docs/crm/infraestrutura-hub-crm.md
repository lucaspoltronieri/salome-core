# Infraestrutura do Hub CRM

## Decisao de hospedagem

O Hub CRM sera implementado dentro do mesmo processo Spring Boot do
`salome-core`, compartilhando a mesma porta e a mesma infraestrutura de
execucao do Core Financeiro. A separacao sera feita por modulo e por rotas
web, sem criar uma segunda aplicacao ou uma segunda porta.

Na VPS, o Spring Boot escuta internamente em `127.0.0.1:8788` e a entrada publica
existente do Core Financeiro continua em `:8787`. Assim, o endereço público do painel é
`http://corefinanceiro.salome.com.br:8787/hub-crm/`, sem expor uma nova porta do Java.

Rotas futuras do Hub CRM devem ficar sob um contexto proprio, por exemplo
`/hub-crm/...`, enquanto as rotas financeiras existentes permanecem
inalteradas. O agendador de leitura do legado, a integracao com a API do Cubo
e o frontend do Hub CRM devem respeitar essa separacao interna.

Essa decisao reduz consumo de memoria e simplifica a hospedagem na Hostinger.
O Hub CRM deve continuar isolado em pacotes, configuracoes e logs proprios
para evitar impacto funcional no Core Financeiro.

O estado da integracao fica no schema MariaDB exclusivo `salome_hub_crm`.
O MySQL legado continua somente leitura e nao recebe tabelas, campos ou
marcadores da integracao.

## Rotas implementadas

- `/hub-crm/`: painel operacional;
- `/api/hub-crm/status`: saude e totalizadores;
- `/api/hub-crm/clientes`, `/cotacoes` e `/eventos`: auditoria;
- `/api/hub-crm/acoes/*`: validacao, sincronizacao e carga inicial;
- `/api/hub-crm/public/cotacoes/{id}/pdf`: PDF com assinatura e expiracao.

## Padrao do PDF de cotacao

O PDF e gerado pelo Hub CRM com o logotipo oficial da Expresso Salome no
cabecalho. O titulo, o numero e a barra inferior do cabecalho usam a cor preta.
O numero exibido em destaque e sempre o `id` da cotacao no legado;
o mesmo identificador tambem e repetido na secao de dados da cotacao para
preservar a rastreabilidade em impressao ou recorte do documento.

O modelo de referencia validado usa dados reais da cotacao legada `15580` e
fica em `output/pdf/modelo-cotacao-15580.pdf`.

## Segredos

O token ArpaSuite e a chave HMAC nao podem ser versionados. Devem ser injetados
pelas variaveis `SALOME_HUB_CRM_ARPA_API_KEY` e
`SALOME_HUB_CRM_MEDIA_SIGNING_KEY`. A chave fornecida durante o desenvolvimento
deve ser rotacionada antes da producao.
