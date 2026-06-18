# Tarefas de Reimplementação — Módulo viagem

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

| ID | Descrição da Tarefa | Fonte no Legado | Status | Confiança |
|----|---------------------|-----------------|--------|-----------|
| **TSK-VIA-01** | Desenvolver rotina para inserção e atualização de Viagem, gerindo status (Pendente, Trânsito, etc). | `ViagemController.java` | To Do | 🟢 |
| **TSK-VIA-02** | Implementar serviço de vinculação e desvinculação de CT-es, e atualizar o peso total dinamicamente na viagem. | `ViagemController.java` | To Do | 🟢 |
| **TSK-VIA-03** | Desenvolver Client API para emissão e consulta de CIOT na Pamcard para viagens envolvendo terceiros. | `ViagemController.java` (Integração) | To Do | 🟢 |
| **TSK-VIA-04** | Construir regra de cancelamento de viagem que reverta o status dos CT-es vinculados de volta para estoque (Armazém). | `ViagemController.java` | To Do | 🟢 |

**Critério de Pronto Geral:** 
- O backend expõe uma interface que permite montar a Viagem (associando Veículo e Motorista), acoplar N Conhecimentos à Viagem e submeter à Pamcard. Cancelamento desfaz a viagem inteira transacionalmente.
