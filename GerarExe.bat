@echo off
REM ==================================================================
REM  GerarExe.bat
REM  Converte o EditorBanco.py em um executavel (.exe) usando PyInstaller.
REM  Basta dar duplo clique neste arquivo (no Windows).
REM ==================================================================

chcp 65001 >nul
title Gerador de EXE - EditorBanco
cd /d "%~dp0"

echo ==================================================================
echo   GERADOR DE EXECUTAVEL - EditorBanco
echo ==================================================================
echo.

REM ---- 1) Verifica se o Python esta instalado --------------------
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Python nao foi encontrado no sistema.
    echo.
    echo   Instale o Python em https://www.python.org/downloads/
    echo   e marque a opcao "Add Python to PATH" durante a instalacao.
    echo.
    pause
    exit /b 1
)

echo [OK] Python encontrado:
python --version
echo.

REM ---- 2) Verifica se o arquivo fonte existe ---------------------
if not exist "EditorBanco.py" (
    echo [ERRO] O arquivo EditorBanco.py nao foi encontrado nesta pasta.
    echo        Coloque este .bat na mesma pasta do EditorBanco.py.
    echo.
    pause
    exit /b 1
)

REM ---- 3) Atualiza o pip -----------------------------------------
echo [1/4] Atualizando o pip...
python -m pip install --upgrade pip
echo.

REM ---- 4) Instala as dependencias --------------------------------
echo [2/4] Instalando dependencias (pyinstaller e mysql-connector-python)...
python -m pip install --upgrade pyinstaller mysql-connector-python
if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao instalar as dependencias.
    echo        Verifique sua conexao com a internet e tente novamente.
    echo.
    pause
    exit /b 1
)
echo.

REM ---- 5) Gera o executavel --------------------------------------
echo [3/4] Gerando o executavel (isso pode demorar alguns minutos)...
echo.
python -m PyInstaller --noconfirm --onefile --windowed ^
    --name "EditorBanco" ^
    --hidden-import "mysql.connector" ^
    --hidden-import "mysql.connector.locales.eng" ^
    --hidden-import "mysql.connector.plugins.mysql_native_password" ^
    "EditorBanco.py"

if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao gerar o executavel.
    echo.
    pause
    exit /b 1
)

REM ---- 6) Finaliza -----------------------------------------------
echo.
echo [4/4] Concluido!
echo ==================================================================
echo   O executavel foi gerado em:
echo       %~dp0dist\EditorBanco.exe
echo ==================================================================
echo.
echo   Voce pode copiar o EditorBanco.exe para qualquer pasta ou
echo   computador Windows (o MySQL precisa estar instalado la).
echo.

REM ---- Abre a pasta com o executavel gerado ----------------------
if exist "dist\EditorBanco.exe" (
    start "" explorer "%~dp0dist"
)

pause
