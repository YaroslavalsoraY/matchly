# Интеграция модуля рекомендаций в другое приложение

Модуль живёт в пакете `com.matchly.recommendation` и спроектирован так, чтобы его можно было
переносить или подключать по сети. Ниже три способа, от самого простого к самому глубокому.

## Способ 1. Как отдельный сервис по REST

Любое приложение может обращаться к работающему Matchly по HTTP:

1. Получить токен: `POST /api/auth/login` с `{"email": "...", "password": "..."}`.
2. Запросить подборку: `GET /api/recommendations?strategy=HYBRID&limit=10` с заголовком `Authorization: Bearer <token>`.
3. Использовать поля ответа: `items[].profile` (карточка), `items[].score` (0..1), `items[].reasons` (объяснения на русском).

Контракт описан в OpenAPI (`/v3/api-docs`), поэтому клиент на любом языке можно сгенерировать автоматически
(например, `openapi-generator`).

## Способ 2. Как Java-библиотеку внутри своего Spring-приложения

Ядро модуля не зависит от веба и базы данных. Достаточно перенести пакет `recommendation` целиком:

| Класс | Роль |
|-------|------|
| `RecommendationStrategy` | контракт стратегии: `rank(viewer, candidates, context)` |
| `StrategyType` | перечисление доступных алгоритмов с названиями для интерфейса |
| `RecommendationContext` | данные одного запроса: дата, матрица лайков, число полученных лайков |
| `ScoredCandidate` | результат: кандидат, оценка 0..1, список причин |
| `strategy.*` | четыре реализации и общий предок `AbstractRecommendationStrategy` |
| `StrategyRegistry` | выбор реализации по типу, проверка полноты при старте |
| `RecommendationService` | конвейер: фильтры -> контекст -> стратегия -> сортировка |

Стратегии работают с сущностью `Profile` через небольшой набор методов: `getId()`, `getInterests()`, `age(today)`,
`isInSameCityAs(other)`, `commonInterests(other)`. В своём проекте есть два пути:

- **Адаптер.** Реализовать у своей сущности эти же методы (или обёртку над ней) и заменить тип `Profile` в стратегиях.
  Понятия переводятся так: *пользователь* -> `viewer`, *объект рекомендации* -> `candidate`, *оценка* -> ребро в
  `RecommendationContext.likesBySource`.
- **Обобщение.** Вынести нужные методы в интерфейс (например, `Recommendable`) и параметризовать стратегии этим
  интерфейсом. Логика формул при этом не меняется.

Данные для контекста собираются один раз на запрос:

```java
RecommendationContext context = RecommendationContext.build(
        LocalDate.now(),
        likeRepository.findEdges(),      // пары (кто, кого) для матрицы предпочтений
        likeRepository.countPerItem());  // сколько оценок получил каждый объект
List<ScoredCandidate> ranked = registry.get(StrategyType.HYBRID).rank(viewer, candidates, context);
```

## Способ 3. Добавить свой алгоритм

Новая стратегия добавляется одним классом. Пример: учёт расстояния между городами.

```java
@Component
public class DistanceStrategy extends AbstractRecommendationStrategy {

    @Override
    public StrategyType type() {
        return StrategyType.DISTANCE;          // новое значение добавить в перечисление
    }

    @Override
    public List<ScoredCandidate> rank(Profile viewer, List<Profile> candidates, RecommendationContext context) {
        return candidates.stream().map(candidate -> {
            double km = distanceKm(viewer, candidate);
            double score = clamp(1.0 - km / 500.0);
            List<String> reasons = km < 50 ? List.of("Рядом с вами") : List.of();
            return new ScoredCandidate(candidate, score, reasons);
        }).toList();
    }
}
```

`StrategyRegistry` подхватит класс автоматически, а если для нового значения перечисления реализации нет,
приложение не запустится с понятной ошибкой. Чтобы алгоритм появился в интерфейсе, ничего делать не нужно:
переключатель строится по `GET /api/recommendations/strategies`.

## Что учесть при переносе

- Жёсткие фильтры (видимость, взаимные предпочтения, уже оценённые объекты) находятся в `RecommendationServiceImpl.selectCandidates`
  и специфичны для знакомств; в другой предметной области их нужно заменить своими.
- Матрица лайков загружается целиком. Для десятков тысяч оценок этого достаточно; при большем объёме
  стоит кэшировать `RecommendationContext` или считать похожесть пользователей заранее по расписанию.
- Тексты причин на русском языке находятся внутри стратегий; при необходимости их легко вынести в `messages.properties`.
