# Deploy do Hub CRM na VPS

## Banco proprio

Criar o schema e o usuario administrativo fora do legado. As tabelas sao
criadas exclusivamente pelo Flyway ao iniciar a aplicacao.

```sql
CREATE DATABASE salome_hub_crm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'salome_hub_crm'@'127.0.0.1' IDENTIFIED BY 'SEGREDO_FORTE';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON salome_hub_crm.* TO 'salome_hub_crm'@'127.0.0.1';
```

O SQL acima nao deve ser executado no schema `salome_rp` nem no schema do
Nextcloud.

## Ordem de liberacao

1. Rotacionar o token ArpaSuite fornecido no desenvolvimento.
2. Instalar as variaveis de `deploy/salome-hub-crm.env.example` no servico
   `salome-web`, inicialmente com polling desligado.
3. Aplicar a configuracao nginx para o PDF assinado e recarregar o nginx.
4. Implantar o commit desejado com `DEPLOY_REF=origin/feat/hub-crm` durante a
   homologacao; depois da promocao, voltar ao padrao `origin/main`.
5. Abrir `/hub-crm/`, validar o catalogo ArpaSuite e executar a carga inicial.
   O servico processa 10 itens primeiro e so continua se o piloto nao falhar.
6. Conferir organizacoes, pessoas, cards, responsaveis e timelines.
7. Ativar `SALOME_HUB_CRM_POLLING_ENABLED=true` e reiniciar `salome-web`.

## Rollback

Desligar `SALOME_HUB_CRM_POLLING_ENABLED` e `SALOME_HUB_CRM_ENABLED` e reiniciar
o servico. O rollback nao exclui objetos ja criados no ArpaSuite e nunca altera
o banco legado.
