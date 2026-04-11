# bitrix24-ra

Outbound Jakarta Connectors (JCA) Resource Adapter for Bitrix24 webhook.

## Build

```bash
cd bitrix24-ra
./gradlew clean assemble
```

Artifact:

- `build/distributions/bitrix24-ra.rar`

## Deploy To WildFly

1. Copy `.rar` to WildFly deployments:

```bash
cp build/distributions/bitrix24-ra.rar $WILDFLY_HOME/standalone/deployments/
```

2. Ensure env vars are available for WildFly process (optional):

- `BITRIX_WEBHOOK_BASE_URL`
- `BITRIX_CONNECT_TIMEOUT_MS`
- `BITRIX_READ_TIMEOUT_MS`

3. Start WildFly and verify JNDI `java:/eis/Bitrix24CF` exists.

## CCI Contract

Input `MappedRecord` keys accepted by interaction:

- `operation` (required) - e.g. `crm.deal.add.json`
- `path` (fallback for `operation`)
- `body` (JSON body)
- `payload` (fallback for `body`)

Output `MappedRecord` keys:

- `statusCode`
- `body`
