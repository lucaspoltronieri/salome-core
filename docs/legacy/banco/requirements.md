# Requisitos — Módulo banco

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Visão Geral
O módulo **banco** é central para a saúde financeira do sistema. Ele gerencia o cadastro de contas bancárias da empresa e as contas dos parceiros (motoristas/proprietários), além de servir como o repositório de lançamentos e baixas através da entidade `Extrato`. Este módulo consolida todas as entradas financeiras (`Faturamento`) e saídas (`PagamentoCaixa`). 🟡

## 2. Requisitos Funcionais (RF)

| ID | Requisito | Regra de Negócio / Origem | Confiança |
|----|-----------|---------------------------|-----------|
| **RF-01** | Cadastro de Bancos e Contas | O sistema deve permitir o cadastro de contas bancárias próprias (Caixa interno/Bancos oficiais) associadas às filiais, contendo saldo atual e códigos Febraban. | 🟡 |
| **RF-02** | Registro de Transações (Extrato) | Todo pagamento (débito) ou recebimento (crédito) deve gerar um lançamento rastreável na tabela de Extrato. | 🟡 |
| **RF-03** | Cálculo de Saldo | O saldo da conta bancária deve refletir fielmente a soma algébrica de todas as entradas e saídas associadas a ela. | 🟡 |
| **RF-04** | Conciliação Interna | A conferência de saldos e lançamentos do Extrato contra o banco externo é estritamente visual/manual. Não há OFX. | 🟢 |
| **RF-05** | Integração Pamcard | Algumas contas correntes podem pertencer a motoristas e ser usadas como destino para pagamentos via CIOT/Pamcard. | 🟢 |

## 3. Requisitos Não Funcionais (RNF)

| ID | Requisito | Restrição / Evidência no Código | Confiança |
|----|-----------|---------------------------------|-----------|
| **RNF-01** | Segurança Transacional | Todo débito ou crédito originado em outros módulos (`Faturamento`, `NotaCompra`) precisa persistir o `Extrato` dentro da mesma transação do banco de dados JDBC (`Conecta`). | 🟢 |
| **RNF-02** | Isolamento por Filial | Visualização de saldos de caixa só é permitida se a conta bancária pertencer à filial logada do usuário, exceto matriz. | 🟡 |

## 4. Matriz MoSCoW

| Funcionalidade | Prioridade | Justificativa |
|----------------|------------|---------------|
| Lançamento Automático em Extrato | **Must** | Necessário para evitar divergências financeiras. Operações no caixa *têm* que refletir no extrato. |
| Controle de Contas Internas/Caixa | **Must** | Essencial para emissão de boleto (que conta recebe?) e pagamento (que conta paga?). |
| Cadastro manual de lançamentos | **Should** | Permite registrar tarifas bancárias e estornos não cobertos automaticamente pelas faturas. |

## 5. Critérios de Aceitação (Gherkin)

**Cenário: Lançamento de Crédito (Caminho Feliz)**
- **Dado** que uma Fatura no valor de R$ 100,00 foi paga (status mudou para "Baixada")
- **Quando** o `FaturaBaixaController` finaliza a operação
- **Então** o sistema insere um registro de "Crédito" no `Extrato` associado ao `Banco` da fatura
- **E** o saldo total da conta bancária é acrescido em R$ 100,00.

**Cenário: Lançamento de Débito (Caminho Feliz)**
- **Dado** que o `PagamentoCaixa` de uma duplicata de R$ 50,00 é aprovado
- **Quando** a operação for salva
- **Então** o sistema insere um registro de "Débito" no `Extrato` da conta correspondente
- **E** o saldo bancário da conta é reduzido em R$ 50,00.
