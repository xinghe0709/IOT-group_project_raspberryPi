package org.xinghe.AIThermalGuardIoT.weather.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xinghe.AIThermalGuardIoT.weather.dto.WeatherRecordResponse;
import org.xinghe.AIThermalGuardIoT.weather.service.WeatherRecordService;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

    @Mock
    private WeatherRecordService service;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        var controller = new WeatherController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new org.xinghe.AIThermalGuardIoT.common.exception.GlobalExceptionHandler())
            .build();
    }

    @Test
    void shouldAcceptValidRecord() throws Exception {
        var response = WeatherRecordResponse.builder()
            .id(1L)
            .stationId("pi-weather-01")
            .temperature(25.3)
            .humidity(62.0)
            .pressure(1013.0)
            .lux(4500.0)
            .alerts("[\"HIGH_TEMP:41.2\"]")
            .createdAt(Instant.now())
            .build();
        when(service.saveRecord(any())).thenReturn(response);

        var body = objectMapper.createObjectNode();
        body.put("station_id", "pi-weather-01");
        body.put("temperature", 25.3);
        body.put("humidity", 62.0);
        body.put("pressure", 1013.0);
        body.put("lux", 4500.0);
        body.putArray("alerts").add("HIGH_TEMP:41.2");

        mockMvc.perform(post("/api/weather/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectEmptyStationId() throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("station_id", "");
        body.put("temperature", 25.3);

        mockMvc.perform(post("/api/weather/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }
}
