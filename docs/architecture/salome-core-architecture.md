# Arquitetura alvo do salome-core

## Objetivo

Este documento define a arquitetura alvo do `salome-core` antes da criacao do
projeto Java 25 + Spring Boot 4 + Vaadin. A proposta existe para orientar as
proximas fases da migracao do Contas a Pagar, preservando o comportamento do
legado e evitando repetir a arquitetura Swing/MVC atual.

A fase 5 entrega apenas um contrato arquitetural revisavel. Ela nao cria
`pom.xml`, codigo Java, Views Vaadin, configuracao Spring Boot, scripts Flyway
ou qualquer alteracao no banco.

O objetivo do `salome-core` e migrar o Contas a Pagar completo para web, com
equivalencia funcional ao legado e mantendo o legado em operacao durante a
migracao. A primeira tela somente leitura e apenas um checkpoint tecnico para
validar dados e reduzir risco antes de liberar escrita financeira.

## Escopo e nao escopo

Escopo desta proposta:

- Definir camadas e responsabilidades do novo modulo.
- Definir a direcao de dependencia entre UI, services, dominio e adapters.
- Definir a fronteira de acesso ao banco legado.
- Definir como o usuario logado do legado sera adaptado para Spring Security.
- Definir a politica read-first como checkpoint e a preparacao para migracao
  completa de escrita, baixa, exclusao, rateio, produtos e duplicatas.
- Definir regras de governanca para schema, Flyway e testes financeiros.

Fora do escopo desta fase:

- Criar o projeto Maven do `salome-core`.
- Implementar tela Vaadin.
- Implementar repositories reais.
- Criar migrations Flyway.
- Alterar o banco de producao.
- Alterar qualquer arquivo em `salome-legacy`.
- Liberar escrita, baixa, exclusao, edicao ou rateio.

## Camadas e responsabilidades

### `ui`

Contem Views Vaadin e componentes de apresentacao. A camada `ui` deve cuidar de
layout, navegacao, binding visual, eventos de tela e chamada de casos de uso.

Responsabilidades permitidas:

- Receber filtros e selecoes do usuario.
- Chamar services de `application`.
- Exibir read models retornados pelos services.
- Mostrar estados de carregamento, erro e vazio.

Responsabilidades proibidas:

- Montar SQL.
- Acessar JDBC, `DataSource`, `Conecta`, `BancoDados` ou tabelas diretamente.
- Conter regra financeira pesada.
- Reproduzir listeners Swing como arquitetura.

### `application`

Contem Services de caso de uso. Esta camada coordena consultas, comandos futuros,
transacoes futuras e composicao de read models.

Exemplos esperados:

- `GestaoPagamentosService`
- `NotaCompraQueryService`
- `NotaCompraDuplicataQueryService`
- `ExtratoQueryService`
- `FornecedorLookupService`
- `FilialLookupService`
- `BancoLookupService`
- `PlanoContasLookupService`

Quando escrita for liberada em fases futuras, esta camada tambem deve receber
commands explicitos, como `SalvarDuplicataNotaCompraCommand`, `BaixarDuplicataCommand` ou
`ExcluirNotaCompraCommand`. Esses commands substituem o padrao legado de beans
mutaveis com flags `...Gravar`.

### `domain`

Contem entidades, value objects e regras financeiras testaveis. A camada de
dominio deve representar conceitos de negocio sem depender de Vaadin, JDBC ou
Spring MVC.

Exemplos de regras que pertencem ao dominio ou a services de dominio:

- Vencimento nao pode anteceder emissao.
- Duplicata baixada nao pode ser excluida.
- Rateio nao pode exceder valor restante.
- Baixa deve preservar consistencia entre duplicata e extrato.
- Banco caixa altera comportamento de baixa e conciliacao.

### `infrastructure`

Contem implementacoes tecnicas. Para o primeiro ciclo, o subpacote mais
importante e `infrastructure.legacy`, responsavel por encapsular acesso ao
banco MySQL legado.

