# Dependências — salome-core

> Gerado pelo Scout em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

---

## Gerenciador de Pacotes

**Maven** via `pom.xml` 🟢

- Parent: `org.springframework.boot:spring-boot-starter-parent:4.0.6`
- GroupId: `br.com.salome`
- ArtifactId: `salome-core`
- Version: `0.0.1-SNAPSHOT`
- Packaging: `jar`
- Java Version: `25`

---

## Dependências de Produção

| GroupId | ArtifactId | Versão | Propósito |
|---------|-----------|--------|-----------|
| `org.springframework.boot` | `spring-boot-starter-jdbc` | (gerenciada pelo parent 4.0.6) | JDBC + DataSource + JdbcTemplate |
| `com.mysql` | `mysql-connector-j` | (gerenciada pelo parent) | Driver MySQL JDBC |
| `com.google.apis` | `google-api-services-sheets` | `v4-rev20260213-2.0.0` | Google Sheets API v4 |
| `com.google.auth` | `google-auth-library-oauth2-http` | `1.47.0` | Autenticação OAuth2 Google (Service Account) |

---

## Dependências de Teste

| GroupId | ArtifactId | Versão | Propósito |
|---------|-----------|--------|-----------|
| `org.springframework.boot` | `spring-boot-starter-test` | (gerenciada pelo parent 4.0.6) | JUnit 5, Mockito, AssertJ, etc. |

---

## Plugins de Build

| GroupId | ArtifactId | Versão | Propósito |
|---------|-----------|--------|-----------|
| `org.springframework.boot` | `spring-boot-maven-plugin` | (gerenciada pelo parent) | Gera JAR executável |

---

## Dependências Implícitas do salome-legacy 🟡

O código em `salome-legacy/` **não tem pom.xml nem build.gradle próprio**. Ele é referência de código-fonte pura, não compilada pelo Maven do salome-core. As dependências implícitas observadas por análise de imports são:

| Tecnologia | Evidência | Versão |
|-----------|-----------|--------|
| Java 8 | Padrão de código, classes Swing, inferido do AGENTS.md | 🟡 INFERIDO |
| Swing (javax.swing) | 395 classes View + 392 formulários .form | 🟢 |
| JDBC (java.sql) | 239 classes Data com SQL textual | 🟢 |
| MySQL | Queries SQL MySQL no model.data | 🟢 |
| NetBeans GUI Builder | Arquivos .form (Matisse) | 🟢 |

---

## Ferramentas Externas

| Ferramenta | Versão | Uso |
|-----------|--------|-----|
| Apache Maven | Detectado em NetBeans (`C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd`) | Build |
| JDK 25 | `C:\Program Files\Java\jdk-25.0.3` (detectado no script de distribuição) | Runtime |
| jpackage | JDK 25 built-in | Gera SalomeCore.exe |
| Google Cloud Service Account | JSON em `google-service-account.json` | Autenticação Sheets API |

---

## Observações

1. **Sem framework web** — o `web-application-type` é `none` por default. O AGENTS.md indica que módulos web futuros usarão Spring Boot com `servlet` type.
2. **JDBC puro, sem ORM** — tanto o salome-core quanto o legado usam JDBC direto. Nenhum Hibernate, JPA ou Spring Data JPA detectado.
3. **Datasource condicional** — o datasource legado só é criado quando `SALOME_LEGACY_DB_ENABLED=true`, e é forçadamente read-only.
4. **Google Sheets como destino** — a exportação de CT-e é unidirecional (MySQL legado → Google Sheets). O gateway tem um fallback desabilitado (`DisabledManifestoBaixaSheetGateway`).
