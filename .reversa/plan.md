# Plano de Exploração — salome-core

> Criado pelo Reversa em 2026-06-08
> Marque cada tarefa com ✅ quando concluída.
> Você pode editar este plano antes de iniciar: adicione, remova ou reordene tarefas conforme necessário.

---

## Fase 1: Reconhecimento 🔍

- [x] ✅ **Scout** — Mapeamento de estrutura de pastas e tecnologias
- [x] ✅ **Scout** — Análise de dependências e gerenciadores de pacotes
- [x] ✅ **Scout** — Identificação de entry points, CI/CD e configurações

## Decisão de organização das specs 🗂️

> Entre o Scout e o Arqueólogo, o Reversa pergunta como você quer organizar as specs (por módulo, caso de uso, endpoint, híbrida, por features ou customizada). A escolha fica persistida em `.reversa/config.toml` na seção `[specs]` e não será reperguntada em execuções futuras. Para reapresentar o menu, remova manualmente a seção.

## Fase 2: Escavação 🏗️

> O foco da análise é exclusivamente o `salome-legacy/` (ERP legado MVC/Swing).
> O `src/` (salome-core) é módulo moderno Java 25 e não entra na engenharia reversa.

- [x] **Arqueólogo** — Análise do legado `controller/` (238 controllers Swing/MVC)
- [x] **Arqueólogo** — Análise do legado `model/bean/` (250 beans mutáveis)
- [x] **Arqueólogo** — Análise do legado `model/data/` (239 DAOs JDBC)
- [x] **Arqueólogo** — Análise do legado `model/table/` (245 constantes de tabelas)
- [x] **Arqueólogo** — Análise do legado `view/` (395 classes + 392 forms)

## Fase 3: Interpretação 🧠

- [x] **Detetive** — Arqueologia Git e ADRs retroativos
- [x] **Detetive** — Regras de negócio implícitas e máquinas de estado
- [x] **Detetive** — Matriz de permissões (RBAC/ACL)
- [x] **Arquiteto** — Diagramas C4 (Contexto, Containers, Componentes)
- [x] **Arquiteto** — ERD completo e integrações externas
- [x] **Arquiteto** — Spec Impact Matrix

## Fase 4: Geração 📝

- [X] **Redator** — Specs SDD por unit mapeada
- [X] **Redator** — User Stories (se aplicável)
- [X] **Redator** — Code/Spec Matrix

## Fase 5: Revisão ✅

- [X] **Revisor** — Leitura crítica, detecção de lacunas e validação humana
- [X] **Revisor** — Resolução de lacunas com o usuário
- [X] **Revisor** — Relatório de confiança final

---

## Agentes Independentes

> Execute estes agentes quando os recursos estiverem disponíveis — podem rodar em qualquer fase.

- [ ] **Visor** — Análise de interface via screenshots
- [ ] **Data Master** — Análise completa do banco de dados
- [ ] **Design System** — Extração de tokens de design
- [ ] **Tracer** — Análise dinâmica (requer sistema acessível)

---

## Próximo passo

Após o Time de Descoberta concluir e o `_reversa_sdd/` estar populado, você pode disparar um dos fluxos seguintes:

- `/reversa-migrate`: orquestrador do **Time de Migração** (Paradigm Advisor → Curator → Strategist → Designer → Screen Translator → Inspector). Gera as specs do sistema novo. Saída em `_reversa_sdd/migration/` e `_reversa_sdd/screens/`.
- `/reversa-reconstructor`: gera plano bottom-up para reimplementar o software a partir das specs do legado (uma tarefa por sessão).
