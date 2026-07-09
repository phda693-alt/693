@echo off
setlocal EnableExtensions
chcp 65001 >nul

cd /d "%~dp0"
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="" set "ROOT=%ROOT:~0,-1%"

set "LOG=%ROOT%\ARQUIVO_LOG_COMPILACAO.txt"
set "FINALAPK=%ROOT%\PDV_Pro_v8.0.23.2_RELEASE_ASSINADO.apk"

if exist "%LOG%" del /f /q "%LOG%" >nul 2>nul
if exist "%FINALAPK%" del /f /q "%FINALAPK%" >nul 2>nul

echo ===========================================================
echo COMPILAR APK RELEASE ASSINADO - PDV PRO v8.0.23.2
echo Android 5.0+ ^| targetSdk 34 ^| Assinatura v1+v2+v3
echo resources.arsc STORED via Gradle noCompress arsc
echo FIX: usa a pasta real do BAT, nao usa %%CD%%
echo ===========================================================
echo.

echo ===========================================================>>"%LOG%"
echo LOG COMPILACAO PDV PRO v8.0.23.2>>"%LOG%"
echo ===========================================================>>"%LOG%"
echo Pasta real do projeto: %ROOT%>>"%LOG%"
echo Data: %DATE% %TIME%>>"%LOG%"
echo.>>"%LOG%"

if not exist "%ROOT%\gradlew.bat" (
    echo ERRO: gradlew.bat nao encontrado na pasta real do projeto.
    echo Pasta verificada:
    echo %ROOT%
    echo ERRO: gradlew.bat nao encontrado em %ROOT%.>>"%LOG%"
    exit /b 1
)

set "ANDROID_SDK=%ANDROID_HOME%"
if "%ANDROID_SDK%"=="" set "ANDROID_SDK=%ANDROID_SDK_ROOT%"
if "%ANDROID_SDK%"=="" set "ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk"

if not exist "%ANDROID_SDK%\platforms\android-34" (
    echo ERRO: Android SDK 34 nao encontrado em:
    echo %ANDROID_SDK%
    echo Instale Android SDK Platform 34 pelo Android Studio.
    echo ERRO: SDK 34 ausente em %ANDROID_SDK%>>"%LOG%"
    exit /b 1
)

set "SDK_PROP=%ANDROID_SDK:\=/%"
echo sdk.dir=%SDK_PROP%>"%ROOT%\local.properties"

set "GRADLE_USER_HOME=C:\PDVGRADLE"
set "TEMP=C:\PDVTEMP"
set "TMP=C:\PDVTEMP"
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Djava.io.tmpdir=C:\PDVTEMP"
if not exist "C:\PDVGRADLE" mkdir "C:\PDVGRADLE" >nul 2>nul
if not exist "C:\PDVTEMP" mkdir "C:\PDVTEMP" >nul 2>nul

echo Java detectado:
java -version
java -version >>"%LOG%" 2>>&1
echo.
echo SDK detectado: %ANDROID_SDK%
echo GRADLE_USER_HOME=%GRADLE_USER_HOME%
echo TEMP=%TEMP%
echo.

echo Limpando caches locais do projeto...
if exist "%ROOT%\.gradle" rmdir /s /q "%ROOT%\.gradle" >nul 2>nul
if exist "%ROOT%\app\build" rmdir /s /q "%ROOT%\app\build" >nul 2>nul
if exist "%ROOT%\build" rmdir /s /q "%ROOT%\build" >nul 2>nul

echo Limpando projeto...
call "%ROOT%\gradlew.bat" --no-daemon clean --stacktrace >>"%LOG%" 2>>&1

echo Compilando assembleRelease...
call "%ROOT%\gradlew.bat" --no-daemon :app:assembleRelease --stacktrace >>"%LOG%" 2>>&1
if errorlevel 1 goto MODERNO

goto COPIAR_APK

