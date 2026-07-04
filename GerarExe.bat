@echo off
REM ============================================================================
REM  GerarExe.bat - Converte o GeradorLicenca.py em GeradorLicenca.exe
REM  Uso do fabricante (Windows). Basta dar duplo clique neste arquivo.
REM
REM  Requisitos: Python 3 instalado (https://www.python.org/downloads/)
REM              Marque "Add Python to PATH" na instalacao.
REM ============================================================================
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"

echo.
echo ============================================================
echo   GERADOR DE LICENCAS - Build do .EXE (PyInstaller)
echo ============================================================
echo.

REM --- Verifica se o script existe ---------------------------------------------
if not exist "GeradorLicenca.py" (
    echo [ERRO] Nao encontrei "GeradorLicenca.py" nesta pasta:
    echo        %CD%
    echo        Coloque este .bat na MESMA pasta do GeradorLicenca.py.
    echo.
    pause
    exit /b 1
)

REM --- Localiza o Python --------------------------------------------------------
set "PY="
py -3 --version >nul 2>&1 && set "PY=py -3"
if not defined PY (
    python --version >nul 2>&1 && set "PY=python"
)
if not defined PY (
    echo [ERRO] Python 3 nao encontrado no PATH.
    echo        Instale em https://www.python.org/downloads/ e marque
    echo        "Add Python to PATH" durante a instalacao.
    echo.
    pause
    exit /b 1
)
echo [1/3] Python encontrado: 
%PY% --version
echo.

REM --- Garante pip + PyInstaller -----------------------------------------------
echo [2/3] Instalando/atualizando PyInstaller...
%PY% -m pip install --upgrade pip >nul 2>&1
%PY% -m pip install --upgrade pyinstaller
if errorlevel 1 (
    echo [ERRO] Falha ao instalar o PyInstaller. Verifique sua conexao com a internet.
    echo.
    pause
    exit /b 1
)
echo.

REM --- Gera o .exe (arquivo unico, sem janela de console) ----------------------
echo [3/3] Gerando o executavel... isso pode levar alguns minutos.
echo.
%PY% -m PyInstaller --onefile --noconsole --clean --name "GeradorLicenca" "GeradorLicenca.py"
if errorlevel 1 (
    echo.
    echo [ERRO] O PyInstaller retornou erro. Veja as mensagens acima.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   CONCLUIDO COM SUCESSO!
echo ============================================================
echo   Executavel gerado em:
echo       %CD%\dist\GeradorLicenca.exe
echo.
echo   Dica: os arquivos de build ("build" e "GeradorLicenca.spec")
echo   podem ser apagados; o que importa e o .exe dentro de "dist".
echo ============================================================
echo.

REM --- Abre a pasta do executavel ----------------------------------------------
if exist "dist\GeradorLicenca.exe" (
    explorer "dist"
)

pause
endlocal
