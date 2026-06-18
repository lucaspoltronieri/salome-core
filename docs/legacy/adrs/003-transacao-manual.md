# ADR-003: Transação e Pool de Conexão Manual

## Data
2026-06-08 (Extraído retroativamente)

## Status
Aceito (Legado)

## Contexto
O sistema Swing legado precisava manter o controle das transações ao alterar dados em múltiplas tabelas (ex: Cancelamento de Viagem altera Viagem, CT-es e Coletas na mesma operação). Sem frameworks como Spring (`@Transactional`) ou servidores de aplicação JEE (EJBs), o gerenciamento transacional precisava ser feito de forma explícita.

## Decisão
A equipe optou por **desabilitar o Auto-Commit da conexão JDBC principal (classe `Conecta`) e realizar transações manualmente nas ações da View e Controller**.
O ciclo de vida da transação funciona da seguinte forma:
1. A interface de usuário (Swing Event) dispara a ação no Controller.
2. O Controller realiza as validações e as chamadas sequenciais para os métodos `salvar()` das classes `Data`.
3. Se qualquer `salvar()` falhar, o Controller ou a View invoca explicitamente `BancoDados.rollback()`.
4. Caso tudo ocorra perfeitamente, invoca `BancoDados.commit()`.

## Alternativas Consideradas
- **Usar um Pool de Conexões (C3P0, Hikari):** Rejeitado em favor de uma única conexão global mantida aberta por cliente (Desktop Swing) ligada diretamente ao banco MySQL, devido ao número controlado de terminais internos rodando o ERP.

## Consequências
- **Positivas:** Simplicidade extrema e controle exato do momento do commit.
- **Negativas:** Forte acoplamento de infraestrutura nas lógicas de negócio. Risco alto de bloqueios (locks) no banco de dados se um terminal Swing travar ou demorar na execução de um `SwingWorker`, pois a transação fica presa pelo lado cliente. Dificulta muito a transição para um modelo Web sem estado.
