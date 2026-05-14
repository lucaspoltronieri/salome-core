# Phase 9: Migrar NotaCompra completa - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 09-migrar-notacompra-completa
**Areas discussed:** Recorte da edicao, Regra de salvamento, Validacoes e bloqueios financeiros, Experiencia Vaadin de edicao

---

## Recorte da edicao

| Option | Description | Selected |
|--------|-------------|----------|
| Cabecalho apenas | Migrar primeiro somente campos principais da nota. | |
| Cabecalho + parcelas | Migrar nota e duplicatas sem produtos/rateio completos. | |
| Nota completa | Migrar cabecalho, fornecedor, filial, produtos, duplicatas, rateio, plano de contas, inclusao, edicao, salvamento, exclusao, auditoria, permissoes e testes. | yes |

**User's choice:** Nota completa.
**Notes:** A fase 9 nao deve aceitar recorte parcial.

---

## Regra de salvamento

| Option | Description | Selected |
|--------|-------------|----------|
| Transacao unica para a nota completa | Salvar cabecalho, produtos, duplicatas e rateio juntos. | partial |
| Rascunho incremental | Permitir salvar parcialmente e completar depois. | |
| Seguir exatamente o legado | Reproduzir comportamento Swing mesmo que parcial. | |

**User's choice:** Tela unica, mas com secoes separadas como no legado.
**Notes:** Cabecalho, produtos, duplicatas/parcelas e rateio ficam na mesma experiencia web, mas cada um tem botao proprio. Lancamento manual salva por secao. Importacao XML/chave salva tudo automaticamente.

| Option | Description | Selected |
|--------|-------------|----------|
| Salvar so a secao alterada | Cada botao grava somente sua secao e dependencias minimas. | yes |
| Salvar secao, mas revalidar totais da nota | Conferir impactos globais ao salvar cada parte. | |
| Salvar secao sem validacao global, e validar tudo no final | Flexivel, mas pode deixar inconsistencia escondida. | |

**User's choice:** Salvar so a secao alterada.
**Notes:** Nao regravar produtos ao salvar duplicatas, nem regravar rateio ao salvar cabecalho.

| Option | Description | Selected |
|--------|-------------|----------|
| Permitir divergencia temporaria | Nota pode ficar incompleta durante lancamento. | yes |
| Bloquear divergencia ao salvar cada secao | Cada secao deve bater com valor da nota. | |
| Alertar, mas permitir | Salvar e destacar pendencia/inconsistencia. | |

**User's choice:** Permitir divergencia temporaria.
**Notes:** O usuario pode estar ainda lancando produtos, parcelas ou rateio.

| Option | Description | Selected |
|--------|-------------|----------|
| Importar como preenchimento, usuario salva por secao | Importacao preenche tela sem gravar. | |
| Importar e salvar tudo automaticamente | Importacao validada grava cabecalho, produtos e duplicatas/rateio aplicavel. | yes |
| Importar e pedir confirmacao unica | Mostrar resumo e salvar apos confirmacao. | |

**User's choice:** Importar e salvar tudo automaticamente.
**Notes:** Depois da importacao, correcoes futuras acontecem entrando em cada secao e salvando a parte correspondente.

---

## Validacoes e bloqueios financeiros

| Option | Description | Selected |
|--------|-------------|----------|
| Bloquear qualquer edicao da nota | Nota com duplicata baixada fica congelada. | yes |
| Bloquear so o que afeta financeiro | Permitir campos administrativos. | |
| Permitir edicao com alerta | Avisar, mas permitir alteracoes. | |

**User's choice:** Bloquear edicao normal quando ha baixa/extrato.
**Notes:** Excecao real: despesas lancadas sem nota, como boleto, podem ser pagas antes da chegada do XML. Nesse caso, usuario com permissao precisa poder importar/vincular XML depois da baixa.

| Option | Description | Selected |
|--------|-------------|----------|
| Permissao especial apenas | Usuario autorizado pode importar/vincular XML em nota paga. | yes |
| Permissao especial + auditoria obrigatoria | Registrar antes/depois, usuario, data e justificativa. | |
| Nao permitir na fase 9 | Deixar para fase futura. | |

**User's choice:** Permissao especial apenas.
**Notes:** A acao especifica de XML pos-pagamento deve existir; edicao normal continua bloqueada.

