package org.xinghe.AIThermalGuardIoT.weather.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherRecord;
import java.util.List;

@Repository
public interface WeatherRecordRepository extends JpaRepository<WeatherRecord, Long> {
    WeatherRecord findTopByOrderByCreatedAtDesc();
    List<WeatherRecord> findTop20ByOrderByCreatedAtDesc();
}
