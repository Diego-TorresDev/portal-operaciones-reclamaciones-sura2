package com.sura.portal_reclamaciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sura.portal_reclamaciones.entity.Usuario;

/**
 * Acceso a la tabla app_user.
 *
 * Spring Data JPA genera la implementación de esta interfaz al arrancar
 * la aplicación. No necesitamos escribir una clase con SQL para estas
 * consultas sencillas.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /*
     * Busca un usuario por su nombre de inicio de sesión.
     *
     * Optional permite representar que el usuario existe o que no se
     * encontró, sin devolver null directamente.
     */
    Optional<Usuario> findByUsername(String username);

    /*
     * Comprueba si un nombre de usuario ya está registrado.
     * Será útil cuando creemos los usuarios de prueba y validemos altas.
     */
    boolean existsByUsername(String username);
}