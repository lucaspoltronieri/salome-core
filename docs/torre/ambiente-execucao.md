# Ambiente de Execução — Torre de Controle

> **Orientação oficial do projeto.** Vale para todo o módulo Torre de Controle
> (backend Spring + app Flutter + painel TV).

## Regra principal

**Nada roda local.** Não buildar nem rodar a Torre na máquina de desenvolvimento.
O **padrão é rodar tudo no deploy**, no servidor.

- Não existe Maven nem Flutter SDK instalados na máquina local — e **isso é
  intencional**. Não instalar para "rodar local".
- O `mvn package` no servidor é o que valida que o **backend compila**.
- O `flutter build` no servidor é o que valida que o **app** está OK.
- Validar sempre **no servidor** (build + serviço ativo + endpoint respondendo),
  não num ambiente local.

## Ambiente

- **Homologação = o servidor dedicado da Torre** (`187.127.32.124`, porta
  `8789`). É o ambiente onde estamos conectados e onde tudo é exercitado.
- Esse servidor é o "Hostinger dedicado" da Torre — **não** confundir com o VPS
  financeiro (`191.101.235.119`), que é outra coisa.
- Toda mudança (backend ou app) vira deploy no servidor e é testada lá.

## Por quê

- A Torre é produção real e a stack é fechada; manter um caminho local divergiria
  do que de fato roda.
- Builds pesados (APK Flutter) estouram memória no Docker do Windows; o servidor
  (Docker Linux nativo, 16GB) é o lugar certo.
- Um único caminho de execução = menos "na minha máquina funciona".

## Como rodar / deployar

Ver o procedimento completo em **[deploy-runbook.md](deploy-runbook.md)**:

- **A)** Redeploy do backend (empacota `pom.xml`+`src`, builda no servidor,
  reinicia o systemd `salome-torre`).
- **B)** Build do APK (roda no servidor via container Flutter; traz `torre.apk`).

Credencial de acesso ao servidor: DPAPI em `secrets/torre-server-root.cred.xml`
(só descriptografa nesta máquina/usuário; `secrets/` está no `.gitignore`).
