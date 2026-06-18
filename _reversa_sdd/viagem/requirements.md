# Requisitos — Módulo viagem

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Visão Geral
O módulo **viagem** é o core logístico da transportadora. Ele agrupa coletas, transferências e entregas, gerenciando a relação com o motorista, o veículo e parceiros de frete (Pamcard). Controla adiantamentos, pedágios, acerto final de contas e status operacional da carga em trânsito. 🟢

## 2. Requisitos Funcionais (RF)

| ID | Requisito | Regra de Negócio / Origem | Confiança |
|----|-----------|---------------------------|-----------|
| **RF-01** | Controle de Status de Viagem | A viagem deve transitar pelos status: Pendente, Em Trânsito, Aguardando Acerto e Finalizada. | 🟢 |
| **RF-02** | Agrupamento de Conhecimentos | Uma viagem pode consolidar múltiplos CT-es de entrega e transferência, calculando o peso total embarcado. | 🟢 |
| **RF-03** | Adiantamento e Pedágio | O sistema deve calcular o valor do frete a pagar ao motorista, subtraindo adiantamentos já pagos e somando valores de Vale Pedágio (obrigatório). | 🟡 |
| **RF-04** | Integração de Frete Eletrônico | Viagens com motoristas terceiros devem registrar o contrato de frete via integração com a Pamcard (CIOT). | 🟢 |
| **RF-05** | Cancelamento | O cancelamento de uma viagem devolve os CT-es vinculados para o status "Armazém". | 🟢 |

## 3. Requisitos Não Funcionais (RNF)

| ID | Requisito | Restrição / Evidência no Código | Confiança |
|----|-----------|---------------------------------|-----------|
| **RNF-01** | Consistência Operacional | Não é permitido finalizar uma viagem sem que os valores de adiantamento, pedágio e saldo final estejam calculados e conciliados. | 🟡 |

## 4. Matriz MoSCoW

| Funcionalidade | Prioridade | Justificativa |
|----------------|------------|---------------|
| Controle Operacional de Viagem | **Must** | Sem viagem o caminhão não sai. |
| Integração Pamcard / CIOT | **Must** | Exigência legal (ANTT) para pagamento de motoristas autônomos. |
| Agrupamento Inteligente | **Should** | Facilita a consolidação, mas pode ser feita manualmente. |

## 5. Critérios de Aceitação (Gherkin)

**Cenário: Cancelamento de viagem pendente (Caminho Feliz)**
- **Dado** que uma Viagem com 2 CT-es associados está com status "Pendente"
- **Quando** o usuário confirmar a exclusão/cancelamento da Viagem
- **Então** a Viagem muda para "Cancelada"
- **E** os 2 CT-es são liberados da viagem e voltam ao status "Armazém".
