@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo  tvsOS - build do APK
echo ============================================
echo.

where gradle >nul 2>nul
if errorlevel 1 goto nogradle

echo Gradle encontrado. Rodando assembleRelease...
echo.
gradle assembleRelease
if errorlevel 1 goto failed

echo.
echo ============================================
echo  APK gerado em:
echo  app\build\outputs\apk\release\
echo ============================================
dir /b app\build\outputs\apk\release\*.apk
pause
exit /b 0

:nogradle
echo Gradle nao encontrado no PATH.
echo.
echo Escolha um caminho:
echo   1^) Abrir esta pasta no Android Studio e usar
echo      Build ^> Build Bundle(s)/APK(s) ^> Build APK(s)
echo   2^) Instalar Gradle 8.2+ (https://gradle.org/install/) e rodar este .bat de novo
echo   3^) Subir o projeto no GitHub e rodar o workflow:
echo      .github/workflows/build-apk.yml  ^(nao precisa instalar nada^)
echo.
pause
exit /b 1

:failed
echo.
echo O build falhou. Veja as mensagens acima.
echo Dica: confirme que o ANDROID_SDK_ROOT aponta para o seu SDK do Android
echo e que o "platforms;android-34" esta instalado.
pause
exit /b 1
