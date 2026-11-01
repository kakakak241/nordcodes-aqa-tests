# Матрица функционального покрытия

Обозначения: R — явно описанное требование; A — допущение из README. Проверяемое соответствие не означает, что предоставленная версия приложения проходит каждую проверку.

| ID | Требование / риск | Проверки |
| --- | --- | --- |
| R1 | POST `/endpoint`, form-urlencoded, JSON-ответ | Все сценарии через `ApiClient`; `ApiResponse` проверяет тип и структуру JSON |
| R2 | Статический настроенный `X-Api-Key` требуется на каждом запросе | `ApiKeyIT`: все три действия × пять неверных/отсутствующих ключей; регистр имени заголовка; отказ не создаёт/не удаляет сессию |
| R3 | Токен: ровно 32 символа A-Z0-9 | `TokenValidationIT.acceptsTokensFromRequiredAlphabet`: цифры, A, F, G, Z, смешанные токены. G/Z/полный алфавит выявляют NC-001 |
| R3 | Границы длины и недопустимые символы | `rejectsInvalidTokens`: 17 наборов × три действия; длины 0/1/31/33/1024; lowercase, кириллица, пробелы, пунктуация, перевод строки, NUL |
| R4 | Нужны token и известное action | `rejectsMissingToken`, `rejectsMissingParameters`, `rejectsInvalidAction` |
| R5 | LOGIN обращается к `/auth`, успех сохраняет токен | `SessionLifecycleIT.completesUserSession`, `ExternalServiceIT.acceptsSuccessfulExternalStatus` |
| R6 | ACTION обращается к `/doAction` только после успешного LOGIN | `rejectsActionBeforeLogin`, `doesNotShareAuthorization`, `handlesExternalErrorsAndCanRecover` |
| R7 | LOGOUT удаляет токен | `completesUserSession`, `allowsLoginAfterLogout`, `keepsSessionsIndependent`; отсутствие внешних запросов при выходе |
| R8 | Во внешний сервис передаются POST, form-urlencoded, Accept JSON и тот же token | `ApiTest.verifyCall` в сценариях LOGIN и ACTION; запрет лишнего маршрута |
| R9 | Тело внешнего ответа не влияет на результат | `ExternalServiceIT.ignoresExternalBody`: два действия × пять тел, включая JSON с ERROR и повреждённый JSON |
| R10 | Успех — OK, ошибка — ERROR с message | `ApiResponse.isOk/isError` во всех положительных/отрицательных сценариях; точные статусы см. A1/A3 |
| R11 | Понятный отчёт | Русские DisplayName, Allure steps, HTTP-вложения, внешний журнал, лог падения, категории и environment |
| A2 | Внешние 2xx означают успех | `acceptsSuccessfulExternalStatus`: два действия × пять кодов |
| A3 | Ошибки внешнего сервиса не становятся успехом | `handlesExternalErrorsAndCanRecover`: два действия × десять кодов; проверка отсутствия авторизации после неуспешного LOGIN и восстановления |
| A3 | Разрыв соединения и небольшая задержка | `handlesConnectionReset`, `acceptsDelayedSuccess`; без выдуманных требований к продуктовому тайм-ауту |
| A4 | Повторные вход/выход | `AdditionalBehaviorIT`: 409 при повторном LOGIN, 403 при неизвестном/удалённом токене; сохранение действующей сессии |
| A5 | Дополнительная нормализация ввода | `trimsAction`, `ignoresAdditionalField`, явно отделены от требований |
| R5-R7 | Состояния разных пользователей не смешиваются | `keepsSessionsIndependent`, `doesNotShareAuthorization`, `handlesIndependentConcurrentSessions` |

## Сознательные границы

- Нет исходников и цели измерять покрытие строк/ветвей JAR; процент code coverage не заявляется.
- Нет нагрузки, проверки SLA, исчерпывающего fuzzing и доказательства отсутствия гонок. Параллельный сценарий проверяет ограниченный пример изоляции.
- Неподдерживаемые методы/Content-Type и редиректы исследованы как наблюдения, см. `DEFECTS.md`; ожидаемый продуктовый контракт для них не задан.
- Не фиксируются точные локализованные тексты ошибок, время ответа и количество сетевых повторов при обрыве соединения.
- Явно заданный алфавит A-Z0-9 имеет приоритет над фактической hex-валидацией приложения; соответствующие проверки остаются падающими.
