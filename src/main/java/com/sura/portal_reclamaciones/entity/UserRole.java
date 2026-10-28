package com.sura.portal_reclamaciones.entity;

/**
 * Roles que reconoce el portal.
 *
 * Se utilizan como valores controlados para evitar que un usuario
 * tenga un rol arbitrario. Los nombres deben coincidir con los
 * valores permitidos por la restricción de PostgreSQL.
 */
public enum UserRole {

    // Puede gestionar la bandeja de revisión y las operaciones permitidas.
    ANALISTA,

    // Puede realizar las operaciones administrativas autorizadas.
    ADMIN
}