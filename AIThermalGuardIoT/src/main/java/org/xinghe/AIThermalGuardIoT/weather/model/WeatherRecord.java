package org.xinghe.AIThermalGuardIoT.weather.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "weather_records", indexes = {
    @Index(name = "idx_records_created_at", columnList = "createdAt DESC"),
    @Index(name = "idx_records_station_id", columnList = "station_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class WeatherRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false, length = 50)
    private String stationId;

    @Column
    private Double temperature;

    @Column
    private Double humidity;

    @Column
    private Double pressure;

    @Column
    private Double lux;

    @Column(name = "heat_index")
    private Double heatIndex;

    @Column(name = "heat_stress_category", length = 50)
    private String heatStressCategory;

    @Column(length = 500)
    private String alerts;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