Responsabilidades:

- Configurar acesso ao MySQL legado.
- Implementar repositories/adapters com JDBC/JdbcTemplate.
- Centralizar SQL e mapeamento de `ResultSet`.
- Isolar nomes fisicos de tabelas e colunas.
- Encapsular chamadas futuras a procedures ou views legadas.

### `security`

Contem autenticacao, autorizacao e contexto do usuario corrente no novo modulo.
Spring Security deve centralizar autenticacao e autorizacao. A semantica do
usuario legado deve ser reaproveitada por adapter, sem acoplar Swing ao novo
modulo.

## Estrutura de pacotes proposta

Estrutura alvo sugerida para a fase 6:

```text
src/main/java/br/com/salome/core/
  ui/
    contaspagar/
    notacompra/
    financeiro/
    cadastros/
  application/
    contaspagar/
    notacompra/
    financeiro/
    cadastros/
  domain/
    contaspagar/
    notacompra/
    financeiro/
    cadastros/
  infrastructure/
    legacy/
      contaspagar/
      notacompra/
      financeiro/
      cadastros/
      security/
    migration/
  security/
docs/
  architecture/
```

A divisao principal fica por camada. Dentro de cada camada, os subpacotes seguem
funcionalidades ou dominios: `contaspagar`, `notacompra`, `financeiro`,
`cadastros` e `legacy`.

Essa combinacao evita mega-pacotes por camada e evita acoplar tudo por feature
sem fronteira arquitetural.

## Fluxo de dependencia

O fluxo obrigatorio do novo modulo e:

```text
Vaadin View
  -> Application Service
    -> Domain model/rule
    -> Repository/Adapter port
      -> Infrastructure legacy adapter
        -> MySQL legado
```

Frases de contrato:

- Views Vaadin chamam Services.
- Services chamam Repositories/Adapters.
- Repositories/Adapters acessam MySQL legado.

A View Vaadin nunca acessa SQL, JDBC ou tabela. O Service nunca conhece detalhe
visual da tela. O adapter nunca chama Vaadin.

## Politica read-first e migracao completa

A primeira entrega funcional do `salome-core` deve ser somente leitura. Isso e
uma regra de seguranca financeira e uma decisao de migracao, mas nao define o
escopo final do modulo. O objetivo aprovado e migrar Contas a Pagar completo,
incluindo `NotaCompra`, produtos, duplicatas, rateio, baixa, banco, extrato,
cheque, permissoes, edicao, salvamento, exclusao e auditoria.

O desenho de repositories pode ser CRUD-capable desde o inicio, mas a interface
exposta para a primeira tela deve permitir apenas consultas. Escrita, exclusao,
edicao, rateio e baixa so podem ser liberadas em fases posteriores, depois de:

- regra documentada com origem no legado;
- comando de aplicacao explicito;
- transacao definida;
- teste de regra critica;
- validacao contra o comportamento esperado do legado.

Leitura e escrita devem ter modelos separados:

- Read models para grids, detalhes e lookups Vaadin.
- Commands para alteracoes futuras.
- Entidades/value objects para regras financeiras.

Esse desenho preserva D-05: os repositories podem ser preparados para CRUD
completo, mas o primeiro fluxo web permanece read-only ate a validacao de dados
passar.

## Integracao com banco legado

O acesso ao banco legado deve ficar em `infrastructure.legacy`.

Families iniciais de adapters/repositories:

- `LegacyGestaoPagamentosRepository`
- `LegacyNotaCompraRepository`
- `LegacyNotaCompraDuplicataRepository`
- `LegacyNotaCompraRateioRepository`
- `LegacyNotaCompraProdutoRepository`
- `LegacyExtratoRepository`
- `LegacyFornecedorRepository`
- `LegacyFilialRepository`
- `LegacyBancoRepository`
- `LegacyPlanoContasRepository`
- `LegacyPlanoContasCentroCustoRepository`

