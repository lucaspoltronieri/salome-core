# Runbook de Deploy — Torre de Controle

> **Política de ambiente:** nada roda local — tudo é buildado/testado no servidor.
> Ver **[ambiente-execucao.md](ambiente-execucao.md)**.

Procedimento padrão para buildar/deployar a Torre **no servidor dedicado** (o build não
roda na máquina local: não há Maven nem Flutter SDK instalados aqui).

> ⚠️ Este NÃO é o VPS financeiro (Hostinger `191.101.235.119`). A Torre tem servidor próprio.

## Servidor
- **Host:** `187.127.32.124` (Ubuntu 24.04, "Hostinger dedicado" da Torre)
- **Backend:** systemd `salome-torre`, porta **8789**, JAR em `/opt/salome-torre/salome-core.jar`
- **Fonte/build:** `/opt/salome-torre/build`
- **Stack:** Temurin JDK 25 (`/usr/lib/jvm/temurin-25-jdk-amd64`), Maven 3.8.7, MySQL 8 local
- **App aponta para:** `http://187.127.32.124:8789` (APK buildado com `--dart-define=TORRE_BASE_URL=...`)

## Credencial (acesso)
- Usuário **root**, senha guardada **criptografada (DPAPI)** em `secrets/torre-server-root.cred.xml`
  (só descriptografa nesta máquina/usuário; pasta `secrets/` está no `.gitignore`).
- Recriar a credencial (se sumir):
  ```powershell
  New-Item -ItemType Directory -Force secrets | Out-Null
  $sec = ConvertTo-SecureString '<SENHA_ROOT>' -AsPlainText -Force
  (New-Object System.Management.Automation.PSCredential('root',$sec)) |
    Export-Clixml secrets\torre-server-root.cred.xml
  ```
- Acesso via **Posh-SSH** (módulo já instalado):
  ```powershell
  Import-Module Posh-SSH
  $cred = Import-Clixml secrets\torre-server-root.cred.xml
  $s = New-SSHSession -ComputerName 187.127.32.124 -Credential $cred -AcceptKey
  ```

## Versão
- Versão no `pom.xml` (`<version>`). Hoje **1.1.0** → jar `salome-core-1.1.0.jar`.
- Se mudar a versão do pom, ajustar o nome do jar nos comandos abaixo.

## A) Redeploy do BACKEND (após mudar Java)
```powershell
# 1. empacota só o necessário (pom + src) e envia
$tmp = "$env:TEMP\torre-deploy-src.tar.gz"
tar -czf $tmp -C . pom.xml src
$cred = Import-Clixml secrets\torre-server-root.cred.xml
Set-SCPItem -ComputerName 187.127.32.124 -Credential $cred -AcceptKey -Path $tmp -Destination /root -NewName deploy-src.tar.gz

# 2. extrai, builda (skip tests) e reinicia o serviço
$s = New-SSHSession -ComputerName 187.127.32.124 -Credential $cred -AcceptKey
Invoke-SSHCommand -SSHSession $s -TimeOut 600 -Command @'
cd /opt/salome-torre/build && \
tar xzf /root/deploy-src.tar.gz && \
JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64 mvn -B -DskipTests clean package && \
cp target/salome-core-1.1.0.jar /opt/salome-torre/salome-core.jar && \
systemctl restart salome-torre && sleep 3 && systemctl is-active salome-torre
'@
Remove-SSHSession -SSHSession $s
```
Validar: `curl http://187.127.32.124:8789/...` ou `systemctl is-active salome-torre` = `active`.

## B) Build do APK (após mudar o app Flutter)
O build Android roda **no servidor** (Docker Linux nativo, 16GB; o Docker do Windows estoura OOM).
```powershell
# sobe lib + pubspec + analysis_options + script de build
$cred = Import-Clixml secrets\torre-server-root.cred.xml
$tmp = "$env:TEMP\torre-app.tar.gz"
tar -czf $tmp -C mobile lib pubspec.yaml analysis_options.yaml _server_build.sh
Set-SCPItem -ComputerName 187.127.32.124 -Credential $cred -AcceptKey -Path $tmp -Destination /root -NewName torre-app.tar.gz
$s = New-SSHSession -ComputerName 187.127.32.124 -Credential $cred -AcceptKey
Invoke-SSHCommand -SSHSession $s -TimeOut 1200 -Command @'
rm -rf /opt/torre-app && mkdir -p /opt/torre-app && \
tar xzf /root/torre-app.tar.gz -C /opt/torre-app && \
mv /opt/torre-app/_server_build.sh /opt/torre-app/_build.sh && \
docker run --rm -v /opt/torre-app:/work -w /work ghcr.io/cirruslabs/flutter:stable bash /work/_build.sh && \
ls -la /opt/torre-app/build/app/outputs/flutter-apk/app-release.apk
'@
# traz o apk
Get-SCPItem -ComputerName 187.127.32.124 -Credential $cred -AcceptKey -PathType File `
  -Path /opt/torre-app/build/app/outputs/flutter-apk/app-release.apk -Destination mobile\torre.apk
Remove-SSHSession -SSHSession $s
```
Entregar `mobile/torre.apk` direto (NÃO publicar download; ~63MB).

## Notas / armadilhas
- O `mvn package` valida que o **backend compila** (substitui o `mvn` que falta localmente).
- O `flutter build` valida o **app** (substitui o `flutter analyze` que falta localmente).
- PowerShell "come" aspas ao passar script pro `docker.exe` no Windows — por isso o script vai
  como arquivo (`_build.sh`), não inline.
- O `secrets/torre-server-root.cred.xml` é DPAPI: só funciona nesta máquina/usuário.
