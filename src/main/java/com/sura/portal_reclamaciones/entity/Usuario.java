package com.sura.portal_reclamaciones.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Representa un usuario registrado en la tabla app_user.
 *
 * Esta entidad permite que el backend consulte los usuarios y almacene
 * sus datos de acceso, rol, estado y fechas de control.
 *
 * Las contraseñas no se guardan en texto claro: passwordHash debe contener
 * el resultado de BCrypt generado por el servicio de autenticación.
 */
@Entity
@Table(name = "app_user")
public class Usuario {

    /*
     * Identificador interno generado por PostgreSQL.
     * No se pide al usuario ni se construye manualmente.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Nombre utilizado para iniciar sesión.
     * La base de datos exige que sea único y esté en minúsculas.
     */
    @Column(name = "username", nullable = false, unique = true, length = 80)
    private String username;

    /*
     * Hash BCrypt de la contraseña.
     * No debemos asignar aquí la contraseña original que escribe el usuario.
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /*
     * Nombre que se mostrará en la interfaz del portal.
     */
    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    /*
     * Rol funcional del usuario.
     *
     * EnumType.STRING guarda valores como ANALISTA o ADMIN, en lugar
     * de guardar un número que podría cambiar si se modifica el enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /*
     * Permite desactivar una cuenta sin eliminar su historial.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /*
     * Número de intentos de acceso fallidos.
     * Después podremos utilizarlo para aplicar la política de bloqueo.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /*
     * Momento hasta el cual la cuenta permanece bloqueada.
     * Si es null, no existe un bloqueo temporal registrado.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /*
     * Fecha del último inicio de sesión correcto.
     * La actualizará el servicio de autenticación.
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /*
     * Fecha del último cambio de contraseña.
     * El servicio que cambie la contraseña deberá actualizarla.
     */
    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    /*
     * Fechas de auditoría técnica del registro.
     * updatedAt se refresca cuando se modifica la entidad.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /*
     * JPA necesita un constructor sin argumentos para reconstruir
     * objetos a partir de los registros que devuelve PostgreSQL.
     */
    public Usuario() {
    }

    /*
     * Se ejecuta antes de insertar un usuario.
     *
     * Inicializamos las fechas y los valores predeterminados en Java
     * para que la entidad tenga esos datos antes de guardarse.
     */
    @PrePersist
    protected void antesDeGuardar() {
        Instant ahora = Instant.now();

        if (passwordChangedAt == null) {
            passwordChangedAt = ahora;
        }

        if (createdAt == null) {
            createdAt = ahora;
        }

        if (updatedAt == null) {
            updatedAt = ahora;
        }
    }

    /*
     * Se ejecuta antes de actualizar un usuario existente.
     * Así mantenemos actualizada la fecha del último cambio del registro.
     */
    @PreUpdate
    protected void antesDeActualizar() {
        updatedAt = Instant.now();
    }

    // Métodos de acceso utilizados por los servicios y por JPA.

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}