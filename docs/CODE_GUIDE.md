# Путеводитель по коду Matchly

Документ для того, кто открывает проект впервые и хочет разобраться в нём глубоко: как устроен запуск,
как запрос проходит через слои, что делает каждый файл и как файлы связаны между собой.
Читать лучше по порядку: сначала карта и сквозные сценарии, затем файлы по пакетам.

## 1. Карта проекта за две минуты

Matchly — сервис знакомств с модулем рекомендаций. Один процесс Spring Boot отдаёт и REST API, и веб-интерфейс;
данные лежат в PostgreSQL. Всё делится на четыре уровня:

| Уровень | Где лежит | Что делает |
|---------|-----------|------------|
| Запуск и инфраструктура | `start.sh`, `start.cmd`, `Dockerfile`, `docker-compose.yml`, `infra/` | поднимает базу и приложение, задаёт окружение |
| Бэкенд | `src/main/java/com/matchly/**` | контроллеры → сервисы → репозитории → сущности; безопасность; алгоритмы |
| Схема и конфигурация | `src/main/resources/` | `application.yml`, логирование, миграции Flyway |
| Интерфейс | `src/main/resources/static/` | одностраничное приложение на чистом JavaScript, ходит в API с JWT |
| Проверки | `src/test/**`, `e2e/` | 89 JUnit-тестов, два браузерных сценария Playwright |

Каждый предметный пакет бэкенда устроен одинаково, и, разобравшись в одном, вы поймёте все:

```
<пакет>/
├── XxxController.java     принимает HTTP, валидирует DTO, возвращает DTO
├── XxxService.java        интерфейс с Javadoc: что умеет модуль
├── XxxServiceImpl.java    реализация: транзакции, бизнес-правила, логирование
├── XxxRepository.java     Spring Data JPA: запросы к базе
├── Xxx.java               сущность JPA: хранит состояние и свои инварианты
├── XxxMapper / Assembler  превращает сущности в DTO
└── dto/                   неизменяемые record-ы запросов и ответов
```

Правило, которое соблюдается везде: **сущности не покидают сервисный слой**. Контроллер видит только DTO.
Поэтому изменения в базе не ломают контракт API, и наоборот.

## 2. Сквозные сценарии: что происходит на самом деле

### 2.1. Запуск приложения

1. `start.sh` (или `start.cmd`) проверяет Java 21, стучится в PostgreSQL по TCP; если базы нет и есть Docker, выполняет
   `docker compose up -d db` и ждёт, пока контейнер станет `healthy`. Затем запускает `./mvnw spring-boot:run`.
2. Spring Boot читает `application.yml`. Значения вида `${MATCHLY_DB_URL:...}` берутся из переменных окружения,
   а при их отсутствии из значения после двоеточия.
3. Flyway находит `db/migration/V1..V4` и применяет недостающие миграции. Hibernate работает в режиме `validate`:
   сравнивает сущности со схемой и падает при расхождении (защита от «забыл миграцию»).
4. `MatchlyProperties` валидируется: короткий JWT-секрет остановит запуск с понятной ошибкой.
5. `StrategyRegistry` собирает все стратегии рекомендаций и проверяет, что для каждого значения `StrategyType` есть реализация.
6. `AdminInitializer` (порядок 1) создаёт администратора, если его нет. `DemoDataSeeder` (порядок 2) при включённом
   `matchly.seed.enabled` и отсутствии `demo1@matchly.local` создаёт 60 анкет с аватарами, лайками и матчами.
7. Tomcat начинает слушать порт. Интерфейс доступен на `/`, API на `/api/**`, Swagger на `/swagger-ui.html`.

### 2.2. Регистрация и вход

`POST /api/auth/register` → `AuthController.register` → `AuthServiceImpl.register`: email приводится к нижнему регистру
(`User.normalizeEmail`), проверяется уникальность, пароль хэшируется bcrypt (`PasswordEncoder` из `SecurityConfig`),
создаётся `User.register(...)`, `JwtTokenService.issue` подписывает токен HMAC-SHA256. Ответ `AuthResponse` содержит
токен, срок действия и `UserResponse`.

Дальше клиент шлёт заголовок `Authorization: Bearer <jwt>`. Его обрабатывает цепочка Spring Security из `SecurityConfig`:
`NimbusJwtDecoder` (создан в `JwtConfig`) проверяет подпись и срок, затем `JwtUserAuthenticationConverter` **читает пользователя
из базы** по `sub` (это id), отвергает удалённого или заблокированного и кладёт в контекст `AuthenticatedUser(id, email, role)`
с ролью `ROLE_USER` или `ROLE_ADMIN`. Именно поэтому блокировка действует мгновенно, а контроллеры получают готовый принципал
через `@AuthenticationPrincipal AuthenticatedUser user`.

Если что-то не так, `ProblemDetailAuthenticationEntryPoint` / `ProblemDetailAccessDeniedHandler` не пишут ответ сами,
а передают исключение в `GlobalExceptionHandler`, чтобы 401 и 403 имели тот же формат JSON, что и остальные ошибки.

### 2.3. Запрос рекомендаций

`GET /api/recommendations?strategy=HYBRID&limit=10` → `RecommendationController` ограничивает `limit` диапазоном 1..50 →
`RecommendationServiceImpl.recommend`:

1. `ProfileRepository.findWithInterestsByUserId` загружает анкету зрителя вместе с интересами (иначе ленивая коллекция
   упала бы за пределами транзакции, потому что `open-in-view: false`).
2. Если стратегия не передана, `SettingsService.getDefaultStrategy()` читает её из таблицы `app_settings`.
3. `selectCandidates`: все видимые анкеты активных пользователей минус сам зритель, минус те, на кого уже была реакция
   (`ReactionRepository.findTargetIdsBySourceId`), минус несовместимые по полу и возрасту (`Profile.isMutuallyCompatibleWith`).
4. `RecommendationContext.build` один раз загружает матрицу лайков (`findEdgesByType`) и счётчики лайков (`countByTargetGroupedByType`).
5. Выбранная `RecommendationStrategy.rank` оценивает кандидатов числом 0..1 и списком причин.
6. Сортировка по оценке, затем по новизне; `ProfileCardAssembler.toCards` одним запросом подтягивает сведения о фото.

### 2.4. Лайк и матч

`POST /api/reactions {targetProfileId, type}` → `ReactionServiceImpl.react`: находит анкету зрителя и видимую анкету цели,
запрещает реакцию на себя, создаёт или изменяет `Reaction` (пара уникальна). Если это `LIKE` и встречный `LIKE` уже есть,
создаёт `Match.between(a, b)` (пара упорядочена по id, поэтому дубликат невозможен) и возвращает `MatchResponse` с контактом.
`SKIP` после матча удаляет матч. Интерфейс по флагу `matched` показывает окно «Это матч!».

