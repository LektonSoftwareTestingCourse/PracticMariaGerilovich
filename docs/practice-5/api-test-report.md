# API-тесты СМП — отчёт

Перечень эндпоинтов сверялся по трём источникам: `docs/api-spec.md`, контроллерам
сервисов и маршрутам Gateway в `services/gateway/src/main/resources/application.yml`.

## 1. Покрытие

| Эндпоинт | Проверяемые статус-коды | Кол-во проверок | Статус |
|---|---:|---:|:---:|
| `GET /health` | 200 | 2 | ✅ |
| `POST /api/cards` | 201, 400, 404 | 12 | ✅ |
| `GET /api/cards/{pan}` | 200, 404, 400 | 5 | ✅ |
| `GET /api/cards` | 200, 400 | 6 | ✅ |
| `PATCH /api/cards/{pan}` | 200, 404, 400 | 5 | ✅ |
| `DELETE /api/cards/{pan}` | 204, 404, 400 | 3 | ✅ |
| `POST /api/cards/{pan}/reserve`, `/rollback` | 200, 400, 404, 409, 402 | 9 | ✅ |
| `POST /api/cards/generate` | 201, 400 | 6 | ✅ |
| `GET /api/bins` | 200 | 2 | ✅ |
| `GET /api/bin/{bin}` | 200, 404, 500 | 4 | ✅ |
| `POST /api/transactions` | 200, 400 | 35 | ✅ |
| `GET /api/transactions/search` | 200, 400 | 6 | ✅ |
| `GET /api/transactions/export` | 200 | 2 | ✅ |
| `GET /api/dashboard/stats` | 200 | 2 | ✅ |
| `GET /api/dashboard/recent` | 200, 400 | 3 | ✅ |
| `GET /api/dashboard/charts` | 200, 400 | 3 | ✅ |
| `POST /api/simulator/terminal/run` | 200, 400 | 6 | ✅ |
| `POST /api/simulator/merchant/run` | 200, 400 | 2 | ✅ |
| `GET /api/simulator/merchants` | 200 | 2 | ✅ |
| `POST /api/internal/route`, `/authorize` | 200, 400, 403, 404, 422 (503 — fault/integration) | 8 | ✅ |
| `POST /api/internal/log` | 200, 201, 400, 409 | 11 | ✅ |
| `GET /api/notifications` | 200 | 2 | ✅ |
| `WS /ws/transactions` | 404 в текущей реализации | 2 | ✅ |

В наборе 84 тестовых метода и 121 запуск с учётом параметризованных сценариев. Вызовы через
Gateway и прямые внутренние вызовы разделены по классам.

В таблице считается количество запусков сценариев; одно и то же подготовительное
действие через API может участвовать в нескольких проверках разных эндпоинтов.

`/api/simulator/terminal/start` и `/api/simulator/terminal/status` из OpenAPI
отсутствуют в текущей конфигурации Gateway; это зафиксировано как расхождение.

`RequestSpecification` с базовыми адресами и заголовками создаётся в `support/Api.java`.
Проверки сгруппированы по доменам, тела и фикстуры вынесены в Support-слой.


## 2. Бизнес-отказы

| Исход | Код | Причина | Путь подготовки | Способ подготовки данных | Статус |
|---|---:|---|---|---|:---:|
| одобрение | `00` | — (`declineReason=null`) | Gateway → Switch → Authorization → Card Management | ACTIVE-карта с достаточным балансом и лимитами | ✅ |
| карта отсутствует | `14` | `CARD_NOT_FOUND` | Gateway → Switch → Authorization | уникальный PAN в запросе | ✅ |
| неизвестный BIN | `14` | `CARD_NOT_FOUND` | Gateway → Switch | PAN с неизвестным BIN `999999` | ✅ |
| недостаточно средств | `51` | `INSUFFICIENT_FUNDS` | Gateway → Switch → Authorization → Card Management | карта с балансом 1, сумма 100 | ✅ |
| карта заблокирована | `05` | `CARD_BLOCKED` | Gateway → Switch → Authorization | `PATCH /api/cards/{pan}` → `BLOCKED` | ✅ |
| карта просрочена по статусу | `54` | `CARD_EXPIRED` | Gateway → Switch → Authorization | `PATCH /api/cards/{pan}` → `EXPIRED` | ✅ |
| карта просрочена по дате запроса | `54` | `CARD_EXPIRED` | Gateway → Switch → Authorization | ACTIVE-карта и `transmissionDateTime=2099-01-01` | ✅ |
| карта неактивна | `05` | `CARD_INACTIVE` | Gateway → Switch → Authorization | `PATCH /api/cards/{pan}` → `INACTIVE` | ✅ |
| превышен дневной лимит | `61` | `EXCEEDS_AMOUNT_LIMIT` | Gateway → Switch → Authorization | две транзакции выше daily limit | ✅ |
| превышен месячный лимит | `61` | `EXCEEDS_AMOUNT_LIMIT` | Gateway → Switch → Authorization | две транзакции выше monthly limit | ✅ |
| повторное резервирование | HTTP `409` | RRN уже существует | Gateway → Card Management | два API-запроса с одним RRN | ✅ |
| повторный откат | HTTP `409` | rollback уже выполнен | Gateway → Card Management | два API-запроса с одним RRN | ✅ |

