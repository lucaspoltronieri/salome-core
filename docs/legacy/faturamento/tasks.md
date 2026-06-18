# Tarefas de Reimplementação — Módulo faturamento

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

| ID | Descrição da Tarefa | Fonte no Legado | Status | Confiança |
|----|---------------------|-----------------|--------|-----------|
| **TSK-FAT-01** | Implementar serviço de geração de Fatura validando a unicidade de sacado (cliente) para todos os CT-es incluídos. | `FaturaController.java` | To Do | 🟡 |
| **TSK-FAT-02** | Implementar geração de arquivo de Remessa CNAB (padrão Febraban 240/400). | `FaturaController.java` (Exportação) | To Do | 🟡 |
| **TSK-FAT-03** | Implementar parser posicional para ler o arquivo de Retorno Bancário (CNAB). | `FaturaRetornoController.java` | To Do | 🟡 |
| **TSK-FAT-04** | Implementar lógica de conciliação e baixa automática, iterando pelos títulos do arquivo de retorno e debitando o extrato do banco. | `FaturaBaixaController.java` e `FaturaRetornoController.java` | To Do | 🟡 |

**Critério de Pronto Geral:** 
- O sistema de faturamento deve ser capaz de receber IDs de CT-e, gerar a Fatura, exportar o CNAB de remessa, e, simulando um retorno bancário, processar o arquivo CNAB de retorno baixando a fatura com impacto de crédito na tabela correspondente ao Extrato.
