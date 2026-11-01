# Результаты

Последний полный запуск: 2 ноября 2026, Windows 11, Java 17.0.20.1.

147 проверок: 144 прошли, 3 упали. Ошибок выполнения и пропусков нет. Все три падения воспроизводят [NC-001](docs/DEFECTS.md): приложение отклоняет допустимые токены с буквами G и Z. Maven завершился с кодом 1; отчёт Allure сформирован.

[Скачать отчёт Allure](https://github.com/kakakak241/nordcodes-aqa-tests/releases/latest). Распакуйте архив и откройте `index.html`.

Для повторного запуска нужен предоставленный JAR в `app/internal-0.0.1-SNAPSHOT.jar` и команда `./mvnw clean verify` (`.\mvnw.cmd clean verify` в Windows). Другой путь к JAR задаётся через `-Dsut.jar=...`.

Результаты записываются в `target/allure-results`, готовый отчёт — в `target/allure-report/index.html`.

[Сводка проверенных запусков](docs/results/verified-runs.json) содержит также предыдущий прогон на Java 24 с тем же результатом. [Таблица проверок](docs/COVERAGE.md).