Origem dos primeiros adapters:

| Adapter alvo | Origem principal no legado | Tabelas principais |
| --- | --- | --- |
| `LegacyGestaoPagamentosRepository` | `NotaCompraDuplicatasConsulta.java`, `NotaCompraDuplicatas.java`, `NotaCompra.java` | `NotaCompraDuplicatas`, `NotaCompra`, `Fornecedor`, `Filial`, `Extrato`, `NotaCompraProdutos`, `NotaCompraRateio` |
| `LegacyNotaCompraRepository` | `NotaCompra.java`, `NotaCompraController`, `NotaCompraData` | `NotaCompra`, `Fornecedor`, `Filial` |
| `LegacyNotaCompraDuplicataRepository` | `NotaCompraDuplicatas.java`, `NotaCompraDuplicatasData` | `NotaCompraDuplicatas`, `NotaCompra`, `Extrato` |
| `LegacyExtratoRepository` | `Extrato.java`, `ExtratoData`, baixa e cheque | `Extrato`, `Banco`, `Operaca`, `v_saldobancariotalao` |
| `LegacyFornecedorRepository` | `FornecedorController`, `FornecedorData` | `Fornecedor` |
| `LegacyFilialRepository` | `FilialController`, `FilialData` | `Filial` |
| `LegacyBancoRepository` | `BancoController`, `BancoData` | `Banco` |
| `LegacyPlanoContasRepository` | `PlanoContasController`, `PlanoContasData` | `PlanoContas` |
| `LegacyPlanoContasCentroCustoRepository` | `PlanoContasCentroCustoController`, `PlanoContasCentroCustoData` | `PlanoContasCentroCusto` |

Os adapters devem usar JDBC/JdbcTemplate. JPA nao e a primeira escolha porque o
schema legado e as queries mapeadas ja sao centrais para reproduzir o
comportamento com controle direto.

As queries prioritarias para leitura devem seguir a ordem documentada:

- `NotaCompraDuplicatas` + `NotaCompra` - grade principal real de Contas a Pagar.
- `NotaCompra` - grade principal de compras.
- `NotaCompraDuplicatas` - parcelas e vinculo com `Extrato`.
- `Extrato` - lancamento bancario e baixa.
- `NotaCompraRateio` e `NotaCompraProdutos` - detalhes da nota.
- Lookups de `Fornecedor`, `Filial`, `Banco`, `PlanoContas` e `PlanoContasCentroCusto`.

Nota de correcao da fase 11: a familia legada `ContasPagar.java`,
`ContasPagarController`, `ContasPagarData`, `ContasPagarBean` e
`ContasPagarTable` deve ser tratada como codigo morto ate prova contraria. A
tabela `contaspagar` nao existe no banco de producao analisado, e o fluxo ativo
de Contas a Pagar usa `NotaCompraDuplicatas` com `NotaCompra`. Nao criar
adapter, command ou migracao de schema para `contaspagar` sem decisao de produto
e script SQL versionado.

## Seguranca e usuario logado

Spring Security centraliza autenticacao e autorizacao no `salome-core`.

A identidade legada continua sendo fonte de verdade inicial. O novo modulo deve
criar um adapter para mapear a semantica de:

- `Conecta.getUsuario()`
- `UsuarioController`
- tabela `usuario`
- tabela `usuarioalerta`

Esse adapter deve produzir:

- `Principal` ou equivalente do Spring Security;
- `GrantedAuthority` para permissoes;
- contexto de filial;
- contexto de banco/caixa;
- perfil/menu quando confirmado;
- identificadores necessarios para auditoria futura.

Proposta de componentes:

- `LegacyUserDetailsService`
- `LegacyAuthenticationProvider`
- `LegacyUserRepository`
- `LegacyUserContextAdapter`
- `CurrentUserContext`
- `FinanceiroPermissionService`

