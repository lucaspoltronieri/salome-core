#!/usr/bin/env bash
#
# Deploy do salome-core no servidor (Ubuntu/Hostinger).
# Roda NO servidor, a partir de /opt/salome-core:
#
#     bash deploy/deploy-server.sh
#
# Faz: atualiza o codigo (origin/main), recompila, aponta o symlink estavel
# para o JAR novo, reinicia os servicos e verifica que subiram. Version-agnostic:
# detecta o JAR gerado sozinho, entao bumps de versao nao exigem editar nada aqui
# nem no systemd (os servicos apontam para target/salome-core.jar).
set -euo pipefail

APP_DIR=/opt/salome-core
JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64
PORT=8788                       # porta interna (nginx faz o TLS/BasicAuth na 8787)
SERVICES="salome-web salome-export"

export JAVA_HOME
cd "$APP_DIR"

echo "==> Atualizando codigo (origin/main)"
git fetch origin
git reset --hard origin/main
echo "    HEAD: $(git log --oneline -1)"

echo "==> Build (mvn clean package -DskipTests)"
mvn -q -DskipTests clean package

JAR_PATH=$(ls -1 target/salome-core-*.jar | head -1)
JAR_NAME=$(basename "$JAR_PATH")
echo "==> JAR gerado: $JAR_NAME"

echo "==> Atualizando symlink estavel target/salome-core.jar -> $JAR_NAME"
ln -sfn "$JAR_NAME" target/salome-core.jar

echo "==> Reiniciando servicos: $SERVICES"
systemctl restart $SERVICES
sleep 12

echo "==> Status"
for svc in $SERVICES; do
    printf '    %s = %s\n' "$svc" "$(systemctl is-active "$svc")"
done

echo "==> Verificacao HTTP (127.0.0.1:$PORT)"
echo "    versao: $(curl -s "http://127.0.0.1:$PORT/api/versao")"
for p in fluxo-caixa dre-gerencial dre-cliente dre-filial; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/financeiro/$p/")
    printf '    %-14s = %s\n' "$p" "$code"
done

echo "==> Deploy concluido."