| Option | Description | Selected |
|--------|-------------|----------|
| Excluir nota so se nao houver duplicata baixada/extrato | Confirmar regra no legado. | yes |
| Excluir nota nunca pelo web nesta fase | Sem delete operacional. | |
| Excluir nota com permissao especial mesmo se paga | Permitir excecao de exclusao. | |

**User's choice:** Excluir nota so se nao houver duplicata baixada/extrato.
**Notes:** Usuario pediu checar se esta regra bate com o legado.

| Option | Description | Selected |
|--------|-------------|----------|
| Seguir bloqueios do legado mapeado | Usar regras documentadas e confirmar origem. | yes |
| Ser mais rigido que o legado | Bloquear divergencias de totais. | |
| Ser mais flexivel que o legado | Salvar quase tudo e alertar. | |

**User's choice:** Seguir bloqueios do legado mapeado.
**Notes:** Regras novas de rigidez nao devem ser inventadas nesta fase.

| Option | Description | Selected |
|--------|-------------|----------|
| Permissoes separadas por acao | Incluir, editar, excluir e XML pos-pagamento distintos. | |
| Uma permissao geral de NotaCompra | Quem edita faz tudo. | |
| Permissoes como no legado, se existir; senao uma permissao geral provisoria | Preservar legado e usar fallback controlado. | yes |

**User's choice:** Permissoes como no legado, se existir; senao permissao geral provisoria.
**Notes:** A permissao de importar/vincular XML pos-pagamento deve ser destacada no planejamento.

---

## Experiencia Vaadin de edicao

| Option | Description | Selected |
|--------|-------------|----------|
| Abas/secoes na mesma tela | Cabecalho, Produtos, Duplicatas e Rateio alternam sem sair da nota. | yes |
| Tela principal + dialogs | Secoes abrem em dialogs/modais. | |
| Wizard passo a passo | Avancar por etapas. | |

**User's choice:** Abas/secoes na mesma tela.
**Notes:** Uma tela unica com secoes separadas.

| Option | Description | Selected |
|--------|-------------|----------|
| Lista de notas + abrir edicao | `Documento Entrada` lista documentos; clique abre detalhes. | yes |
| Direto em formulario de nova nota | Tela abre no cadastro novo. | |
| A partir da Gestao de Pagamentos | Abrir via duplicata/titulo. | |

**User's choice:** Lista de notas + abrir edicao.
**Notes:** O usuario reforcou que `Documento Entrada` e a listagem e `Documento Entrada Detalhes` abre ao clicar na linha. A listagem pode ter tres areas: principal, painel direito de produtos e rodape com parcelas/rateio. Seguir orientacao UX da pasta `references`.

| Option | Description | Selected |
|--------|-------------|----------|
| Dentro de cada secao | Botao de salvar perto do conteudo correspondente. | preferred |
| Toolbar global no topo | Todos os botoes agrupados no topo. | allowed |
| Ambos | Toolbar global e botoes especificos por secao. | allowed |

**User's choice:** Preferencia por botao dentro da secao, mas o planner pode fazer o que for melhor entre 2 ou 3.
**Notes:** A decisao travada e nao esconder que salvamento e por secao.

| Option | Description | Selected |
|--------|-------------|----------|
| Modo somente leitura com aviso | Campos bloqueados e banner explica motivo e acoes permitidas. | yes |
| Campos desabilitados sem banner | Apenas desabilitar controles. | |
| Permitir abrir edicao, mas bloquear no salvar | Usuario so descobre ao salvar. | |

**User's choice:** Modo somente leitura com aviso.
**Notes:** Quando bloqueada por baixa/extrato, a tela deve explicar o motivo e indicar excecoes permitidas.

---

## the agent's Discretion

- O planner pode escolher a composicao exata de botoes de secao e toolbar global, desde que o salvamento por secao fique claro.
- O planner pode decidir detalhes internos de componentes Vaadin, Services, Commands, DTOs e Repositories respeitando a arquitetura aprovada.

## Deferred Ideas

- Despesa prevista/provisionada para fluxo de caixa previsto, sem afetar financeiro realizado ate virar nota/despesa real.
- Baixa completa, banco, extrato, cheque e reversoes permanecem para fase 10.
