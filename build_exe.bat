@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================================
echo   PDV Pro - Gerador de executaveis (.exe)
echo   Gera: PdvPro.exe  e  print_server.exe
echo ============================================================
echo.

rem ---- Descobrir o Python (python ou py) ----
set "PY="
where python >nul 2>&1 && set "PY=python"
if not defined PY (
    where py >nul 2>&1 && set "PY=py -3"
)
if not defined PY (
    echo [ERRO] Python nao encontrado no PATH.
    echo Instale o Python e marque "Add Python to PATH", ou rode este .bat
    echo a partir da pasta do Python.
    pause
    exit /b 1
)
echo Usando Python: %PY%
echo.

rem ---- Garantir dependencias de build e runtime ----
echo Instalando/atualizando PyInstaller e dependencias...
%PY% -m pip install --upgrade pip >nul 2>&1
%PY% -m pip install --upgrade pyinstaller >nul 2>&1
%PY% -m pip install mysql-connector-python pillow pywin32 >nul 2>&1
echo.

rem ---- Limpar builds anteriores ----
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
if exist PdvPro.spec del /q PdvPro.spec
if exist print_server.spec del /q print_server.spec

rem ============================================================
rem   1) PdvPro.exe  (aplicacao com interface - sem console)
rem ============================================================
echo ------------------------------------------------------------
echo  [1/2] Gerando PdvPro.exe (interface grafica)...
echo ------------------------------------------------------------
%PY% -m PyInstaller --noconfirm --clean --onefile --noconsole ^
  --name PdvPro ^
  --collect-all mysql.connector ^
  --hidden-import PIL._tkinter_finder ^
  --hidden-import win32print ^
  --hidden-import win32ui ^
  --hidden-import win32con ^
  --hidden-import win32gui ^
  PdvPro.py
if errorlevel 1 (
    echo [ERRO] Falha ao gerar PdvPro.exe
    pause
    exit /b 1
)

rem ============================================================
rem   2) print_server.exe  (servidor de impressao - com console)
rem ============================================================
echo.
echo ------------------------------------------------------------
echo  [2/2] Gerando print_server.exe (servidor de impressao)...
echo ------------------------------------------------------------
%PY% -m PyInstaller --noconfirm --clean --onefile ^
  --name print_server ^
  --hidden-import win32print ^
  print_server.py
if errorlevel 1 (
    echo [ERRO] Falha ao gerar print_server.exe
    pause
    exit /b 1
)

rem ---- Copiar o modelo .fr3 para junto dos executaveis ----
if exist cupom.fr3 copy /y cupom.fr3 dist\cupom.fr3 >nul

echo.
echo ============================================================
echo   CONCLUIDO!
echo   Os executaveis estao na pasta:  %CD%\dist
echo     - dist\PdvPro.exe
echo     - dist\print_server.exe
echo     - dist\cupom.fr3  (modelo do servidor de impressao)
echo ============================================================
echo.
echo Dica: mantenha PdvPro.exe, print_server.exe e cupom.fr3 na
echo       mesma pasta. Os arquivos de configuracao/log serao
echo       criados ao lado dos executaveis.
echo.
pause
endlocal
