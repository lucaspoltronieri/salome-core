# Inventário — salome-core

> Gerado pelo Scout em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

---

## Visão Geral

| Atributo | Valor |
|----------|-------|
| **Linguagem principal** | Java 25 🟢 |
| **Framework principal** | Spring Boot 4.0.6 🟢 |
| **Gerenciador de pacotes** | Maven (pom.xml) 🟢 |
| **Tipo de aplicação** | Batch / CLI (web-application-type: none) 🟢 |
| **Banco de dados** | MySQL (acesso JDBC somente leitura ao legado) 🟢 |
| **Integração externa** | Google Sheets API v4 🟢 |
| **Total de arquivos** | ~1.796 (excluindo .git, target, .reversa, .agents) |
| **Arquivos Java** | 1.392 |
| **Arquivos .form (Swing)** | 392 |

---

## Estrutura de Pastas

```
salome-core/
├── .agents/                          # Skills do Reversa
├── .reversa/                         # Estado e config do Reversa
├── docs/
│   └── manifesto-baixas-google-sheets.md
├── salome-legacy/                    # Código-fonte do ERP legado (somente leitura)
│   ├── controller/                   # 238 controllers Swing/MVC
│   ├── model/
│   │   ├── bean/                     # 250 beans mutáveis
│   │   ├── data/                     # 239 DAOs (JDBC direto, SQL textual)
│   │   └── table/                    # 245 enums/constantes de tabelas
│   └── view/                         # 395 classes Java + 392 formulários .form
├── src/
│   ├── main/
│   │   ├── java/br/com/salome/core/
│   │   │   ├── SalomeCoreApplication.java          # Entry point
│   │   │   ├── application/
│   │   │   │   └── manifesto/                      # Serviço de exportação
│   │   │   │       ├── ManifestoBaixaExportService.java
│   │   │   │       ├── ManifestoBaixaRepository.java (interface)
│   │   │   │       ├── ManifestoBaixaSheetGateway.java (interface)
│   │   │   │       └── ManifestoRuleOrigins.java
│   │   │   ├── domain/
│   │   │   │   ├── legacy/
│   │   │   │   │   └── LegacyOrigin.java           # Record de rastreabilidade
│   │   │   │   └── manifesto/                      # Records de domínio
│   │   │   │       ├── CteMapaSjpRecord.java
│   │   │   │       ├── ManifestoBaixaCursor.java
│   │   │   │       ├── ManifestoBaixaExportRecord.java
│   │   │   │       ├── ManifestoBaixaExportRequest.java
│   │   │   │       ├── ManifestoBaixaExportResult.java
│   │   │   │       ├── ManifestoBaixaSheetRow.java
│   │   │   │       └── ManifestoBaixaSituacaoAtual.java
│   │   │   └── infrastructure/
│   │   │       ├── google/                         # Gateway Google Sheets
│   │   │       │   ├── DisabledManifestoBaixaSheetGateway.java
│   │   │       │   └── GoogleSheetsManifestoBaixaGateway.java
│   │   │       ├── legacy/                         # Configuração JDBC legado
│   │   │       │   ├── LegacyDatabaseProperties.java
│   │   │       │   ├── LegacyJdbcConfiguration.java
│   │   │       │   ├── LegacyTransactionConfiguration.java
│   │   │       │   └── manifesto/
│   │   │       │       ├── InMemoryManifestoBaixaRepository.java
│   │   │       │       └── LegacyManifestoBaixaRepository.java
│   │   │       └── manifesto/                      # Scheduling e properties
│   │   │           ├── ManifestoBaixaExportConfiguration.java
│   │   │           ├── ManifestoBaixaExportProperties.java
│   │   │           └── ManifestoBaixaExportScheduler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── db/migration/                       # Vazio (.gitkeep)
│   └── test/java/br/com/salome/core/
│       ├── SalomeCoreApplicationTests.java
│       └── application/manifesto/
│           └── ManifestoBaixaExportServiceTest.java
├── configurar-exportacao-manifestos.ps1
├── criar-distribuicao-salome-core.bat
├── rodar-exportacao-manifestos.bat
├── google-service-account.json
├── pom.xml
├── AGENTS.md
└── GEMINI.md
```

---

## Pontos de Entrada 🟢

| Arquivo | Tipo |
|---------|------|
| `SalomeCoreApplication.java` | Entry point Spring Boot (`@SpringBootApplication`) |
| `ManifestoBaixaExportScheduler.java` | Scheduler cron (`@Scheduled`) + startup (`@EventListener`) |
| `rodar-exportacao-manifestos.bat` | Script de execução do batch (build + java -jar) |
| `criar-distribuicao-salome-core.bat` | Script de distribuição (mvn package + jpackage → SalomeCore.exe) |
| `configurar-exportacao-manifestos.ps1` | Script de configuração de variáveis de ambiente |

---

## Configuração 🟢

