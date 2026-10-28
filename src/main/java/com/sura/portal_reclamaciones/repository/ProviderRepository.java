package com.sura.portal_reclamaciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sura.portal_reclamaciones.entity.Provider;

/** Acceso al catálogo de prestadores de salud. */
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    // El NIT normalizado sirve como clave de búsqueda para las reglas R08 y R09.
    Optional<Provider> findByNit(String nit);

    boolean existsByNit(String nit);
}
