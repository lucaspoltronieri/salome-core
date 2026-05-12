# AGENTS.md - Migração Salomé Legacy para Salomé core

## Contexto

Estamos modernizando partes do sistema legado da Expresso Salomé.

O sistema atual está em Java 8, MVC, Swing, com banco MySQL em VPS.
O legado funciona e deve continuar funcionando durante toda a migração.

O novo módulo será salome-core, usando:

- Java 25
- Spring Boot 4
- Vaadin
- MySQL
- Maven
- Flyway
- Spring Security quando necessário

## Objetivo inicial

Migrar gradualmente o módulo de Contas a Pagar do legado para uma aplicação web Vaadin.

A primeira etapa é replicar a tela de Contas a Pagar em modo web, lendo os mesmos dados do banco legado, respeitando as regras atuais.

## Regras obrigatórias

1. Não alterar o código do legado sem autorização explícita.
2. Não alterar banco de produção.
3. Não criar campo/tabela sem gerar script SQL versionado.
4. Não copiar tela Swing como arquitetura nova.
5. Não colocar regra de negócio dentro da View Vaadin.
6. Não colocar SQL dentro da View Vaadin.
7. Toda regra encontrada deve apontar a origem:
   - classe
   - método
   - botão
   - DAO
   - query
   - tabela
8. Toda alteração deve ser pequena, revisável e documentada.
9. Primeiro mapear, depois planejar, depois implementar.
10. Antes de liberar gravação, criar versão somente leitura.
11. Qualquer regra de baixa, exclusão, edição, rateio, fornecedor, produto, plano de contas, filial ou usuário logado deve ser documentada antes de ser migrada.
12. Regras críticas de financeiro devem ter teste.
13. A tela Vaadin deve chamar Services.
14. Services chamam Repositories/Adapters.
15. Repositories/Adapters acessam banco.
16. O usuário logado deve aproveitar a estrutura existente do legado sempre que possível, mas sem acoplar Swing ao novo módulo.

## Classes e termos importantes do legado

Investigar prioritariamente classes, pacotes e métodos contendo:

- notacompra
- nota compra
- rateio
- produto
- plano contas
- plano de contas
- filial
- fornecedor
- contas pagar
- conta pagar
- baixa
- banco
- extrato
- usuário
- login
- permissão
- centro de custo

## Arquitetura desejada no novo módulo

salome-core deve seguir a estrutura:

- ui
  - Views Vaadin
- application
  - Services de caso de uso
- domain
  - Entidades e regras de domínio
- infrastructure
  - legacy adapters
  - repositories
  - integração banco legado
- security
  - autenticação e usuário logado
- docs
  - documentação da migração

## Importante

O objetivo não é reescrever tudo.
O objetivo é migrar o Contas a Pagar com segurança, preservando comportamento existente e melhorando experiência, governança e manutenção.