## 3. Корень репозитория

| Файл | Назначение |
|------|------------|
| `pom.xml` | Сборка Maven. Родитель `spring-boot-starter-parent 4.1.1` задаёт версии. Зависимости: webmvc, data-jpa, security, oauth2-resource-server (JWT), validation, flyway (+ `flyway-database-postgresql`), actuator, springdoc 3.1.1, драйвер PostgreSQL, H2 (только `test`), Lombok, тестовые стартеры. `finalName` = `matchly`, поэтому jar всегда `target/matchly.jar`. Плагин `maven-dependency-plugin:properties` даёт путь к `mockito-core`, а `surefire` подключает его как java-агент (`-javaagent`), иначе JDK 21 предупреждает о динамической загрузке. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper: скачивает Maven 3.9.16 при первом запуске. Установленный Maven не нужен. |
| `start.sh` | Единая точка входа для Linux/macOS. Функции: `ensure_java` (ищет JDK в `JAVA_HOME`, `PATH`, `~/.jdks/jdk-21*`, требует версию ≥ 21), `db_reachable` (проверка TCP через `/dev/tcp`, без клиентских утилит), `ensure_database` (Docker → `docker compose up -d db` и ожидание `healthy`; иначе `infra/pg-local.sh`; иначе понятная ошибка), режимы `--docker`, `--stop`, `--test`, `--help`. Читает `.env` через `set -a; . ./.env`. |
| `start.cmd` | То же для Windows на чистом ASCII (кириллица в cmd ломается на кодовой странице). Проверка порта через PowerShell `Test-NetConnection`, ожидание базы циклом `docker inspect`. Переменные из `.env` подхватываются циклом `for /f`. |
| `Dockerfile` | Многоэтапная сборка: этап `build` на `eclipse-temurin:21-jdk` сначала копирует только `pom.xml` и wrapper и выполняет `dependency:go-offline` (этот слой кэшируется), потом копирует `src` и собирает jar. Этап запуска на `eclipse-temurin:21-jre`: устанавливается `curl` для healthcheck, создаётся пользователь `matchly` (не root), `ENTRYPOINT java -jar matchly.jar`. |
| `docker-compose.yml` | Две службы. `db`: `postgres:16`, переменные `POSTGRES_*` из `.env`, healthcheck `pg_isready`, том `matchly-pgdata`. `app`: собирается из `Dockerfile`, стартует после `db: service_healthy`, получает `MATCHLY_DB_URL=jdbc:postgresql://db:5432/...` (имя службы как хост), healthcheck по `/actuator/health`, том для логов. Порты наружу задаются `MATCHLY_PORT` и `MATCHLY_DB_PORT`. |
| `.dockerignore` | Не копировать в контекст сборки `target/`, `logs/`, `e2e/`, `docs/`, `.git/`, markdown. |
| `.env.example` | Шаблон всех переменных. Скопируйте в `.env`: его читают и compose, и скрипты. Сам `.env` в `.gitignore`. |
| `.gitignore` | Исключает `target/`, `logs/`, IDE-каталоги, `node_modules/`, `.env`. |
| `.gitattributes` | Нормализация переводов строк: `*.sh` и `mvnw` всегда LF, `*.cmd` всегда CRLF, иначе скрипты сломаются на «чужой» ОС. |
| `README.md` | Инструкция пользователя: установка на Windows и Linux, единая точка входа, адреса, учётные записи, переменные, структура, типичные проблемы. |

## 4. `infra/`

| Файл | Назначение |
|------|------------|
| `infra/init-db.sql` | Идемпотентно создаёт роль `matchly` и базу `matchly_db` через `\gexec` (условие `WHERE NOT EXISTS`). Нужен при локальной установке PostgreSQL; в Docker роль и базу создаёт сам образ postgres. |
| `infra/pg-local.sh` | Управление кластером PostgreSQL, запущенным от обычного пользователя (без root и Docker): `start`, `stop`, `restart`, `status`, `psql`. Кластер живёт в `~/pgdata`, сокет в `/tmp`. Использован при разработке; `start.sh` вызывает его как запасной вариант. |

## 5. `src/main/resources/`

### `application.yml`

Единственный конфигурационный файл. Разделы:

- `spring.datasource` — адрес и учётные данные из `MATCHLY_DB_*`.
- `spring.jpa.hibernate.ddl-auto: validate` — схему меняют только миграции; `open-in-view: false` — сессия JPA не растягивается на весь HTTP-запрос, поэтому всё, что нужно контроллеру, сервис должен загрузить сам (отсюда `@EntityGraph` и `join fetch` в репозиториях).
- `spring.web.locale: en` + `locale-resolver: fixed` — сообщения валидации всегда на английском независимо от браузера; интерфейс переводит их сам. Это делает контракт API детерминированным.
- `spring.servlet.multipart.max-file-size: 2MB` — Tomcat отвергает большие файлы с 413 ещё до сервиса.
- `springdoc.*` — пути Swagger UI и OpenAPI.
- `matchly.*` — собственные настройки, биндятся в `MatchlyProperties`: секрет и срок JWT, генерация демо-данных, администратор.

### `logback-spring.xml`

Консоль + файл `${LOG_PATH:-logs}/matchly.log` с ежедневной ротацией и хранением 14 дней. Файловый аппендер включён только
вне профиля `test` (`<springProfile name="!test">`), пакет `com.matchly` пишет на уровне `DEBUG`, остальное `INFO`.

### `db/migration/`

Flyway применяет файлы по номеру версии один раз и хранит историю в `flyway_schema_history`. SQL написан переносимо
(`GENERATED BY DEFAULT AS IDENTITY`, `TIMESTAMP WITH TIME ZONE`, `BYTEA`), чтобы работать и в PostgreSQL, и в H2 на тестах.

| Миграция | Содержание |
|----------|------------|
| `V1__create_users.sql` | `users`: email (уникальный), хэш пароля, роль, статус, метки времени. |
| `V2__create_profiles.sql` | `interests` (+ 35 стартовых строк), `profiles` (1:1 к users, `ON DELETE CASCADE`), `profile_interests` (M:N), `profile_photos` (bytea, 1:1 к profiles), индекс по городу. |
| `V3__create_reactions_and_matches.sql` | `reactions` с уникальной парой (from, to) и индексом по цели, `profile_matches` с уникальной упорядоченной парой. Все FK каскадные: удаление пользователя чистит всё. |
| `V4__create_app_settings.sql` | `app_settings` ключ–значение и строка `default_strategy = HYBRID`. |

Чтобы изменить схему, добавьте `V5__...sql`; редактировать применённые миграции нельзя (Flyway сверяет контрольные суммы).

### `static/` — интерфейс

