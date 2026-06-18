# Requisitos — Módulo nota-compra

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Visão Geral
O módulo **nota-compra** é responsável pelo registro financeiro de contas a pagar da transportadora. Ele gerencia as notas de compra emitidas pelos fornecedores, as duplicatas vinculadas a essas notas, o rateio de custos entre os centros de custo e a baixa financeira (pagamento) que impacta diretamente o fluxo de caixa. 🟡

## 2. Requisitos Funcionais (RF)

| ID | Requisito | Regra de Negócio / Origem | Confiança |
|----|-----------|---------------------------|-----------|
| **RF-01** | Registrar Nota de Compra | O sistema deve permitir o lançamento de notas fiscais de compra vinculadas a um fornecedor. | 🟡 |
| **RF-02** | Gerar Duplicatas | Ao registrar uma nota, o sistema deve calcular e gerar as parcelas (duplicatas) a pagar, definindo datas de vencimento e valores baseados nas condições de pagamento. | 🟡 |
| **RF-03** | Ratear Custos | O valor da nota deve ser rateado entre diferentes centros de custo (ex: manutenção, frota, despesas gerais) para apropriação contábil. | 🟡 |
| **RF-04** | Baixar Duplicatas (PagamentoCaixa) | O sistema deve permitir a baixa (pagamento) das duplicatas geradas. | 🟡 |
| **RF-05** | Impactar Extrato Bancário | A baixa do pagamento deve debitar o saldo do extrato bancário correspondente da filial responsável. | 🟡 |

## 3. Requisitos Não Funcionais (RNF)

| ID | Requisito | Restrição / Evidência no Código | Confiança |
|----|-----------|---------------------------------|-----------|
| **RNF-01** | Integridade Transacional | Operações de inserção de nota, duplicatas e rateios devem ocorrer no mesmo escopo transacional (`BancoDados.commit()`/`rollback()`) para evitar duplicatas órfãs. | 🟢 |
| **RNF-02** | Segurança de Alteração de Caixa | A baixa de duplicatas no `PagamentoCaixa` requer validação de permissão estrita (`verificaPermissaoAlteraCaixa()`) quando envolve outro caixa que não o principal da filial do usuário logado. | 🟢 |

## 4. Matriz MoSCoW

| Funcionalidade | Prioridade | Justificativa |
|----------------|------------|---------------|
| Registro de Nota com Fornecedor | **Must** | Caminho crítico para dar entrada na dívida. |
| Geração e Baixa de Duplicatas | **Must** | Caminho crítico para controle do fluxo de caixa negativo. |
| Rateio de Centro de Custo | **Should** | Importante para relatórios gerenciais e DRE, mas a operação principal pode ocorrer com centro padrão. |

## 5. Critérios de Aceitação (Gherkin)

**Cenário: Baixa de duplicata com sucesso (Caminho Feliz)**
- **Dado** que existe uma duplicata da Nota de Compra com status "Aberta"
- **Quando** o usuário informa os dados de pagamento (conta bancária, data) no `PagamentoCaixa` e confirma
- **Então** o status da duplicata muda para "Baixada"
- **E** o saldo correspondente é debitado da conta bancária.

**Cenário: Tentativa de baixa com permissão insuficiente (Falha)**
- **Dado** que o usuário pertence à Filial A e possui nível operacional
- **Quando** ele tenta baixar uma duplicata alocada no plano de contas da Matriz (Filial M)
- **Então** o sistema bloqueia a ação informando erro de permissão via `verificaPermissaoAlteraCaixa()`.
