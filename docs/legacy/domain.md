# Domínio e Regras de Negócio Implícitas — salome-legacy

> Gerado pelo Detetive em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Glossário Ubíquo

Termos amplamente utilizados na base de código e seus significados reais na operação:

| Termo | Significado | Confiança |
|-------|-------------|-----------|
| **Conhecimento (CT-e)** | Conhecimento de Transporte Eletrônico. É o documento fiscal principal do frete, gerado a partir de Notas Fiscais. Pode ser de entrega ou de transferência. | 🟢 |
| **Coleta** | Ordem para buscar mercadoria no remetente. Precede a emissão do Conhecimento. | 🟢 |
| **Viagem** | Agrupamento logístico de Coletas, Entregas (CT-es) e Transferências. Vincula um Veículo e Motorista para execução de rota. Associada ao Manifesto (MDF-e). | 🟢 |
| **Pamcard** | Provedor do sistema de pagamento de frete eletrônico (e-frete) e CIOT (Código Identificador da Operação de Transporte). | 🟢 |
| **E-frete** | Sistema da ANTT para registro do pagamento de fretes. | 🟢 |
| **RPA** | Recibo de Pagamento a Autônomo. Gerado na viagem quando o motorista é proprietário/autônomo. | 🟢 |
| **Ksoftlog** | Sistema terceiro de rastreamento de cargas/veículos. A viagem envia solicitações de monitoramento (SM) para eles. | 🟢 |
| **Averbação** | Processo de envio de dados da carga (Viagem/CT-e) para a seguradora (ex: AT&M ou Transsat) para garantia da cobertura contra roubo/acidentes. | 🟢 |
| **Redespacho** | Tipo de operação onde um cliente intermediário assume parte do trajeto. O "Cliente Redespacho" no CT-e substitui o Destinatário padrão. | 🟡 |
| **Dirty-Tracking** | Padrão técnico. Campos do banco só são atualizados (UPDATE) se a flag `<campo>Gravar` estiver verdadeira no Bean. | 🟢 |

---

## 2. Regras de Negócio Críticas (Core Domain)

### 2.1 Emissão de Contrato de Frete (CIOT / Pamcard)
Para gerar o CIOT e o contrato de frete Pamcard em uma Viagem, o sistema força restrições severas:
- **Veículo e Motorista:** O motorista precisa ter cartão válido e RNTRC, assim como o proprietário do veículo. 🟢
- **Favorecidos:** Motorista e proprietário precisam estar registrados como favorecidos na Pamcard. Se não existirem, são inseridos automaticamente no ato da viagem. 🟢
- **CT-es Inclusos:** Todos os clientes (Remetente, Destinatário, Consignatário) de todos os CT-es da viagem são validados (tamanho do número do endereço < 5, CEP < 8, código IBGE existente). Se faltar algo, a emissão bloqueia. 🟢

### 2.2 Cancelamento Estrutural em Cascata
O cancelamento de uma viagem não afeta apenas a tabela `viagem`. Ele reverte o estado logístico de todas as cargas contidas nela:
- Os **CT-es de Entrega** e **CT-es de Transferência** têm a situação alterada compulsoriamente para `"Armazém"` (voltando para o pátio). 🟢
- As **Coletas** têm o status retornado para `"Pendente"`. 🟢
- A transação é bloqueante: se falhar a atualização de um CT-e, reverte toda a viagem (`BancoDados.rollback()`). 🟢

### 2.3 Cálculo Modular do Prazo de Entrega
O prazo prometido ao cliente é dinâmico e resolvido pela seguinte precedência:
1. Pela "Região Setor Local" baseada nos 5 primeiros dígitos do CEP do *Endereço do tipo 'Entrega'*. 🟢
2. Pela "Região Setor Local" baseada nos 5 primeiros dígitos do CEP do *Cadastro base do Cliente*. 🟢
3. Pela *Tabela de Preço* padrão amarrada ao cliente. 🟡

### 2.4 Notificação Assíncrona de Pendências de Cadastro
Sempre que a filial grava um cliente como `"Pendente"`, o sistema verifica se já houve notificação. Se não, gera um email em background (`SwingWorker` + `Velocity`) formatado em HTML com as pendências e envia para a matriz/auditoria (via email da filial), atualizando o flag `emailPendenciasCadastroEnviado = "Sim"`. 🟢

### 2.5 Lógica de Pesos
O peso total logístico não é gravado diretamente na viagem. É calculado somando os campos `pesoNf` de todos os registros em `viagemcoletas`, `viagementrega` e `viagemtransferencia` que pertencem à viagem, através de subqueries diretas no BD. 🟢
