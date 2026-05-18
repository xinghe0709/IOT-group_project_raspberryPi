# Heat Index & Server-Side Alert Evaluation Design

> **Status:** Approved | **Date:** 2026-05-18

## Overview

The Raspberry Pi now sends two new fields — `heat_index` (Double) and `heat_stress_category` (String) — in the POST payload. The backend must persist both, forward `heat_index` to the frontend, display it on the dashboard/trends/charts, and implement server-side alert evaluation with 5 new thresholds, replacing the Pi-computed alerts entirely.

## Data Model Changes

### JPA Entity — `WeatherRecord.java`

Add two columns:

```java
@Column
private Double heatIndex;

@Column(length = 50)
private String heatStressCategory;
```

### Request DTO — `WeatherRecordRequest.java`

Add two fields (match incoming JSON keys):

```java
@JsonProperty("heat_index")
private Double heatIndex;

@JsonProperty("heat_stress_category")
private String heatStressCategory;
```

### Response DTO — `WeatherRecordResponse.java`

Add one field (never expose `heat_stress_category` to frontend):

```java
private Double heatIndex;
```

Update `toResponse()` in `WeatherRecordService` to map `r.getHeatIndex()`.

### Aggregated DTO — `AggregatedRecordDto.java`

Add one field:

```java
Double heatIndex
```

## Database Migration

JPA `ddl-auto: update` will apply automatically. Equivalent DDL:

```sql
ALTER TABLE weather_records
  ADD COLUMN heat_index DOUBLE PRECISION,
  ADD COLUMN heat_stress_category VARCHAR(50);
```

## Repository — Native SQL Updates

Both `findAggregatedByHour` and `findAggregatedByDay` add `AVG(heat_index) AS heat_index` as the 6th column in the SELECT list. Example:

```sql
SELECT
    date_trunc('hour', created_at) AS bucket,
    AVG(temperature) AS temperature,
    AVG(humidity) AS humidity,
    AVG(pressure) AS pressure,
    AVG(lux) AS lux,
    AVG(heat_index) AS heat_index,
    CAST(COUNT(*) AS BIGINT) AS count
FROM weather_records
WHERE created_at >= :from AND created_at <= :to
GROUP BY bucket
ORDER BY bucket ASC
```

## Service — `WeatherRecordService.java` Changes

### Alert Evaluation (replaces Pi alerts)

New constants and method:

```java
private static final double HEAT_INDEX_HIGH = 41.0;
private static final double TEMP_LOW        = 5.0;
private static final double HUMIDITY_HIGH   = 65.0;
private static final double PRESSURE_LOW    = 1000.0;
private static final double LUX_HIGH        = 50000.0;

private List<String> evaluateAlerts(WeatherRecordRequest request) {
    List<String> alerts = new ArrayList<>();
    if (request.getHeatIndex() != null && request.getHeatIndex() >= HEAT_INDEX_HIGH)
        alerts.add("HIGH_HEAT_INDEX:" + request.getHeatIndex());
    if (request.getTemperature() != null && request.getTemperature() <= TEMP_LOW)
        alerts.add("LOW_TEMP:" + request.getTemperature());
    if (request.getHumidity() != null && request.getHumidity() >= HUMIDITY_HIGH)
        alerts.add("HIGH_HUMIDITY:" + request.getHumidity());
    if (request.getPressure() != null && request.getPressure() <= PRESSURE_LOW)
        alerts.add("LOW_PRESSURE:" + request.getPressure());
    if (request.getLux() != null && request.getLux() >= LUX_HIGH)
        alerts.add("HIGH_LUX:" + request.getLux());
    return alerts;
}
```

### `saveRecord()` changes

- Ignore `request.getAlerts()` entirely
- Call `evaluateAlerts(request)` to produce the alerts JSON
- Map new fields into the `WeatherRecord` builder (`.heatIndex(...)`, `.heatStressCategory(...)`)

### `getAggregatedRecords()` changes

- Map `toDouble(row[5])` as `heatIndex` in `AggregatedRecordDto` constructor

## Threshold Comparison (Old → New)

| Sensor | Old Threshold | New Threshold |
|--------|--------------|---------------|
| Heat Index | (did not exist) | **≥ 41.0°C** |
| Temperature | > 40.0°C (HIGH) / < 5.0°C (LOW) | **≤ 5.0°C** (LOW only) |
| Humidity | > 85.0% | **≥ 65.0%** |
| Pressure | < 1000.0 hPa | **≤ 1000.0 hPa** |
| Lux | > 80,000 lux | **≥ 50,000 lux** |

Note: HIGH_TEMP is removed as a standalone alert — heat_index now covers heat stress.

## Frontend Changes

### HTML — `dashboard.html`

**New CSS variable:**
```css
--heat: #eb6b7f;
```

**New reading card (after light card):**
```html
<div class="reading-card heat">
  <div class="card-stripe"></div>
  <div class="card-label">Heat Index</div>
  <div class="card-value" id="val-heat">--</div>
  <div class="card-unit">&deg;C</div>
</div>
```

**Cards grid columns:** `repeat(4, 1fr)` → `repeat(5, 1fr)` with responsive breakpoints.

**New summary card (in `#summary-strip`):**
```html
<div class="summary-card heat">
  <div class="summary-label">Heat Index</div>
  <div class="summary-range" id="sum-heat">-- &ndash; --</div>
  <div class="summary-avg" id="sum-heat-avg">Avg: --&deg;C</div>
</div>
```

**Summary grid columns:** `repeat(4, 1fr)` → `repeat(5, 1fr)`.

**Reading card styles:**
```css
.reading-card.heat .card-stripe { background: var(--heat); }
.reading-card.heat .card-value  { color: var(--heat); }
.summary-card.heat { border-top-color: var(--heat); }
```

### JS — `app.js`

**New DOM ref:**
```javascript
const valHeat = $('#val-heat');
```

**New chartData array:**
```javascript
heatIndex: []
```

**`updateReadingCards()` adds:**
```javascript
valHeat.textContent = fmtNum(record.heatIndex, 1);
```

**`pushChartPoint()` adds:**
```javascript
chartData.heatIndex.push(record.heatIndex);
// shift when > MAX_CHART_POINTS
```

**Real-time chart:** 5th dataset (Heat Index, color `#eb6b7f`, left Y-axis).

**`createTrendsChart()`:** 5th dataset (same spec).

**`pushTrendsPoint()`:** Push `record.heatIndex` to trends datasets[4] + trim.

**Trends summary update:** Compute range + avg for heatIndex data and populate `#sum-heat` / `#sum-heat-avg`.

## Timestamp Handling

The new `timestamp` field in the JSON payload is **ignored**. The server continues to set `created_at = Instant.now()` via `@PrePersist`.

## What Does NOT Change

- `AdvisoryScheduler` — intentionally left unchanged; the LLM prompt can access `heatIndex` via the existing record fields without prompt modification
- `SseBroadcastService` — unchanged; broadcasts whatever `WeatherRecordResponse` contains
- `GlobalExceptionHandler` — unchanged
- Raspberry Pi code — unchanged
