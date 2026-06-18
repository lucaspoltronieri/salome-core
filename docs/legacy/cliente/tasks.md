# Tarefas de Reimplementação — Módulo cliente

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

| ID | Descrição da Tarefa | Fonte no Legado | Status | Confiança |
|----|---------------------|-----------------|--------|-----------|
| **TSK-CLI-01** | Construir endpoint de CRUD para Clientes e múltiplos endereços. | `ClienteController.java` | To Do | 🟢 |
| **TSK-CLI-02** | Implementar serviço utilitário para retornar a quantidade de horas (ETA) dado um ID de cliente ou CEP de entrega. | `ClienteController.java` | To Do | 🟢 |
| **TSK-CLI-03** | Adicionar rotina de verificação de Situação Cadastral (via Sintegra ou WebService Sefaz) no cadastro. | `ClienteController.java` | To Do | 🟡 |

**Critério de Pronto Geral:** 
- O serviço permite buscar os dados cadastrais do cliente e expõe um método otimizado `getHorasSetor(idCliente)` que responde em tempo O(1) com a estimativa baseada nas faixas de CEP.
