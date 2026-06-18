# ADR-001: Regras de Negócio e SQL Direto nos Controllers

## Data
2026-06-08 (Extraído retroativamente)

## Status
Aceito (Legado)

## Contexto
Durante o desenvolvimento do ERP legado (Swing), percebeu-se que a extração completa de regras de negócio para a camada de serviço (ou DAOs) gerava um excesso de classes anêmicas e complexidade de navegação entre pacotes (`view` -> `controller` -> `service` -> `dao`). Ao mesmo tempo, agregações complexas (como cálculo do peso total de uma viagem) exigiam queries altamente específicas e otimizadas que não se encaixavam nos métodos padronizados CRUD das classes `Data`.

## Decisão
Foi decidido concentrar a orquestração e as **regras de negócio acopladas a banco de dados diretamente nos métodos da camada Controller**.
Quando um controller precisa de uma query específica (joins, agregações, subqueries), ele mesmo abre a conexão via `Conecta.getCon().prepareStatement()`, executa a query e faz o map das colunas para os Value Objects (VOs) específicos para as telas (ex: `ViagemColetaVO`).

Os DAOs (camada `model.data`) ficaram restritos apenas às operações estritamente ligadas ao CRUD padrão (`ler`, `lista`, `incluir`, `salvar`, `excluir`) de uma entidade individual.

## Consequências
- **Positivas:** Reduziu a proliferação de DAOs e manteve as queries complexas próximas da lógica de apresentação (Swing Worker). Facilita encontrar o SQL que alimenta uma tela específica.
- **Negativas:** Quebra o isolamento da arquitetura MVC. O controller conhece a infraestrutura de banco de dados (imports de `java.sql.*`), dificultando testes unitários, reaproveitamento de código em outras interfaces e migração para ORM ou Web. Causou inchaço nos controllers centrais (ex: `ViagemController.java` com +2000 linhas).
