# Regra — Clientes destinatários sem frete (2026)

## Objetivo

Identificar clientes cadastrados no legado em 2026 que podem ser candidatos
ao Hub CRM porque aparecem como destinatários de CT-e e não são o pagador do
frete.

## Filtros obrigatórios

1. Cliente com `idCliente >= 32001`, corte correspondente ao primeiro cadastro
   de 2026 informado para esta consulta.
2. Pessoa jurídica: CNPJ válido com 14 dígitos após remover máscaras. Pessoas
   físicas ficam fora.
3. Cliente localizado no estado de São Paulo (`estado.uf = 'SP'`).
4. Deve existir CT-e não cancelado em que o cliente seja o destinatário.
5. O CT-e considerado deve representar frete não pago pelo destinatário. O
   pagamento FOB do destinatário (`tipoPagamento` contendo `DESTINAT` e
   `FOB`) caracteriza o destinatário como pagador e não deve entrar.
6. Se existir qualquer CT-e não cancelado em que o mesmo destinatário seja o
   pagador FOB, o cliente é excluído desta relação.
7. Excluir CNAE cuja descrição seja de transportadora.
8. Excluir MEI identificado como prestação de serviço até validação específica
   da regra de negócio.

## Deduplicação

O resultado final não pode repetir:

- o mesmo CNPJ; ou
- a mesma razão social, comparada sem acentos, pontuação e diferença de caixa.

Quando houver duplicidade, permanece o primeiro registro pelo menor
`idCliente`.

## Dados do relatório

O Excel deve conter:

- ID;
- Razão Social;
- CNPJ;
- Cidade;
- e-mail principal da empresa;
- telefone fixo do cadastro;
- tipo no CT-e (`Destinatário`);
- data de cadastro; se não houver, data do primeiro CT-e sem frete;
- primeiro CT-e sem frete;
- contato separado em nome, setor, e-mail e telefone.

Os contatos vêm somente do legado. Não é feito enriquecimento externo por
Google ou outras fontes.

## Contatos inválidos

Não considerar contatos cujo nome seja `Erick` ou cujo e-mail seja um dos
seguintes:

- `erick@salome.com.br`;
- `erickcardozo@salome.com.br`;
- `ti@ti.com.br`.

## Origem técnica

- Cadastro: tabela `cliente`;
- cidade/UF: tabelas `cidade` e `estado`;
- CT-e e tipo de pagamento: tabela `conhecimento`;
- CNAE: tabela `cnae` e campos `cliente.cnae`/`cliente.cnaeSegmentoDetalhes`;
- contatos: `clientecontato` e `clientesetor`.

A consulta é somente leitura e não altera o banco legado. O arquivo deve ser
regenerado quando o período ou o corte de ID mudar.