O novo modulo nao deve chamar `Conecta` ou `UsuarioController` diretamente da
View Vaadin. Essas referencias ficam documentadas como origem de comportamento e
devem ser encapsuladas pelos adapters.

Lacuna conhecida: a forma de armazenamento e validacao de senha do usuario
legado nao foi confirmada no snapshot. A arquitetura deve manter uma fronteira
clara para resolver essa descoberta antes de producao.

## Governanca de schema e Flyway

Phase 5 nao cria migrations.

Phase 5 nao altera banco de producao.

Qualquer criacao futura de campo, tabela, indice, view ou ajuste estrutural deve
ter script SQL versionado via Flyway. O script deve ser revisavel e associado ao
motivo de negocio.

Regra pratica:

- Leitura do schema legado pode ser feita por adapters.
- Mudanca de schema exige migration versionada.
- Nenhuma View Vaadin pode depender de mudanca manual no banco.
- Nenhum campo novo pode surgir sem documentacao e script.

## Regras financeiras e testes

Toda regra financeira migrada deve apontar origem:

- classe;
- metodo;
- botao;
- DAO;
- query;
- tabela.

Familias criticas que exigem documentacao e teste antes de migracao:

- baixa;
- exclusao;
- edicao;
- rateio;
- fornecedor;
- produto;
- plano de contas;
- filial;
- usuario logado.

Regras criticas ja mapeadas que devem orientar fases futuras:

| Regra | Origem | Teste esperado |
| --- | --- | --- |
| Duplicata baixada nao pode ser excluida | `NotaCompraDuplicatas.btnExcluirActionPerformed` | excluir baixada deve falhar |
| Vencimento nao pode anteceder emissao | `NotaCompraDuplicatas.btnSalvarActionPerformed` | data invalida rejeitada |
| Data de pagamento nao pode anteceder entrada | `NotaCompraDuplicataBaixa.btnBaixarActionPerformed` | baixa com data invalida rejeitada |
| Baixa cria `Extrato` e atualiza duplicata | `NotaCompraDuplicataBaixa.btnBaixarActionPerformed` | transacao atomica |
| Cheque baixa duplicatas selecionadas | `EmitirCheques.btnLancarActionPerformed` | baixa multipla com rollback |
| Rateio nao pode exceder valor restante | `NotaCompraRateio.btnSalvarActionPerformed` | valor maior que restante rejeitado |
| Permissao de caixa depende de usuario e centro de custo | `PagamentoCaixa.verificaPermissaoAlteraCaixa` | usuario sem permissao bloqueado |

## Referencias canonicas

Agentes de planejamento e execucao devem ler estas referencias antes de alterar
codigo ou criar novos planos:

- `.planning/phases/05-propor-arquitetura-do-salome-core/05-CONTEXT.md`
- `.planning/phases/05-propor-arquitetura-do-salome-core/05-RESEARCH.md`
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/codebase/STACK.md`
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/INTEGRATIONS.md`
- `.planning/codebase/USUARIO-ACESSO-MAPA.md`
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`

## Checklist de revisao

- `ARCH-01`: existe uma proposta de arquitetura revisavel para `salome-core`
  antes da criacao do projeto.
- `ARCH-02`: a proposta separa claramente `ui`, `application`, `domain`,
  `infrastructure` e `security`.
- `ARCH-03`: a proposta impede regra pesada e SQL dentro de Views Vaadin.
- `ARCH-04`: a proposta define que Services chamam Repositories/Adapters para
  acesso ao banco legado.

Checklist operacional:

- O documento nao cria codigo de aplicacao.
- O documento nao cria migration.
- O documento nao altera `salome-legacy`.
- O documento preserva read-only first.
- O documento preserva que read-only first nao reduz o escopo final: Contas a
  Pagar deve ser migrado completo.
- O documento documenta Spring Security com adapter legado.
- O documento cita as referencias canonicas de mapeamento.
- O documento prepara futuras fases sem liberar mutacoes agora.
