package com.artemis.kasirku.nativeapp.data

import com.artemis.kasirku.nativeapp.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class StoreMembershipDto(
    @SerialName("store_id") val storeId: String,
    val role: String,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
private data class StoreDto(
    val id: String,
    val name: String
)

@Serializable
private data class BranchDto(
    val id: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
private data class BranchMembershipDto(
    @SerialName("branch_id") val branchId: String
)

data class AuthorizedBranch(
    val id: String,
    val name: String
)

data class AuthorizedStore(
    val id: String,
    val name: String,
    val role: StoreRole,
    val branches: List<AuthorizedBranch>
)

data class CloudAccountAccess(
    val userId: String,
    val email: String,
    val stores: List<AuthorizedStore>
) {
    fun verifiedContext(storeId: String, branchId: String): VerifiedTenantContext {
        val store = stores.singleOrNull { it.id == storeId }
            ?: throw IllegalArgumentException("Toko tidak termasuk akun aktif.")
        val branch = store.branches.singleOrNull { it.id == branchId }
            ?: throw IllegalArgumentException("Cabang tidak diizinkan untuk akun ini.")
        return VerifiedTenantContext(
            userId = userId,
            storeId = store.id,
            storeName = store.name,
            role = store.role,
            branchId = branch.id,
            branchName = branch.name
        )
    }
}

/**
 * Supabase Auth + RLS-backed tenant discovery for Android Native.
 *
 * All store and branch identifiers are read from Supabase after password
 * authentication. Local PIN data is deliberately not used as an authority.
 */
class CloudAuthRepository {
    private val client by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Konfigurasi Cloud belum tersedia."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    suspend fun signIn(email: String, password: String): CloudAccountAccess =
        withContext(Dispatchers.IO) {
            require(email.isNotBlank()) { "Email wajib diisi." }
            require(password.isNotBlank()) { "Kata sandi wajib diisi." }
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            loadAuthorizedAccess()
        }

    suspend fun restoreSession(): CloudAccountAccess? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull() ?: return@withContext null
        loadAuthorizedAccess(user.id, user.email.orEmpty())
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        client.auth.signOut()
    }

    private suspend fun loadAuthorizedAccess(
        expectedUserId: String? = null,
        expectedEmail: String? = null
    ): CloudAccountAccess {
        val user = client.auth.currentUserOrNull()
            ?: throw IllegalStateException("Sesi Cloud tidak ditemukan. Silakan masuk kembali.")
        if (expectedUserId != null && user.id != expectedUserId) {
            throw SecurityException("Identitas Cloud berubah. Silakan masuk kembali.")
        }

        val memberships = client.from("store_memberships").select {
            filter {
                eq("user_id", user.id)
                eq("is_active", true)
            }
        }.decodeList<StoreMembershipDto>()

        if (memberships.isEmpty()) {
            throw SecurityException("Akun belum memiliki keanggotaan toko aktif.")
        }

        val stores = memberships.map { membership ->
            val role = StoreRole.fromCloud(membership.role)
            val storeRows = client.from("stores").select {
                filter { eq("id", membership.storeId) }
            }.decodeList<StoreDto>()
            val store = storeRows.singleOrNull()
                ?: throw SecurityException("Data toko tidak ditemukan atau akses ditolak.")

            val activeBranches = client.from("branches").select {
                filter {
                    eq("store_id", store.id)
                    eq("is_active", true)
                }
            }.decodeList<BranchDto>().filter { it.isActive }

            val allowedBranches = if (role == StoreRole.OWNER) {
                activeBranches
            } else {
                val assignments = client.from("branch_memberships").select {
                    filter {
                        eq("store_id", store.id)
                        eq("user_id", user.id)
                    }
                }.decodeList<BranchMembershipDto>()
                val allowedIds = assignments.map { it.branchId }.toSet()
                activeBranches.filter { it.id in allowedIds }
            }

            AuthorizedStore(
                id = store.id,
                name = store.name,
                role = role,
                branches = allowedBranches.map { AuthorizedBranch(it.id, it.name) }
            )
        }

        if (stores.none { it.branches.isNotEmpty() }) {
            throw SecurityException("Akun belum memiliki cabang aktif yang diizinkan.")
        }

        return CloudAccountAccess(
            userId = user.id,
            email = expectedEmail ?: user.email.orEmpty(),
            stores = stores
        )
    }
}
