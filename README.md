# Weather

Сервис на Kotlin 2.3.21, Spring Boot 4.1.2 и Java 21. Один раз в час, в начале часа по UTC, запрашивает температуру для настроенного города и сохраняет историю в PostgreSQL. Spring Boot 4.1.2 — последняя стабильная версия в Maven Central на момент создания проекта; предварительные версии исключены.

## Запуск

Нужны Java 21+, Docker с Compose. Maven 3.9.11 загружается через Maven Wrapper с проверкой контрольной суммы.

```sh
cp .env.example .env
# Заполните .env своими значениями. Название города с пробелами заключайте в кавычки.
set -a
. ./.env
set +a
docker compose up -d --wait postgres
./mvnw clean package
java -jar target/weather-0.0.1-SNAPSHOT.jar
```

`.env` исключён из Git. Compose читает его автоматически, Java-процесс — только после экспорта переменных. Миграция Flyway создаёт таблицу при старте приложения. Первый запрос выполняется в начале следующего часа. `/actuator/health` проверяет работоспособность приложения и подключения к БД, но не подтверждает успешность последнего запроса OpenWeather.

| Переменная | Назначение |
|---|---|
| `WEATHER_CITY` | Название города для записи в БД |
| `WEATHER_LATITUDE`, `WEATHER_LONGITUDE` | Координаты этого города; допустимые диапазоны −90…90 и −180…180 |
| `OPENWEATHER_API_KEY` | Ваш ключ с доступом к указанному API и необходимой подпиской |
| `POSTGRES_PASSWORD` | Пароль PostgreSQL, обязателен |
| `POSTGRES_USER` | Пользователь БД; по умолчанию `weather` |
| `DATABASE_URL` | JDBC URL; по умолчанию `jdbc:postgresql://localhost:5432/weather` |
| `WEATHER_CRON` | Spring cron с секундами; по умолчанию `0 0 * * * *`, часовой пояс UTC; `-` отключает сбор |
| `OPENWEATHER_ENDPOINT` | По умолчанию `https://api.openweathermap.org/data/2.5/weather` |

Название города служит меткой. Запрос использует координаты, поэтому они должны соответствовать городу. Передаются `lat`, `lon`, `appid` и `units=metric`. Координаты и название обязательны: случайный город по умолчанию не выбирается.

### Получение температуры

Используется Current Weather API: `GET https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lon}&appid={API_KEY}&units=metric`. Температура читается из числового поля `main.temp`; при `units=metric` это градусы Цельсия. Дополнительная настройка JSON Pointer не требуется.

Пример структуры ответа (тестовые значения):

```json
{"main":{"temp":-7.125,"feels_like":-10.0},"name":"Moscow","cod":200}
```

Сохраняется именно `main.temp`, а не `feels_like`, `temp_min` или `temp_max`. Отсутствующая, строковая или `null` температура не сохраняется. При ошибке HTTP, JSON или БД сборщик продолжит работу в следующем часу; автоматических повторов в течение часа нет. Ключ и тело ответа не выводятся в журнал. HTTPS и проверка сертификатов включены; для локальных HTTP-заглушек разрешены только `localhost` и `127.0.0.1`. При наличии `HTTPS_PROXY` внешний запрос использует его.

## Данные

Таблица `weather_reading`:

- `id` — идентификатор записи;
- `requested_at` — дата и время **начала HTTP-запроса**, `TIMESTAMP WITH TIME ZONE`, сохраняется как UTC;
- `city` — настроенное название города;
- `temperature_celsius` — температура в градусах Цельсия, точный `NUMERIC`.

```sql
SELECT requested_at, city, temperature_celsius
FROM weather_reading
ORDER BY requested_at DESC;
```

Временная метка обозначает время запроса, а не время прогноза из API. История сохраняется в Docker volume. Запускайте один экземпляр сборщика: распределённая блокировка и устранение дубликатов между несколькими экземплярами не реализованы.

## Проверки

```sh
./mvnw test
```

Модульные тесты используют локальный HTTP-сервер, проверяют параметры запроса, кодирование ключа, отрицательные температуры, невалидный JSON, HTTP 401/429/500 и часовое расписание. Реальная подписка OpenWeather не нужна.

Интеграционные тесты используют **отдельную тестовую PostgreSQL** и очищают её таблицу `weather_reading`. Никогда не направляйте их на рабочую БД.

```sh
docker compose up -d --wait postgres
docker compose exec -T postgres sh -c 'createdb -U "$POSTGRES_USER" weather_test'
export TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/weather_test
export TEST_DATABASE_USER="$POSTGRES_USER"
export TEST_DATABASE_PASSWORD="$POSTGRES_PASSWORD"
./mvnw -Pintegration verify
```

Создавайте `weather_test` один раз. Для уже существующей БД команду `createdb` пропустите. По умолчанию тесты используют БД `weather_test`, пользователя `weather` и локальный тестовый пароль `weather-local-only`. Проверяются Flyway, запись всех колонок, накопление истории, отсутствие записи при сбое и восстановление следующего опроса. Тестовые JSON соответствуют структуре Current Weather API; проверки используют локальную HTTP-заглушку и не обращаются к реальному OpenWeather.

## Подготовленная облачная среда

В текущей среде `.env` уже содержит локальные настройки БД со случайным паролем и выбранный город Москва (`55.7504461`, `37.6174943`). Этот файл не отслеживается Git. Ключ OpenWeather нужно внести через защищённые настройки среды как `OPENWEATHER_API_KEY`.

Установочный скрипт среды сохраняет настройки Maven-прокси и кэш вне репозитория. Для проверок в этой среде используйте:

```sh
export MAVEN_USER_HOME=/workspace/.cache/weather/maven
./mvnw -s /workspace/.cache/weather/maven-settings.xml -Dmaven.repo.local=/workspace/.cache/weather/repository test
```

Для интеграционных проверок добавьте `-Pintegration verify` вместо `test`, предварительно экспортировав `TEST_DATABASE_USER` и `TEST_DATABASE_PASSWORD` из локальной конфигурации БД, как показано выше.
