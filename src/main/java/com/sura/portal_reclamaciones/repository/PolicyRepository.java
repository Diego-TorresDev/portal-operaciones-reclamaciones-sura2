package com.sura.portal_reclamaciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sura.portal_reclamaciones.entity.Policy;

/** Acceso a pólizas para el importador y las reglas de negocio. */
public interface PolicyRepository extends JpaRepository<Policy, Long> {

    // Busca la póliza normalizada, evitando depender de mayúsculas/minúsculas.
    Optional<Policy> findByPolicyNumberIgnoreCase(String policyNumber);

    // Se utiliza para validar duplicados de póliza antes de insertar datos.
    boolean existsByPolicyNumberIgnoreCase(String policyNumber);
}
