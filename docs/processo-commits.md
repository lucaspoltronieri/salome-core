# Padrão de commits do Salome Core

## Objetivo

Manter pontos de recuperação pequenos e rastreáveis durante o trabalho no
`salome-core`, sem esperar o fim de uma tarefa grande para registrar tudo.

## Quando criar um commit

O agente deve criar um commit checkpoint quando concluir uma unidade coerente,
por exemplo:

- uma regra de negócio documentada;
- uma tela, rota ou serviço funcional;
- uma consulta/repositório com testes;
- uma correção validada;
- uma planilha ou relatório solicitado, quando o artefato for parte oficial do
  projeto.

Não deve criar commit a cada linha alterada nem agrupar mudanças sem relação.
Antes do commit, deve executar a validação proporcional à mudança e conferir o
diff e os arquivos staged.

## Mensagens

Usar o formato:

`tipo(modulo): descrição curta no imperativo`

Tipos aceitos: `feat`, `fix`, `docs`, `refactor`, `test`, `build` e `chore`.

Exemplos:

- `docs(crm): registra regra de clientes destinatarios`
- `feat(hub-crm): adiciona consulta de clientes legados`
- `fix(financeiro): corrige filtro de CT-es cancelados`

## Segurança e escopo

O agente não deve fazer commit automático cego. Deve deixar fora do stage:

- credenciais e segredos;
- arquivos temporários e dumps;
- `node_modules/`, `target/` e caches;
- planilhas de análise temporárias;
- alterações de outros trabalhos em andamento.

Quando houver arquivos misturados, o agente deve fazer stage seletivo. Se não
for possível separar com segurança, deve informar o usuário antes de commitar.

## Limite da automação

Não existe um agente residente observando o diretório continuamente. Este
padrão orienta o agente ativo em cada tarefa a criar checkpoints nos momentos
acima. Um commit só deve ser criado após a alteração realmente estar validada.
