# Быстрый запуск в Docker (без Gradle внутри контейнеров)

Контейнеры запускаются только из готовых артефактов.
Внутри Docker `./gradlew build` не используется.

## 1) Подготовить артефакты один раз на хосте

```bash
./scripts/prepare-artifacts.sh
```

Скрипт соберет:

- `bank-service/build/libs/bank-service-0.0.1-SNAPSHOT.jar`
- `ticket-service/build/libs/ticket-service-0.0.1-SNAPSHOT.war`
- `bitrix24-ra/build/distributions/bitrix24-ra.rar`

## 2) Поднять окружение

```bash
docker compose up --build -d
```

Поднимутся:

- PostgreSQL (`5432`)
- Zookeeper (`2181`)
- Kafka (`9092`)
- WildFly с Bitrix24 RA + ticket-service (`8088`, mgmt `9990`)
- `bank-service` (`8081`)

## 3) Проверка

- API ticket-service: `http://localhost:8088/api/v1`
- API bank-service: `http://localhost:8081/api/v1`
- WildFly HTTP: `http://localhost:8088`

Логи:

```bash
docker compose logs -f wildfly-bitrix-ra bank-service
```

## 4) Настройки Bitrix24 webhook

По умолчанию в RA уже прописан ваш webhook.
Если нужно переопределить:

```bash
BITRIX_WEBHOOK_BASE_URL="https://.../rest/1/.../" docker compose up --build -d
```

## 5) Пересборка только после изменений

Если меняли код сервиса/RA:

```bash
./scripts/prepare-artifacts.sh
docker compose up --build -d
```
