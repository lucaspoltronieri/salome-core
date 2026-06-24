# Torre de Controle — Deploy

> **Política:** produção é a Hostinger; não mexer no VPS sem pedido; deploy só
> com versão nova (bump SemVer + tag). Esta página descreve **como** subir — a
> execução é manual e sob demanda.

## Topologia

- **Agora (provisório):** a Torre roda como **3º serviço systemd** no servidor
  atual, usando o **mesmo JAR** do `salome-core` com `SALOME_TORRE_ENABLED=true`
  numa porta própria (`8789`), ao lado de:
  - serviço web financeiro (`8787`);
  - serviço de export de manifesto.
- **Depois:** a Torre migra para um **servidor Hostinger dedicado** (evita
  acoplar a escala da Torre ao financeiro). O mesmo unit/env serve no servidor
  novo — só muda host do banco e o `EnvironmentFile`.

## Pré-requisitos no servidor

1. **Banco próprio** `salome_torre` (MySQL RW, separado do legado). Rodar as
   migrações Flyway de `db/migration/torre/` (o app aplica no boot).
2. **Diretório de fotos** persistente: `/var/lib/salome/torre/fotos`
   (`SALOME_TORRE_FOTOS_DIR`), dono `salome`. Fica **fora** do repo e do
   `/target` (já no `.gitignore`: `/dados/`).
3. **Java 25** (mesmo runtime dos serviços atuais).

## Passos

```bash
# 1. Build versionado (gera build-info p/ /api/versao)
mvn -q clean package           # salome-core-1.1.0.jar

# 2. Publicar o JAR no servidor (mesmo artefato dos outros serviços)
scp target/salome-core-1.1.0.jar salome@SERVIDOR:/opt/salome/

# 3. Unit + env (uma vez)
sudo cp docs/torre/salome-torre.service /etc/systemd/system/
sudo cp docs/torre/salome-torre.env.example /etc/salome/torre.env  # editar segredos
sudo mkdir -p /var/lib/salome/torre/fotos && sudo chown salome: /var/lib/salome/torre/fotos

# 4. Subir
sudo systemctl daemon-reload
sudo systemctl enable --now salome-torre
sudo systemctl status salome-torre
curl -s localhost:8789/api/versao
```

## nginx

A Torre **não usa o Basic Auth do nginx** — a autenticação é do próprio Spring
Security (JWT). As rotas da Torre passam direto para o serviço `8789` com
`auth_basic off`:

```nginx
# Torre: auth é do Spring Security, não do nginx.
location /api/torre/ { auth_basic off; proxy_pass http://127.0.0.1:8789; }
location /torre/     { auth_basic off; proxy_pass http://127.0.0.1:8789; }
```

> O Basic Auth do nginx (`/etc/nginx/.htpasswd-salome`) continua protegendo os
> painéis financeiros. Ver memória `basic-auth-nginx-vps`.

> **Pendência (auth do painel):** hoje `TorreSecurityConfig` deixa
> `/api/torre/painel/**` e `/torre/**` como `permitAll` — a TV abre sem login.
> A decisão é fazer o painel seguir o login do Spring Security (JWT). Isso é uma
> mudança de comportamento (a TV passaria a autenticar, ex.: token de painel por
> filial) e fica como follow-up antes de expor o painel fora da rede interna.

## Versão e tag

- `pom.xml` em **1.1.0** (Torre é feature nova). Após validar, criar a tag:
  ```bash
  git tag -a v1.1.0 -m "Torre de Controle - 1.1.0"
  git push origin v1.1.0
  ```

## Variáveis

Ver `salome-torre.env.example` (neste diretório) para a lista completa.
