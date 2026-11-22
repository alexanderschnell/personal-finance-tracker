@echo off
rem Builds MyFinancePal if needed, then starts it (no console window).
rem Your data lives in %USERPROFILE%\.myfinancepal\finance.db - never in this folder.
setlocal
cd /d "%~dp0"

set "JAR=target\finance-tracker-1.0-SNAPSHOT.jar"

if not exist "%JAR%" goto build
if "%1"=="rebuild" goto build
goto run

:build
echo Building MyFinancePal...
call mvn -q -B clean package -DskipTests
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)

:run
start "" javaw -jar "%JAR%"
