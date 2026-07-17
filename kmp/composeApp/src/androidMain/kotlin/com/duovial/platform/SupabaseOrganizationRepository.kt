package com.duovial.platform

import android.util.Log
import com.duovial.organizations.MemberRole
import com.duovial.organizations.Organization
import com.duovial.organizations.OrganizationMember
import com.duovial.organizations.OrganizationRepository
import com.duovial.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Implementación de OrganizationRepository usando Supabase.
 */
class SupabaseOrganizationRepository : OrganizationRepository {

    private val TAG = "DuoVial_OrgRepo"
    private val supabase get() = SupabaseClientProvider.getClient()

    private val _currentOrg = MutableStateFlow<Organization?>(null)
    override val currentOrg: StateFlow<Organization?> = _currentOrg.asStateFlow()

    private val _userOrganizations = MutableStateFlow<List<Organization>>(emptyList())
    override val userOrganizations: StateFlow<List<Organization>> = _userOrganizations.asStateFlow()

    override suspend fun createOrganization(name: String): Result<Organization> = withContext(Dispatchers.IO) {
        try {
            val slug = generateSlug(name)
            val orgData = mapOf("name" to name, "slug" to slug, "plan" to "free")

            val result = supabase.from("organizations")
                .insert(orgData) { select() }
                .decodeSingle<OrganizationResponse>()

            val organization = Organization(
                id = result.id,
                name = result.name,
                slug = result.slug,
                plan = result.plan ?: "free",
                createdAt = result.createdAt ?: ""
            )

            Log.i(TAG, "Organización creada: ${organization.name}")
            Result.success(organization)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando organización: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun inviteMember(email: String, role: MemberRole): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val orgId = _currentOrg.value?.id
                ?: return@withContext Result.failure(Exception("No hay organización seleccionada"))

            val userProfile = supabase.from("profiles")
                .select { filter { eq("email", email) } }
                .decodeSingleOrNull<UserProfileResponse>()

            if (userProfile == null) {
                return@withContext Result.failure(Exception("Usuario no encontrado: $email"))
            }

            val memberData = mapOf(
                "org_id" to orgId,
                "user_id" to userProfile.id,
                "role" to role.name.lowercase()
            )

            supabase.from("organization_members").insert(memberData)
            Log.i(TAG, "Miembro invitado: $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error invitando miembro: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(invitationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("organization_members")
                .update(mapOf("accepted_at" to kotlinx.datetime.Clock.System.now().toString())) {
                    filter { eq("id", invitationId) }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error aceptando invitación: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun switchOrganization(orgId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Cargar datos de la org
            val org = supabase.from("organizations")
                .select { filter { eq("id", orgId) } }
                .decodeSingle<OrganizationResponse>()

            _currentOrg.value = Organization(
                id = org.id,
                name = org.name,
                slug = org.slug,
                plan = org.plan ?: "free",
                createdAt = org.createdAt ?: ""
            )

            Log.i(TAG, "Organización cambiada a: ${org.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cambiando organización: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getMembers(orgId: String): Result<List<OrganizationMember>> = withContext(Dispatchers.IO) {
        try {
            val members = supabase.from("organization_members")
                .select {
                    filter { eq("org_id", orgId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<MemberResponse>()
                .map { member ->
                    OrganizationMember(
                        userId = member.userId,
                        email = member.email ?: "",
                        role = try { MemberRole.valueOf(member.role.uppercase()) } catch (e: Exception) { MemberRole.DRIVER },
                        acceptedAt = member.acceptedAt,
                        joinedAt = member.createdAt
                    )
                }
            Result.success(members)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo miembros: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateMemberRole(userId: String, role: MemberRole): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("organization_members")
                .update(mapOf("role" to role.name.lowercase())) {
                    filter { eq("user_id", userId) }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando rol: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun removeMember(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.from("organization_members")
                .delete { filter { eq("user_id", userId) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando miembro: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun loadUserOrganizations() {
        withContext(Dispatchers.IO) {
            try {
                val orgs = supabase.from("organizations")
                    .select { order("created_at", Order.DESCENDING) }
                    .decodeList<OrganizationResponse>()
                    .map { org ->
                        Organization(
                            id = org.id,
                            name = org.name,
                            slug = org.slug,
                            plan = org.plan ?: "free",
                            createdAt = org.createdAt ?: ""
                        )
                    }

                _userOrganizations.value = orgs

                if (_currentOrg.value == null && orgs.isNotEmpty()) {
                    _currentOrg.value = orgs.first()
                }

                Log.i(TAG, "Organizaciones cargadas: ${orgs.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando organizaciones: ${e.message}")
            }
        }
    }

    private fun generateSlug(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    @Serializable
    private data class OrganizationResponse(
        val id: String,
        val name: String,
        val slug: String,
        val plan: String? = "free",
        @kotlinx.serialization.SerialName("created_at")
        val createdAt: String? = null
    )

    @Serializable
    private data class UserProfileResponse(
        val id: String,
        val email: String? = null
    )

    @Serializable
    private data class MemberResponse(
        @kotlinx.serialization.SerialName("user_id")
        val userId: String,
        val email: String? = null,
        val role: String,
        @kotlinx.serialization.SerialName("accepted_at")
        val acceptedAt: String? = null,
        @kotlinx.serialization.SerialName("created_at")
        val createdAt: String? = null
    )
}
