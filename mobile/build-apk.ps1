<#
  Builda o APK release da Torre dentro do container Docker do Flutter,
  sem instalar Flutter/Android Studio na máquina. Gera mobile/torre.apk.

  Uso:
    ./build-apk.ps1 -BaseUrl http://192.168.0.10:8789
    ./build-apk.ps1                      # usa o default (emulador: 10.0.2.2:8789)

  Requer Docker Desktop aberto. O <BaseUrl> é fixado no APK (--dart-define);
  trocar de servidor = rebuildar. Para celular físico, use o IP da máquina na LAN
  (mesma Wi-Fi) e mantenha o backend de homologação rodando (run-torre-dev.sh).
#>
param(
  [string]$BaseUrl = "http://10.0.2.2:8789",
  [string]$Image = "ghcr.io/cirruslabs/flutter:stable"
)

$ErrorActionPreference = "Stop"
$mobile = $PSScriptRoot

$script = @'
set -e
git config --global --add safe.directory '*' || true
flutter --version

# Builda na FS nativa do container (/work), não no bind mount Windows (/app):
# o gradle 9 falha em mergeReleaseResources lendo diretórios no mount.
WORK=/work
rm -rf "$WORK"
mkdir -p "$WORK"
cp -r /app/lib /app/pubspec.yaml "$WORK"/
[ -f /app/analysis_options.yaml ] && cp /app/analysis_options.yaml "$WORK"/ || true
cd "$WORK"

# 1) Scaffold do Android (preserva lib/ e pubspec.yaml).
rm -rf /tmp/scaffold
flutter create --org br.com.salome --project-name torre_app --platforms=android /tmp/scaffold
cp -r /tmp/scaffold/android "$WORK"/android

# 2) Permissões + cleartext no manifesto principal (release NÃO traz INTERNET por padrão).
#    Patches ancorados (não quebram o XML): permissões após a linha <manifest>,
#    cleartext no primeiro marcador "<application".
MANIFEST="$WORK"/android/app/src/main/AndroidManifest.xml
sed -i '/<manifest /a\    <uses-permission android:name="android.permission.INTERNET"/>\n    <uses-permission android:name="android.permission.CAMERA"/>' "$MANIFEST"
sed -i '0,/<application/{s#<application#<application android:usesCleartextTraffic="true"#}' "$MANIFEST"

# 2b) Gradle dentro do container: heap baixo + sem daemon (o default -Xmx8G é morto por OOM).
GP="$WORK"/android/gradle.properties
sed -i '/^org.gradle.jvmargs=/d' "$GP"
echo 'org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=1g' >> "$GP"
echo 'org.gradle.daemon=false' >> "$GP"
echo 'org.gradle.workers.max=2' >> "$GP"

# 3) Build.
flutter pub get
flutter build apk --release --dart-define=TORRE_BASE_URL=__BASEURL__

cp "$WORK"/build/app/outputs/flutter-apk/app-release.apk /app/torre.apk
echo "==> APK gerado: mobile/torre.apk (servidor: __BASEURL__)"
'@
$script = $script.Replace("__BASEURL__", $BaseUrl)

Write-Host "Buildando APK (servidor: $BaseUrl) via $Image ..." -ForegroundColor Cyan
# IMPORTANTE: o PowerShell remove aspas ao passar args pra docker.exe (quebra o
# sed do manifesto). Passamos o script via base64 (linha de comando sem aspas).
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($script))
# Volumes de cache (gradle/pub) aceleram rebuilds; build roda em /work (FS nativa).
docker run --rm `
  -v "${mobile}:/app" `
  -v "salome_gradle:/root/.gradle" `
  -v "salome_pubcache:/root/.pub-cache" `
  -w /app $Image bash -lc "echo $b64 | base64 -d | bash"

if ($LASTEXITCODE -eq 0) {
  Write-Host "OK -> $mobile\torre.apk" -ForegroundColor Green
} else {
  Write-Host "Falha no build (exit $LASTEXITCODE)" -ForegroundColor Red
}
