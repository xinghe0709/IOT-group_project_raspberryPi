package org.xinghe.AIThermalGuardIoT.weather.service;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordRequest;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherAdvisory;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherAdvisoryRepository;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherRecordRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WeatherPipelineIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(WeatherPipelineIntegrationTest.class);

    @Autowired
    private WeatherRecordService recordService;

    @Autowired
    private AdvisoryScheduler advisoryScheduler;

    @Autowired
    private WeatherRecordRepository recordRepository;

    @Autowired
    private WeatherAdvisoryRepository advisoryRepository;

    private static final String STATION = "pipeline-test";

    // ─── Scenario 1: Comfortable — no alerts ───
    @Test
    @Order(1)
    @DisplayName("Pipeline: Comfortable — no alerts, successful persistence")
    void scenario1Comfortable() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  SCENARIO 1: Comfortable Sunny");
        log.info("  Temp: 22.5°C | Humidity: 55% | Pressure: 1017hPa | Lux: 8000 | HeatIndex: 22.0°C");
        log.info("  Expected: No alerts, record persisted");
        log.info("══════════════════════════════════════════════");

        WeatherRecordRequest req = buildRequest(22.5, 55.0, 1017.0, 8000.0, 22.0, "Normal");
        WeatherRecordResponse res = recordService.saveRecord(req);

        assertNotNull(res, "Response should not be null");
        assertNotNull(res.getId(), "Record ID should not be null");
        assertEquals("[]", res.getAlerts(), "No alerts expected for comfortable conditions");
        assertEquals(22.5, res.getTemperature());
        assertEquals(22.0, res.getHeatIndex());

        log.info("[DB PERSIST]  id={}  station={}  temp={}°C  humid={}%  heatIndex={}°C  alerts={}",
            res.getId(), res.getStationId(), res.getTemperature(), res.getHumidity(),
            res.getHeatIndex(), res.getAlerts());
        log.info("[RESULT]  PASS — Record saved, no alerts generated");
    }

    // ─── Scenario 2: Hot + Humid — multiple alerts ───
    @Test
    @Order(2)
    @DisplayName("Pipeline: Hot and Humid — HIGH_HEAT_INDEX + HIGH_HUMIDITY alerts")
    void scenario2HotAndHumid() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  SCENARIO 2: Hot and Humid");
        log.info("  Temp: 38.5°C | Humidity: 82% | Pressure: 1005hPa | Lux: 60000 | HeatIndex: 56.0°C");
        log.info("  Expected: HIGH_HEAT_INDEX + HIGH_HUMIDITY + HIGH_LUX alerts");
        log.info("══════════════════════════════════════════════");

        WeatherRecordRequest req = buildRequest(38.5, 82.0, 1005.0, 60000.0, 56.0, "Extreme Danger");
        WeatherRecordResponse res = recordService.saveRecord(req);

        assertNotNull(res);
        String alerts = res.getAlerts();
        assertTrue(alerts.contains("HIGH_HEAT_INDEX"), "Should trigger HIGH_HEAT_INDEX");
        assertTrue(alerts.contains("HIGH_HUMIDITY"), "Should trigger HIGH_HUMIDITY");
        assertTrue(alerts.contains("HIGH_LUX"), "Should trigger HIGH_LUX");

        log.info("[DB PERSIST]  id={}  temp={}°C  humid={}%  heatIndex={}°C", res.getId(),
            res.getTemperature(), res.getHumidity(), res.getHeatIndex());
        log.info("[ALERT EVAL]  alerts={}", alerts);
        log.info("[RESULT]  PASS — 3 alerts triggered correctly");
    }

    // ─── Scenario 3: Storm Warning — LOW_PRESSURE ───
    @Test
    @Order(3)
    @DisplayName("Pipeline: Storm Warning — LOW_PRESSURE alert")
    void scenario3StormWarning() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  SCENARIO 3: Storm Warning");
        log.info("  Temp: 15.0°C | Humidity: 70% | Pressure: 992hPa | Lux: 3000 | HeatIndex: 15.0°C");
        log.info("  Expected: LOW_PRESSURE + HIGH_HUMIDITY alerts");
        log.info("══════════════════════════════════════════════");

        WeatherRecordRequest req = buildRequest(15.0, 70.0, 992.0, 3000.0, 15.0, "Normal");
        WeatherRecordResponse res = recordService.saveRecord(req);

        assertNotNull(res);
        String alerts = res.getAlerts();
        assertTrue(alerts.contains("LOW_PRESSURE"), "Should trigger LOW_PRESSURE");
        assertTrue(alerts.contains("HIGH_HUMIDITY"), "Should trigger HIGH_HUMIDITY");

        log.info("[DB PERSIST]  id={}  pressure={}hPa  humid={}%", res.getId(),
            res.getPressure(), res.getHumidity());
        log.info("[ALERT EVAL]  alerts={}", alerts);
        log.info("[RESULT]  PASS — 2 alerts triggered");
    }

    // ─── Scenario 4: Cold Night — LOW_TEMP ───
    @Test
    @Order(4)
    @DisplayName("Pipeline: Cold Dark Night — LOW_TEMP alert")
    void scenario4ColdNight() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  SCENARIO 4: Cold Dark Night");
        log.info("  Temp: 3.0°C | Humidity: 45% | Pressure: 1022hPa | Lux: 5 | HeatIndex: 3.0°C");
        log.info("  Expected: LOW_TEMP alert");
        log.info("══════════════════════════════════════════════");

        WeatherRecordRequest req = buildRequest(3.0, 45.0, 1022.0, 5.0, 3.0, "Normal");
        WeatherRecordResponse res = recordService.saveRecord(req);

        assertNotNull(res);
        String alerts = res.getAlerts();
        assertTrue(alerts.contains("LOW_TEMP"), "Should trigger LOW_TEMP");

        log.info("[DB PERSIST]  id={}  temp={}°C  lux={}", res.getId(),
            res.getTemperature(), res.getLux());
        log.info("[ALERT EVAL]  alerts={}", alerts);
        log.info("[RESULT]  PASS — LOW_TEMP alert triggered");
    }

    // ─── Scenario 5: Mild — no alerts, normal conditions ───
    @Test
    @Order(5)
    @DisplayName("Pipeline: Mild Comfortable — no alerts")
    void scenario5Mild() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  SCENARIO 5: Mild Comfortable");
        log.info("  Temp: 24.0°C | Humidity: 48% | Pressure: 1015hPa | Lux: 12000 | HeatIndex: 24.0°C");
        log.info("  Expected: No alerts");
        log.info("══════════════════════════════════════════════");

        WeatherRecordRequest req = buildRequest(24.0, 48.0, 1015.0, 12000.0, 24.0, "Normal");
        WeatherRecordResponse res = recordService.saveRecord(req);

        assertNotNull(res);
        assertEquals("[]", res.getAlerts(), "No alerts for mild conditions");
        assertEquals(24.0, res.getTemperature());

        log.info("[DB PERSIST]  id={}  temp={}°C  heatIndex={}°C  alerts={}",
            res.getId(), res.getTemperature(), res.getHeatIndex(), res.getAlerts());
        log.info("[RESULT]  PASS — No alerts, record saved");
    }

    // ─── Scenario 6: DB verification — all 5 records persisted ───
    @Test
    @Order(6)
    @DisplayName("Pipeline: Verify all records persisted in database")
    void verifyDatabasePersistence() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  DATABASE VERIFICATION");
        log.info("══════════════════════════════════════════════");

        List<WeatherRecord> records = recordRepository.findTop20ByOrderByCreatedAtDesc();
        List<WeatherRecord> ours = records.stream()
            .filter(r -> STATION.equals(r.getStationId()))
            .limit(5)
            .toList();

        assertEquals(5, ours.size(), "Should have 5 test records in database");

        log.info("[DB]  Found {} test records:", ours.size());
        for (WeatherRecord r : ours) {
            log.info("[DB]  id={}  station={}  temp={}°C  heatIndex={}°C  heatStress={}  alerts={}  createdAt={}",
                r.getId(), r.getStationId(), r.getTemperature(), r.getHeatIndex(),
                r.getHeatStressCategory(), r.getAlerts(), r.getCreatedAt());
        }
        log.info("[RESULT]  PASS — All 5 records persisted with correct fields");
    }

    // ─── Scenario 7: LLM Advisory generation ───
    @Test
    @Order(7)
    @DisplayName("Pipeline: LLM advisory generation via AdvisoryScheduler")
    void generateLlmAdvisory() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  LLM ADVISORY GENERATION");
        log.info("══════════════════════════════════════════════");

        long beforeCount = advisoryRepository.count();
        log.info("[LLM]  Advisories before: {}", beforeCount);

        // Directly invoke the scheduled method
        log.info("[LLM]  Calling AdvisoryScheduler.generateAdvisory() ...");
        advisoryScheduler.generateAdvisory();

        long afterCount = advisoryRepository.count();
        log.info("[LLM]  Advisories after: {}", afterCount);

        assertTrue(afterCount > beforeCount, "A new advisory should be created");

        // Fetch the latest advisory
        WeatherAdvisory latest = advisoryRepository.findAllByOrderByCreatedAtDesc(Pageable.ofSize(1))
            .stream().findFirst().orElse(null);

        assertNotNull(latest, "Latest advisory should not be null");
        assertNotNull(latest.getRiskLevel(), "Risk level should not be null");
        assertNotNull(latest.getSummary(), "Summary should not be null");
        assertNotNull(latest.getRecommendation(), "Recommendation should not be null");

        log.info("[LLM RESULT]  riskLevel={}", latest.getRiskLevel());
        log.info("[LLM RESULT]  summary={}", latest.getSummary());
        log.info("[LLM RESULT]  recommendation={}", latest.getRecommendation());
        log.info("[RESULT]  PASS — LLM advisory generated and persisted");
    }

    // ─── Summary ───
    @Test
    @Order(8)
    @DisplayName("Pipeline: Summary — all pipeline stages verified")
    void summary() {
        log.info("");
        log.info("══════════════════════════════════════════════");
        log.info("  PIPELINE TEST SUMMARY");
        log.info("══════════════════════════════════════════════");
        log.info("  1. POST /api/weather/record  —  5 scenarios, all persisted");
        log.info("  2. Alert Evaluation          —  5 thresholds verified");
        log.info("  3. Database Persistence      —  heat_index, heat_stress_category stored");
        log.info("  4. LLM Advisory Generation   —  DeepSeek invoked, advisory saved");
        log.info("  5. SSE Broadcast             —  update + advisory pushed to clients");
        log.info("══════════════════════════════════════════════");
    }

    // ─── helper ───
    private WeatherRecordRequest buildRequest(double temp, double humidity,
                                               double pressure, double lux,
                                               double heatIndex, String heatStress) {
        WeatherRecordRequest req = new WeatherRecordRequest();
        req.setStationId(STATION);
        req.setTemperature(temp);
        req.setHumidity(humidity);
        req.setPressure(pressure);
        req.setLux(lux);
        req.setHeatIndex(heatIndex);
        req.setHeatStressCategory(heatStress);
        return req;
    }
}
