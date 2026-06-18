# Diagrama C4 — Nível 2: Containers

> Gerado pelo Arquiteto em 2026-06-08

```mermaid
C4Container
    title Diagrama de Containers para Expresso Salome ERP

    Person(user_operacional, "Usuário Operacional")
    Person(user_gestor, "Gestor")

    System_Boundary(erp_salome_boundary, "Expresso Salome ERP") {
        Container(app_desktop, "Aplicação Desktop Legada", "Java 8 / Swing", "App pesada, instalada nos terminais. Controla interface, lida com faturamento, emissão e lógicas de UI")
        Container(app_batch, "Worker Batch (Novo Core)", "Java 25 / Spring Boot", "Serviço em background que automatiza integrações como leitura de planilhas e baixa de manifestos")
        ContainerDb(db, "Banco de Dados Central", "MySQL", "Armazena todas as tabelas transacionais, clientes, viagens, faturamento e rastreamento")
    }

    System_Ext(sefaz, "SEFAZ")
    System_Ext(pamcard, "Pamcard (e-frete)")
    System_Ext(gerenciamento_risco, "Sistemas de GR")
    System_Ext(rastreamento, "Ksoftlog")
    System_Ext(google_sheets, "Google Sheets API")
    System_Ext(smtp, "SMTP Server")

    Rel(user_operacional, app_desktop, "Acessa telas", "UI Swing")
    Rel(user_gestor, app_desktop, "Acessa relatórios", "UI Swing")

    Rel(app_desktop, db, "Lê e escreve dados operacionais", "JDBC (3306)")
    Rel(app_batch, db, "Lê manifestos, aplica baixas via script", "JDBC / JPA")

    Rel(app_desktop, sefaz, "Envia XML", "HTTPS")
    Rel(app_desktop, pamcard, "Envia/Recebe dados", "HTTPS")
    Rel(app_desktop, gerenciamento_risco, "Averba carga", "HTTPS")
    Rel(app_desktop, rastreamento, "Envia rota", "HTTPS")
    Rel(app_desktop, smtp, "Envia e-mail", "SMTP")

    Rel(app_batch, google_sheets, "Lê planilhas", "HTTPS / REST")
```

> **Nota Arquitetural:** Há uma forte dívida técnica onde o `app_desktop` se conecta diretamente ao `db` sem intermédio de uma API. Qualquer nova funcionalidade tem que lidar com as lógicas implementadas isoladamente dentro do Swing (fat-client). O `app_batch` (Spring Boot) é uma primeira tentativa de desacoplar processos do cliente desktop.
