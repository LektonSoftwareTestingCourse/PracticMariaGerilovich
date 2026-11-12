# API-тесты СМП — отчёт

Набор находится в `services/api-tests`. Он использует Java 21, Maven, JUnit 5,
REST-assured 5.4.0, Hamcrest, Awaitility 4.2.2 и JSON Schema Validator.

## 1. Покрытие

| Эндпоинт | Проверяемые статус-коды | Проверки |
|---|---:|---|
| `GET /health` | 200 | `HealthAndCardsApiTest` |
| `POST/GET/PATCH/DELETE /api/cards` | 201, 200, 204, 400, 404, 409 | `HealthAndCardsApiTest` |
| `POST /api/cards/{pan}/reserve`, `/rollback` | 200, 400, 404, 409 | `HealthAndCardsApiTest` |
| `POST /api/cards/generate`, `GET /api/bins` | 201, 200, 400 | `EndpointSurfaceApiTest`, `CardManagementContractApiTest` |
| `GET /api/bin/{bin}` | 200, 404, 500 | `BinLookupApiTest` |
| `POST /api/transactions` | 200, 400 | `AuthorizationApiTest` |
| `GET /api/transactions/search`, `/export` | 200, 400 | `LoggerApiTest` |
| `GET /api/dashboard/stats`, `/recent`, `/charts` | 200, 400 | `SimulatorAndDashboardApiTest` |
| `POST /api/simulator/terminal/run` | 200, 400 | `SimulatorAndDashboardApiTest` |
| `POST /api/simulator/merchant/run` | 200, 400 | `SimulatorAndDashboardApiTest` |
| `GET /api/simulator/merchants` | 200 | `EndpointSurfaceApiTest` |
| `POST /api/internal/route`, `/authorize` | 200, 4xx | `EndpointSurfaceApiTest` |
| `POST /api/internal/log` | 200, 201, 400, 409 | `LoggerApiTest` |
| `GET /api/notifications` | 200 | `NotificationApiTest` |
| `WS /ws/transactions` | 101 | `WebSocketContractTest` (strict profile) |

`/api/simulator/terminal/start` и `/api/simulator/terminal/status` из OpenAPI
проверяются в strict profile: текущая конфигурация Gateway их не маршрутизирует,
что является намеренно зафиксированным расхождением.

`RequestSpecification` с базовыми адресами и заголовками создаётся в `support/Api.java`.
Проверки сгруппированы по доменам, тела и фикстуры вынесены в Support-слой.

Тест-кейсы `TC-CM-01..38` из `docs/practice-3-pyramid/test-cases.md` отмечены
комментариями `#TC-CM-...` непосредственно над соответствующими методами.
Новые API-кейсы `TC-N-04..08` и сквозные `TC-N-11..12` также промаркированы;
`TC-N-09` и `TC-N-10` требуют управляемого отказа RabbitMQ и проверки DLQ, поэтому
оставлены для интеграционной части модуля 6.

## 2. Бизнес-отказы

| Исход | Код | Причина | Путь подготовки |
|---|---:|---|---|
| карта отсутствует | `14` | `CARD_NOT_FOUND` | уникальный PAN в запросе |
| недостаточно средств | `51` | `INSUFFICIENT_FUNDS` | карта с балансом 1, сумма 100 |
| карта заблокирована | `05` | `CARD_BLOCKED` | `PATCH /api/cards/{pan}` |
| карта просрочена | `54` | `CARD_EXPIRED` | `PATCH /api/cards/{pan}` |
| повторное резервирование | HTTP `409` | RRN уже существует | два API-запроса с одним RRN |
| повторный откат | HTTP `409` | rollback уже выполнен | два API-запроса с одним RRN |

## 3. Валидация и данные

Параметризованные тесты покрывают классы неправильного PAN, границы `limit`,
поля ISO 8583, сценарии симуляторов и `count`. Идентификаторы PAN, STAN, RRN,
UUID и merchant ID генерируются для каждого теста. Начальное состояние карты
задаётся запросом `POST /api/cards`; прямого доступа к БД нет.

