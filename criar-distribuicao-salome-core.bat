@echo off
setlocal EnableExtensions
title Criar distribuicao - Salome Core

set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

set "MAVEN_CMD=%MAVEN_CMD%"
if "%MAVEN_CMD%"=="" if exist "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" set "MAVEN_CMD=C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
if "%MAVEN_CMD%"=="" if exist "C:\dev\IngrationCuboCrm\tools\apache-maven-3.9.12\bin\mvn.cmd" set "MAVEN_CMD=C:\dev\IngrationCuboCrm\tools\apache-maven-3.9.12\bin\mvn.cmd"
if "%MAVEN_CMD%"=="" set "MAVEN_CMD=mvn"

set "JAR_SOURCE=target\salome-core-0.0.1-SNAPSHOT.jar"
set "DIST_ROOT=target\dist"
set "DIST_DIR=%DIST_ROOT%\salome-core"
set "APP_INPUT=%DIST_ROOT%\jpackage-input"
set "APP_IMAGE_DEST=%DIST_ROOT%\app-image"
set "JPACKAGE_CMD=%JPACKAGE_CMD%"
if "%JPACKAGE_CMD%"=="" if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE_CMD=%JAVA_HOME%\bin\jpackage.exe"
if "%JPACKAGE_CMD%"=="" if exist "C:\Program Files\Java\jdk-25.0.3\bin\jpackage.exe" set "JPACKAGE_CMD=C:\Program Files\Java\jdk-25.0.3\bin\jpackage.exe"
if "%JPACKAGE_CMD%"=="" if exist "C:\Program Files\Apache NetBeans\jdk\bin\jpackage.exe" set "JPACKAGE_CMD=C:\Program Files\Apache NetBeans\jdk\bin\jpackage.exe"

echo.
echo ============================================================
echo  Criando distribuicao do Salome Core
echo ============================================================
echo.

echo [1/4] Gerando JAR com Maven...
call "%MAVEN_CMD%" -DskipTests package
if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao gerar o JAR.
    pause
    exit /b 1
)

if not exist "%JAR_SOURCE%" (
    echo.
    echo [ERRO] JAR nao encontrado em %JAR_SOURCE%.
    pause
    exit /b 1
)

echo.
echo [2/4] Preparando pasta %DIST_DIR%...
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%DIST_DIR%"
copy /y "%JAR_SOURCE%" "%DIST_DIR%\salome-core.jar" >nul
copy /y "scripts\salome-core.bat" "%DIST_DIR%\salome-core.bat" >nul
copy /y "docs\setup\salome-core-local.md" "%DIST_DIR%\README.md" >nul

echo.
echo [3/4] Tentando gerar SalomeCore.exe com jpackage...
if "%JPACKAGE_CMD%"=="" (
    where jpackage >nul 2>nul
    if errorlevel 1 goto no_jpackage
    set "JPACKAGE_CMD=jpackage"
)

if exist "%APP_INPUT%" rmdir /s /q "%APP_INPUT%"
if exist "%APP_IMAGE_DEST%" rmdir /s /q "%APP_IMAGE_DEST%"
mkdir "%APP_INPUT%"
mkdir "%APP_IMAGE_DEST%"
copy /y "%DIST_DIR%\salome-core.jar" "%APP_INPUT%\salome-core.jar" >nul

"%JPACKAGE_CMD%" ^
  --type app-image ^
  --name SalomeCore ^
  --input "%APP_INPUT%" ^
  --main-jar salome-core.jar ^
  --dest "%APP_IMAGE_DEST%" ^
  --arguments "--spring.profiles.active=local"

if errorlevel 1 goto jpackage_failed

echo.
echo [4/4] Distribuicao criada.
echo.
echo Pasta com BAT:
echo   %ROOT_DIR%%DIST_DIR%
echo.
echo Executavel jpackage:
echo   %ROOT_DIR%%APP_IMAGE_DEST%\SalomeCore\SalomeCore.exe
echo.
pause
exit /b 0

:no_jpackage
echo [AVISO] jpackage nao encontrado. A distribuicao BAT foi criada.
goto bat_only

:jpackage_failed
echo [AVISO] jpackage falhou. A distribuicao BAT foi criada.
goto bat_only

:bat_only
echo.
echo [4/4] Distribuicao criada.
echo.
echo Execute:
echo   %ROOT_DIR%%DIST_DIR%\salome-core.bat
echo.
pause
exit /b 0
