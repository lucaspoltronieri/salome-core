#!/usr/bin/env bash
# Dev runner da Torre (homologação local). Legado vem do ambiente (SALOME_LEGACY_DB_*).
# Manifesto export DESLIGADO para não tocar a planilha de produção.
set -e
export SALOME_TORRE_ENABLED=true
export SALOME_TORRE_DB_URL="jdbc:mysql://127.0.0.1:3307/salome_torre"
export SALOME_TORRE_DB_USERNAME=root
export SALOME_TORRE_DB_PASSWORD=root
export SALOME_TORRE_JWT_SECRET="segredo-dev-torre-com-mais-de-32-caracteres-123"
export SALOME_SERVER_PORT=8789
export SALOME_LEGACY_DB_ENABLED=true
export SALOME_MANIFESTO_EXPORT_ENABLED=false
exec java -jar target/salome-core-1.1.0.jar
