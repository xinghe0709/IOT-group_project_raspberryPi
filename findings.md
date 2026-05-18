# Findings: Heat Index Feature

## Design Decisions
- `heat_stress_category` stored in DB but NOT exposed to frontend (only `heat_index` shown)
- `timestamp` field in new JSON ignored; server continues using `Instant.now()`
- Server-side alert evaluation; Pi's alerts field completely ignored
- 5 new thresholds: Heat ≥41°C, Temp ≤5°C, Humidity ≥65%, Pressure ≤1000hPa, Lux ≥50000lx
- HIGH_TEMP alert removed; heat_index covers heat stress

## Files to Change
| Layer | File | Change |
|------|------|--------|
| Entity | WeatherRecord.java | +heatIndex, +heatStressCategory |
| Request | WeatherRecordRequest.java | +heatIndex, +heatStressCategory |
| Response | WeatherRecordResponse.java | +heatIndex |
| Aggregated | AggregatedRecordDto.java | +heatIndex |
| Repository | WeatherRecordRepository.java | +AVG(heat_index) in both queries |
| Service | WeatherRecordService.java | +evaluateAlerts(), map new fields |
| HTML | dashboard.html | 5th reading card, 5th summary card, --heat var |
| JS | app.js | 5th dataset everywhere, heatIndex handling |
