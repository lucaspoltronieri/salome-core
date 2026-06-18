# Design — Módulo nota-compra

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Decisões Arquiteturais
- O registro de notas de compra no legado é fortemente acoplado à interface `NotaCompra.java`, que orquestra a geração de duplicatas através da delegação ao `NotaCompraDuplicatasController`.
- A persistência utiliza o padrão Dirty-Tracking dos Beans (`*Gravar = true`) acoplado ao JDBC dinâmico, manipulando tabelas `notacompra`, `notacompraduplicatas`, `notacomprarateio` e `pagamentocaixa`. 🟢

## 2. Diagrama de Fluxo Principal (Mermaid)

Fluxo de Inserção de Nota e Baixa de Duplicata:

```mermaid
sequenceDiagram
    participant User
    participant Controller as NotaCompraController
    participant ControllerCaixa as PagamentoCaixaController
    participant BD as Banco de Dados

    User->>Controller: Inserir Nova Nota (Fornecedor, Valor)
    Controller->>Controller: Calcular Rateio (se aplicável)
    Controller->>BD: Inserir Nota e Rateio (Transação Manual)
    
    Controller->>User: Pedir Condições de Pagamento
    User->>Controller: Informar parcelas (Duplicatas)
    Controller->>BD: Inserir Duplicatas
    Controller-->>User: Nota Registrada

    note over User, BD: Algum tempo depois...

    User->>ControllerCaixa: Informar Pagamento da Duplicata
    ControllerCaixa->>ControllerCaixa: verificaPermissaoAlteraCaixa()
    ControllerCaixa->>BD: Debitar Extrato e Alterar Duplicata para 'Baixada'
    ControllerCaixa-->>User: Pagamento Confirmado
```

## 3. Modelo de Dados Relacional (Core)

```mermaid
erDiagram
    FORNECEDOR ||--o{ NOTACOMPRA : "emite"
    NOTACOMPRA ||--|{ NOTACOMPRADUPLICATAS : "gera"
    NOTACOMPRA ||--o{ NOTACOMPRARATEIO : "distribui custo"
    NOTACOMPRADUPLICATAS ||--o| PAGAMENTOCAIXA : "baixada por"
    
    NOTACOMPRA {
        int idNotaCompra PK
        int idFornecedor FK
        double valorTotal
        string situacao
    }
    
    NOTACOMPRADUPLICATAS {
        int idDuplicata PK
        int idNotaCompra FK
        date dataVencimento
        double valorDuplicata
        string status
    }
```