| Arquivo | Propósito |
|---------|-----------|
| `application.yml` | Config principal: datasource legado, manifesto export, cron |
| `application-local.yml` | Profile local: logging e datasource |
| `google-service-account.json` | Credenciais Google Service Account |

### Variáveis de Ambiente

| Variável | Propósito |
|----------|-----------|
| `SALOME_WEB_APPLICATION_TYPE` | Tipo de app Spring (default: none) |
| `SALOME_LEGACY_DB_ENABLED` | Habilita datasource legado |
| `SALOME_LEGACY_DB_URL` | URL JDBC MySQL |
| `SALOME_LEGACY_DB_USERNAME` | Usuário MySQL |
| `SALOME_LEGACY_DB_PASSWORD` | Senha MySQL |
| `SALOME_MANIFESTO_EXPORT_ENABLED` | Habilita scheduler de exportação |
| `SALOME_MANIFESTO_EXPORT_CRON` | Expressão cron (default: a cada 15 min) |
| `SALOME_MANIFESTO_EXPORT_SPREADSHEET_ID` | ID da Google Sheet destino |
| `SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH` | Caminho do JSON de credenciais |
| `SALOME_MANIFESTO_EXPORT_FILIAL_DESTINO_ID` | ID da filial destino |
| `SALOME_MANIFESTO_EXPORT_BATCH_SIZE` | Tamanho do batch (default: 500) |
| `SALOME_MANIFESTO_EXPORT_DATA_CORTE` | Data de corte (default: 2026-05-01) |

---

## Módulos Identificados

### salome-core (Spring Boot) — 23 arquivos Java

| Módulo | Descrição | Arquivos |
|--------|-----------|----------|
| `manifesto` | Batch de exportação de baixas de CT-e para Google Sheets | 16 |
| `legacy` | Configuração JDBC e rastreabilidade do legado | 5 |
| `google` | Gateway Google Sheets API | 2 |

### salome-legacy (ERP Swing) — 1.367 arquivos Java + 392 .form

| Camada | Arquivos | Descrição |
|--------|----------|-----------|
| `controller/` | 238 | Controllers finos delegando para model.data |
| `model/bean/` | 250 | Beans mutáveis com flags de campos |
| `model/data/` | 239 | DAOs com JDBC direto e SQL textual |
| `model/table/` | 245 | Enums/constantes com nomes de tabelas e colunas |
| `view/` | 787 | Telas Swing (395 .java + 392 .form) |

---

## Schema de Banco de Dados (superficial) 🟡

- **Diretório de migrations:** `src/main/resources/db/migration/` — vazio (apenas `.gitkeep`)
- **ORM/DDL:** Não identificados
- **Acesso ao banco:** Via JDBC direto (leitura) em `LegacyManifestoBaixaRepository.java` (17KB)
- **Mapeamento implícito:** 245 arquivos em `model/table/` contêm constantes de nomes de tabelas e colunas do MySQL legado

---

## Cobertura de Testes 🟢

| Atributo | Valor |
|----------|-------|
| **Framework** | JUnit 5 (via spring-boot-starter-test) |
| **Arquivos de teste** | 2 |
| **Testes identificados** | `SalomeCoreApplicationTests.java`, `ManifestoBaixaExportServiceTest.java` |
| **Cobertura estimada** | Mínima — apenas smoke test e teste do serviço principal |

---

## CI/CD e Docker 🔴

- **CI/CD:** Nenhum pipeline identificado (sem `.github/workflows/`, `Jenkinsfile`, etc.)
- **Docker:** Nenhum `Dockerfile` ou `docker-compose.yml` encontrado
- **Distribuição:** Via `criar-distribuicao-salome-core.bat` (mvn package + jpackage → SalomeCore.exe)

---

## Padrão Legado (salome-legacy) 🟢

O legado segue o padrão MVC-Swing documentado em AGENTS.md:

- **View:** Telas Swing com formulários `.form` e handlers como `btnSalvarActionPerformed`
- **Controller:** Controllers finos que delegam para `model.data`
- **Model/Bean:** Beans mutáveis com flags de campos a gravar
- **Model/Data:** JDBC direto com SQL textual
- **Model/Table:** Enums/constantes com nomes de tabelas e colunas

### Controllers mais complexos (por tamanho)

| Controller | Tamanho | Domínio provável |
|-----------|---------|-------------------|
| `ViagemController.java` | 115 KB | Gestão de viagens |
| `TabelaPrecoController.java` | 95 KB | Tabela de preços |
| `FaturaController.java` | 65 KB | Faturamento |
| `NotaServicoFaturaController.java` | 43 KB | Notas de serviço / fatura |
| `RpaController.java` | 31 KB | RPA (recibo de pagamento a autônomos) |
| `ComprovanteentregaController.java` | 31 KB | Comprovantes de entrega |
| `ClienteController.java` | 21 KB | Cadastro de clientes |