| Файл | Назначение |
|------|------------|
| `index.html` | Каркас страницы: шапка с логотипом и `<nav id="nav">`, контейнер `<main id="app">`, слой уведомлений `#toasts`, модальное окно `#modal`. Подключает `css/app.css`, `js/api.js`, `js/app.js`. Favicon встроен как data-URI. |
| `css/app.css` | Вся стилизация. В начале переменные палитры (`--accent`, `--ok`, `--danger`…), далее блоки: шапка, кнопки (`.btn--primary`, `.btn--round` для лайка/пропуска), формы и ошибки полей (`.field-error`), чипы интересов, аватары, экраны входа, лента (`.deck-card`, `.reasons`, `.match-score`), матчи, модальные окна и тосты, админ (плитки, полосы, таблицы, пагинация), медиазапрос до 860px (шапка столбиком, карточка выше панели алгоритмов). |
| `js/api.js` | Модуль `Api`: обёртка над `fetch`. Хранит JWT в `localStorage` (`matchly.token`), подставляет заголовок, сериализует JSON, разбирает `application/problem+json` в `ApiError` со `status`, `problem`, `errors`. Любой 401 при наличии токена (кроме запроса входа) считается концом сессии: токен удаляется и посылается событие `matchly:session-expired` с причиной. `imageUrl(url)` загружает защищённое фото с токеном и отдаёт blob-URL, кэшируя его. |
| `js/app.js` | Модуль `App`, всё поведение интерфейса. Устройство: словарь `MESSAGES` переводит английские сообщения API в русские; `translate()` также обрабатывает шаблон `size must be between`; утилиты `esc` (экранирование HTML, защита от XSS), `toast`, `showModal`, `confirmDialog`, `showFieldErrors`, `handleError` (ошибки 400 показывает под полями, 422 сопоставляет с полем по таблице `RULE_FIELDS`, остальное тостом); `loadSession`/`loadProfile` восстанавливают состояние по токену; хеш-роутер `route()` с правилами перенаправления (без сессии → вход; без анкеты → онбординг; `/admin` только ADMIN; `#/` → лента); экраны `renderLogin`, `renderRegister`, `renderProfileForm` (онбординг и редактирование одной функцией: сохранение анкеты и отдельно загрузка фото, при провале фото анкета не теряется), `renderDiscover`/`showNextCard`/`react` (очередь карточек, счётчик `seq` защищает от устаревших ответов при смене алгоритма, флаг `busy` от двойного клика, горячие клавиши ← →, окно матча), `renderMatches`, `renderAdmin` с вкладками `renderAdminStats`, `renderAdminUsers` (поиск с задержкой 350 мс, пагинация, блокировка/удаление с подтверждением), `renderAdminInterests` (инлайн-редактирование), `renderAdminSettings`. |
## 6. Бэкенд: точка входа, конфигурация, общие классы

### `MatchlyApplication.java`

`@SpringBootApplication` плюс `@ConfigurationPropertiesScan`, благодаря которому record `MatchlyProperties` подхватывается
без явного `@EnableConfigurationProperties`. В Javadoc класса перечислены пакеты и их роли.

### `config/`

| Файл | Назначение |
|------|------------|
| `MatchlyProperties.java` | Неизменяемый record с вложенными `Security(jwtSecret, jwtTtl)`, `Seed(enabled, profiles)`, `Admin(email, password)`. Аннотации `@Validated`, `@NotBlank`, `@Size(min = 32)` проверяются при старте: приложение не поднимется с плохой конфигурацией. Внедряется в `JwtConfig`, `JwtTokenService`, `AdminInitializer`, `DemoDataSeeder`. |
| `JwtConfig.java` | Три бина: `SecretKey` из строки секрета (HMAC-SHA256), `JwtEncoder` (`NimbusJwtEncoder` с `ImmutableSecret`) для выдачи токенов и `JwtDecoder` (`NimbusJwtDecoder.withSecretKey`) для проверки. Наличие бина `JwtDecoder` отключает автоконфигурацию пользователя по умолчанию Spring Security. |
| `OpenApiConfig.java` | `@OpenAPIDefinition` (название, версия, описание) и `@SecurityScheme` `bearerAuth`: в Swagger UI появляется кнопка Authorize. Публичные операции помечены в контроллерах `@SecurityRequirements` (пустым), чтобы Swagger не требовал токен. |

### `common/entity/BaseEntity.java`

`@MappedSuperclass` для всех сущностей: `id` (identity), `createdAt`, `updatedAt`. Метки проставляются колбэками
`@PrePersist`/`@PreUpdate`, наследники о них не думают. `equals`/`hashCode` по идентификатору (для несохранённых объектов
равенство только по ссылке), чтобы сущности корректно работали в `Set`.

### `common/exception/`

Иерархия прикладных исключений. Корень `MatchlyException` (абстрактный) хранит `HttpStatus` и `title`; обработчик ошибок
превращает любого наследника в ответ, не зная его типа (полиморфизм).

| Класс | Статус | Когда бросается |
|-------|--------|-----------------|
| `NotFoundException` | 404 | ресурс не найден; конструктор `(entity, id)` собирает текст «User with id 5 not found» |
| `ConflictException` | 409 | дубликат email или имени интереса |
| `BusinessRuleException` | 422 | нарушено правило предметной области: возраст < 18, лайк самому себе, неверный пароль подтверждения удаления |
| `InvalidCredentialsException` | 401 | неверная пара email/пароль; текст нейтральный, не уточняет, что именно неверно |
| `AccountBlockedException` | 403 | вход заблокированного пользователя |
| `ForbiddenException` | 403 | действие запрещено (зарезервировано для расширений) |

### `common/api/GlobalExceptionHandler.java`

`@RestControllerAdvice`, наследует `ResponseEntityExceptionHandler`, поэтому стандартные ошибки Spring MVC (неверный JSON,
неизвестный метод, неподдерживаемый тип содержимого, `MaxUploadSizeExceededException` → 413) уже возвращаются как ProblemDetail.
Собственные обработчики: `MatchlyException` (статус из исключения), `AuthenticationException` (401, текст зависит от подтипа:
нет токена → «Authentication is required», плохой токен → «Invalid or expired token», блокировка → сообщение исключения),
`AccessDeniedException` (403), `DataIntegrityViolationException` (409), `Exception` (500 со стеком в лог). Переопределён
`handleMethodArgumentNotValid`: к ответу 400 добавляется список `errors` из пар `field`/`message`, который интерфейс
раскладывает по полям формы. Каждый ответ получает `timestamp`.

### `common/dto/PageResponse.java`

Обёртка страницы `content, page, size, totalElements, totalPages` со статическим `of(Page<T>)`. Используется вместо
`Page` Spring Data, чтобы формат JSON не зависел от внутренностей библиотеки.

### `common/util/`

