#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────
#  Weather Station Simulator
#  Cycles through 5 weather scenarios and POSTs
#  to the Java backend every 5s.
# ─────────────────────────────────────────────

ENDPOINT="${1:-http://localhost:8080/api/weather/record}"
STATION_ID="pi-simulator-01"
INTERVAL=5

# 5 weather scenarios
# name|temp|humidity|pressure|lux|heat_index|heat_stress_category
SCENARIOS=(
  "Comfortable Sunny|22.5|55|1017|8000|22.0|Normal"
  "Hot and Humid|38.5|82|1005|60000|56.0|Extreme Danger"
  "Storm Warning|15.0|70|992|3000|15.0|Normal"
  "Cold Dark Night|3.0|45|1022|5|3.0|Normal"
  "Mild Comfortable|24.0|48|1015|12000|24.0|Normal"
)

N=${#SCENARIOS[@]}

echo "╔══════════════════════════════════════════╗"
echo "║     Weather Station Simulator           ║"
echo "╠══════════════════════════════════════════╣"
echo "║  Endpoint: $ENDPOINT"
echo "║  Station:  $STATION_ID"
echo "║  Interval: ${INTERVAL}s"
echo "║  Scenarios: $N"
echo "║  Alerts evaluated server-side"
echo "╚══════════════════════════════════════════╝"
echo ""

trap 'echo ""; echo "Simulator stopped."; exit 0' INT TERM

i=0
while true; do
  IFS='|' read -r name temp humidity pressure lux heat_index heat_stress_category <<< "${SCENARIOS[$i]}"

  TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  PAYLOAD=$(cat <<JSON
{
  "station_id": "$STATION_ID",
  "timestamp": "$TIMESTAMP",
  "temperature": $temp,
  "humidity": $humidity,
  "pressure": $pressure,
  "lux": $lux,
  "heat_index": $heat_index,
  "heat_stress_category": "$heat_stress_category"
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
