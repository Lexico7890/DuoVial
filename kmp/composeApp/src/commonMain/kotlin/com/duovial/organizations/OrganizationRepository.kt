package com.duovial.organizations

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface que define el contrato para la gestión de organizaciones (multi-tenancy).
 * Implementada en androidMain por SupabaseOrganizationRepository.
 *
 * Modelo:
 * - Cada empresa es una "organización" con sus propios vehículos, conductores, incidentes.
 * - RLS (Row Level Security) garantiza aislamiento de datos.
 * - Un usuario puede pertenecer a múltiples organizaciones.
 */
interface OrganizationRepository {

    /**
     * Organización actual seleccionada.
     */
    val currentOrg: StateFlow<Organization?>

    /**
     * Lista de organizaciones a las que pertenece el usuario.
     */
    val userOrganizations: StateFlow<List<Organization>>

    /**
     * Crea una nueva organización.
     *
     * @param name Nombre de la organización
     * @return Result con la organización creada o error
     */
    suspend fun createOrganization(name: String): Result<Organization>

    /**
     * Invita a un miembro a la organización.
     *
     * @param email Email del miembro a invitar
     * @param role Rol del miembro (owner, admin, supervisor, driver)
     * @return Result Unit o error
     */
    suspend fun inviteMember(email: String, role: MemberRole): Result<Unit>

    /**
     * Acepta una invitación pendiente.
     *
     * @param invitationId ID de la invitación
     * @return Result Unit o error
     */
    suspend fun acceptInvitation(invitationId: String): Result<Unit>

    /**
     * Cambia la organización activa.
     * Esto establece el contexto para RLS.
     *
     * @param orgId ID de la nueva organización
     * @return Result Unit o error
     */
    suspend fun switchOrganization(orgId: String): Result<Unit>

    /**
     * Obtiene los miembros de una organización.
     *
     * @param orgId ID de la organización
     * @return Result con la lista de miembros o error
     */
    suspend fun getMembers(orgId: String): Result<List<OrganizationMember>>

    /**
     * Actualiza el rol de un miembro.
     *
     * @param userId ID del usuario
     * @param role Nuevo rol
     * @return Result Unit o error
     */
    suspend fun updateMemberRole(userId: String, role: MemberRole): Result<Unit>

    /**
     * Elimina un miembro de la organización.
     *
     * @param userId ID del usuario a eliminar
     * @return Result Unit o error
     */
    suspend fun removeMember(userId: String): Result<Unit>

    /**
     * Carga las organizaciones del usuario actual.
     */
    suspend fun loadUserOrganizations()
}

/**
 * Modelo de organización.
 */
data class Organization(
    val id: String,
    val name: String,
    val slug: String,
    val plan: String = "free",
    val createdAt: String = ""
)

/**
 * Roles de miembros en una organización.
 */
enum class MemberRole {
    OWNER,
    ADMIN,
    SUPERVISOR,
    DRIVER
}

/**
 * Modelo de miembro de organización.
 */
data class OrganizationMember(
    val userId: String,
    val email: String,
    val role: MemberRole,
    val acceptedAt: String? = null,
    val joinedAt: String? = null
)
