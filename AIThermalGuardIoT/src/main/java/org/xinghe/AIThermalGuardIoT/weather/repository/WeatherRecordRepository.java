package org.xinghe.AIThermalGuardIoT.weather.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;

import java.time.Instant;
import java.util.List;

@Repository
public interface WeatherRecordRepository extends JpaRepository<WeatherRecord, Long> {
    WeatherRecord findTopByOrderByCreatedAtDesc();
    List<WeatherRecord> findTop20ByOrderByCreatedAtDesc();

    @Query(value = """
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
        """, nativeQuery = true)
    List<Object[]> findAggregatedByHour(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT
            date_trunc('day', created_at) AS bucket,
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
        """, nativeQuery = true)
    List<Object[]> findAggregatedByDay(@Param("from") Instant from, @Param("to") Instant to);

    List<WeatherRecord> findTop300ByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
}
