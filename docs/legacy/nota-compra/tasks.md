# Tarefas de Reimplementação — Módulo nota-compra

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

| ID | Descrição da Tarefa | Fonte no Legado | Status | Confiança |
|----|---------------------|-----------------|--------|-----------|
| **TSK-NC-01** | Implementar serviço de inserção de `NotaCompra` com validação de dados básicos (fornecedor e valor). | `NotaCompraController.java` | To Do | 🟡 |
| **TSK-NC-02** | Implementar lógica de geração de parcelas (duplicatas) a partir das condições de pagamento da Nota. | `NotaCompraDuplicatasController.java` | To Do | 🟡 |
| **TSK-NC-03** | Implementar transacionalidade no rateio de custos da Nota para o Centro de Custo adequado. | `NotaCompraRateioController.java` | To Do | 🟡 |
| **TSK-NC-04** | Desenvolver API/Use-Case para baixa de duplicata, com injeção de dependência do serviço de contas bancárias (extrato). | `PagamentoCaixaController.java` | To Do | 🟡 |
| **TSK-NC-05** | Implementar regra de autorização `verificaPermissaoAlteraCaixa()` antes de realizar a baixa bancária. | `PagamentoCaixa.java` | To Do | 🟢 |

**Critério de Pronto Geral:** 
- A API REST correspondente permite criar uma nota, gerar duplicatas e baixá-las impactando o saldo da conta simulada, tudo sob cobertura de testes de integração, replicando o comportamento das antigas classes `Data`.
