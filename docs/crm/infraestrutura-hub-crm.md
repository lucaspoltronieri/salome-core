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
