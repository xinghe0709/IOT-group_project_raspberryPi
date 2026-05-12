package org.xinghe.AIThermalGuardIoT.weather.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.xinghe.AIThermalGuardIoT.weather.model.WeatherAdvisory;

@Repository
public interface WeatherAdvisoryRepository extends JpaRepository<WeatherAdvisory, Long> {
    Page<WeatherAdvisory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
