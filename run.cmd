@echo off
REM Run Matchly on Windows: build and start via Maven Wrapper.
REM Requires JDK 21 (JAVA_HOME or java in PATH) and a reachable PostgreSQL (see README.md).
setlocal
cd /d "%~dp0"
java -version
if errorlevel 1 (
  echo [Matchly] Java not found. Install JDK 21 and set JAVA_HOME.
  exit /b 1
)
call mvnw.cmd -q spring-boot:run %*
endlocal