:MODERNO
echo.
echo Primeira tentativa falhou. Aplicando modo moderno SEM POWERSHELL...
echo Gradle 8.7 + Android Gradle Plugin 8.6.1.
echo.>>"%LOG%"
echo ===== APLICANDO MODO MODERNO =====>>"%LOG%"

if exist "%ROOT%\_compilador_templates\build.gradle.agp861" copy /Y "%ROOT%\_compilador_templates\build.gradle.agp861" "%ROOT%\build.gradle" >>"%LOG%" 2>>&1
if exist "%ROOT%\_compilador_templates\gradle-wrapper-8.7.properties" copy /Y "%ROOT%\_compilador_templates\gradle-wrapper-8.7.properties" "%ROOT%\gradle\wrapper\gradle-wrapper.properties" >>"%LOG%" 2>>&1

if exist "%ROOT%\.gradle" rmdir /s /q "%ROOT%\.gradle" >nul 2>nul
if exist "%ROOT%\app\build" rmdir /s /q "%ROOT%\app\build" >nul 2>nul
if exist "%ROOT%\build" rmdir /s /q "%ROOT%\build" >nul 2>nul

echo Limpando projeto no modo moderno...
call "%ROOT%\gradlew.bat" --no-daemon clean --stacktrace >>"%LOG%" 2>>&1

echo Compilando assembleRelease no modo moderno...
call "%ROOT%\gradlew.bat" --no-daemon :app:assembleRelease --stacktrace >>"%LOG%" 2>>&1
if errorlevel 1 goto FALHA

goto COPIAR_APK

:COPIAR_APK
set "APK1=%ROOT%\app\build\outputs\apk\release\app-release.apk"
set "APK2=%ROOT%\app\build\outputs\apk\release\app-release-unsigned.apk"

if exist "%APK1%" (
    copy /Y "%APK1%" "%FINALAPK%" >nul
    goto VERIFICAR
)

if exist "%APK2%" (
    copy /Y "%APK2%" "%FINALAPK%" >nul
    goto ASSINAR_MANUAL
)

echo ERRO: APK release nao encontrado apos compilacao.>>"%LOG%"
goto FALHA

:ASSINAR_MANUAL
echo APK unsigned encontrado. Tentando assinar manualmente...>>"%LOG%"
set "BT=%ANDROID_SDK%\build-tools\34.0.0"
if not exist "%BT%\apksigner.bat" goto VERIFICAR
if exist "%BT%\zipalign.exe" (
    copy /Y "%FINALAPK%" "%ROOT%\apk_nao_alinhado.apk" >nul
    "%BT%\zipalign.exe" -f -p 4 "%ROOT%\apk_nao_alinhado.apk" "%FINALAPK%" >>"%LOG%" 2>>&1
)
"%BT%\apksigner.bat" sign --ks "%ROOT%\keystore\pdv-release-new.jks" --ks-key-alias pdv-key --ks-pass pass:pdvpro123 --key-pass pass:pdvpro123 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true "%FINALAPK%" >>"%LOG%" 2>>&1
if errorlevel 1 goto FALHA

:VERIFICAR
if not exist "%FINALAPK%" goto FALHA
set "BT=%ANDROID_SDK%\build-tools\34.0.0"
if exist "%BT%\apksigner.bat" (
    echo Verificando assinatura...
    "%BT%\apksigner.bat" verify --verbose --print-certs "%FINALAPK%" >>"%LOG%" 2>>&1
)

echo.
echo ===========================================================
echo APK COMPILADO COM SUCESSO:
echo %FINALAPK%
echo ===========================================================
echo.
echo APK final: %FINALAPK%>>"%LOG%"
exit /b 0

:FALHA
echo.
echo ERRO NA COMPILACAO GRADLE.
echo Log completo salvo em:
echo %LOG%
echo.
echo DICA: envie as ultimas linhas deste arquivo para diagnostico:
echo %LOG%
echo.
exit /b 1
