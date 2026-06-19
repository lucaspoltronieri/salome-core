# Salome Core

**Core financeiro da Expresso Salomé** — aplicação Spring Boot que expõe relatórios e
automações financeiras sobre os dados do ERP legado (Salome), sem alterá-lo.

O projeto começou como um *batch* Windows que rodava de 15 em 15 minutos apenas para
atualizar uma planilha (export das baixas de manifesto para o Google Sheets). Hoje é uma
aplicação web Spring Boot que mantém esse agendamento **dentro do próprio processo** e
acrescenta as APIs financeiras (fluxo de caixa, DRE, exportações).

> O ERP legado (Java 8 / Swing / MySQL) continua sendo o sistema principal. O `salome-core`
> apenas **lê** o banco legado e oferece módulos web complementares, integrados ao ERP por
> URL. Veja as regras de governança em [AGENTS.md](AGENTS.md).

---

## Funcionalidades

- **Export de manifestos (scheduler)** — sincroniza as baixas de CT-es do MySQL legado para
  o Google Sheets a cada 15 min (`ManifestoBaixaExportScheduler`, somente leitura).
- **Fluxo de Caixa** — realizado e previsto (`/financeiro/fluxo-caixa/...`).
- **DRE** — Gerencial (`/dre/...`), por Cliente (`/dre/cliente/...`) e por Filial (`/dre/filial/...`).
- **Exportação de CT-es sem fatura** — XLSX (`GET /api/financeiro/ctes-sem-fatura/export`).
- **Plano de contas** — árvore de classificação Receita/Despesa.

## Arquitetura

- **Stack:** Spring Boot 4 · Java 25 · Maven.
- **Camadas** (`br.com.salome.core`): `application/` (serviços de negócio) ·
  `domain/` (DTOs e regras) · `infrastructure/` (acesso ao MySQL legado, Google Sheets,
  controllers web).
- **Servidor web:** porta `8787` (configurável).
- **Banco:** leitura somente do MySQL do ERP legado (`read-only: true`); o `salome-core` não
  altera o banco de produção.
- **Integração:** Google Sheets (export de manifestos) via conta de serviço.

## Build e execução local

> Requer JDK 25. O build gera um JAR executável em `target/`.

```bash
mvn package -DskipTests
java -jar target/salome-core-1.0.0.jar
```

O profile padrão é `local`. Para rodar apenas o agendador, sem servidor web, defina
`SALOME_WEB_APPLICATION_TYPE=none`.

## Configuração (variáveis de ambiente)

As credenciais e parâmetros vêm de variáveis de ambiente (nada sensível é versionado).

| Variável | Padrão | Descrição |
|---|---|---|
| `SALOME_SERVER_PORT` | `8787` | Porta do servidor web. |
| `SALOME_WEB_APPLICATION_TYPE` | `servlet` | `servlet` (web) ou `none` (só scheduler). |
| `SALOME_LEGACY_DB_ENABLED` | `false` | Habilita o datasource do MySQL legado. |
| `SALOME_LEGACY_DB_URL` | — | JDBC do MySQL legado. |
| `SALOME_LEGACY_DB_USERNAME` / `SALOME_LEGACY_DB_PASSWORD` | — | Credenciais (somente leitura). |
| `SALOME_MANIFESTO_EXPORT_ENABLED` | `false` | Liga o agendador de export de manifestos. |
| `SALOME_MANIFESTO_EXPORT_CRON` | `0 */15 * * * *` | Frequência do export (padrão: 15 min). |
| `SALOME_MANIFESTO_EXPORT_SPREADSHEET_ID` | — | ID da planilha Google de destino. |
| `SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH` | — | Caminho do `google-service-account.json`. |
| `SALOME_MANIFESTO_EXPORT_FILIAL_DESTINO_ID` | — | Filial de destino do export. |
| `SALOME_MANIFESTO_EXPORT_BATCH_SIZE` | `500` | Tamanho do lote. |
| `SALOME_MANIFESTO_EXPORT_DATA_CORTE` | `2026-05-01` | Data de corte do export. |

## Ambientes

| Ambiente | Onde | Papel |
|---|---|---|
| **Local** | máquina do desenvolvedor | produção / desenvolvimento |
| **Hostinger** | servidor Ubuntu (Java 25) | homologação |

Na Hostinger a aplicação roda como **JAR + serviços systemd** (web na porta 8787 + export de
manifesto), com o banco MySQL externo. O versionamento no git deve refletir cada versão boa
promovida para a Hostinger.

## Estrutura do repositório

```
src/main/java/br/com/salome/core/   código da aplicação (Spring Boot)
src/main/resources/                 application.yml e profiles
docs/financeiro/                    docs do módulo financeiro (core)
docs/legacy/                        mapeamento/engenharia reversa do ERP legado
docs/manifesto-baixas-google-sheets.md   automação de export de manifestos
AGENTS.md                           regras de governança do projeto
```

> O código legado (`salome-legacy/`) e ferramentas de desenvolvimento locais não são
> versionados — veja [.gitignore](.gitignore).
