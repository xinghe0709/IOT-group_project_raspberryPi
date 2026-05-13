# Trends Page Design

**Date**: 2026-05-13
**Status**: Draft

## Motivation

当前 Dashboard 的四合一线图只保留最近 50 个数据点（约 25 分钟）。需要新增一个 Trends 页面，支持更长时间跨度的环境数据查看（24 小时、7 天、30 天，以及自定义日期范围）。

## Backend

### New API: GET /api/weather/records

**Query parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `from` | ISO-8601 | required | Query start (inclusive) |
| `to` | ISO-8601 | required | Query end (inclusive) |
| `aggregation` | string | `auto` | `hour` / `day` / `auto` |

`auto` logic:
- span ≤ 2 days → `hour`
- span > 2 days → `day`

**Response:**

```json
{
  "code": 200,
  "data": [
    {
      "bucket": "2026-05-13T08:00:00Z",
      "temperature": 23.5,
      "humidity": 67.2,
      "pressure": 1013.1,
      "lux": 420.0,
      "count": 120
    }
  ]
}
```

Each element is a time-bucket average. `count` is the number of raw records in that bucket.

**Error codes:**

| Code | Meaning |
|------|---------|
| 12005 | Missing `from` or `to` parameter |
| 12006 | `from` is after `to` |
| 12007 | Date range exceeds 90 days |

**Implementation:**

- `WeatherRecordRepository`: new `@Query(nativeQuery=true)` method using `date_trunc(?3, created_at)` + `GROUP BY` + `AVG()`
- `WeatherRecordService.getAggregatedRecords(from, to, aggregation)` → `List<AggregatedRecordDto>`
- `WeatherController`: new `@GetMapping("/records")`

`AggregatedRecordDto` is a simple record:

```java
public record AggregatedRecordDto(
    Instant bucket,
    Double temperature,
    Double humidity,
    Double pressure,
    Double lux,
    Long count
) {}
```

## Frontend

### New Page: Trends

Sidebar navigation: Dashboard → **Trends** (new, icon `◈`) → Advisories

### Layout

- **Top bar**: station title "pi-weather-01" + preset buttons (24h / 7d / 30d) + optional custom date range pickers (two `<input type="datetime-local">` + Apply button)
- **Chart area**: 4-in-1 combined Chart.js line chart, height ~400px. Same dual Y-axis as Dashboard (left linear for temperature/humidity/pressure, right logarithmic for lux). Same color palette, fonts, and tooltip style.
- **Summary strip**: 4 mini cards showing min / max / average for each metric over the selected period

### Interactions

- Clicking a preset button immediately fetches data with `aggregation=auto`
- When a preset is active, custom date inputs are cleared; when custom dates are applied, preset highlight is removed
- SSE events do not affect the Trends page (it shows history, not live data)
- Chart uses `maintainAspectRatio: false` with the wrapping div setting the height

### API call

```js
fetch('/api/weather/records?from=' + fromISO + '&to=' + toISO + '&aggregation=auto')
  .then(res => res.json())
  .then(result => renderTrends(result.data))
```

### HTML structure

New `<section class="page" id="page-trends">` in `dashboard.html`, mirroring the existing page pattern. New sidebar `<li>` with `data-page="trends"`.

### JS state

New `loadTrends(from, to, aggregation)` function. Renders chart with fresh data (destroy + recreate, or update datasets). Summary stats computed client-side from the aggregated buckets.

## Data Flow

```
User selects range → fetch GET /api/weather/records
  → WeatherController → WeatherRecordService
    → WeatherRecordRepository (native SQL date_trunc)
      → PostgreSQL (idx_records_created_at index)
  ← JSON [{bucket, temp, humid, press, lux, count}, ...]
  → Chart.js render + summary cards
```

## Testing

| Test | Type | What |
|------|------|------|
| `WeatherRecordRepositoryTest` | Unit (DataJpaTest) | Native query returns correct buckets for hour/day aggregation |
| `WeatherControllerTest` | Integration (MockMvc) | Parameter validation: missing from/to, from > to, >90 days |
| `WeatherControllerTest` | Integration (MockMvc) | Valid request returns 200 with aggregated data |

Frontend tested manually: open Trends page, click presets, verify chart renders with correct time axis and data density.

## Scope

Does NOT include:
- Data export (CSV/PDF)
- Comparison mode (e.g., "today vs yesterday")
- Multi-station selection (only station_id filtering in the query, unused for now)
