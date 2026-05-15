#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────
#  Weather Station Simulator
#  Cycles through 5 weather scenarios and POSTs
#  to the Java backend every 30s (matching the
#  real Raspberry Pi hardware interval).
# ─────────────────────────────────────────────

ENDPOINT="${1:-http://localhost:8080/api/weather/record}"
STATION_ID="pi-simulator-01"
INTERVAL=5

# 5 weather scenarios (aligned with AdvisoryPromptTemplateTest)
# name|temp|humidity|pressure|lux|alerts_json
SCENARIOS=(
  "Comfortable Sunny|22.5|55|1017|8000|[]"
  "Hot and Humid|38.5|82|1005|60000|[\"HIGH_TEMP:41.2\"]"
  "Storm Warning|15.0|70|992|3000|[\"LOW_PRESSURE:992\"]"
  "Cold Dark Night|3.0|45|1022|5|[\"LOW_TEMP:3.0\"]"
  "Mild Comfortable|24.0|48|1015|12000|[]"
)

N=${#SCENARIOS[@]}

echo "╔══════════════════════════════════════════╗"
echo "║     Weather Station Simulator           ║"
echo "╠══════════════════════════════════════════╣"
echo "║  Endpoint: $ENDPOINT"
echo "║  Station:  $STATION_ID"
echo "║  Interval: ${INTERVAL}s"
echo "║  Scenarios: $N"
echo "╚══════════════════════════════════════════╝"
echo ""

trap 'echo ""; echo "Simulator stopped."; exit 0' INT TERM

i=0
while true; do
  IFS='|' read -r name temp humidity pressure lux alerts <<< "${SCENARIOS[$i]}"

  TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  PAYLOAD=$(cat <<JSON
{
  "station_id": "$STATION_ID",
  "timestamp": "$TIMESTAMP",
  "temperature": $temp,
  "humidity": $humidity,
  "pressure": $pressure,
  "lux": $lux,
  "alerts": $alerts
}
JSON
)

  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$ENDPOINT" \
    -H "Content-Type: application/json" \
    -H "User-Agent: PiicoDev-WeatherStation-Sim/1.0" \
    -d "$PAYLOAD")

  echo "[$(date '+%H:%M:%S')]  Scenario $((i+1))/$N — $name  →  HTTP $HTTP_CODE"

  i=$(( (i + 1) % N ))
  sleep "$INTERVAL"
done