## 3. Валидация

### 3.1 Gateway `POST /api/transactions`

| Поле | Правило | Классы | Границы | Кол-во проверок |
|---|---|---|---|---:|
| `mti` | обязательное, только `0100` | отсутствует / другое значение | — | 2 |
| `stan` | обязательное, 6 символов | отсутствует | — | 1 |
| `pan` | обязательное, 16 цифр | отсутствует / 15 / 17 цифр / буквы | 15–16–17 | 4 |
| `processingCode` | обязательное, 6 символов | отсутствует | — | 1 |
| `amount` | обязательное, больше нуля | отсутствует / `0` / `-1` | 0 и -1 | 3 |
| `currencyCode` | обязательное, 3 символа | отсутствует / 1 / 2 / 4 символа | 1–2–3–4 | 4 |
| `transmissionDateTime` | обязательное, ISO-8601 | отсутствует / неверный формат | — | 2 |
| `terminalId` | обязательное, 8 символов | отсутствует | — | 1 |
| `merchantId` | обязательное, 15 символов | отсутствует | — | 1 |
| `mcc` | обязательное, 4 цифры | отсутствует / 3 символа / буквы | 3–4 | 3 |
| `acquirerId` | обязательное | отсутствует | — | 1 |
| тело | валидный JSON | битый JSON / пустое | — | 2 |

Проверяется HTTP `400`, `error=VALIDATION_ERROR`, `serviceName=gateway` и
соответствующее поле в `message`. Параметризованные сценарии используют
`@ParameterizedTest`, `@ValueSource` и `@CsvSource`; один запуск проверяет один
класс нарушения.

### 3.2 Card Management `POST /api/cards`

| Поле | Правило | Классы | Границы | Кол-во проверок |
|---|---|---|---|---:|
| `bin` | `@NotBlank`, ровно 6 цифр | отсутствует / 5 / 7 цифр | 5–6–7 | 3 |
| `cardholderName` | `@NotBlank`, uppercase pattern | отсутствует / lowercase | — | 2 |
| `currencyCode` | 3 цифры | 2 / 4 символа / буквы | 2–3–4 | 3 |
| `dailyLimit` | неотрицательное | `-1` | 0 | 1 |
| `monthlyLimit` | неотрицательное | `-1` | 0 | 1 |

Тело ошибки проверяется по тексту правила. Реализация не возвращает имя поля
отдельным свойством, поэтому принадлежность нарушения определяется по уникальному
сообщению валидатора.

### 3.3 Logger Search и `POST /api/internal/log`

| Параметр/поле | Правило | Классы | Границы | Кол-во проверок |
|---|---|---|---|---:|
| `limit` | положительный, максимум 500 | `0` / `-1` / `501` | 0–1 и 500–501 | 3 |
| `offset` | неотрицательный | `-1` / `0` | -1–0 | 1 |
| `dateFrom/dateTo` | `from <= to` | инвертированный диапазон | — | 1 |
| `status` | `APPROVED` / `DECLINED` | валидное / неизвестное значение | — | 1 |
| `stan` | 6 цифр | валидное / короткое | — | 1 |
| `pan` | 16 цифр | валидное / короткое | — | 1 |
| `id` | обязательный UUID | валидное / отсутствует | — | 1 |
| `amount` | положительное число | валидное / `0` | 0 | 1 |
| `status` в log | enum | валидное / `UNKNOWN` | — | 1 |

Дополнительно проверяются пустая выборка, фильтры `pan/status/dateFrom/dateTo`,
`merchantId/issuerId/mcc/declineReason`, GPath-фильтр
`transactions.findAll { ... }`, пагинация и CSV-экспорт.

Идентификаторы PAN, STAN, RRN, UUID и merchant ID создаются для каждого теста;
начальное состояние подготавливается только через API.

## 4. Расхождения контракта и реализации

