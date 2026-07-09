@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
set "APK=PDV_Pro_v8.0.23.2_RELEASE_ASSINADO.apk"
if not exist "%APK%" set "APK=app\build\outputs\apk\release\app-release.apk"
if not exist "%APK%" (
  echo APK nao encontrado: %APK%
  pause
  exit /b 1
)
if not exist "%SDK_DIR%\build-tools\34.0.0\apksigner.bat" (
  echo apksigner nao encontrado em %SDK_DIR%\build-tools\34.0.0
  pause
  exit /b 1
)
"%SDK_DIR%\build-tools\34.0.0\apksigner.bat" verify --verbose --print-certs "%APK%"
echo.
"%SDK_DIR%\build-tools\34.0.0\zipalign.exe" -c -p 4 "%APK%"

echo Verificando se resources.arsc esta STORED ^(sem compressao^)...
powershell -NoProfile -Command "$apk='%APK%'; Add-Type -AssemblyName System.IO.Compression.FileSystem; $z=[IO.Compression.ZipFile]::OpenRead($apk); $e=$z.Entries | Where-Object { $_.FullName -eq 'resources.arsc' }; if(-not $e){ Write-Error 'resources.arsc nao encontrado'; exit 2 }; if($e.CompressedLength -ne $e.Length){ Write-Error ('resources.arsc comprimido: {0}/{1}' -f $e.CompressedLength,$e.Length); exit 3 }; Write-Host ('OK resources.arsc STORED: {0} bytes' -f $e.Length); $z.Dispose()"
if errorlevel 1 (
  echo ERRO: resources.arsc precisa ficar STORED/sem compressao.
  pause
  exit /b 1
)

echo.
pause
