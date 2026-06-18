# ADR-002: Controle de Alterações via Dirty-Tracking Manual nos Beans

## Data
2026-06-08 (Extraído retroativamente)

## Status
Aceito (Legado)

## Contexto
O sistema Swing legado possui formulários complexos contendo mais de 200 campos (ex: `Conhecimento`, `Viagem`, `Cliente`). A cada clique no botão "Salvar", era ineficiente enviar um comando `UPDATE` contendo todas as 200 colunas para o banco de dados MySQL, especialmente sobre redes de baixa performance. Além disso, usar frameworks ORM pesados (como Hibernate/JPA) na época não era aderente à cultura da equipe, que preferia SQL puro (JDBC).

## Decisão
Foi implementado um **mecanismo manual de Dirty-Tracking nos JavaBeans (`model.bean`)**. 
Para cada campo da entidade, foi criado um atributo booleano de controle (`*Gravar`). Quando o método `setX(valor)` é invocado, o bean altera o valor e marca a flag `xGravar = true`.

Na hora de salvar, o Controller passa a instância atual e a original (`bean` e `beanAntigo`) para a camada `Data`. O método `salvar` do Data inspeciona quais flags estão ligadas para montar a query de `UPDATE` dinamicamente, atualizando **somente** os campos que sofreram alteração.

## Alternativas Consideradas
- **Usar JPA/Hibernate:** Rejeitado pelo peso de gerenciamento de sessões Swing e curva de aprendizado da equipe.
- **Comparação por Reflection:** Rejeitado por custo de performance na UI thread do Swing e complexidade de manutenção.

## Consequências
- **Positivas:** Geração de queries `UPDATE` hiper-otimizadas; controle estrito e explícito do que é modificado no banco de dados.
- **Negativas:** Dobrou a quantidade de atributos e getters/setters em todos os beans (ex: um bean de 200 campos tem 400 atributos locais). Impede o uso fácil de bibliotecas de mapeamento automático (MapStruct, Jackson) ou migrações diretas para Lombok sem reescrever a lógica do "Gravar".
