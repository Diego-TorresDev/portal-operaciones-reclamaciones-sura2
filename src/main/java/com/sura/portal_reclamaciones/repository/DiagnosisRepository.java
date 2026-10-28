package com.sura.portal_reclamaciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sura.portal_reclamaciones.entity.Diagnosis;

/** Acceso al catálogo de diagnósticos utilizado en R12 y R13. */
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

    // Busca el código de diagnóstico normalizado sin importar mayúsculas/minúsculas.
    Optional<Diagnosis> findByDiagnosisCodeIgnoreCase(String diagnosisCode);

    boolean existsByDiagnosisCodeIgnoreCase(String diagnosisCode);
}
