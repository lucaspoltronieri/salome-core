# Arquitetura e Visão Sistêmica — salome-core

> Gerado pelo Arquiteto em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Visão Geral do Sistema

O sistema **Expresso Salome** é um software de gestão logística e transporte rodoviário de cargas (TMS/ERP). Ele é composto de duas partes distintas convivendo no mesmo repositório:

1. **`salome-legacy`**: A aplicação Swing desktop principal (Java 8). Concentra a operação da transportadora (cadastros, rotas, faturamento, emissão de CT-e, controle de viagens). 🟢
2. **`salome-core`**: Um conjunto de módulos Spring Boot / Java modernos (Java 25) criados para estender o legado com novas automações (ex: automação de baixa de manifesto via Google Sheets) e possivelmente atuar como base para futuros módulos web. 🟢

### 1.1 Topologia Atual 🟢
O sistema opera em uma topologia Client-Server "Two-Tier". A aplicação Swing (desktop) conecta-se diretamente ao banco de dados MySQL via JDBC. A comunicação não passa por uma camada de aplicação centralizada (API). O novo `salome-core` atua como um worker assíncrono conectado ao mesmo banco.

---

## 2. Dívidas Técnicas Identificadas

| Componente | Dívida Técnica | Risco | Status |
|------------|----------------|-------|--------|
| **Conexão Banco** | As aplicações desktop conectam-se diretamente ao MySQL em produção pela rede, sem camada de API. | 🔴 Alto (Segurança e Concorrência) | Identificado |
| **SQL nos Controllers** | Queries SQL complexas, manipulação de JDBC, joins e regras de formatação estão fortemente acopladas nas classes de View/Controller. | 🟡 Médio (Manutenção e Migração) | Identificado |
| **Transação Longa Cliente**| As transações dependem do cliente Swing. Se um terminal cair no meio de um `salvar`, pode travar as tabelas no servidor (lock-wait). | 🔴 Alto (Disponibilidade) | Identificado |
| **Dirty Tracking Manual** | O sistema obriga a duplicação de todos os getters/setters usando booleanos (`campoGravar = true`) para montar as queries de Update. | 🟡 Médio (Velocidade de Dev) | Identificado |

---

## 3. Integrações Externas 🟢

O sistema comunica-se com diversos serviços externos cruciais para a logística e faturamento rodoviário:

### Governamentais & Regulatórios
- **SEFAZ (Secretaria da Fazenda):** Emissão, cancelamento e carta de correção de Nota Fiscal (NF-e) e Conhecimento de Transporte (CT-e). Validação XML.
- **ANTT (E-frete):** Emissão de CIOT, validação de RNTRC e cadastro de favorecidos, operado majoritariamente através do parceiro Pamcard.

### Parceiros Logísticos
- **Pamcard:** Gateway principal de pagamento eletrônico de frete (PEF). Usado exaustivamente no `ViagemController` para gerar contratos de frete de motoristas e proprietários autônomos.
- **Ksoftlog:** Provedor de software de logística de rastreamento de cargas (Solicitações de Monitoramento - SM).
- **Transsat / AT&M:** Provedores de gerenciamento de risco e averbação de cargas. O sistema comunica informações sobre viagem e CT-e para o seguro das mercadorias.

### Integrações Internas (Novo Core)
- **Google Sheets API v4:** O `salome-core` lê planilhas no Google Drive e atualiza o manifesto e as baixas no banco de dados local.

---

## 4. Padrão Arquitetural Legado (MVC) 🟢

A estrutura do `salome-legacy` obedece um padrão rígido com 5 arquivos por funcionalidade:
- **View:** `NomeView.java` + `NomeView.form` (Swing/Matisse)
- **Controller:** `NomeController.java` (Delega pro Data ou executa SQL direto se complexo)
- **Bean:** `NomeBean.java` (POJO + flags de gravação)
- **Data:** `NomeData.java` (JDBC, SQL Generator)
- **Table:** `NomeTable.java` (Enums para nomes de coluna, mitigando hardcodes parciais nas views)