| Файл | Назначение |
|------|------------|
| `ImageTypeDetector.java` | Определяет тип изображения по первым байтам: JPEG (`FF D8 FF`), PNG (сигнатура 8 байт), WebP (`RIFF....WEBP`). Заголовку `Content-Type` от клиента не доверяем. Возвращает `Optional<String>` с MIME-типом. |
| `AvatarGenerator.java` | Строит SVG-аватар для демо-анкет: градиент с оттенком, вычисленным из имени и номера, два полупрозрачных круга и первая буква имени. Не требует внешних картинок и библиотек. |

## 7. `security/`

| Файл | Назначение |
|------|------------|
| `SecurityConfig.java` | Одна цепочка фильтров: CSRF выключен (stateless API с токеном), сессии `STATELESS`. Правила: `POST /api/auth/register` и `/login` открыты; Swagger, `/actuator/health/**` открыты; `/api/admin/**` → `hasRole("ADMIN")`; остальной `/api/**` → аутентификация; всё прочее (статика интерфейса) открыто. `oauth2ResourceServer().jwt()` использует `JwtDecoder` и `JwtUserAuthenticationConverter`; entry point и access-denied handler заменены на классы `ProblemDetail*`. Бин `PasswordEncoder` — `DelegatingPasswordEncoder` (bcrypt по умолчанию, хэши с префиксом `{bcrypt}`). |
| `JwtTokenService.java` | `issue(User)` формирует claims: `iss=matchly`, `sub=<id>`, `email`, `role`, `iat`, `exp = now + jwt-ttl`, подписывает HS256. Возвращает `IssuedToken(token, expiresAt)`. |
| `JwtUserAuthenticationConverter.java` | `Converter<Jwt, AbstractAuthenticationToken>`: парсит `sub` как id, грузит `User`, бросает `BadCredentialsException` для несуществующего и `LockedException` для заблокированного (оба → 401 через общий обработчик), создаёт `UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities)` с принципалом `AuthenticatedUser` и ролью `ROLE_<role>`. Цена — один запрос к базе на HTTP-запрос; выигрыш — мгновенный отзыв доступа. |
| `AuthenticatedUser.java` | Record `(id, email, role)` + `isAdmin()`. Именно его получают контроллеры через `@AuthenticationPrincipal`. |
| `ProblemDetailAuthenticationEntryPoint.java` | Вместо записи ответа вызывает `HandlerExceptionResolver.resolveException`, то есть отдаёт исключение в `GlobalExceptionHandler`. Так 401 выглядит как остальные ошибки. |
| `ProblemDetailAccessDeniedHandler.java` | То же для 403. |
## 8. `auth/` — регистрация, вход, удаление аккаунта

| Файл | Назначение |
|------|------------|
| `AuthController.java` | `POST /api/auth/register` (201), `POST /api/auth/login` (200), `GET /api/auth/me`, `DELETE /api/auth/me` (204). Регистрация и вход помечены `@SecurityRequirements` (без токена в Swagger). |
| `AuthService.java` | Интерфейс с Javadoc: `register`, `login`, `currentUser`, `deleteAccount`. |
| `AuthServiceImpl.java` | `register`: нормализация email, проверка `existsByEmailIgnoreCase` → `ConflictException`, хэширование, `User.register`, токен. `login`: поиск по email, `passwordEncoder.matches`, проверка блокировки (`AccountBlockedException`), лог неудачных попыток. `deleteAccount`: сверяет пароль (неверный → `BusinessRuleException`, а не 401, иначе клиент принял бы это за конец сессии), запрещает удалять администратора, удаляет пользователя (каскад в базе убирает анкету, фото, реакции, матчи). |
| `dto/RegisterRequest.java` | `email` (`@Email`, до 255), `password` (8–72 символа; 72 — предел bcrypt). |
| `dto/LoginRequest.java` | `email`, `password`, оба `@NotBlank`. |
| `dto/AuthResponse.java` | `token`, `expiresAt`, вложенный `UserResponse`. |
| `dto/DeleteAccountRequest.java` | `password` для подтверждения. |

## 9. `user/` — учётные записи

| Файл | Назначение |
|------|------------|
| `User.java` | Сущность таблицы `users`. Приватный конструктор; фабрики `register(email, hash)` (роль USER) и `createAdmin(...)`. Поведение: `block()`, `unblock()`, `changePassword()`, `isBlocked()`, `isAdmin()`. Статический `normalizeEmail` (trim + lower case). Сеттеров нет: состояние меняется только этими методами (инкапсуляция). |
| `Role.java` | `USER`, `ADMIN`. |
| `UserStatus.java` | `ACTIVE`, `BLOCKED`. |
| `UserRepository.java` | `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`, `findByEmailContainingIgnoreCase(Pageable)` для поиска админом, `countByStatus`, `countByCreatedAtAfter` для статистики. |
| `UserService.java` / `UserServiceImpl.java` | Операции администратора: `getById`, `search` (пустой запрос → все), `block(id, actorId)` и `delete(id, actorId)` запрещают действие над собой (`BusinessRuleException`), `unblock`. Все методы логируют, кто и что сделал. |
| `UserMapper.java` | `toResponse(User)` → `UserResponse` без хэша пароля. |
| `dto/UserResponse.java` | `id, email, role, status, createdAt`. |

## 10. `admin/` — контроллеры администратора

Все адреса начинаются с `/api/admin/` и защищены правилом `hasRole("ADMIN")` в `SecurityConfig`; отдельных проверок в коде нет.

| Файл | Назначение |
|------|------------|
| `AdminUserController.java` | `GET /api/admin/users?q=&page=&size=` (размер страницы ограничен 1..100, сортировка по дате создания), `GET /{id}`, `POST /{id}/block`, `POST /{id}/unblock`, `DELETE /{id}`. Использует `UserService` и `UserMapper`, отдаёт `PageResponse<UserResponse>`. |
| `AdminInterestController.java` | `POST`, `PUT /{id}`, `DELETE /{id}` для справочника интересов через `InterestService`. |
| `AdminSettingsController.java` | `GET`/`PUT /api/admin/settings`: алгоритм рекомендаций по умолчанию. |
| `AdminStatsController.java` | `GET /api/admin/stats` → `AdminStatsResponse`. |
| `AdminStatsService.java` / `AdminStatsServiceImpl.java` | Собирает счётчики из шести репозиториев одной транзакцией только для чтения: пользователи (всего, активных, заблокированных, новых за 7 дней), анкеты (всего, видимых, с фото), лайки, пропуски, матчи, интересы, топ-5 интересов (`ProfileRepository.countProfilesPerInterest`). |
| `dto/AdminStatsResponse.java` | Record со всеми показателями и списком `InterestUsage`. |

## 11. `interest/` — справочник интересов

