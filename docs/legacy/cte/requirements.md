# Requisitos — Módulo cte

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Visão Geral
O módulo **cte** cuida de todas as obrigações fiscais e logísticas de emissão do Conhecimento de Transporte Eletrônico (CT-e). Ele lida com o registro, a transmissão dos XMLs pelo sistema SPED (SEFAZ), além das operações pós-emissão como Carta de Correção Eletrônica (CC-e) e Inutilização de numeração. 🟢

## 2. Requisitos Funcionais (RF)

| ID | Requisito | Regra de Negócio / Origem | Confiança |
|----|-----------|---------------------------|-----------|
| **RF-01** | Emissão de CT-e | O sistema deve gerar o arquivo XML do CT-e com base nos dados de frete, remetente, destinatário, expedidor, recebedor e impostos (ICMS). | 🟢 |
| **RF-02** | Assinatura e Transmissão (SPED) | O sistema deve assinar o XML digitalmente via certificado digital e transmiti-lo à SEFAZ (`CteSpedController`). | 🟢 |
| **RF-03** | Cancelamento | O sistema deve permitir o cancelamento do CT-e desde que esteja no prazo legal e a mercadoria não tenha sido entregue. | 🟡 |
| **RF-04** | Carta de Correção (CC-e) | O sistema deve permitir corrigir erros em campos específicos do CT-e emitido que não influenciem o imposto ou valor do frete (`CteCartaCorrecaoController`). | 🟢 |
| **RF-05** | Inutilização | O sistema deve permitir a inutilização de uma faixa de numeração de CT-e em caso de quebra de sequência ou erro operacional não consumado (`CteinutilizacaoController`). | 🟢 |

## 3. Requisitos Não Funcionais (RNF)

| ID | Requisito | Restrição / Evidência no Código | Confiança |
|----|-----------|---------------------------------|-----------|
| **RNF-01** | Conformidade com Schema | O arquivo XML deve validar contra o arquivo XSD governamental mais recente exigido pela SEFAZ antes do envio. | 🟢 |
| **RNF-02** | Timeout SEFAZ | O envio para a SEFAZ está sujeito a indisponibilidade, necessitando mecanismos de re-consulta ou contingência. | 🟡 |

## 4. Matriz MoSCoW

| Funcionalidade | Prioridade | Justificativa |
|----------------|------------|---------------|
| Emissão de CT-e SEFAZ | **Must** | Sem isso a transportadora não pode transportar mercadoria (infração fiscal grave). |
| Carta de Correção | **Must** | Evita o pagamento duplicado de impostos ou necessidade de reemissão total por erro simples de digitação. |
| Inutilização | **Should** | Requisito contábil para evitar auditorias fiscais sobre lacunas de numeração. |

## 5. Critérios de Aceitação (Gherkin)

**Cenário: Emissão bem sucedida de CT-e (Caminho Feliz)**
- **Dado** que os dados de remetente, destinatário, valor e impostos estão preenchidos e válidos
- **Quando** o usuário aciona a geração e transmissão pelo SPED
- **Então** o sistema assina o arquivo, transmite
- **E** recebe a chave de acesso e o protocolo de autorização
- **E** o status do CT-e muda para "Autorizado" (ou similar na Sefaz) e sua situação local vira "Aberta" / "Armazém".

**Cenário: Tentativa de Carta de Correção Inválida (Falha)**
- **Dado** um CT-e já autorizado na SEFAZ
- **Quando** o usuário tenta gerar uma Carta de Correção alterando o "Valor do Frete"
- **Então** o sistema ou a SEFAZ devem rejeitar a alteração, informando que valores não podem ser corrigidos via CC-e.
