# Design — Módulo faturamento

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Decisões Arquiteturais
- O controle de retorno CNAB no legado é processado proceduralmente dentro de `FaturaRetornoController.java`, que faz a leitura posicional (substrings) do arquivo texto do banco.
- Ao baixar a fatura (`FaturaBaixaController`), o sistema executa DML direto para atualizar o status da fatura para "Baixada" e injeta a entrada no extrato bancário numa única transação via `Conecta`. 🟢

## 2. Diagrama de Fluxo Principal (Mermaid)

Fluxo de Retorno e Baixa Bancária:

```mermaid
sequenceDiagram
    participant User
    participant ControllerRetorno as FaturaRetornoController
    participant ControllerFatura as FaturaController
    participant BD as Banco de Dados

    User->>ControllerRetorno: Importar Arquivo CNAB (Retorno)
    ControllerRetorno->>ControllerRetorno: Ler posições do arquivo TXT
    
    loop Para cada título no arquivo
        ControllerRetorno->>BD: Buscar Fatura pelo 'Nosso Número'
        
        alt Fatura Encontrada & Paga no CNAB
            ControllerRetorno->>BD: Update Fatura.situacao = 'Baixada'
            ControllerRetorno->>BD: Insert Extrato (Crédito)
        else Fatura não encontrada
            ControllerRetorno->>User: Registrar Log de Título Órfão
        end
    end
    
    ControllerRetorno->>BD: Commit Lote
    ControllerRetorno-->>User: Retorno Processado
```

## 3. Modelo de Dados Relacional (Core)

```mermaid
erDiagram
    CLIENTE ||--o{ FATURA : "é sacado de"
    FATURA ||--|{ CONHECIMENTO : "agrupa"
    BANCO ||--o{ FATURA : "emite boleto por"
    
    FATURA {
        int idFatura PK
        int idCliente FK
        int idBanco FK
        double valorBruto
        double valorAcrescimos
        double valorDescontos
        string nossoNumero
        string situacao
    }
    
    CONHECIMENTO {
        int idConhecimento PK
        int idFatura FK
        string cte
    }
```