| Файл | Назначение |
|------|------------|
| `Interest.java` | Сущность `interests`: `name` (уникальное), `category`. Не наследует `BaseEntity`, потому что справочнику не нужны метки времени. `equals`/`hashCode` по id: важно для `Set<Interest>` в анкете. |
| `InterestCategory.java` | `ACTIVE, CULTURE, FOOD, HOBBY, LIFESTYLE, TECH`; интерфейс показывает русские названия из своего словаря `CATEGORY`. |
| `InterestRepository.java` | `findAllByOrderByCategoryAscNameAsc`, `existsByNameIgnoreCase`. |
| `InterestService.java` / `InterestServiceImpl.java` | `findAll`, `create` (дубликат имени → 409), `update` (переименование с проверкой, что имя не занято другим), `delete` (строки `profile_interests` удаляет каскад в базе). |
| `InterestController.java` | `GET /api/interests` для любого пользователя. |
| `InterestUsage.java` | Record `(name, profiles)` для статистики: заполняется JPQL-запросом `select new ...`. |
| `dto/InterestRequest.java` | `name` (2–50), `category`. |
| `dto/InterestResponse.java` | `id, name, category`, фабрика `from(Interest)`. |

## 12. `profile/` — анкеты и фото

| Файл | Назначение |
|------|------------|
| `Profile.java` | Центральная сущность предметной области. Поля: `user` (1:1, lazy), `displayName`, `birthDate`, `gender`, `lookingFor`, `ageMin/ageMax`, `city`, `bio`, `contact`, `visible`, `interests` (M:N через `profile_interests`). Метод `update(ProfileDetails, Set<Interest>, LocalDate today)` — единственный способ заполнить анкету; он же проверяет инварианты: возраст ≥ 18, `ageMin ≤ ageMax`, диапазон 18..99, от 1 до 10 интересов; нормализует имя, город (первая буква заглавная), пустое описание → `null`. Методы для алгоритмов: `age(today)`, `wants(candidate, today)` (кандидат подходит под мои пол и возраст), `isMutuallyCompatibleWith` (подходим друг другу), `isInSameCityAs`, `commonInterests`, `countCommonInterests`. Дата передаётся параметром, чтобы правила были тестируемы без «сегодня». |
| `ProfileDetails.java` | Record с данными анкеты без веб-аннотаций: граница между DTO контроллера и сущностью. |
| `Gender.java` | `MALE, FEMALE, OTHER`. |
| `Preference.java` | `MALE, FEMALE, EVERYONE` с методом `accepts(Gender)`. |
| `ProfilePhoto.java` | Отдельная сущность `profile_photos`: `contentType`, `sizeBytes`, `data` (bytea). Вынесена из `Profile`, чтобы байты не грузились вместе с анкетами при подборе. `replace(type, data)` для замены фото. |
| `PhotoMeta.java` | Record `(profileId, updatedAt)` и метод `url()` → `/api/profiles/{id}/photo?v=<epochMillis>`. Параметр `v` меняется при замене фото, поэтому браузер может кэшировать картинку на месяц. |
| `ProfileRepository.java` | `findByUserId`, `findWithInterestsByUserId` (`@EntityGraph`), `findVisibleById(id, ACTIVE)` (видима и владелец активен), `findAllVisible(status)` для подбора кандидатов, `countByVisibleTrue`, `countProfilesPerInterest(Pageable)` для статистики. |
| `ProfilePhotoRepository.java` | `findByProfileId` (с байтами), `findMetaByProfileId` и `findMetaByProfileIdIn` — проекции `PhotoMeta` без байтов через `select new`. |
| `ProfileService.java` / `ProfileServiceImpl.java` | `getOwn` (404 → интерфейс открывает онбординг), `saveOwn` (upsert: `UpsertResult(profile, created)` даёт контроллеру выбрать 201 или 200; интересы проверяются через `findAllById`, лишние id → 422), `getCard` (чужая карточка, скрытые и заблокированные выглядят как 404), `uploadPhoto` (пустой файл, лимит 2 МБ, тип по сигнатуре, замена или создание, `saveAndFlush` чтобы получить свежий `updatedAt` для ссылки), `deletePhoto`, `getPhoto`. |
| `ProfileMapper.java` | `toDetails(ProfileRequest)`, `toResponse(Profile, PhotoMeta)` (полная анкета владельца с контактом), `toCard(Profile, PhotoMeta)` (карточка без контакта и настроек поиска), `toInterests` (сортировка по категории и имени). |
| `ProfileCardAssembler.java` | Сборка карточек с фото для других модулей (`matching`, `recommendation`): `toCards(List<Profile>)` берёт `PhotoMeta` для всех анкет одним запросом и сохраняет порядок. |
| `ProfileController.java` | `GET/PUT /api/profiles/me`, `PUT /me/photo` (multipart, поле `file`), `DELETE /me/photo`, `GET /{id}` (карточка), `GET /{id}/photo` (байты с `Cache-Control: private, max-age=30d` и `Last-Modified`). |
| `dto/ProfileRequest.java` | Валидация формата: имя 2–50, дата в прошлом, возраст 18..99, город до 100, описание до 1000, контакт обязателен, 1–10 интересов; `@AssertTrue isAgeRangeValid()` даёт ошибку поля `ageRangeValid`; `visible` необязателен (`visibleOrDefault()` → true). |
| `dto/ProfileResponse.java` | Полная анкета: все поля, вычисленный `age`, интересы, `photoUrl`, `updatedAt`. |
| `dto/ProfileCardResponse.java` | Карточка для ленты и матчей: `id, displayName, age, gender, city, bio, interests, photoUrl`. Контакта здесь нет намеренно. |
| `dto/PhotoContent.java` | `data, contentType, updatedAt` для отдачи файла. |
## 13. `reaction/` — лайки и пропуски

| Файл | Назначение |
|------|------------|
| `Reaction.java` | Сущность `reactions`: `source` (кто), `target` (кого), `type`. Поля названы `source`/`target`, а не `from`/`to`, потому что `from` — ключевое слово JPQL. Конструктор запрещает реакцию на себя; `change(type)` меняет тип; `isLike()`. Это и есть Rating из постановки задачи. |
| `ReactionType.java` | `LIKE`, `SKIP`. |
| `ReactionRepository.java` | `findBySource_IdAndTarget_Id`, `existsBySource_IdAndTarget_IdAndType` (проверка встречного лайка), `findTargetIdsBySourceId` (кого пользователь уже оценил — исключаются из ленты), `findEdgesByType` (все лайки как пары id → матрица для коллаборативной фильтрации), `countByTargetGroupedByType` (лайков получено по анкетам → популярность), `countByType`, `countByTarget_IdAndType`. |
| `LikeEdge.java` | Record `(sourceProfileId, targetProfileId)` — ребро графа лайков. |
| `LikeCount.java` | Record `(profileId, likes)`. |
| `ReactionService.java` / `ReactionServiceImpl.java` | `react(userId, request)`: анкета зрителя (`findByUserId`, 404 если нет), запрет на себя (422), видимая цель (`findVisibleById`, иначе 404), upsert реакции, затем `tryMatch` (встречный LIKE есть → найти или создать `Match`, вернуть `MatchResponse`) или `breakMatch` (SKIP удаляет матч). Поиск матча всегда по `(min(id), max(id))`. |
| `ReactionController.java` | `POST /api/reactions` → `ReactionResponse`. |
| `dto/ReactionRequest.java` | `targetProfileId`, `type`. |
| `dto/ReactionResponse.java` | `targetProfileId, type, matched, match` (матч заполнен только при взаимности). |

