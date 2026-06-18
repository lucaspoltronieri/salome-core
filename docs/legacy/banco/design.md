# Design — Módulo banco

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Decisões Arquiteturais
- O `Extrato` não é um repositório centralizado assíncrono. No modelo legado, os controllers de `Faturamento` e `PagamentoCaixa` invocam instâncias de `ExtratoData` ou inserem JDBC raw na tabela de extratos no momento da operação (Event-Driven de forma síncrona). 🟢
- O banco local (da empresa) é fortemente acoplado com filiais para separar o "Caixa" físico da matriz e das filiais. 🟡

## 2. Diagrama de Fluxo Principal (Mermaid)

Fluxo Genérico de Conciliação Financeira (Caixa e Banco):

```mermaid
sequenceDiagram
    participant User
    participant ExtratoController as ExtratoController
    participant ControllerAcao as Controllers de Entrada/Saída
    participant BD as Banco de Dados

    ControllerAcao->>BD: Inserir Registro (Pagamento ou Recebimento)
    ControllerAcao->>BD: Inserir Linha em Extrato (Valor, Tipo, ID_Banco)
    
    note over User, BD: Abertura de Tela de Extrato
    
    User->>ExtratoController: Filtrar por Banco e Período
    ExtratoController->>BD: Query Sumarizada de Saldos Iniciais
    ExtratoController->>BD: Query Detalhada de Extrato
    BD-->>ExtratoController: Lista de Movimentações
    ExtratoController-->>User: Exibir Saldo Atual e Detalhes
```

## 3. Modelo de Dados Relacional (Core)

```mermaid
erDiagram
    FILIAL ||--o{ BANCO : "possui contas em"
    BANCO ||--o{ EXTRATO : "possui movimentações"
    EXTRATO ||--o| FATURA : "originado de (crédito)"
    EXTRATO ||--o| PAGAMENTOCAIXA : "originado de (débito)"
    
    BANCO {
        int idBanco PK
        int idFilial FK
        string codigoFebraban
        string agencia
        string conta
        double saldoAtual
    }
    
    EXTRATO {
        int idExtrato PK
        int idBanco FK
        date dataTransacao
        string tipoOperacao "C (Crédito) ou D (Débito)"
        double valor
        string historico
    }
```
