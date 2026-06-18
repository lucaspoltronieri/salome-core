# Matriz de Impacto das Especificações (Spec Impact Matrix)

> Gerada pelo Arquiteto em 2026-06-08

Esta matriz mapeia como as alterações nos domínios do sistema afetam uns aos outros, guiando a análise de impacto durante refatorações e migrações.

| Domínio / Componente Modificado | Impacta Diretamente | Nível de Impacto | Razão / Regra de Negócio |
|---------------------------------|---------------------|------------------|--------------------------|
| **Tabela Cliente** | Conhecimento (CT-e) | 🔴 Alto | A emissão do CT-e exige que o CEP do cliente tenha < 8 dígitos e que o IBGE exista. Se a tabela/validação do cliente mudar, trava o faturamento. |
| **Status da Viagem** | Coleta | 🔴 Alto | Ao cancelar uma viagem, todas as coletas amarradas a ela devem voltar para o status "Pendente". |
| **Status da Viagem** | Conhecimento (CT-e) | 🔴 Alto | Ao cancelar uma viagem, todos os CT-es alocados nela (entregas e transferências) voltam para "Armazém". |
| **Bean de Motorista** | Integração Pamcard | 🔴 Alto | Pamcard exige `cartaoNumero`, `rntrc`, `nomeFavorecido`. Alterações na modelagem de motorista ou na tabela quebram a emissão do CIOT. |
| **Bean de Veículo** | Integração Pamcard | 🟡 Médio | Pamcard consulta proprietário do veículo na hora do CIOT. |
| **Conexão Banco (Conecta)** | Transações Gerais | 🔴 Alto | Como transações são controladas manualmente nas Views/Controllers através do JDBC, trocar a classe Conecta quebra todo o gerenciamento de locks do sistema inteiro. |
| **Endereço (Cidade/UF)** | Prazos de Entrega | 🟡 Médio | A região de rota baseia-se nos 5 dígitos do CEP (`clienteController`). Mudar formato de endereço quebra o agendamento de entregas. |