## 14. `matching/` — матчи

| Файл | Назначение |
|------|------------|
| `Match.java` | Сущность `profile_matches` с `profileA`, `profileB`. Фабрика `between(first, second)` ставит анкету с меньшим id в `profileA`, поэтому матч A–B и B–A — одна запись, и уникальный индекс в базе это гарантирует. `involves(id)`, `other(id)` — собеседник для указанного участника. |
| `MatchRepository.java` | `findByProfileA_IdAndProfileB_Id`, `findAllForProfile(id)` (обе стороны, с `@EntityGraph` на анкеты и их интересы, сортировка по дате), `findWithProfilesById`. |
| `MatchService.java` / `MatchServiceImpl.java` | `listMatches(userId)` → список `MatchResponse` глазами зрителя; `unmatch(userId, matchId)`: матч должен включать зрителя (иначе 404, чтобы не раскрывать существование чужих матчей), удаляется, а собственный лайк переводится в SKIP, чтобы анкета не вернулась в ленту. |
| `MatchAssembler.java` | `toResponse(match, viewerId)` и пакетный `toResponses` через `ProfileCardAssembler.toCards`: карточка собеседника + его контакт + дата матча. |
| `MatchController.java` | `GET /api/matches`, `DELETE /api/matches/{id}` (204). |
| `dto/MatchResponse.java` | `id, profile (карточка), contact, matchedAt`. |

## 15. `recommendation/` — ядро проекта

### Контракт и данные

| Файл | Назначение |
|------|------------|
| `RecommendationStrategy.java` | Интерфейс: `type()` и `rank(viewer, candidates, context) → List<ScoredCandidate>`. Порядок результата не важен, сервис сортирует сам; оценки обязаны лежать в 0..1, чтобы стратегии можно было складывать. |
| `StrategyType.java` | Перечисление `CONTENT, COLLABORATIVE, POPULARITY, HYBRID` с русскими `title` и `description` для интерфейса. Добавление значения без реализации остановит запуск (проверка в реестре). |
| `RecommendationContext.java` | Record: `today`, `likesBySource` (id → множество лайкнутых id), `likesReceived` (id → число лайков). `build(today, edges, counts)` собирает его из результатов репозитория; помощники `likesOf`, `likesReceivedBy`, `hasLiked`; `empty` и `of(...)` для тестов. Один контекст на запрос, стратегии в базу не ходят. |
| `ScoredCandidate.java` | Record `(profile, score, reasons)`; `zero(profile)` для случаев, когда стратегии нечего сказать. |
| `StrategyRegistry.java` | Получает от Spring список всех бинов `RecommendationStrategy`, раскладывает по типу в `EnumMap`, при старте проверяет отсутствие дубликатов и полноту. `get(type)`. |

### `strategy/`

| Файл | Формула и поведение |
|------|---------------------|
| `AbstractRecommendationStrategy.java` | Общий предок: `clamp(value)` в 0..1 и `plural(n, one, few, many)` для русских форм («1 лайк», «2 лайка», «5 лайков», с учётом 11–14 и 21). |
| `ContentBasedStrategy.java` | `score = 0.6·J + 0.25·A + 0.15·C`. `J` — Жаккар множеств интересов (общие / объединение), `A = max(0, 1 − |Δвозраст| / 15)`, `C = 1` при одном городе. Причины: «Общие интересы: …» (первые три и «и ещё N»), «Тот же город», «Близкий возраст» (Δ ≤ 3). Единственная стратегия, полезная новичку без лайков. Метод `score` пакетный (без `private`) ради unit-тестов. |
| `CollaborativeStrategy.java` | User-based коллаборативная фильтрация. `similarities(viewerId, myLikes, ctx)` считает косинусную близость `|L(v) ∩ L(u)| / √(|L(v)|·|L(u)|)` с каждым, у кого есть общий лайк. Оценка кандидата = сумма близостей тех похожих пользователей, кто его лайкнул, делённая на сумму всех близостей. Без собственных лайков — нули (холодный старт). Причина: «Нравится N пользователям с похожими вкусами». |
| `PopularityStrategy.java` | `score = лайки(c) / max лайков среди кандидатов`; при нуле у всех — нули. Причина: «N лайков от других пользователей». |
| `HybridStrategy.java` | Компоновщик над тремя предыдущими (внедряются конструктором): `0.5·content + 0.3·collab + 0.2·popularity`, плюс `+0.15` и причина «Проявил(а) интерес к вам», если кандидат уже лайкнул зрителя (`ctx.hasLiked(candidate, viewer)`). Результат ограничен единицей, причины объединяются без дубликатов (`LinkedHashSet`). |

### Сервис и контроллер

| Файл | Назначение |
|------|------------|
| `RecommendationService.java` / `RecommendationServiceImpl.java` | Конвейер из раздела 2.3: анкета зрителя → стратегия (явная или по умолчанию из `SettingsService`) → `selectCandidates` → контекст → `rank` → сортировка (оценка ↓, затем `createdAt` ↓) → `limit` → карточки через `ProfileCardAssembler` → `RecommendationResponse` с числом рассмотренных кандидатов. `strategies()` возвращает список `StrategyInfo` с флагом `isDefault`. Оценки округляются до тысячных. |
| `RecommendationController.java` | `GET /api/recommendations?strategy=&limit=` (limit приводится к 1..50; неизвестная стратегия → 400 стандартным обработчиком преобразования типов), `GET /api/recommendations/strategies`. |
| `dto/RecommendationItem.java` | `profile (карточка), score, reasons`. |
| `dto/RecommendationResponse.java` | `strategy, strategyTitle, candidatesConsidered, items`. |
| `dto/StrategyInfo.java` | `type, title, description, isDefault`. |

## 16. `settings/` — настройки, изменяемые на лету

| Файл | Назначение |
|------|------------|
| `AppSetting.java` | Сущность ключ–значение (`setting_key` — первичный ключ), `update(value)` обновляет и метку времени. |
| `AppSettingRepository.java` | Стандартный `JpaRepository<AppSetting, String>`. |
| `SettingsService.java` / `SettingsServiceImpl.java` | `getDefaultStrategy()` читает ключ `default_strategy`, при отсутствии или неизвестном значении возвращает `HYBRID` с предупреждением в лог; `get()`, `update(request)` создаёт или обновляет строку. |
| `dto/SettingsResponse.java`, `dto/UpdateSettingsRequest.java` | Пока единственная настройка — `defaultStrategy`; структура готова к расширению. |

