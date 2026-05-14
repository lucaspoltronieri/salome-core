# Phase 8: Validar dados da tela web contra o legado - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 8-Validar dados da tela web contra o legado
**Areas discussed:** Recorte de comparacao, Amostras de dados, Registro de divergencias, Nivel de automacao

---

## Recorte de comparacao

| Question | Options | User's choice |
|----------|---------|---------------|
| Qual recorte deve ser considerado obrigatorio para a fase 8 passar? | Todos os filtros principais; Jornada inicial; Foco operacional | Todos os filtros principais |
| Quais partes da tela entram na comparacao obrigatoria? | Grade + detalhe completo; Grade + detalhe financeiro; So grade principal | Grade + detalhe completo |
| Qual deve ser a regua para considerar que bateu com o legado? | Campos criticos exatos + campos descritivos reconciliados; Tudo exato; Paridade operacional | Tudo exato |
| A comparacao deve usar qual fonte legado como verdade? | Queries legadas mapeadas + comportamento da tela Swing; Tela Swing como verdade principal; Banco legado como verdade principal | Queries legadas mapeadas + comportamento da tela Swing |

**Notes:** O usuario tambem reforcou que a validacao deve puxar dados completos do banco legado.

---

## Amostras de dados

| Question | Options | User's choice |
|----------|---------|---------------|
| Como o completo do banco deve ser tratado na pratica? | Completo por query/filtro; Completo do banco + amostras auditaveis; Completo com janela operacional | Completo do banco + amostras auditaveis |
| Como lidar com volume quando o filtro Todas retornar muitos registros? | Comparar tudo e resumir evidencias; Comparar tudo e listar tudo; Comparar tudo tecnicamente, bloquear relatorio enorme | Comparar tudo e resumir evidencias |
| Quais cenarios precisam aparecer obrigatoriamente nas evidencias representativas? | Status + vinculo completo; Operacao diaria; Casos extremos financeiros | Status + vinculo completo |

**Notes:** O usuario explicou que o novo aplicativo conectara no banco MySQL do legado como fonte oficial de verdade e que futuramente sera onde acontecera o CRUD completo. O agente esclareceu que a fase 8 permanece read-only e o usuario aprovou seguir com evidencias recomendadas.

---

## Registro de divergencias

| Question | Options | User's choice |
|----------|---------|---------------|
| Quando a validacao encontrar diferenca entre o novo Vaadin e o legado/MySQL, como isso deve aparecer? | Classificar por gravidade; Lista simples de diferencas; So divergencias bloqueadoras | Lista simples de diferencas |
| Quando encontrar divergencia, a fase deve fazer o que? | Documentar e planejar correcao; Corrigir dentro da fase 8; Separar por tipo | Documentar e planejar correcao |
| Qual formato para o relatorio final da validacao? | Markdown versionado no diretorio da fase; Markdown + CSV de divergencias; Somente log tecnico/teste | Markdown versionado no diretorio da fase |
| Quando a validacao nao conseguir comparar algum campo por falta de regra/origem clara, como registrar? | Divergencia pendente; Ignorar ate mapear melhor; Aceitar como diferenca esperada | Divergencia pendente |

**Notes:** O relatorio esperado e `08-VALIDATION.md` ou equivalente no diretorio da fase.

---

## Nivel de automacao

| Question | Options | User's choice |
|----------|---------|---------------|
| Como a fase 8 deve validar tecnicamente os dados? | Validador reutilizavel + relatorio; Checklist manual com queries; Testes automatizados apenas | Validador reutilizavel + relatorio |
| Esse validador deve rodar como que? | Comando/procedimento local de validacao; Botao interno na tela Vaadin; Teste JUnit de integracao | Comando/procedimento local de validacao |
| Qual politica quando o banco legado nao estiver configurado no ambiente local? | Falhar com orientacao clara; Usar dados em memoria; Pular validacao automaticamente | Falhar com orientacao clara |
| A fase 8 deve incluir teste automatizado alem do relatorio? | Sim, teste do motor de comparacao com fixtures; Nao agora; Sim, teste de integracao com banco real | Sim, teste do motor de comparacao com fixtures |

**Notes:** A validacao deve ficar fora da UI Vaadin e deve ser testavel sem depender do banco real.

---

## the agent's Discretion

- Definir formato interno do comando/procedimento local de validacao.
- Definir estrutura do motor de comparacao e fixtures.
- Definir estrategia de processamento de volume, mantendo comparacao completa e relatorio resumido.

## Deferred Ideas

- CRUD completo no `salome-core` sobre o MySQL legado fica para as fases de escrita do roadmap, depois da validacao read-only da fase 8.
- Botao de validacao dentro da UI Vaadin nao entra nesta fase.
