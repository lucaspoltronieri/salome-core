# Phase 10: Migrar baixa completa de Contas a Pagar - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-15
**Phase:** 10-Migrar baixa completa de Contas a Pagar
**Areas discussed:** Baixa manual, Cheque e multiplas duplicatas, Banco caixa e conciliacao, Reversao e bloqueios

---

## Baixa manual

| Question | Options considered | User's choice |
|----------|--------------------|---------------|
| Onde a baixa manual deve acontecer na UI? | Central de Pagamentos; Documento Entrada Detalhes; ambos usando o mesmo Service | Documento Entrada Detalhes, aba de duplicatas |
| Como tratar valor pago diferente do valor da duplicata? | Permitir seguindo legado; bloquear por valor igual; permitir so com confirmacao | Permitir seguindo o legado, gravando `valorpago` e documentando diferenca |
| Quais campos o usuario deve preencher? | Data/valor/banco/operacao/historico; campos derivados; igual ao legado com numero de cheque | Igual ao legado: data, valor, banco, operacao, numero de cheque e historico |
| Quando a baixa deve ser gravada? | Imediatamente; preparar lista para lote; individual imediata e lote separado | Imediatamente ao confirmar, igual ao legado |

**Notes:** Usuario reforcou "igual legado" para a gravacao da baixa manual.

---

## Cheque e multiplas duplicatas

| Question | Options considered | User's choice |
|----------|--------------------|---------------|
| Onde deve ficar o fluxo de cheque/multiplas duplicatas? | Tela propria equivalente a `EmitirCheques`; Central de Pagamentos; Documento Entrada Detalhes | Central de Pagamentos, selecionando uma ou mais duplicatas |
| A selecao em lote deve permitir misturar fornecedores/notas/filiais? | Seguir legado; mesmo fornecedor; mesma filial e banco | Seguir o legado exatamente apos confirmar em `EmitirCheques` |
| Como tratar cheque e caixa no fluxo em lote? | Migrar completo; pagamento em lote sem cheque fisico; preparar sem gravar | Migrar completo, igual ao legado |
| Se uma duplicata do lote falhar, o que deve acontecer? | Rollback total; baixar validas e listar falhas; perguntar antes de baixa parcial | Rollback total, igual ao legado |

**Notes:** Usuario explicou que a Central de Pagamentos vai ficar junto da experiencia operacional principal.

---

## Banco caixa e conciliacao

| Question | Options considered | User's choice |
|----------|--------------------|---------------|
| Como escolher o banco na baixa? | Usuario escolhe manualmente; sistema sugere banco do usuario; sistema fixa banco | Usuario escolhe manualmente em cada baixa/lote, igual ao legado |
| Como tratar conta caixa? | Replicar legado completo; so gravar Extrato/duplicata; aviso/confirmacao | Replicar exatamente o legado |
| Como exibir saldo/conciliacao na UI? | Saldo pela origem legada; nao mostrar saldo; status simples | Mostrar saldo pela origem legada como `v_saldobancariotalao` |
| Permissao de caixa deve entrar nesta fase? | Bloqueio real; aviso visual; deixar para seguranca posterior | Bloqueio real conforme legado |

**Notes:** Decisao reforca que permissao fina de caixa/banco e parte da fase 10, nao apenas preparacao visual.

---

## Reversao e bloqueios

| Question | Options considered | User's choice |
|----------|--------------------|---------------|
| Como desfazer uma baixa? | Excluir `Extrato` e limpar duplicata; lancamento reverso; nao permitir reversao | Excluir `Extrato` e limpar `datapagamento`, `valorpago` e `idExtrato`, igual ao legado |
| Onde a reversao deve aparecer? | Documento Entrada Detalhes; Central de Pagamentos; ambos com mesmo Service | Nos dois lugares, usando o mesmo Service |
| Como proteger duplicata baixada? | Bloquear editar/excluir; bloquear so excluir; seguir exatamente legado | Seguir exatamente o legado, documentando campos bloqueados |
| Como mostrar erro/bloqueio ao usuario? | Mensagem operacional curta; mensagem tecnica; impedir visualmente sem popup | Mensagem operacional curta, igual ao legado quando houver equivalente |

**Notes:** Usuario reforcou "igual legado" para reversao, bloqueios e mensagens.

---

## the agent's Discretion

- Escolher a composicao visual exata entre modal, drawer ou painel lateral.
- Nomear Services, Commands, DTOs e Repositories conforme padroes do codigo.
- Definir formato exato de saldo/historico/mensagens no Vaadin, preservando origem e comportamento legado.

## Deferred Ideas

None.