## 17. `bootstrap/` — действия при старте

| Файл | Назначение |
|------|------------|
| `AdminInitializer.java` | `ApplicationRunner` с `@Order(1)`: если email из `matchly.admin` не занят, создаёт `User.createAdmin` с bcrypt-хэшем пароля из настроек. Пароль в лог не пишется. |
| `DemoDataSeeder.java` | `ApplicationRunner` с `@Order(2)`. Пропускается, если `matchly.seed.enabled=false` или уже есть `demo1@matchly.local`. Детерминирован (`new Random(42)`): создаёт N анкет (`matchly.seed.profiles`, по умолчанию 60) с чередованием пола, именами из двух списков, возрастом 20–45, широкими диапазонами поиска, 3–6 интересами, описанием из шаблона, SVG-аватаром; затем каждая анкета реагирует не более чем на половину совместимых кандидатов (максимум 6) — лайк с вероятностью 0.6, иначе пропуск — через настоящий `ReactionService`, поэтому матчи создаются той же логикой, что и в проде. Ограничение «половина кандидатов» появилось после дефекта: раньше у части демо-пользователей лента оказывалась пустой. Всё выполняется в одной транзакции. Пароль демо-аккаунтов `demo1234`. |
## 18. Тесты: `src/test/`

Запуск: `./mvnw test` (Windows: `mvnw.cmd test`). База не нужна: профиль `test` (`src/test/resources/application-test.yml`)
включает H2 в памяти в режиме совместимости с PostgreSQL и отключает генерацию демо-данных. Все API-тесты делят один контекст
Spring и одну базу, поэтому используют уникальные email (`uniqueEmail`) и не зависят от порядка выполнения.

### `support/` — общая обвязка

| Файл | Назначение |
|------|------------|
| `AbstractApiTest.java` | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`. Помощники: `register`, `login`, `adminToken` (учётные данные из `MatchlyProperties`), `createProfile`, `credentials(email, password)` (JSON), `bearer(token)`, `uniqueEmail(prefix)`. |
| `ProfileJson.java` | Построитель JSON анкеты с валидными значениями по умолчанию и методами для переопределения (`displayName`, `birthDate`, `ageRange`, `interests`, `visible`…). |
| `TestImages.java` | Генерирует настоящие PNG и JPEG размером 4×4 через `ImageIO`, чтобы тесты загрузки фото проверяли реальные сигнатуры. |

### Unit-тесты (без Spring)

| Файл | Что проверяет |
|------|---------------|
| `auth/AuthServiceImplTest.java` | Mockito: нормализация email и хэширование при регистрации, конфликт email, неверный пароль, неизвестный email, заблокированный пользователь, успешный вход. |
| `security/JwtTokenServiceTest.java` | Выданный токен декодируется тем же ключом и содержит `sub`, `email`, `role`, `iss`, `exp`; чужой ключ отвергается. |
| `profile/ProfileTest.java` | Возраст на дату, отказ несовершеннолетнему, перевёрнутый диапазон, пустые интересы, нормализация текста, `wants` и `isMutuallyCompatibleWith`, общие интересы и город, `Preference.accepts`. |
| `matching/MatchTest.java` | Упорядочивание пары, `other`, запрет матча с самим собой. |
| `common/util/ImageTypeDetectorTest.java` | PNG, JPEG, WebP, неизвестные и короткие данные, `null`. |
| `recommendation/strategy/StrategyTestData.java` | Фабрика анкет и интересов с нужными id для тестов стратегий (id проставляются через `ReflectionTestUtils`). |
| `recommendation/strategy/ContentBasedStrategyTest.java` | Идентичные анкеты → 1.0 и все три причины; ничего общего → 0; веса покомпонентно; разница возрастов ≥ 15 обнуляет возрастную часть; сокращение списка интересов «и ещё N». |
| `recommendation/strategy/CollaborativeStrategyTest.java` | Похожий пользователь «советует» кандидата; взвешивание по косинусной близости с точными значениями; холодный старт; исключение зрителя из похожих. |
| `recommendation/strategy/PopularityStrategyTest.java` | Нормализация по максимуму, нули без лайков, русские формы множественного числа. |
| `recommendation/strategy/HybridStrategyTest.java` | Взвешенная сумма и бонус за встречный лайк; оценка не превышает 1. |

### API-тесты (полный контекст, MockMvc, H2)

| Файл | Что проверяет |
|------|---------------|
| `MatchlyApplicationTests.java` | Контекст поднимается, миграции применяются. |
| `auth/AuthApiTest.java` | Регистрация (201, нижний регистр email), дубликат (409 problem+json), валидация полей (400 со списком `errors`), слишком длинный email, пустое тело, неверный Content-Type (415), неизвестный адрес (404), неверный метод (405), некорректный JSON, вход и его ошибки, `/me` без токена, с мусорным и с настоящим токеном, удаление аккаунта (неверный пароль → 422 и сессия жива; верный → 204, токен и вход мёртвы), запрет самоудаления администратора, публичность Swagger и health. |
| `admin/AdminUsersApiTest.java` | 403 для USER, поиск по email, блокировка с мгновенным отзывом токена и запретом входа, разблокировка, удаление (токен мёртв, 404 при чтении), запрет действий над собой (422), пределы пагинации (size 1000 → 100, size 0 → 1, page −3 → 0, страница за пределами → пусто, нечисловой id → 400). |
| `admin/AdminStatsApiTest.java` | 403 для USER, состав и минимальные значения счётчиков. |
| `interest/InterestApiTest.java` | Список требует токен и содержит стартовые интересы; USER не может менять справочник; админ создаёт, ловит дубликат (409), переименовывает, получает 400 на пустое имя, удаляет (повторно 404); удалённый интерес исчезает из анкет. |
| `profile/ProfileApiTest.java` | 404 без анкеты; создание (201, нормализация имени и города, возраст, интересы, `photoUrl` пуст); обновление (200, замена интересов, скрытие); 400 с полями `displayName` и `ageRangeValid`; несовершеннолетний (422); неизвестный интерес (422); чужая карточка без контакта, скрытая — 404; фото: загрузка PNG, скачивание байт в байт с `Cache-Control`, замена на JPEG с неверным заявленным типом, отказ текстовому файлу (422), удаление; файл больше лимита отклоняется сервисом; пустой файл; загрузка без анкеты; 11 интересов → 400, ровно 10 и границы 18/99 → успех, 17/100 → 400; карточка заблокированного владельца пропадает и возвращается; фото требует аутентификации; удаление пользователя убирает анкету и фото. |
| `reaction/ReactionApiTest.java` | Полный цикл: лайк без взаимности, встречный лайк → матч с контактом у обоих, идемпотентный повторный лайк, пропуск разрывает матч, повторный лайк восстанавливает, разрыв кнопкой (204, повторно 404), после разрыва встречный лайк стал пропуском; реакция без анкеты (404), на себя (422), на скрытую или несуществующую (404), неверное тело (400), чужой матч нельзя разорвать (404). |
| `recommendation/RecommendationApiTest.java` | Изоляция через возраст 60–65 лет, чтобы анкеты других тестов не попадали в выборку: фильтрация (скрытые, пропущенные, несовместимые), порядок и причины для CONTENT, HYBRID по умолчанию, `limit`, POPULARITY после лайка, причина «Проявил(а) интерес к вам»; 404 без анкеты; неизвестная стратегия и нечисловой `limit` → 400, отрицательный и огромный `limit` приводятся к диапазону; список стратегий и смена значения по умолчанию администратором (с возвратом в `finally`, потому что настройка глобальна). |
| `bootstrap/DemoDataSeederTest.java` | Отдельный контекст с собственной базой H2 и `matchly.seed.profiles=12`: создаются 12 пользователей и анкет с фото, реакции; пароль демо совпадает; повторный запуск ничего не добавляет. |

## 19. `e2e/` — браузерные сценарии Playwright

Требуют Node.js 18+ и запущенного приложения (по умолчанию `http://localhost:8080`, переопределяется `MATCHLY_URL`).

