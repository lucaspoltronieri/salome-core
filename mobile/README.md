# Torre — App do operador (Flutter)

App Android do operador da Torre de Controle. Consome o backend `/api/torre/*`.
Fluxos: login, descarga de transferência (com destino por CT-e), descarga de
coleta (scan de NF + destino), separação, carregamento, outras atividades,
ocorrências (com foto) e entrar/sair/finalizar/cancelar.

## Build do APK (sem instalar Flutter)

Requer **Docker Desktop** aberto. O build roda no container `ghcr.io/cirruslabs/flutter`
e gera `mobile/torre.apk`. A URL do servidor é **fixada no build**:

```powershell
# IP da sua máquina na LAN (celular físico na mesma Wi-Fi):
./build-apk.ps1 -BaseUrl http://192.168.0.10:8789

# emulador Android (host = 10.0.2.2):
./build-apk.ps1
```

Trocar de servidor (homologação → produção) = rebuildar com outro `-BaseUrl`.

## Rodar / testar

1. Suba o backend de homologação na máquina: `run-torre-dev.sh` (porta 8789,
   MySQL 3307, legado real, `SALOME_MANIFESTO_EXPORT_ENABLED=false`). Garanta a
   filial 2 ativa e um usuário (ex.: `joao`/`senha123`). Os 3 boxes
   (Separação/Distribuição/Transferência) são criados automaticamente ao salvar a filial.
2. Build com o IP da máquina; instale `torre.apk` no celular (mesma Wi-Fi).
3. Login `joao`/`senha123` e rode os fluxos. A câmera (DACTE/NF) e a foto da
   ocorrência só funcionam em device real.

## Notas técnicas

- `lib/config.dart`: `TORRE_BASE_URL` via `--dart-define` (default emulador).
- `android/` é **gerado no build** (não versionado) — ver `.gitignore`.
- O manifesto recebe `INTERNET` + `CAMERA` e `usesCleartextTraffic="true"`
  (homologação é HTTP em LAN; produção deve usar HTTPS).
- Destino na descarga: ver `lib/models/box_destino.dart` (espelha `BoxPadrao` do backend).
