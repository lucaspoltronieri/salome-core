# Tarefas de Reimplementação — Módulo banco

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

| ID | Descrição da Tarefa | Fonte no Legado | Status | Confiança |
|----|---------------------|-----------------|--------|-----------|
| **TSK-BNC-01** | Implementar serviço de registro de entrada/saída (Extrato), garantindo que seja transacional com os serviços chamadores (Faturamento/NotaCompra). | `ExtratoController.java` | To Do | 🟡 |
| **TSK-BNC-02** | Desenvolver API de totalização dinâmica (Saldos) para não depender de um campo de cache `saldoAtual` sujeito a drift, calculando baseado no histórico de extrato. | `BancoController.java` | To Do | 🟡 |
| **TSK-BNC-03** | Adicionar regra de visibilidade, bloqueando listagem de extrato de filiais que não correspondem à filial do usuário logado (exceto se for permissão global/matriz). | `Extrato.java` (View) | To Do | 🟢 |

**Critério de Pronto Geral:** 
- É possível lançar movimentações no extrato de um banco cadastrado e o saldo reflete exatamente a soma algébrica de `C - D`. Toda consulta respeita o escopo de `idFilial`.
