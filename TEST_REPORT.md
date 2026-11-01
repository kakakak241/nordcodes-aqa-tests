# Результаты

Последний полный запуск: 1 ноября 2026, Windows 11, Java 17.0.20.1.

147 проверок: 144 прошли, 3 упали. Ошибок выполнения и пропусков нет. Все три падения воспроизводят [NC-001](docs/DEFECTS.md): приложение отклоняет допустимые токены с буквами G и Z. Maven завершился с кодом 1; отчёты сформированы.

[Скачать готовый отчёт](https://github.com/kakakak241/nordcodes-aqa-tests/releases/tag/report-2026-11-01). В архиве есть краткая сводка и подробный Allure с запросами, ответами и логами.

Для повторного запуска нужен предоставленный JAR в `app/internal-0.0.1-SNAPSHOT.jar` и команда `./mvnw clean verify` (`.\mvnw.cmd clean verify` в Windows). Другой путь к JAR задаётся через `-Dsut.jar=...`.

Результаты записываются в `target/allure-results`, отчёты — в `target/allure-report/summary.html` и `index.html`.

[Сводка проверенных запусков](docs/results/verified-runs.json) содержит также предыдущий прогон на Java 24 с тем же результатом. [Таблица проверок](docs/COVERAGE.md).