| Декларировано | Наблюдается по коду | Источник декларации | Классификация |
|---|---|---|---|
| Ответ авторизации содержит `mti="0110"`, а таблица кодов включает `05/12/30/41/43` | Обычные `approved/declined` возвращают `mti="0100"`; реально достижимы коды `00`, `05`, `14`, `51`, `54`, `61`, `96` | `docs/api-spec.md`, `AuthorizationResponse`, `DeclineOutcome` | **Дефект и устаревшая часть контракта.** Формат ответа и перечень кодов расходятся с реализацией; тесты покрывают фактические ветки. |
| `expiryDate` — `String(4)` в формате `MMYY` | DTO содержит `YearMonth`, JSON возвращает `yyyy-MM`, например `2029-11` | `docs/api-spec.md`, `CardModel` | **Дефект формата.** Потребитель получает другой формат даты; расхождение подтверждено CI-прогоном. |
| `POST /api/transactions` возвращает `400` для валидации и `503` при ошибке сервиса | Бизнес-отказы через Switch возвращаются HTTP `200` с `status=DECLINED`; `400` формирует Gateway, `503` требует недоступности downstream | OpenAPI, `RouteController`, `TransactionValidationFilter` | **Известное ограничение HTTP-семантики.** HTTP `200` не означает одобрение, поэтому нужно проверять `status`, `responseCode`, `declineReason`. |
| Входные DTO полностью валидируют поля и не требуют `issuerId` от клиента | Короткие `stan/terminalId/merchantId` принимаются или нормализуются, `issuerId` помечен `@NotBlank`, а ошибки card-management не содержат имя поля | DTO, `TransactionRequestValidator`, `AuthorizationRequestNormalizer`, `GlobalExceptionHandler` | **Системное расхождение валидации.** Ограничения документации, DTO и фактических ошибок не совпадают. |
| Тело terminal simulator содержит только `{count, scenario}` | `tps` обязателен и без него сервис отвечает `400` | `docs/api-spec.md`, `TerminalRunRequest` | **Дефект полноты спецификации.** Реализация требует дополнительное поле. |
| Состав сгенерированных карт не задан | Генератор распределяет статусы как `ACTIVE 95% / INACTIVE 3% / BLOCKED 2%` | `docs/api-spec.md`, `CardGeneratorService` | **Недокументированное поведение.** Статус сгенерированной карты влияет на последующие операции. |
| Повтор Logger возвращает `status=already_exists` в `LogResponse` | HTTP `200` содержит полную `TransactionResponse` | OpenAPI, `InternalTransactionController`, `TransactionService` | **Дефект формы ответа.** Идемпотентность работает, но тело отличается от заявленной схемы. |
| Для внутренних Authorization-эндпоинтов статусы не описаны | `/api/internal/authorize` возвращает `200`, `400`, `403`, `404`, `422`; `503` зависит от инфраструктуры | OpenAPI, `AuthControllerImpl` | **Недокументированный HTTP-контракт.** Статусы зафиксированы в `InternalRouteAuthorizeApiTest`; `503` относится к fault/integration-прогону. |
| Bin Lookup соответствует заявленной модели и доступен через общий API | DTO содержит `bin/issuerId/issuerName`, без `bankName/countryCode`; Gateway не маршрутизирует `/api/bin/{bin}` | `test-cases.md` TC-N-05, `BinLookupResponse`, `Gateway application.yml` | **Расхождение схемы и маршрутизации.** Прямой сервисный контракт проверяется, но через Gateway endpoint недоступен. |

Вне покрытия остаются управляемые отказы RabbitMQ/БД, DLQ, HTTP 429 rate-limit
и полноценный WebSocket handshake: для них требуется отдельный fault/integration
прогон, а не обычный стабильный API-прогон.

## 5. Обоснование достаточности

Набор составлен по трём источникам: `docs/api-spec.md`, контроллерам и DTO
сервисов, а также маршрутам Gateway из
`services/gateway/src/main/resources/application.yml`. Поэтому проверяются
публичные Gateway-контракты и внутренние вызовы Switch, Authorization, Card
Management, Transaction Logger и Notification Service там, где это необходимо
для проверки отдельного контракта.

**Валидация Gateway.** Классы входных данных построены по правилам
`TransactionRequestValidator` и полевому контракту `AuthorizationRequest`:
обязательность 11 полей, `mti=0100`, PAN длиной 16 цифр, currencyCode длиной 3,
MCC из 4 цифр, положительная сумма, обязательная дата, битый JSON и пустое тело.
Проверяются отсутствующие поля, классы неверного формата и границы `amount=0/-1`,
длины PAN, currencyCode и MCC. Каждый параметризованный запуск изменяет один
класс нарушения и проверяет `400`, `error=VALIDATION_ERROR`,
`serviceName=gateway` и сообщение об ошибке.

