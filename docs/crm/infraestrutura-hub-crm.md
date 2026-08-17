# Infraestrutura do Hub CRM

## Decisao de hospedagem

O Hub CRM sera implementado dentro do mesmo processo Spring Boot do
`salome-core`, compartilhando a mesma porta e a mesma infraestrutura de
execucao do Core Financeiro. A separacao sera feita por modulo e por rotas
web, sem criar uma segunda aplicacao ou uma segunda porta.

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

## Segredos

O token ArpaSuite e a chave HMAC nao podem ser versionados. Devem ser injetados
pelas variaveis `SALOME_HUB_CRM_ARPA_API_KEY` e
`SALOME_HUB_CRM_MEDIA_SIGNING_KEY`. A chave fornecida durante o desenvolvimento
deve ser rotacionada antes da producao.
