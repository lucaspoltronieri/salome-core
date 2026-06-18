# Requisitos — Módulo cliente

> Gerado pelo Redator em 2026-06-08
> Confiança: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Visão Geral
O módulo **cliente** é o repositório cadastral de pessoas físicas e jurídicas (embarcadores e recebedores) e detém as regras fundamentais de precificação logística: cálculo de horas de setor e identificação de praça. Ele define quem paga o frete e qual o prazo/setor para a entrega. 🟢

## 2. Requisitos Funcionais (RF)

| ID | Requisito | Regra de Negócio / Origem | Confiança |
|----|-----------|---------------------------|-----------|
| **RF-01** | Manutenção de Cadastro | Permitir o CRUD de clientes (CNPJ/CPF, Razão Social, IE, Endereços, Contatos). | 🟢 |
| **RF-02** | Cálculo de Prazo por Setor | Dada a localidade (CEP/Cidade) do cliente destinatário, o sistema deve resolver as horas estimadas do setor de entrega (`getHorasSetor`). | 🟢 |
| **RF-03** | Validação Fiscal (Integração Sefaz) | Impedir cadastro de CNPJ com IE inválida ou bloqueada para emissão de Conhecimento. | 🟡 |

## 3. Requisitos Não Funcionais (RNF)

| ID | Requisito | Restrição / Evidência no Código | Confiança |
|----|-----------|---------------------------------|-----------|
| **RNF-01** | Performance no Cache | Dados de cliente são lidos massivamente durante a geração do CT-e. Devem utilizar índices em CNPJ. | 🟡 |

## 4. Matriz MoSCoW

| Funcionalidade | Prioridade | Justificativa |
|----------------|------------|---------------|
| Cadastro Base (Endereços, Fiscal) | **Must** | Dados essenciais para validação no CT-e e NF-e. |
| Rotina de Horas de Setor | **Must** | Crucial para o dimensionamento do tempo estimado de entrega (ETA). |

## 5. Critérios de Aceitação (Gherkin)

**Cenário: Resolução de horas por setor de entrega (Caminho Feliz)**
- **Dado** um Cliente que possui endereço em CEP pertencente ao Setor "Sul" (prazo base: 48h)
- **Quando** a rotina de emissão invoca o cálculo de horas
- **Então** o sistema retorna `48` como tempo em horas previsto para a localidade do cliente.
