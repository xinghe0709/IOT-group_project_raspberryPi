package org.xinghe.AIThermalGuardIoT.weather.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordRequest;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherRecordRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationTest {

    @Mock
    private WeatherRecordRepository repository;

    @Mock
    private SseBroadcastService broadcastService;

    @InjectMocks
    private WeatherRecordService service;

    private WeatherRecordRequest requestWith(double temp, double humidity,
                                              double pressure, double lux,
                                              Double heatIndex) {
        WeatherRecordRequest req = new WeatherRecordRequest();
        req.setStationId("test-01");
        req.setTemperature(temp);
        req.setHumidity(humidity);
        req.setPressure(pressure);
        req.setLux(lux);
        req.setHeatIndex(heatIndex);
        req.setHeatStressCategory("Test");
        return req;
    }

    @Test
    void shouldTriggerHighHeatIndexAlert() {
        WeatherRecord record = savedRecord(requestWith(30, 50, 1015, 10000, 42.0));
        assertTrue(record.getAlerts().contains("HIGH_HEAT_INDEX:42.0"));
        assertEquals(1, countAlerts(record));
    }

    @Test
    void shouldTriggerLowTempAlert() {
        WeatherRecord record = savedRecord(requestWith(3.0, 50, 1015, 10000, 35.0));
        assertTrue(record.getAlerts().contains("LOW_TEMP:3.0"));
    }

    @Test
    void shouldTriggerHighHumidityAlert() {
        WeatherRecord record = savedRecord(requestWith(25, 70.0, 1015, 10000, 35.0));
        assertTrue(record.getAlerts().contains("HIGH_HUMIDITY:70.0"));
    }

    @Test
    void shouldTriggerLowPressureAlert() {
        WeatherRecord record = savedRecord(requestWith(25, 50, 995.0, 10000, 35.0));
        assertTrue(record.getAlerts().contains("LOW_PRESSURE:995.0"));
    }

    @Test
    void shouldTriggerHighLuxAlert() {
        WeatherRecord record = savedRecord(requestWith(25, 50, 1015, 55000.0, 35.0));
        assertTrue(record.getAlerts().contains("HIGH_LUX:55000.0"));
    }

    @Test
    void shouldTriggerMultipleAlertsSimultaneously() {
        WeatherRecord record = savedRecord(requestWith(3.0, 70.0, 995.0, 55000.0, 42.0));
        assertEquals(5, countAlerts(record));
        assertTrue(record.getAlerts().contains("HIGH_HEAT_INDEX:42.0"));
        assertTrue(record.getAlerts().contains("LOW_TEMP:3.0"));
        assertTrue(record.getAlerts().contains("HIGH_HUMIDITY:70.0"));
        assertTrue(record.getAlerts().contains("LOW_PRESSURE:995.0"));
        assertTrue(record.getAlerts().contains("HIGH_LUX:55000.0"));
    }

    @Test
    void shouldNotTriggerAlertsInNormalConditions() {
        WeatherRecord record = savedRecord(requestWith(25, 50, 1015, 10000, 35.0));
        assertEquals("[]", record.getAlerts());
    }

    @Test
    void shouldTriggerHeatIndexAtExactBoundary() {
        WeatherRecord record = savedRecord(requestWith(25, 50, 1015, 10000, 41.0));
        assertTrue(record.getAlerts().contains("HIGH_HEAT_INDEX:41.0"));
    }

    @Test
    void shouldTriggerHumidityAtExactBoundary() {
        WeatherRecord record = savedRecord(requestWith(25, 65.0, 1015, 10000, 35.0));
        assertTrue(record.getAlerts().contains("HIGH_HUMIDITY:65.0"));
    }

    @Test
    void shouldTriggerTempAtExactBoundary() {
        WeatherRecord record = savedRecord(requestWith(5.0, 50, 1015, 10000, 35.0));
        assertTrue(record.getAlerts().contains("LOW_TEMP:5.0"));
    }

    @Test
    void shouldTriggerPressureAtExactBoundary() {
        WeatherRecord record = savedRecord(requestWith(25, 50, 1000.0, 10000, 35.0));
        assertTrue(record.getAlerts().contains("LOW_PRESSURE:1000.0"));
    }

    @Test
    void shouldTriggerLuxAtExactBoundary() {
        WeatherRecord record = savedRecord(requestWith(25, 50, 1015, 50000.0, 35.0));
        assertTrue(record.getAlerts().contains("HIGH_LUX:50000.0"));
    }

    @Test
    void shouldNotTriggerJustAboveTempBoundary() {
        WeatherRecord record = savedRecord(requestWith(5.1, 50, 1015, 10000, 35.0));
        assertFalse(record.getAlerts().contains("LOW_TEMP"));
    }

    @Test
    void shouldNotTriggerJustBelowHumidityBoundary() {
        WeatherRecord record = savedRecord(requestWith(25, 64.9, 1015, 10000, 35.0));
        assertFalse(record.getAlerts().contains("HIGH_HUMIDITY"));
    }

    @Test
    void shouldPersistHeatIndexAndHeatStressCategory() {
        WeatherRecord record = savedRecord(requestWith(25, 50, 1015, 10000, 46.2));
        assertEquals(46.2, record.getHeatIndex());
        assertEquals("Test", record.getHeatStressCategory());
    }

    @Test
    void shouldMapHeatIndexToResponse() {
        WeatherRecordRequest req = requestWith(30, 55, 1013, 8000, 42.0);
        WeatherRecord saved = new WeatherRecord();
        saved.setId(1L);
        saved.setStationId("test-01");
        saved.setTemperature(30.0);
        saved.setHumidity(55.0);
        saved.setPressure(1013.0);
        saved.setLux(8000.0);
        saved.setHeatIndex(42.0);
        saved.setAlerts("[\"HIGH_HEAT_INDEX:42.0\"]");

        when(repository.save(any())).thenReturn(saved);
        WeatherRecordResponse response = service.saveRecord(req);

        assertEquals(42.0, response.getHeatIndex());
    }

    @Test
    void shouldIgnorePiAlertsCompletely() {
        WeatherRecordRequest req = requestWith(25, 50, 1015, 10000, 35.0);
        req.setAlerts(java.util.List.of("HIGH_TEMP:50.0", "LOW_HUM:10.0"));

        WeatherRecord record = savedRecord(req);
        // Pi alerts are ignored; normal conditions produce no server-side alerts
        assertEquals("[]", record.getAlerts());
    }

    // === helpers ===

    private WeatherRecord savedRecord(WeatherRecordRequest req) {
        WeatherRecord saved = new WeatherRecord();
        saved.setId(1L);
        saved.setStationId(req.getStationId());
        saved.setTemperature(req.getTemperature());
        saved.setHumidity(req.getHumidity());
        saved.setPressure(req.getPressure());
        saved.setLux(req.getLux());
        saved.setHeatIndex(req.getHeatIndex());
        saved.setHeatStressCategory(req.getHeatStressCategory());

        when(repository.save(any())).thenReturn(saved);
        service.saveRecord(req);

        ArgumentCaptor<WeatherRecord> captor = ArgumentCaptor.forClass(WeatherRecord.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private int countAlerts(WeatherRecord record) {
        String alerts = record.getAlerts();
        if (alerts == null || alerts.equals("[]")) return 0;
        return alerts.split("\",\"").length;
    }
}
