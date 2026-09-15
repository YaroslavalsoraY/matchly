@echo off
REM Matchly single entry point for Windows.
REM   start.cmd            local mode: check Java 21 and PostgreSQL, start the database in Docker if needed, run the app
REM   start.cmd --docker   everything in Docker: build the image and start database + app
REM   start.cmd --stop     stop Docker containers
REM   start.cmd --test     run automated tests (no PostgreSQL required)
REM Settings come from the .env file (see .env.example) or environment variables.
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if exist .env for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do if not defined %%a set "%%a=%%b"
if not defined MATCHLY_DB_HOST set MATCHLY_DB_HOST=localhost
if not defined MATCHLY_DB_PORT set MATCHLY_DB_PORT=5432
if not defined MATCHLY_DB_NAME set MATCHLY_DB_NAME=matchly_db
if not defined MATCHLY_PORT set MATCHLY_PORT=8080

if "%~1"=="--help" goto :usage
if "%~1"=="-h" goto :usage
if "%~1"=="--stop" ( docker compose down & exit /b %errorlevel% )
if "%~1"=="--docker" (
  docker info >nul 2>&1 || ( echo [matchly] Docker is not available. Install Docker Desktop and start it. & exit /b 1 )
  echo [matchly] Building image and starting database + app. UI: http://localhost:%MATCHLY_PORT%
  docker compose up --build
  exit /b %errorlevel%
)
if "%~1"=="--test" ( call mvnw.cmd test & exit /b %errorlevel% )
if not "%~1"=="" ( echo [matchly] Unknown parameter: %~1 & goto :usage )

java -version >nul 2>&1 || ( echo [matchly] Java not found. Install JDK 21 (README.md, step 1) or use start.cmd --docker & exit /b 1 )
for /f "tokens=3" %%v in (java -version 2^>^&1 ^| findstr /i "version") do set JAVA_VER=%%v
set JAVA_VER=%JAVA_VER:"=%
for /f "delims=." %%m in ("%JAVA_VER%") do set JAVA_MAJOR=%%m
if %JAVA_MAJOR% LSS 21 ( echo [matchly] Java 21 or newer is required, found %JAVA_VER% & exit /b 1 )
echo [matchly] Java %JAVA_VER% found

powershell -NoProfile -Command "if (Test-NetConnection -ComputerName $env:MATCHLY_DB_HOST -Port $env:MATCHLY_DB_PORT -InformationLevel Quiet -WarningAction SilentlyContinue) { exit 0 } else { exit 1 }"
if not errorlevel 1 (
  echo [matchly] PostgreSQL is reachable on %MATCHLY_DB_HOST%:%MATCHLY_DB_PORT%
  goto :run
)
echo [matchly] PostgreSQL is not reachable on %MATCHLY_DB_HOST%:%MATCHLY_DB_PORT%
docker info >nul 2>&1 || ( echo [matchly] Neither PostgreSQL nor Docker is available. See README.md, step 2. & exit /b 1 )
echo [matchly] Starting the database in Docker (docker compose up -d db)
docker compose up -d db || exit /b 1
set /a TRIES=0
:waitdb
for /f %%s in (docker inspect --format "{{.State.Health.Status}}" matchly-db 2^>nul) do set DB_STATUS=%%s
if "%DB_STATUS%"=="healthy" goto :dbready
set /a TRIES+=1
if %TRIES% GEQ 40 ( echo [matchly] Database did not become healthy in 40 seconds: docker compose logs db & exit /b 1 )
timeout /t 1 /nobreak >nul
goto :waitdb
:dbready
echo [matchly] Database is ready

:run
if not defined MATCHLY_DB_URL set MATCHLY_DB_URL=jdbc:postgresql://%MATCHLY_DB_HOST%:%MATCHLY_DB_PORT%/%MATCHLY_DB_NAME%
echo [matchly] Starting the app: %MATCHLY_DB_URL%
echo [matchly] UI: http://localhost:%MATCHLY_PORT%   Swagger: http://localhost:%MATCHLY_PORT%/swagger-ui.html
call mvnw.cmd -q spring-boot:run
exit /b %errorlevel%

:usage
echo Usage: start.cmd [--docker ^| --stop ^| --test ^| --help]
echo   (no args)  local mode: Java + PostgreSQL (Docker for the DB if needed), then run the app
echo   --docker   build and run everything in Docker
echo   --stop     stop Docker containers
echo   --test     run automated tests
exit /b 0
