# WildFly + Bitrix24 JCA запуск

## 1) Сборка resource adapter

```bash
cd bitrix24-ra
GRADLE_USER_HOME=/tmp/gradle-bitrix24-ra ./gradlew clean assemble
```

Артефакт:

- `bitrix24-ra/build/distributions/bitrix24-ra.rar`

## 2) Деплой `.rar` в WildFly

```bash
cp bitrix24-ra/build/distributions/bitrix24-ra.rar $WILDFLY_HOME/standalone/deployments/
```

Можно задать параметры через env для процесса WildFly:

- `BITRIX_WEBHOOK_BASE_URL`
- `BITRIX_CONNECT_TIMEOUT_MS`
- `BITRIX_READ_TIMEOUT_MS`

## 3) Проверка, что connection factory поднялась

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  '/subsystem=resource-adapters:read-resource(recursive=true)'
```

Ожидаемый JNDI:

- `java:/eis/Bitrix24CF`

(он задается в `bitrix24-ra/src/main/rar/META-INF/ironjacamar.xml`)

## 4) Конфиг ticket-service

В `ticket-service/src/main/resources/application.yml`:

- `integration.bitrix.enabled=true`
- `integration.bitrix.jca.jndi-name=java:/eis/Bitrix24CF`

Если `ticket-service` работает в том же контейнере WildFly, этого достаточно.

## 5) Если ticket-service запускается отдельно от WildFly

Нужно указывать remote JNDI параметры:

- `BITRIX_JCA_INITIAL_CONTEXT_FACTORY`
- `BITRIX_JCA_PROVIDER_URL`
- `BITRIX_JCA_SECURITY_PRINCIPAL`
- `BITRIX_JCA_SECURITY_CREDENTIALS`

Пример:

```bash
BITRIX_JCA_INITIAL_CONTEXT_FACTORY=org.wildfly.naming.client.WildFlyInitialContextFactory
BITRIX_JCA_PROVIDER_URL=remote+http://localhost:8080
BITRIX_JCA_SECURITY_PRINCIPAL=appuser
BITRIX_JCA_SECURITY_CREDENTIALS=apppass
```

И затем запуск сервиса:

```bash
cd ticket-service
./gradlew bootRun
```

## 6) CCI контракт адаптера

`Bitrix24InteractionImpl` принимает `MappedRecord`:

- `operation` (или `path`) — REST method, например `crm.deal.add.json`
- `body` (или `payload`) — JSON body

Возвращает `MappedRecord`:

- `statusCode`
- `body`

Файлы реализации:

- `bitrix24-ra/src/main/java/com/example/bitrix24/ra/*`
