#!/usr/bin/env sh
set -eu
: "${INFRAI_API_KEY:?Set INFRAI_API_KEY first}"
mvn -q spring-boot:run &
service_pid=$!
trap 'kill "$service_pid" 2>/dev/null || true' EXIT INT TERM
sleep 4
curl --fail-with-body --request POST http://localhost:8080/orders/checkout \
  --header 'Content-Type: application/json' \
  --data '{"orderId":"ord_1042","paid":true,"stockReserved":true,"receiptEmail":"buyer@example.test"}'
