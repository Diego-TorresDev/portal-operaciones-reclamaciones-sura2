package com.sura.portal_reclamaciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sura.portal_reclamaciones.entity.ServiceCatalog;

/** Acceso al catálogo de servicios y sus soportes requeridos. */
public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, Long> {

    // Permite resolver el tipo de servicio recibido desde el Excel.
    Optional<ServiceCatalog> findByServiceCodeIgnoreCase(String serviceCode);

    boolean existsByServiceCodeIgnoreCase(String serviceCode);
}
