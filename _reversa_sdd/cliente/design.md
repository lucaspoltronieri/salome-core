# Design — Módulo cliente

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Decisões Arquiteturais
- O cadastro de cliente no legado suporta múltiplos endereços através de uma tabela filho (normalizada). O cálculo de setor (`getHorasSetor`) utiliza uma tabela estática de CEPs na base local. 🟢

## 2. Diagrama de Fluxo Principal (Mermaid)

Fluxo de Resolução de Setor para Previsão de Entrega:

```mermaid
sequenceDiagram
    participant CteService as Serviço Emissor de CT-e
    participant Controller as ClienteController
    participant BD as Banco de Dados

    CteService->>Controller: Solicitar Horas do Destinatário(idCliente)
    Controller->>BD: Query: Busca Endereço de Entrega
    BD-->>Controller: Endereço / CEP
    
    Controller->>BD: Query: Busca Faixa de CEP no Setor
    BD-->>Controller: ID do Setor
    
    Controller->>BD: Query: Busca Horas do Setor
    BD-->>Controller: Int (Ex: 48)
    
    Controller-->>CteService: Retorna Horas p/ Cálculo de Previsão
```

## 3. Modelo de Dados Relacional (Core)

```mermaid
erDiagram
    CLIENTE ||--o{ ENDERECO : "possui"
    ENDERECO ||--|{ SETOR : "pertence a faixa do"
    
    CLIENTE {
        int idCliente PK
        string cnpjCpf
        string razaoSocial
        string inscricaoEstadual
    }
    
    ENDERECO {
        int idEndereco PK
        int idCliente FK
        string cep
        string tipo "Principal, Entrega, Cobrança"
    }
    
    SETOR {
        int idSetor PK
        string nome
        string faixaCepInicio
        string faixaCepFim
        int horasPrevisao
    }
```
