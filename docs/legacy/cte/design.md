# Design — Módulo cte

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Decisões Arquiteturais
- O envio e validação de arquivos para a SEFAZ está centralizado nas classes `CteSpedController`, que processam o certificado A1/A3, assinam o XML e abrem socket HTTPS com os WebServices da Sefaz correspondente à UF da filial emissora. 🟢
- As correções (Carta de Correção) não atualizam a entidade principal de Conhecimento, mas geram eventos em anexo (tabela filha) para manter o histórico inalterável do documento fiscal original. 🟡

## 2. Diagrama de Fluxo Principal (Mermaid)

Fluxo de Emissão de CT-e no SPED:

```mermaid
sequenceDiagram
    participant User
    participant Controller as CteSpedController
    participant BD as Banco de Dados
    participant Sefaz as WebService SEFAZ

    User->>Controller: Solicitar Transmissão (Lote CT-e)
    Controller->>BD: Ler Dados do CT-e
    Controller->>Controller: Gerar XML e Assinar
    Controller->>Controller: Validar XSD
    
    alt Schema XML Inválido
        Controller-->>User: Erro de Validação Local
    else Schema Válido
        Controller->>Sefaz: Enviar Lote Assinado (HTTPS/SOAP)
        Sefaz-->>Controller: Recibo do Lote
        
        loop Consultar Recibo (Polling)
            Controller->>Sefaz: Consultar Status
            Sefaz-->>Controller: Autorizado / Rejeitado
        end
        
        Controller->>BD: Gravar Protocolo e Alterar Situacao
        Controller-->>User: CT-e Emitido
    end
```

## 3. Modelo de Dados Relacional (Core)

```mermaid
erDiagram
    CONHECIMENTO ||--o{ CTECARTACORRECAO : "possui correções"
    CONHECIMENTO ||--|{ CONHECIMENTONOTASFISCAIS : "transporta"
    
    CONHECIMENTO {
        int idConhecimento PK
        string cte
        string chaveAcesso
        string protocoloAutorizacao
        double valorFrete
        double valorIcms
        string situacao
    }
    
    CTECARTACORRECAO {
        int idCarta PK
        int idConhecimento FK
        int sequenciaEvento
        string campoAlterado
        string valorNovo
        string protocoloAutorizacao
    }
    
    CONHECIMENTONOTASFISCAIS {
        int idCteNf PK
        int idConhecimento FK
        string numeroNfe
        string chaveNfe
    }
```
