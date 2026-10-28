package com.sura.portal_reclamaciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sura.portal_reclamaciones.entity.InsurancePlan;

/** Acceso a planes; Spring Data genera las consultas a partir de los métodos. */
public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, Long> {

    // Permite buscar un plan por código sin diferenciar mayúsculas/minúsculas.
    Optional<InsurancePlan> findByPlanCodeIgnoreCase(String planCode);
}
