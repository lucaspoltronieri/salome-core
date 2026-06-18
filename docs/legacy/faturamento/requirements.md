# Requisitos — Módulo faturamento

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Visão Geral
O módulo **faturamento** lida com a ponta de recebimento da transportadora. Ele pega os Conhecimentos (CT-es) emitidos com pagamento "A Prazo" e os agrupa em **Faturas**. O módulo emite os boletos via arquivos remessa (CNAB) para os bancos e processa o retorno bancário para dar baixa automática nas faturas, creditando o caixa. 🟡

## 2. Requisitos Funcionais (RF)

| ID | Requisito | Regra de Negócio / Origem | Confiança |
|----|-----------|---------------------------|-----------|
| **RF-01** | Agrupamento de CT-es | O sistema deve permitir agrupar múltiplos Conhecimentos (CT-es) de um mesmo cliente pagador em uma única Fatura. | 🟡 |
| **RF-02** | Geração de Fatura | A fatura deve ser gerada com um valor bruto correspondente à soma dos CT-es inclusos, mais eventuais taxas ou impostos. | 🟡 |
| **RF-03** | Arquivo de Remessa (CNAB) | O sistema deve gerar arquivo remessa contendo os dados dos boletos das faturas para envio ao banco credenciado. | 🟡 |
| **RF-04** | Arquivo de Retorno Bancário | O sistema deve ler arquivos de retorno (CNAB) fornecidos pelo banco, conciliar o "nosso número", e baixar as faturas pagas automaticamente. | 🟡 |
| **RF-05** | Impactar Extrato Bancário | A baixa de uma fatura, seja manual ou por retorno, deve creditar o valor no extrato da conta bancária configurada na fatura. | 🟡 |

## 3. Requisitos Não Funcionais (RNF)

| ID | Requisito | Restrição / Evidência no Código | Confiança |
|----|-----------|---------------------------------|-----------|
| **RNF-01** | Tolerância a Falhas no CNAB | O processamento de arquivo de retorno (`FaturaRetornoController`) não deve interromper todo o lote caso um título apresente erro. Erros devem ser "logados" ou apresentados ao usuário sem rollback total. | 🟡 |
| **RNF-02** | Desempenho | Consultas que agregam centenas de CT-es para formar uma fatura (`FaturaController`) devem ser eficientes e usar paginação/limites na UI (SwingWorker). | 🟢 |

## 4. Matriz MoSCoW

| Funcionalidade | Prioridade | Justificativa |
|----------------|------------|---------------|
| Geração de Fatura de CT-es | **Must** | Sem isso, a transportadora não tem documento de cobrança agrupado para o cliente. |
| Processamento de Retorno CNAB | **Must** | Caminho crítico para a escalabilidade da baixa de títulos; baixa manual é inviável no longo prazo. |
| Geração de Remessa CNAB | **Must** | Exigido pelos bancos para emissão de boleto registrado. |

## 5. Critérios de Aceitação (Gherkin)

**Cenário: Baixa automática via Retorno Bancário (Caminho Feliz)**
- **Dado** que a Fatura 1234 tem status "Aberta" e um "Nosso Número" gerado
- **Quando** o usuário importa o arquivo de retorno CNAB contendo a confirmação de pagamento do boleto 1234
- **Então** o status da Fatura muda para "Baixada" (Paga)
- **E** o valor do pagamento é lançado como crédito no Extrato Bancário correspondente.

**Cenário: Geração de Fatura com clientes diferentes (Falha)**
- **Dado** o CT-e A (Pagador: Cliente 1) e CT-e B (Pagador: Cliente 2)
- **Quando** o usuário tenta gerar uma fatura única agrupando os dois CT-es
- **Então** o sistema bloqueia a ação, exigindo que todos os CT-es de uma fatura pertençam ao mesmo sacado (pagador).
