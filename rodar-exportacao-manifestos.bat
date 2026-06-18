@echo off
setlocal EnableExtensions
title Exportacao de Baixas de Manifesto - Salome

cd /d C:\dev\salome-core

set "MAVEN_CMD=C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
set "JAR_PATH=target\salome-core-0.0.1-SNAPSHOT.jar"

for %%V in (
    SALOME_LEGACY_DB_ENABLED
    SALOME_LEGACY_DB_URL
    SALOME_LEGACY_DB_USERNAME
    SALOME_LEGACY_DB_PASSWORD
    SALOME_MANIFESTO_EXPORT_ENABLED
    SALOME_MANIFESTO_EXPORT_CRON
    SALOME_MANIFESTO_EXPORT_SPREADSHEET_ID
    SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH
    SALOME_MANIFESTO_EXPORT_FILIAL_DESTINO_ID
    SALOME_MANIFESTO_EXPORT_BATCH_SIZE
    SALOME_MANIFESTO_EXPORT_DATA_CORTE
) do (
    if not defined %%V (
        for /f "tokens=2,*" %%A in ('reg query HKCU\Environment /v %%V 2^>nul') do set "%%V=%%B"
    )
)

echo.
echo ============================================================
echo  Exportacao de Baixas de Manifesto - Salome Core
echo ============================================================
echo.

if not exist "%MAVEN_CMD%" (
    echo [ERRO] Maven nao encontrado em:
    echo        %MAVEN_CMD%
    echo.
    pause
    exit /b 1
)

if "%SALOME_LEGACY_DB_ENABLED%"=="" goto missing_db_enabled
if "%SALOME_LEGACY_DB_URL%"=="" goto missing_db_url
if "%SALOME_LEGACY_DB_USERNAME%"=="" goto missing_db_username
if "%SALOME_LEGACY_DB_PASSWORD%"=="" goto missing_db_password
if "%SALOME_MANIFESTO_EXPORT_ENABLED%"=="" goto missing_export_enabled
if "%SALOME_MANIFESTO_EXPORT_SPREADSHEET_ID%"=="" goto missing_sheet_id
if "%SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH%"=="" goto missing_credentials_path

if not exist "%SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH%" (
    echo [ERRO] Arquivo JSON da conta de servico nao encontrado:
    echo        %SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH%
    echo.
    echo Crie a conta de servico no Google Cloud, baixe o JSON e salve nesse caminho.
    echo Depois compartilhe a Google Sheet com o client_email desse JSON.
    echo.
    pause
    exit /b 1
)

echo [1/2] Gerando JAR...
call "%MAVEN_CMD%" -DskipTests package
if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao gerar o JAR.
    pause
    exit /b 1
)

if not exist "%JAR_PATH%" (
    echo.
    echo [ERRO] JAR nao encontrado em %JAR_PATH%.
    pause
    exit /b 1
)

echo.
echo [2/2] Iniciando automacao. Deixe esta janela aberta.
echo A consulta ao banco legado roda conforme SALOME_MANIFESTO_EXPORT_CRON.
echo.
set "SALOME_WEB_APPLICATION_TYPE=none"
java -jar "%JAR_PATH%" --spring.profiles.active=local

echo.
echo Aplicacao encerrada.
pause
exit /b 0

:missing_db_enabled
set "MISSING_VAR=SALOME_LEGACY_DB_ENABLED"
goto missing_config

:missing_db_url
set "MISSING_VAR=SALOME_LEGACY_DB_URL"
goto missing_config

:missing_db_username
set "MISSING_VAR=SALOME_LEGACY_DB_USERNAME"
goto missing_config

:missing_db_password
set "MISSING_VAR=SALOME_LEGACY_DB_PASSWORD"
goto missing_config

:missing_export_enabled
set "MISSING_VAR=SALOME_MANIFESTO_EXPORT_ENABLED"
goto missing_config

:missing_sheet_id
set "MISSING_VAR=SALOME_MANIFESTO_EXPORT_SPREADSHEET_ID"
goto missing_config

:missing_credentials_path
set "MISSING_VAR=SALOME_MANIFESTO_EXPORT_CREDENTIALS_PATH"
goto missing_config

:missing_config
echo [ERRO] Variavel obrigatoria sem preencher: %MISSING_VAR%
echo.
echo Para configurar tudo, execute:
echo   powershell -ExecutionPolicy Bypass -File C:\dev\salome-core\configurar-exportacao-manifestos.ps1
echo.
echo Se faltar apenas a senha do banco, execute no PowerShell:
echo   $p = Read-Host "Digite a senha do MySQL lucas" -AsSecureString
echo   $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($p)
echo   $s = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b)
echo   [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)
echo   [Environment]::SetEnvironmentVariable("SALOME_LEGACY_DB_PASSWORD", $s, "User")
echo.
pause
exit /b 1
