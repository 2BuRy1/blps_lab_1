#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

echo "[1/3] Building bank-service fat jar"
( cd bank-service && ./gradlew --no-daemon -x test bootJar )

echo "[2/3] Building ticket-service war"
( cd ticket-service && ./gradlew --no-daemon -x test bootWar )

echo "[3/3] Building bitrix24-ra rar"
( cd bitrix24-ra && ./gradlew --no-daemon clean assemble )

echo "Artifacts ready:"
ls -lh bank-service/build/libs/bank-service-0.0.1-SNAPSHOT.jar \
       ticket-service/build/libs/ticket-service-0.0.1-SNAPSHOT.war \
       bitrix24-ra/build/distributions/bitrix24-ra.rar
