package com.sura.portal_reclamaciones.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.HolidayCache;

/** Consultas locales de festivos para calcular ANS sin llamar la API por cada caso. */
public interface HolidayCacheRepository extends JpaRepository<HolidayCache, Long> {
    List<HolidayCache> findByCountryCodeAndApiYearOrderByHolidayDateAsc(String countryCode, Integer apiYear);
    Optional<HolidayCache> findByCountryCodeAndHolidayDate(String countryCode, LocalDate holidayDate);
    boolean existsByCountryCodeAndApiYear(String countryCode, Integer apiYear);
}
