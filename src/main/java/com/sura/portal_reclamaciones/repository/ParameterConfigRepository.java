package com.sura.portal_reclamaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.ParameterConfig;

/** Consulta los parámetros vigentes que utiliza el motor de negocio. */
public interface ParameterConfigRepository extends JpaRepository<ParameterConfig, String> {
    // Devuelve los parámetros ordenados por clave para mostrarlos en la pantalla de ADMIN.
    List<ParameterConfig> findAllByOrderByParameterKeyAsc();
}
