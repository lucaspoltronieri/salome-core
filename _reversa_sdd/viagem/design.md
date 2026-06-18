# Design — Módulo viagem

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Decisões Arquiteturais
- O cálculo de peso e roteamento utiliza agregações do banco via queries complexas em `ViagemData`. O controller recupera IDs dos Conhecimentos e os amarra através da tabela associativa `viagemconhecimento`. 🟢
- O envio de dados para a Pamcard é feito por uma camada de integração síncrona dentro da ação de inserção do Controller (sem fila de retentativas nativa). 🟡

## 2. Diagrama de Fluxo Principal (Mermaid)

Fluxo de Abertura de Viagem e Vinculação de CT-e:

```mermaid
sequenceDiagram
    participant User
    participant Controller as ViagemController
    participant BD as Banco de Dados
    participant Pamcard as API Pamcard

    User->>Controller: Criar Viagem (Placa, Motorista, Filial)
    Controller->>BD: Insert Viagem
    User->>Controller: Adicionar CT-es à Viagem
    Controller->>BD: Insert viagemconhecimento (Associação)
    
    Controller->>BD: getPesoTotal(idViagem)
    BD-->>Controller: Soma de Pesos dos CT-es
    
    alt Motorista Terceiro
        Controller->>Pamcard: Registrar CIOT
        Pamcard-->>Controller: Numero CIOT Retornado
        Controller->>BD: Update Viagem (Numero CIOT)
    end
    
    Controller-->>User: Viagem Liberada
```

## 3. Modelo de Dados Relacional (Core)

```mermaid
erDiagram
    VEICULO ||--o{ VIAGEM : "realiza"
    MOTORISTA ||--o{ VIAGEM : "conduz"
    VIAGEM ||--o{ VIAGEMCONHECIMENTO : "transporta"
    CONHECIMENTO ||--o{ VIAGEMCONHECIMENTO : "está na"
    
    VIAGEM {
        int idViagem PK
        int idVeiculo FK
        int idMotorista FK
        string status
        string ciot
        double adiantamento
        double valePedagio
    }
    
    VIAGEMCONHECIMENTO {
        int idViagemConhecimento PK
        int idViagem FK
        int idConhecimento FK
    }
```
