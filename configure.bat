@echo off
REM    uniCenta oPOS - configuration dialog
REM    Copyright (c) 2009-2014 uniCenta
REM    http://sourceforge.net/projects/unicentaopos
REM
REM    This file is part of uniCenta oPOS, released under the GNU GPL v3.

setlocal
pushd "%~dp0" >nul

REM --- Locate Java 8: JAVA_HOME, then PATH, then common installation folders ---
set "JAVAX="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAX=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVAX for %%J in ("javaw.exe") do set "JAVAX=%%~$PATH:J"
if not defined JAVAX (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-8*") do if exist "%%D\bin\javaw.exe" set "JAVAX=%%D\bin\javaw.exe"
)
if not defined JAVAX (
    for /d %%D in ("%ProgramFiles(x86)%\Eclipse Adoptium\jdk-8*") do if exist "%%D\bin\javaw.exe" set "JAVAX=%%D\bin\javaw.exe"
)
if not defined JAVAX (
    echo.
    echo No se encontro Java 8 ^(javaw.exe^).
    echo Instale un JRE/JDK 8 o defina la variable JAVA_HOME.
    echo.
    pause
    popd
    endlocal
    exit /b 1
)

set "CP=unicentaopos.jar;lib\*;locales;reports"
start "" "%JAVAX%" -cp "%CP%" -Ddirname.path=./ com.openbravo.pos.config.JFrmConfig %*

popd
endlocal
