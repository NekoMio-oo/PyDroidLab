@rem
@rem Gradle startup script for Windows (PyDroidLab)
@rem
@if "%DEBUG%"=="" @echo off
setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%
set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVACMD=java.exe
)

if exist "%WRAPPER_JAR%" (
    "%JAVACMD%" -Xmx64m -Xms64m -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
    exit /b !ERRORLEVEL!
)

set GRADLE_VERSION=8.7
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin\auto
set GRADLE_BIN=%DIST_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
    echo Bootstrapping Gradle %GRADLE_VERSION% distribution...
    mkdir "%DIST_DIR%" 2>nul
    powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST_DIR%\gradle.zip'; Expand-Archive -Path '%DIST_DIR%\gradle.zip' -DestinationPath '%DIST_DIR%' -Force; Remove-Item '%DIST_DIR%\gradle.zip'"
)

call "%GRADLE_BIN%" -p "%APP_HOME%" %*
exit /b !ERRORLEVEL!