| Файл | Назначение |
|------|------------|
| `package.json` | Зависимость `playwright 1.47.2`, скрипты `install-browser`, `test` (e2e.js), `test:corner`, `test:all`, `shots`. |
| `e2e.js` | Основной путь из 18 шагов: второй пользователь создаётся через API, первый проходит регистрацию, ошибку короткого пароля, онбординг с фото, ленту со стратегией CONTENT, взаимный лайк и окно матча, матчи, пропуск клавишей, редактирование анкеты, разрыв матча, вход администратора, статистику, поиск и блокировку, интересы, настройки; в конце удаляет тестовых пользователей и проверяет мобильную ширину. Собирает ошибки консоли и ответы 5xx. |
| `corner.js` | 19 групп корнер-кейсов (см. `e2e/README.md`). Важные приёмы: второй пользователь живёт в отдельном контексте браузера (у контекстов общий `localStorage`), после подмены токена страница перезагружается (переход только по хешу документ не перезагружает), при сбое печатаются последние навигации и ответы ≥ 400. |
| `shots.js` | Делает чистые скриншоты всех экранов в `docs/screenshots` и проверяет, что уведомления исчезают за 4.5 с. |
| `photo.png` | Тестовое изображение 64×64 для загрузки фото. |
| `README.md`, `.gitignore` | Как запускать; исключение `node_modules` и скриншотов сбоев. |

## 20. `docs/`

| Файл | Назначение |
|------|------------|
| `REPORT.md` | Техническое описание для проверяющего: требования и их выполнение, архитектура, схема БД (Mermaid), алгоритмы с формулами, API, интерфейс, тестирование. |
| `INTEGRATION.md` | Как использовать модуль рекомендаций в другом приложении: по REST, как Java-библиотеку с адаптером, добавлением новой стратегии. |
| `CODE_GUIDE.md` | Этот документ. |
| `Отчёт_по_учебной_практике_Высоцкий_А_А.docx` / `.pdf` | Отчёт по практике; `report-src/report_gen.py` генерирует его из текста и файлов проекта, `report-src/find_pages.py` считает номера страниц для содержания по PDF. |
| `screenshots/` | Экранные формы для отчёта и README. |

## 21. Как выполнять типовые изменения

**Добавить новую стратегию рекомендаций.** Добавьте значение в `StrategyType` с названием и описанием, создайте класс
в `recommendation/strategy`, унаследованный от `AbstractRecommendationStrategy`, с аннотацией `@Component`, реализуйте `type()`
и `rank(...)`. Реестр подхватит его сам, интерфейс покажет в переключателе, а если забыть реализацию — приложение не запустится.
Напишите unit-тест по образцу `PopularityStrategyTest`.

**Добавить поле в анкету.** Миграция `V5__...sql` → поле в `Profile` и `ProfileDetails` → `ProfileRequest` (валидация) →
`ProfileMapper.toDetails/toResponse` → `ProfileResponse` → форма в `renderProfileForm` (`app.js`) → тест в `ProfileApiTest`.

**Добавить эндпоинт.** Метод в интерфейсе сервиса с Javadoc → реализация с `@Transactional` и логированием → метод контроллера
с `@Operation` → при необходимости правило в `SecurityConfig` → тест в соответствующем `*ApiTest`. Ошибки бросайте
наследниками `MatchlyException`, формат ответа сложится сам.

**Изменить сообщение об ошибке.** Текст в исключении или в `message` аннотации валидации меняется на английском,
перевод добавляется в словарь `MESSAGES` в `app.js`; тесты, проверяющие текст, обновляются.

## 22. Куда смотреть при отладке

- Запрос упал с 500: `logs/matchly.log`, запись уровня `ERROR` со стеком от `GlobalExceptionHandler`.
- 401 там, где не ожидалось: в `GlobalExceptionHandler.handleAuthentication` виден подтип исключения; проверьте срок токена
  и не заблокирован ли пользователь (`JwtUserAuthenticationConverter`).
- Hibernate не стартует с ошибкой валидации схемы: сущность разошлась с миграцией — добавьте миграцию, а не правьте старую.
- Пустая лента: проверьте `selectCandidates` в `RecommendationServiceImpl` — совместимость предпочтений взаимна,
  уже оценённые исключены; в ответе есть `candidatesConsidered`.
- Интерфейс показывает английский текст ошибки: сообщения нет в `MESSAGES`, добавьте перевод.
- Тест падает на H2, но не на PostgreSQL: проверьте SQL миграции на переносимость (типы, функции).

## 23. Глоссарий

| Термин | Значение |
|--------|----------|
| Анкета (Profile) | То, что видят другие: имя, возраст, интересы, фото; плюс настройки поиска. Одна на пользователя. |
| Реакция (Reaction) | LIKE или SKIP одной анкеты на другую; аналог Rating из задания. |
| Матч (Match) | Взаимный LIKE; раскрывает контакты обеим сторонам. |
| Стратегия | Алгоритм ранжирования кандидатов; реализация `RecommendationStrategy`. |
| Контекст рекомендаций | Данные одного запроса: дата, матрица лайков, счётчики; загружается один раз. |
| ProblemDetail | Формат ошибок RFC 9457: `status`, `title`, `detail`, `instance`, плюс `timestamp` и `errors`. |
| JWT | Подписанный токен, который клиент передаёт в заголовке `Authorization: Bearer`. |
| Онбординг | Первый экран после регистрации, где пользователь заполняет анкету; пока её нет, лента и матчи недоступны. |
