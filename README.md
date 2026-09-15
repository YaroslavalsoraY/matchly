# Matchly

Сервис знакомств с модулем персональных рекомендаций. Учебный проект: backend на Spring Boot с PostgreSQL,
Spring Security (JWT), Swagger и веб-интерфейсом.

![Лента знакомств](docs/screenshots/02-discover.png)

## Возможности

- Регистрация и вход по email и паролю, JWT, роли `USER` и `ADMIN`.
- Анкета: имя, возраст, пол, кого ищем и в каком возрасте, город, интересы из справочника, фото (хранится в базе).
- Лента «Знакомства»: карточки с объяснением «почему показываем», лайк и пропуск, переключение алгоритма.
- Четыре алгоритма рекомендаций: по интересам, коллаборативная фильтрация, по популярности, гибрид.
- Взаимный лайк создаёт матч и раскрывает контакт собеседника. Матч можно разорвать.
- Панель администратора: статистика, пользователи (блокировка, удаление), справочник интересов, алгоритм по умолчанию.
- Демо-данные при первом запуске: 60 анкет с аватарами, лайками и матчами.
- Swagger UI, логирование в консоль и файл, единый формат ошибок (RFC 9457 `application/problem+json`).

Подробное описание архитектуры и алгоритмов: [docs/REPORT.md](docs/REPORT.md).
Как встроить модуль рекомендаций в другое приложение: [docs/INTEGRATION.md](docs/INTEGRATION.md).

## Стек

Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Actuator), PostgreSQL 16, Flyway,
springdoc-openapi (Swagger UI), Lombok, JUnit 5, Mockito, H2 (только для тестов), интерфейс на HTML/CSS/JavaScript без сборки.

## Быстрый старт

Нужны только JDK 21 и PostgreSQL. Maven ставить не нужно: в проекте есть Maven Wrapper (`mvnw`, `mvnw.cmd`),
который сам скачает нужную версию при первом запуске.

### Шаг 1. JDK 21

**Windows** (PowerShell или cmd):

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

После установки откройте новое окно терминала и проверьте: `java -version` должна показать `21`.
Если команда не найдена, задайте переменную `JAVA_HOME` (например, `C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot`)
и добавьте `%JAVA_HOME%\bin` в `PATH`.

**Linux** (Ubuntu/Debian):

```bash
sudo apt install openjdk-21-jdk
```

Без прав администратора можно распаковать [Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21) в `~/.jdks`
и указать `export JAVA_HOME=~/.jdks/jdk-21...; export PATH=$JAVA_HOME/bin:$PATH` (скрипт `run.sh` находит такой JDK сам).

### Шаг 2. База данных PostgreSQL

Приложению нужна база `matchly_db` и роль `matchly` с паролем `matchly` (значения можно изменить через переменные окружения, см. ниже).
Выберите один из вариантов.

**Вариант A. Docker (Windows с Docker Desktop или Linux):**

```bash
docker compose -f infra/docker-compose.yml up -d
```

