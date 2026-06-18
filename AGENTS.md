# AGENTS.md - Salome Core como modulo web integrado ao ERP legado

## Contexto

O Salome e o ERP legado da Expresso Salome. O sistema principal continua sendo
Java 8, Swing, MVC, JDBC direto e MySQL. O legado funciona e deve continuar
funcionando.

O `salome-core` nao deve tentar recriar o executavel Swing completo. Ele deve
servir como:

- aplicacao Spring Boot batch para a automacao de baixas de manifesto;
- area de documentacao e mapeamento do ERP legado;
- base para novos modulos Spring Boot/web locais, chamados pelo ERP principal
  por botao, link ou abertura de URL;
- base para orientar futuras alteracoes no mesmo padrao do legado somente
  quando isso for explicitamente pedido.

## Regras obrigatorias

1. Nao alterar codigo dentro de `salome-legacy/` sem autorizacao explicita.
2. Nao alterar banco de producao.
3. Nao criar campo ou tabela sem script SQL versionado e aprovacao.
4. Primeiro mapear, depois planejar, depois implementar.
5. Toda regra encontrada deve apontar origem: classe, metodo, botao, DAO/query
   e tabela quando existir.
6. Toda alteracao deve ser pequena, revisavel e documentada.
7. Preservar o padrao real do legado: Swing/MVC, Controller, Bean, Data, Table e
   SQL na camada `model.data`, quando a tarefa for mexer no legado.
8. Para telas novas, preferir modulo web no `salome-core`, aberto pelo ERP
   legado via botao/URL/executavel.
9. Nao mover regra de negocio para o `salome-core` apenas por organizacao.
10. O batch de manifesto deve continuar somente leitura no MySQL legado.
11. O arquivo `rodar-exportacao-manifestos.bat` e a configuracao relacionada
    devem continuar funcionando.
12. Escritas no MySQL legado por modulos web exigem mapeamento, aprovacao e
    script SQL versionado quando houver alteracao de schema.

## Padrao legado observado

- `view/`: telas Swing, formularios `.form` e handlers como
  `btnSalvarActionPerformed`.
- `controller/`: controladores finos que delegam para `model.data`.
- `model/bean/`: beans mutaveis, muitas vezes com flags de campos a gravar.
- `model/data/`: JDBC e SQL textual.
- `model/table/`: enums/constantes com nomes de tabelas e colunas.
- `deploy/`: executaveis, runtime, relatorios Jasper, templates e configs.

## Como trabalhar

- Consulte `docs/legacy/` antes de propor mudancas no legado.
- Ao mapear uma funcionalidade, registre tela, botoes, controller, bean, data,
  table, tabelas, queries e relatorios envolvidos.
- Para funcionalidade nova com tela, mapear a origem no legado e implementar a
  experiencia no `salome-core` como rota web local.
- O ERP legado deve integrar esses modulos abrindo `SalomeCore.exe` ou uma URL
  local, por exemplo `http://localhost:8787/...`.
- Ao criar uma classe nova no estilo legado, siga o conjunto correspondente:
  View/Form, Controller, Bean, Data e Table quando aplicavel e quando houver
  autorizacao explicita para alterar o legado.
- Ao mexer no `salome-core`, preserve a separacao do batch de manifesto:
  `application.manifesto`, `domain.manifesto`, `infrastructure.manifesto`,
  `infrastructure.legacy.manifesto` e `infrastructure.google`.
- Novos modulos web devem ficar separados por dominio, com controllers web,
  servicos de aplicacao, modelos de dominio e repositorios legados explicitos.

## Fora de escopo atual

- Recriar o ERP Swing completo ou substituir o `Salome.exe`.
- Fazer engenharia reversa do executavel nativo para recuperar bytecode Java.
- Migrar o ERP inteiro para web de uma vez.
- Alterar estrutura do legado sem autorizacao.


---

# Reversa

> Framework de Engenharia Reversa instalado neste projeto.

## Como usar

Digite `reversa` para ativar o Reversa e iniciar ou retomar a análise do projeto.

## Comportamento ao ativar

Quando o usuário digitar `reversa` sozinho em uma mensagem:

1. Ative o skill `reversa` disponível em `.agents/skills/reversa/SKILL.md`
2. Leia o SKILL.md na íntegra e siga exatamente as instruções do Reversa

## Regra não-negociável

Nunca apague, modifique ou sobrescreva arquivos pré-existentes do projeto legado.
O Reversa escreve **apenas** em `.reversa/` e `_reversa_sdd/`.