**Валидация Card Management и Logger.** Для `POST /api/cards` проверяются
`@NotBlank`, BIN длиной 6, uppercase-паттерн имени, currencyCode из трёх цифр и
неотрицательные дневной и месячный лимиты. Для Logger проверяются обязательные и
форматные поля записи (`id`, `stan`, `pan`, `amount`, `status`), а для поиска —
границы `limit` и `offset`, неизвестный статус и инвертированный диапазон дат.
Таблица раздела 3 перечисляет каждый выделенный класс и число его запусков.

**Бизнес-отказы.** Набор покрывает достижимые без остановки сервисов ветки
алгоритма Authorization: `CARD_NOT_FOUND`, неизвестный BIN, `CARD_BLOCKED`,
`CARD_INACTIVE`, `CARD_EXPIRED`, `INSUFFICIENT_FUNDS` и превышение дневного или
месячного лимита. В каждом случае одновременно проверяются HTTP-статус,
`status`, `responseCode` и `declineReason`. Два разных пути проверяются отдельно:
`CARD_EXPIRED` через статус карты и через дату запроса, `CARD_NOT_FOUND` через
отсутствие карты и через неизвестный BIN.

**Поиск, пагинация и повторная доставка.** Проверяется каждый фильтр
`TransactionFilter`: PAN, статус, даты, merchant ID, issuer ID, MCC и
`declineReason`; отдельно проверяются попарное сочетание фильтров, пустая выборка,
GPath-фильтр и размер страницы. Для `POST /api/internal/log` проверены новая запись (`201`),
повтор с теми же данными (`200`), повтор с тем же ID и изменёнными данными (`409`)
и невалидное тело (`400`).

**Полнота и воспроизводимость.** Полнота подтверждена сопоставлением таблиц
разделов 1–3 с исходными правилами валидаторов, контроллеров и ветками
`DeclineOutcome`. Каждый тест создаёт собственные PAN, STAN, RRN, UUID и merchant
ID; состояние карты и лимиты подготавливаются только через API, прямого доступа к
БД нет. Асинхронные условия проверяются Awaitility с явными `atMost` и
`pollInterval`, фиксированные паузы не используются.

**Осознанно вне покрытия.** Остановка RabbitMQ или БД и связанные с ней `503`,
`DB_UNAVAILABLE`, `RESERVATION_FAILED`, DLQ и rollback после отказа брокера требуют
fault/integration-сценариев модуля 6. Также отдельно оставлены HTTP 429 rate-limit,
полноценный WebSocket handshake и маршруты, которых нет в текущей конфигурации
Gateway. Это инфраструктурные сценарии или недоступные маршруты, а не пропуски
стабильных HTTP-контрактов.

**Асинхронные эффекты.** Появление эффекта проверяется для транзакции через
Gateway, которая становится доступной в поиске Logger, и для события
резервирования, которое появляется в Notification Service. Отсутствие побочного
эффекта проверяется отдельным тестом для заблокированной карты: отклонённая
транзакция не уменьшает баланс. Конкретные цепочки и границы ожидания приведены
ниже.

### Асинхронность и сквозная цепочка

Под сквозной цепочкой здесь понимается один пользовательский запрос, который
проходит через несколько сервисов и заканчивается проверкой результата в другом
сервисе. Для авторизации это не прямой вызов Authorization: запрос начинается в
Gateway, затем Gateway проксирует его в Switch, Switch определяет эмитента по
BIN и вызывает Authorization, а Authorization обращается в Card Management для
резервирования средств. После ответа Authorization Switch формирует запись
транзакции и публикует её в RabbitMQ для Transaction Logger. Поэтому HTTP-ответ
Gateway и появление записи в поиске Logger происходят в разные моменты.

### Асинхронный тест: `AuthorizationApiTest.gatewayTransactionIsEventuallySearchableAndReservesBalance`

1. Через `POST /api/cards` создаётся отдельная ACTIVE-карта с балансом `1000000`.
2. Через `POST /api/transactions` в Gateway отправляется авторизационный запрос.
3. Gateway передаёт запрос в Switch (`POST /api/internal/route`). Switch
   маршрутизирует его по BIN в Authorization (`POST /api/internal/authorize`).
