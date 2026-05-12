package org.xinghe.AIThermalGuardIoT.weather.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.xinghe.AIThermalGuardIoT.common.ai.LlmProviderRegistry;
import org.xinghe.AIThermalGuardIoT.common.ai.StructuredOutputInvoker;
import org.xinghe.AIThermalGuardIoT.common.exception.BusinessException;
import org.xinghe.AIThermalGuardIoT.common.exception.ErrorCode;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryDto;
import org.xinghe.AIThermalGuardIoT.weather.dto.AdvisoryOutput;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherAdvisory;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherAdvisoryRepository;
import org.xinghe.AIThermalGuardIoT.weather.repository.WeatherRecordRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvisorySchedulerTest {

    @Mock private WeatherRecordRepository recordRepository;
    @Mock private WeatherAdvisoryRepository advisoryRepository;
    @Mock private LlmProviderRegistry llmProviderRegistry;
    @Mock private StructuredOutputInvoker structuredOutputInvoker;
    @Mock private SseBroadcastService broadcastService;
    @Mock private AdvisoryMappingService mappingService;

    @InjectMocks
    private AdvisoryScheduler scheduler;

    private WeatherRecord sampleRecord;

    @BeforeEach
    void setUp() {
        sampleRecord = WeatherRecord.builder()
            .id(1L)
            .stationId("pi-weather-01")
            .temperature(35.2)
            .humidity(78.0)
            .pressure(1002.0)
            .lux(12000.0)
            .alerts("[\"HIGH_TEMP:41.2\"]")
            .createdAt(Instant.now())
            .build();
    }

    @Test
    void shouldGenerateAdvisoryWhenRecordExists() {
        when(recordRepository.findTopByOrderByCreatedAtDesc()).thenReturn(sampleRecord);

        ChatClient mockClient = mock(ChatClient.class);
        when(llmProviderRegistry.getChatClient("deepseek")).thenReturn(mockClient);

        AdvisoryOutput output = new AdvisoryOutput("MODERATE", "Warm and muggy", "Stay hydrated and crack a window");
        when(structuredOutputInvoker.invoke(
            eq(mockClient), anyString(), anyString(),
            any(BeanOutputConverter.class), eq(ErrorCode.WEATHER_ADVISORY_GENERATION_FAILED),
            anyString(), eq("weather-advisory"), any(Logger.class)
        )).thenReturn(output);

        WeatherAdvisory saved = WeatherAdvisory.builder()
            .id(10L)
            .record(sampleRecord)
            .riskLevel("MODERATE")
            .summary("Warm and muggy")
            .recommendation("Stay hydrated and crack a window")
            .createdAt(Instant.now())
            .build();
        when(advisoryRepository.save(any(WeatherAdvisory.class))).thenReturn(saved);

        AdvisoryDto dto = AdvisoryDto.builder().id(10L).riskLevel("MODERATE")
            .summary("Warm and muggy").recommendation("Stay hydrated").createdAt(Instant.now()).build();
        when(mappingService.toDto(any(WeatherAdvisory.class))).thenReturn(dto);

        scheduler.generateAdvisory();

        verify(advisoryRepository).save(any(WeatherAdvisory.class));
        verify(broadcastService).broadcastAdvisory(any(AdvisoryDto.class));
    }

    @Test
    void shouldSkipWhenNoRecordsExist() {
        when(recordRepository.findTopByOrderByCreatedAtDesc()).thenReturn(null);

        scheduler.generateAdvisory();

        verify(llmProviderRegistry, never()).getChatClient(anyString());
        verify(advisoryRepository, never()).save(any());
        verify(broadcastService, never()).broadcastAdvisory(any());
    }

    @Test
    void shouldSurviveLlmFailure() {
        when(recordRepository.findTopByOrderByCreatedAtDesc()).thenReturn(sampleRecord);

        ChatClient mockClient = mock(ChatClient.class);
        when(llmProviderRegistry.getChatClient("deepseek")).thenReturn(mockClient);

        when(structuredOutputInvoker.invoke(
            eq(mockClient), anyString(), anyString(),
            any(BeanOutputConverter.class), eq(ErrorCode.WEATHER_ADVISORY_GENERATION_FAILED),
            anyString(), eq("weather-advisory"), any(Logger.class)
        )).thenThrow(new BusinessException(ErrorCode.WEATHER_ADVISORY_GENERATION_FAILED));

        // should not throw
        assertDoesNotThrow(() -> scheduler.generateAdvisory());

        verify(advisoryRepository, never()).save(any());
        verify(broadcastService, never()).broadcastAdvisory(any());
    }

    @Test
    void shouldBuildPromptWithRealData() {
        when(recordRepository.findTopByOrderByCreatedAtDesc()).thenReturn(sampleRecord);
        when(llmProviderRegistry.getChatClient("deepseek")).thenReturn(mock(ChatClient.class));

        AdvisoryOutput output = new AdvisoryOutput("HIGH", "Dangerously hot", "Stay indoors and drink water");
        when(structuredOutputInvoker.invoke(
            any(ChatClient.class), anyString(), anyString(),
            any(BeanOutputConverter.class), any(ErrorCode.class), anyString(), anyString(), any(Logger.class)
        )).thenReturn(output);

        when(advisoryRepository.save(any())).thenReturn(mock(WeatherAdvisory.class));
        when(mappingService.toDto(any())).thenReturn(mock(AdvisoryDto.class));

        scheduler.generateAdvisory();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(structuredOutputInvoker).invoke(
            any(ChatClient.class), promptCaptor.capture(), promptCaptor.capture(),
            any(BeanOutputConverter.class), any(), anyString(), anyString(), any()
        );

        String prompt = promptCaptor.getAllValues().get(0);
        assertTrue(prompt.contains("35.2"), "Prompt should contain temperature");
        assertTrue(prompt.contains("78"), "Prompt should contain humidity");
        assertTrue(prompt.contains("1002"), "Prompt should contain pressure");
        assertTrue(prompt.contains("12000"), "Prompt should contain lux");
        assertTrue(prompt.contains("HIGH_TEMP:41.2"), "Prompt should contain alerts");
        assertTrue(prompt.contains("warm, thoughtful weather companion"), "Prompt should contain persona");
    }
}