**Вариант B. Локальный PostgreSQL на Windows.** Установите PostgreSQL 16
(`winget install PostgreSQL.PostgreSQL.16` или установщик с [postgresql.org](https://www.postgresql.org/download/windows/)),
запомните пароль пользователя `postgres`, затем выполните в PowerShell из папки проекта:

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -f infra\init-db.sql
```

**Вариант C. Локальный PostgreSQL на Linux:**

```bash
sudo apt install postgresql
sudo -u postgres psql -f infra/init-db.sql
```

Без прав root можно поднять кластер от своего пользователя: `infra/pg-local.sh start` (см. комментарии в скрипте),
затем `psql -h localhost -U postgres -f infra/init-db.sql`.

Скрипт `infra/init-db.sql` идемпотентен: повторный запуск ничего не сломает. Таблицы создаёт само приложение (Flyway) при старте.

### Шаг 3. Запуск

**Windows:**

```powershell
.\run.cmd
```

**Linux / macOS:**

```bash
./run.sh
```

Оба скрипта выполняют `mvnw spring-boot:run`. Первый запуск скачивает зависимости (2-3 минуты), дальнейшие занимают секунды.
Альтернатива: собрать jar и запускать его напрямую.

```bash
./mvnw -DskipTests package          # Windows: mvnw.cmd -DskipTests package
java -jar target/matchly.jar
```

При первом старте приложение создаст схему базы, администратора и 60 демо-анкет.

### Шаг 4. Открыть

| Что | Адрес |
|-----|-------|
| Интерфейс | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Проверка состояния | http://localhost:8080/actuator/health |

Учётные записи:

| Роль | Email | Пароль |
|------|-------|--------|
| Администратор | `admin@matchly.local` | `admin123` |
| Демо-пользователи | `demo1@matchly.local` … `demo60@matchly.local` | `demo1234` |

В Swagger нажмите **Authorize** и вставьте токен из ответа `POST /api/auth/login`.

## Настройка

Все параметры задаются переменными окружения; без них используются значения по умолчанию.

| Переменная | По умолчанию | Назначение |
|------------|--------------|------------|
| `MATCHLY_DB_URL` | `jdbc:postgresql://localhost:5432/matchly_db` | JDBC-адрес базы |
| `MATCHLY_DB_USER` | `matchly` | пользователь базы |
| `MATCHLY_DB_PASSWORD` | `matchly` | пароль базы |
| `MATCHLY_PORT` | `8080` | HTTP-порт |
| `MATCHLY_JWT_SECRET` | тестовый ключ | секрет подписи JWT, минимум 32 символа. **Обязательно сменить вне учебной среды** |
| `MATCHLY_JWT_TTL` | `12h` | срок жизни токена |
| `MATCHLY_ADMIN_EMAIL` / `MATCHLY_ADMIN_PASSWORD` | `admin@matchly.local` / `admin123` | администратор, создаваемый при первом запуске |
| `MATCHLY_SEED` | `true` | генерировать демо-данные при первом запуске |
| `LOG_PATH` | `logs` | каталог файлов логов |

Как задать переменную перед запуском:

```powershell
# Windows PowerShell
$env:MATCHLY_DB_PASSWORD = "secret"; .\run.cmd
```

```cmd
:: Windows cmd
set MATCHLY_DB_PASSWORD=secret && run.cmd
```

```bash
# Linux / macOS
MATCHLY_DB_PASSWORD=secret ./run.sh
```

## Тесты

```bash
./mvnw test          # Windows: mvnw.cmd test
```

81 автотест (JUnit 5, Mockito, MockMvc) выполняются на встроенной H2 и не требуют PostgreSQL или Docker,
поэтому одинаково работают на Windows и Linux. Отчёты: `target/surefire-reports`.

Сквозные браузерные тесты интерфейса (Playwright, нужен Node.js) описаны в [e2e/README.md](e2e/README.md).

## Структура проекта

```
src/main/java/com/matchly
├── MatchlyApplication.java   точка входа
├── config/                   настройки (MatchlyProperties), JWT, OpenAPI
├── common/                   базовая сущность, иерархия исключений, обработчик ошибок, утилиты
├── security/                 фильтр безопасности, выдача и проверка JWT
├── auth/                     регистрация, вход, удаление аккаунта
├── user/                     аккаунты и роли
├── admin/                    контроллеры администратора: пользователи, интересы, настройки, статистика
├── interest/                 справочник интересов
├── profile/                  анкеты и фото
├── reaction/                 лайки и пропуски
├── matching/                 матчи
├── recommendation/           модуль рекомендаций: стратегии, контекст, сервис
├── settings/                 настройки, изменяемые администратором
└── bootstrap/                создание администратора и демо-данных при старте
src/main/resources
├── application.yml           конфигурация
├── logback-spring.xml        логирование
├── db/migration/             миграции Flyway (V1..V4)
└── static/                   интерфейс: index.html, css/app.css, js/app.js, js/api.js
src/test/java                 unit- и API-тесты
infra/                        init-db.sql, docker-compose.yml, pg-local.sh
e2e/                          браузерные тесты Playwright
docs/                         отчёт, инструкция по интеграции, скриншоты
```

## Частые проблемы

- **`Connection refused` при старте.** PostgreSQL не запущен или слушает другой порт. Проверьте `pg_isready -h localhost -p 5432`
  (Windows: `"C:\Program Files\PostgreSQL\16\bin\pg_isready.exe"`), при необходимости задайте `MATCHLY_DB_URL`.
- **`password authentication failed for user "matchly"`.** Не выполнен `infra/init-db.sql` или изменён пароль: задайте `MATCHLY_DB_PASSWORD`.
- **Порт 8080 занят.** Запустите с другим портом: `MATCHLY_PORT=8081 ./run.sh` (Windows: `$env:MATCHLY_PORT=8081; .\run.cmd`).
- **Кракозябры в консоли Windows.** Выполните `chcp 65001` перед запуском или смотрите файл `logs/matchly.log` (он всегда в UTF-8).
- **`java` не найдена.** Установите JDK 21 и откройте новое окно терминала, чтобы обновился `PATH`.