4. Authorization проверяет карту и вызывает Card Management
   (`POST /api/cards/{pan}/reserve`). Switch также запрашивает комиссию у
   Merchant Acquirer, после чего собирает итоговый объект транзакции. При
   успешной авторизации Gateway сразу возвращает `APPROVED` и код `00`.
5. Switch публикует сформированный `TransactionRequest` в RabbitMQ. Consumer
   Transaction Logger принимает сообщение и сохраняет его в хранилище.
6. Тест через `GET /api/transactions/search` Gateway ждёт появления записи с
   нужным PAN и STAN. Используется Awaitility с `atMost(15s)` и
   `pollInterval(250ms)`, без фиксированной задержки. После появления записи
   дополнительно проверяется баланс карты `999900`, то есть резервирование на
   сумму транзакции действительно произошло.

Это основной сквозной API-сценарий: `Gateway → Switch → Authorization → Card
Management + Merchant Acquirer`, а затем `Switch → RabbitMQ → Transaction
Logger → Search через Gateway`.

### Проверка eventual consistency без RabbitMQ: `LoggerApiTest.newTransactionIsStoredAndEventuallySearchable`

Это не сквозной RabbitMQ-сценарий: тест проверяет участок Logger отдельно от
авторизации и Switch. Он напрямую отправляет готовый `TransactionRequest` в
`POST /api/internal/log` на порт
Transaction Logger, проверяет ответ `201` и идентификатор записи, затем выполняет
поиск через публичный Gateway `GET /api/transactions/search`. Awaitility ожидает
до `10s` с интервалом `200ms`, пока в ответе не появится ровно одна запись с тем
же UUID и PAN. Так проверяется согласованность между записью Logger и его
Gateway-маршрутом; RabbitMQ в этом сценарии не участвует.

### Асинхронный тест: `NotificationApiTest.cardEventIsDeliveredToNotificationService`

1. Через Gateway создаётся карта и вызывается
   `POST /api/cards/{pan}/reserve` с уникальным RRN.
2. Card Management сохраняет резервирование и создаёт событие
   `CardServiceReserveEvent` в outbox.
3. Планировщик Card Management публикует outbox-событие в RabbitMQ.
4. Notification Service принимает сообщение из очереди и сохраняет уведомление.
5. Тест опрашивает прямой endpoint Notification Service
   `GET /api/notifications?size=100` и ждёт запись с routing key
   `card.CardServiceReserveEvent` и этим RRN.

Для ожидания доставки используются `atMost(15s)` и `pollInterval(250ms)`, фиксированных
пауз нет. Метод `notificationEndpointReturnsPageMetadata` является синхронной
контрактной проверкой того же сервиса: он не ждёт событие, а проверяет страницу,
`pageable` и `totalElements`.

Остальные тесты Dashboard, Simulator, поиска, экспорта и идемпотентности Logger
также являются синхронными: они проверяют ответ сразу после вызова и не используют
Awaitility. Таким образом, полноценную асинхронную доставку через RabbitMQ
проверяют два теста: авторизация через Gateway и доставка события карты в
Notification Service.

## 6. Доказательства работоспособности проверок

| Тест | Намеренное изменение | Фактический результат |
|---|---|---|
| `AuthorizationApiTest.approvedTransactionHasGeneratedValuesAndNullDeclineReason` | ожидание `responseCode` изменено с `00` на `01` | **КРАСНЫЙ**: `Expected: 01, Actual: 00` — тест различает коды одобрения |
| `LoggerApiTest.exactRedeliveryIsIdempotent` | ожидание повторной доставки изменено с `200` на `201` | **КРАСНЫЙ**: `Expected status code <201> but was <200>` — тест ловит потерю дедупликации |
| `AuthorizationApiTest.blockedCardIsADeclineAndLeavesBalanceUntouched` | `CARD_BLOCKED` заменено на `DO_NOT_HONOR` | **КРАСНЫЙ**: `Expected: DO_NOT_HONOR, Actual: CARD_BLOCKED` — проверяется причина, а не только код `05` |
| `CardManagementContractApiTest.generatorReturnsTheDocumentedStatusDistribution` | ожидание количества ACTIVE-карт изменено | предусмотренный mutation check: тест должен стать красным при изменении распределения `95/3/2` |
| `CardManagementContractApiTest.expiryContractIsReadableAndExpiredStateIsReturned` | ожидание `MMYY` вместо текущего `yyyy-MM` | ранее получен **КРАСНЫЙ** результат: ожидался `[0-9]{4}`, фактически `2029-11` |


## 7. Порядок запуска

```text
cp .env.example .env
docker compose up -d --build
mvn -f services/api-tests/pom.xml verify
docker compose down -v
```