Проверяются `matchesPattern` для PAN/RRN/authCode, `nullValue` для полей,
`hasSize`, `oneOf`/`equalTo`, числовые границы и размер страницы. GPath-фильтр
`transactions.findAll { ... }` связывает поиск с данными текущего теста.

## 4. Асинхронность и сквозная цепочка

`LoggerApiTest.newTransactionIsStoredAndEventuallySearchable` ждёт публикацию
через `await().atMost(10s).pollInterval(200ms).untilAsserted(...)`; фиксированных
пауз нет. В тесте отдельно проверяется размер результата после фильтрации. Для
отката проверяется восстановление баланса и отдельный отрицательный исход
повторного отката. Цепочка авторизации идёт через Gateway и завершается поиском
транзакции по PAN.

## 5. Расхождения контракта и реализации

| Декларировано | Наблюдается по коду | Источник | Классификация |
|---|---|---|---|
| `Card.expiryDate` — строка `MMYY` | Java DTO содержит `YearMonth`, стандартная сериализация даёт формат года-месяца | `docs/api/openapi.yaml`, `CardModel` | несовместимость формата ответа |
| `POST /api/transactions` должен вернуть 400/503 для ошибок | Switch преобразует неизвестный BIN и недоступную Authorization в HTTP 200 с `DECLINED` | OpenAPI, `RouteController`, `RouteService` | семантика HTTP-статуса |
| `stan`, `terminalId`, `merchantId` должны проходить проверки длины контракта | Gateway/Switch принимает короткие значения и нормализует их перед downstream-вызовом | `AuthorizationRequest`, `TransactionRequestValidator`, `AuthorizationRequestNormalizer` | расхождение валидации |
| `issuerId` не заполняется клиентом | `AuthorizationRequest` помечает `issuerId` как `@NotBlank`; публичный тестовый payload обязан его передать | OpenAPI, DTO | противоречие валидации |
| Log повтор с тем же ID возвращает `status=already_exists` в `LogResponse` | реализация возвращает полную `TransactionResponse` при HTTP 200 | OpenAPI, `InternalTransactionController` | форма тела ответа |
| Search поддерживает `dateFrom/dateTo`, OpenAPI перечисляет базовые фильтры | реализация также принимает `issuerId` и `mcc`, а экспорт доступен через дополнительный endpoint | OpenAPI, `TransactionFilter`, `TransactionController` | расхождение поверхности API |
| Bin Lookup доступен как `GET /api/bin/{bin}` | Gateway не содержит маршрута к Bin Lookup, тест обращается к сервису напрямую | OpenAPI/ТЗ Authorization, `Gateway application.yml` | расхождение маршрутизации |
| `BinLookupResponse` содержит `bankName` | фактический DTO отдаёт `issuerName` | `test-cases.md` TC-N-05, `BinLookupResponse` | имя поля ответа |

## 6. Доказательства способности падать

Три проверки были спроектированы как mutation checks: изменить ожидаемый
`responseCode` в `AuthorizationApiTest`, убрать `declineReason` из проверки
отказа и заменить `transactions.findAll` на проверку только `total`. Каждое
изменение должно приводить к красному тесту; в CI эти изменения не включаются.
Для идемпотентности отдельный конфликтный тест меняет только `amount` при том же
UUID, поэтому отключение дедупликации меняет наблюдаемый результат с `409`.

## 7. Порядок запуска

```text
cp .env.example .env
docker compose up -d --build
mvn -f services/api-tests/pom.xml verify
docker compose down -v
```

CI выполняет те же шаги в `.github/workflows/api-tests.yml`. Локальный запуск
стека в рамках этой работы не выполнялся.

Обычный прогон исключает проверки с `@Tag("fault")`; для воспроизведения
известных расхождений используется `mvn -Pstrict-contract -f services/api-tests/pom.xml verify`.
