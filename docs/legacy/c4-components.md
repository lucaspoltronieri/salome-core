# Diagrama C4 — Nível 3: Componentes

> Gerado pelo Arquiteto em 2026-06-08
> Foco: **Container Aplicação Desktop Legada**

```mermaid
C4Component
    title Diagrama de Componentes para a Aplicação Desktop Legada

    Container_Boundary(app_desktop, "Aplicação Desktop Legada (Java Swing)") {
        Component(view_layer, "Camada de View", "Swing (Matisse)", "Formulários JDialog e JFrame. Interage com usuário, formata dados visuais e chama Controllers.")
        Component(controller_layer, "Camada Controller", "Java Classes", "Gerencia o fluxo de controle. Realiza validações cruzadas, abre transações e executa queries JDBC avançadas.")
        Component(bean_layer, "Camada de Beans", "JavaBeans", "Modelos de domínio ricos em atributos de Dirty-Tracking (*Gravar = true). Transfere dados entre View, Controller e Data.")
        Component(data_layer, "Camada Data (DAO)", "Java Classes", "Executa queries JDBC e DML (CRUD básico) contra o banco de dados. Monta UPDATEs baseados no dirty-tracking.")
        Component(util_layer, "Camada Util / Infra", "Classes Utilitárias", "Serviços transversais como EmailUtil, Pamcard (SOAP/REST), Conecta (Pool conexão), JasperReports.")
    }

    ContainerDb(db, "Banco de Dados Central", "MySQL", "Tabelas relacionais")

    System_Ext(pamcard, "APIs Pamcard")
    System_Ext(smtp, "SMTP Server")

    Rel(view_layer, controller_layer, "Delega ações (salvar, listar)", "Invocação direta de método")
    Rel(view_layer, bean_layer, "Lê/Escreve dados de interface", "Getters/Setters")
    Rel(controller_layer, bean_layer, "Lê/Escreve estados de negócio", "Getters/Setters")
    Rel(controller_layer, data_layer, "Delega CRUD e abre transações", "Invocação direta")
    Rel(controller_layer, util_layer, "Usa utilitários", "Invocação direta")
    
    Rel(controller_layer, db, "Acessa dados complexos (bypass DAO)", "JDBC")
    Rel(data_layer, db, "Executa persistência básica e CRUD", "JDBC")

    Rel(util_layer, pamcard, "Chama serviços externos", "HTTP(S)")
    Rel(util_layer, smtp, "Envia emails", "SMTP")
```

> **Nota sobre o Bypass:** O Diagrama ilustra a dívida técnica onde a camada Controller faz Bypass do DAO e se conecta diretamente ao Banco de Dados (via `Conecta.getCon()`) para executar queries com agregação e Joins pesados.
