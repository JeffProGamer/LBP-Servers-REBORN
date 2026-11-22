@echo off
REM ===================================================================
REM  build.cmd - one-shot, repeatable build of the LBPWN sticker.
REM
REM    build.cmd                        (uses %LBPWN_DOMAIN%)
REM    build.cmd lbpreborn.onrender.com
REM    build.cmd lbpreborn.onrender.com --keep-headers
REM
REM  Always: deletes old outputs, compiles from scratch, builds both
REM  resources, then re-parses them through toolkit-0.1.jar. Exits
REM  non-zero if anything fails, so it is safe to call from CI.
REM ===================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "DOMAIN=%~1"
if "%DOMAIN%"=="" set "DOMAIN=%LBPWN_DOMAIN%"
if "%DOMAIN%"=="" (
  echo ERROR: no domain given. Usage: build.cmd ^<host^>
  echo        or set LBPWN_DOMAIN=lbpreborn.onrender.com
  exit /b 2
)

set "EXTRA=%~2"
if not exist "toolkit-0.1.jar" (
  echo ERROR: toolkit-0.1.jar not found next to this script.
  exit /b 2
)

echo [1/4] cleaning previous outputs
if exist BuildSticker.class   del /q BuildSticker.class
if exist VerifyFinal.class    del /q VerifyFinal.class
if exist lbpwn.ff             del /q lbpwn.ff
if exist lbp_reborn_sticker.plan del /q lbp_reborn_sticker.plan
if exist build.out            del /q build.out

echo [2/4] compiling
javac -cp toolkit-0.1.jar BuildSticker.java VerifyFinal.java
if errorlevel 1 (
  echo ERROR: javac failed.
  exit /b 1
)

echo [3/4] building sticker for "%DOMAIN%"
java -cp "toolkit-0.1.jar;." BuildSticker "%DOMAIN%" %EXTRA%
if errorlevel 1 (
  echo ERROR: BuildSticker failed.
  exit /b 1
)

if not exist lbpwn.ff exit /b 1
if not exist lbp_reborn_sticker.plan exit /b 1

echo [4/4] verifying round-trip
java -cp "toolkit-0.1.jar;." VerifyFinal
if errorlevel 1 (
  echo ERROR: verification failed.
  exit /b 1
)

echo.
echo BUILD OK
echo   lbpwn.ff                 ^(SCRIPT resource^)
echo   lbp_reborn_sticker.plan  ^(RPlan^)
endlocal
exit /b 0
