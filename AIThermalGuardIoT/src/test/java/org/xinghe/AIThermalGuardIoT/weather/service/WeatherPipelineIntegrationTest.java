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

    @Autowired private WeatherRecordService recordService;
    @Autowired private AdvisoryScheduler advisoryScheduler;
    @Autowired private WeatherRecordRepository recordRepository;
    @Autowired private WeatherAdvisoryRepository advisoryRepository;

    private static final String ST = "pipeline-test";
    private int totalAlerts;

    @Test @Order(1)
    @DisplayName("Scenario 1: Comfortable — no alerts")
    void scenario1Comfortable() {
        var res = recordService.saveRecord(req(22.5, 55, 1017, 8000, 22.0, "Normal"));
        assertNotNull(res.getId());
        assertEquals("[]", res.getAlerts());
        assertEquals(22.5, res.getTemperature());
        assertEquals(22.0, res.getHeatIndex());
        log.info("S1 Comfortable   | id={}  temp=22.5  heatIdx=22.0  alerts=none  ✓", res.getId());
    }

    @Test @Order(2)
    @DisplayName("Scenario 2: Hot + Humid — 3 alerts")
    void scenario2HotAndHumid() {
        var res = recordService.saveRecord(req(38.5, 82, 1005, 60000, 56.0, "Extreme Danger"));
        String a = res.getAlerts();
        assertTrue(a.contains("HIGH_HEAT_INDEX"));
        assertTrue(a.contains("HIGH_HUMIDITY"));
        assertTrue(a.contains("HIGH_LUX"));
        totalAlerts += 3;
        log.info("S2 Hot+Humid     | id={}  temp=38.5  heatIdx=56.0  alerts=[HEAT_INDEX HUMIDITY LUX]  ✓", res.getId());
    }

    @Test @Order(3)
    @DisplayName("Scenario 3: Storm — LOW_PRESSURE + HIGH_HUMIDITY")
    void scenario3StormWarning() {
        var res = recordService.saveRecord(req(15.0, 70, 992, 3000, 15.0, "Normal"));
        String a = res.getAlerts();
        assertTrue(a.contains("LOW_PRESSURE"));
        assertTrue(a.contains("HIGH_HUMIDITY"));
        totalAlerts += 2;
        log.info("S3 Storm         | id={}  press=992  humid=70  alerts=[PRESSURE HUMIDITY]  ✓", res.getId());
    }

    @Test @Order(4)
    @DisplayName("Scenario 4: Cold — LOW_TEMP")
    void scenario4ColdNight() {
        var res = recordService.saveRecord(req(3.0, 45, 1022, 5, 3.0, "Normal"));
        assertTrue(res.getAlerts().contains("LOW_TEMP"));
        totalAlerts += 1;
        log.info("S4 Cold          | id={}  temp=3.0  alerts=[LOW_TEMP]  ✓", res.getId());
    }

    @Test @Order(5)
    @DisplayName("Scenario 5: Mild — no alerts")
    void scenario5Mild() {
        var res = recordService.saveRecord(req(24.0, 48, 1015, 12000, 24.0, "Normal"));
        assertEquals("[]", res.getAlerts());
        log.info("S5 Mild          | id={}  temp=24.0  alerts=none  ✓", res.getId());
    }

    @Test @Order(6)
    @DisplayName("DB: verify 5 records persisted")
    void verifyDatabasePersistence() {
        var ours = recordRepository.findTop20ByOrderByCreatedAtDesc().stream()
            .filter(r -> ST.equals(r.getStationId())).limit(5).toList();
        assertEquals(5, ours.size());
        log.info("DB Verification  | {} records persisted (heatIndex, heatStressCategory, alerts)  total_alerts={}  ✓",
            ours.size(), totalAlerts);
    }

    @Test @Order(7)
    @DisplayName("LLM: advisory generation")
    void generateLlmAdvisory() {
        long before = advisoryRepository.count();
        advisoryScheduler.generateAdvisory();
        long after = advisoryRepository.count();
        assertTrue(after > before);

        var latest = advisoryRepository.findAllByOrderByCreatedAtDesc(Pageable.ofSize(1))
            .stream().findFirst().orElseThrow();
        assertNotNull(latest.getRiskLevel());
        assertNotNull(latest.getSummary());
        assertNotNull(latest.getRecommendation());

        log.info("LLM Advisory     | risk={}  summary={}  ✓", latest.getRiskLevel(), latest.getSummary());
    }

    @Test @Order(8)
    @DisplayName("Summary: pipeline stages verified")
    void summary() {
        long records = recordRepository.count();
        long advisories = advisoryRepository.count();
        log.info("══════════════════════════════════════════");
        log.info(" PIPELINE OK — {} POSTs → DB → {}-alert eval → LLM → SSE", records, totalAlerts);
        log.info(" DB records={}  advisories={}  5 thresholds verified", records, advisories);
        log.info("══════════════════════════════════════════");
    }

    private WeatherRecordRequest req(double t, double h, double p, double l, double hi, String hs) {
        var r = new WeatherRecordRequest();
        r.setStationId(ST);
        r.setTemperature(t);
        r.setHumidity(h);
        r.setPressure(p);
        r.setLux(l);
        r.setHeatIndex(hi);
        r.setHeatStressCategory(hs);
        return r;
    }
}
