package com.artemis.kasirku.nativeapp.data

import com.artemis.kasirku.nativeapp.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.builtins.ListSerializer
import java.time.ZoneId

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
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("timezone_id") val timeZoneId: String? = null
)

@Serializable
private data class BranchMembershipDto(
    @SerialName("branch_id") val branchId: String
)

data class AuthorizedBranch(
    val id: String,
    val name: String,
    val timeZoneId: String?
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
            branchName = branch.name,
            branchTimeZoneId = branch.timeZoneId
        )
    }
}

/**
 * Supabase Auth + RLS-backed tenant discovery for Android Native.
 *
 * All store and branch identifiers are read from Supabase after password
 * authentication. Local PIN data is deliberately not used as an authority.
 */

@Serializable
private data class StoreTableCloudDto(
    val id: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("branch_id") val branchId: String,
    val name: String,
    val capacity: Int,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("occupied_order_local_id") val occupiedOrderLocalId: String? = null,
    @SerialName("occupied_at") val occupiedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class StoreTableInsertDto(
    val id: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("branch_id") val branchId: String,
    val name: String,
    val capacity: Int,
    @SerialName("is_active") val isActive: Boolean = true
)

class CloudAuthRepository {
    private val client: io.github.jan.supabase.SupabaseClient
        get() = sharedClient

    companion object {
        /**
         * Keep one authenticated client for the whole app process. The Auth
         * plugin's Android session manager persists/restores the session
         * between process launches; sharing this instance also avoids
         * competing refresh cycles when Compose recreates account screens.
         */
        private val sharedClient: io.github.jan.supabase.SupabaseClient by lazy {
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
        // Wait until persisted Android credentials have finished loading.
        // Reading currentUser too early can incorrectly show the login screen.
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        when (status) {
            is SessionStatus.Authenticated -> {
                val user = status.session.user
                    ?: throw SecurityException("Sesi Cloud tidak memiliki identitas pengguna.")
                loadAuthorizedAccess(user.id, user.email.orEmpty())
            }
            is SessionStatus.NotAuthenticated -> null
            is SessionStatus.RefreshFailure ->
                throw IllegalStateException("Sesi Cloud perlu diperbarui. Silakan masuk kembali.")
            SessionStatus.Initializing -> error("Status sesi belum siap.")
        }
    }

    /**
     * Upload one sale through the atomic, idempotent database RPC.
     * The server still enforces the caller's existing tenant RLS policies.
     */
    suspend fun uploadOrder(payload: OrderSyncPayload, allowDelegatedCashier: Boolean = false) = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
            ?: throw SecurityException("Sesi Cloud tidak ditemukan. Silakan masuk kembali.")
        if (payload.order.createdBy != user.id && !allowDelegatedCashier) {
            throw SecurityException("Transaksi kasir lain hanya dapat disinkronkan oleh owner/admin.")
        }
        require(payload.order.storeId.isNotBlank() && payload.order.branchId.isNotBlank()) {
            "Identitas toko atau cabang transaksi tidak lengkap."
        }
        client.postgrest.rpc(
            function = "sync_order",
            parameters = buildJsonObject {
                put("p_order", Json.encodeToJsonElement(OrderSyncDto.serializer(), payload.order))
                put("p_items", Json.encodeToJsonElement(ListSerializer(OrderItemSyncDto.serializer()), payload.items))
            }
        )
    }


    suspend fun fetchStoreTables(tenant: VerifiedTenantContext): List<StoreTableEntity> =
        withContext(Dispatchers.IO) {
            require(tenant.storeId.isNotBlank() && tenant.branchId.isNotBlank())
            client.from("store_tables").select {
                filter {
                    eq("store_id", tenant.storeId)
                    eq("branch_id", tenant.branchId)
                }
            }.decodeList<StoreTableCloudDto>().map { row ->
                StoreTableEntity(
                    id = row.id,
                    storeId = row.storeId,
                    branchId = row.branchId,
                    name = row.name,
                    capacity = row.capacity,
                    isActive = row.isActive,
                    createdAt = java.time.Instant.parse(row.createdAt).toEpochMilli(),
                    updatedAt = java.time.Instant.parse(row.updatedAt).toEpochMilli(),
                    occupiedSaleId = row.occupiedOrderLocalId,
                    occupiedAt = row.occupiedAt?.let { java.time.Instant.parse(it).toEpochMilli() }
                )
            }
        }

    suspend fun createStoreTable(
        tenant: VerifiedTenantContext,
        id: String,
        name: String,
        capacity: Int
    ) = withContext(Dispatchers.IO) {
        require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
            "Hanya Owner, Admin, atau Supervisor yang dapat mengelola meja."
        }
        require(id.isNotBlank() && name.isNotBlank() && capacity in 1..999)
        client.from("store_tables").insert(
            StoreTableInsertDto(id, tenant.storeId, tenant.branchId, name.trim(), capacity)
        )
    }

    suspend fun updateStoreTable(
        tenant: VerifiedTenantContext,
        tableId: String,
        name: String,
        capacity: Int
    ): Boolean = withContext(Dispatchers.IO) {
        require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
            "Hanya Owner, Admin, atau Supervisor yang dapat mengelola meja."
        }
        require(tableId.isNotBlank() && name.isNotBlank() && capacity in 1..999)
        client.postgrest.rpc(
            function = "update_store_table",
            parameters = buildJsonObject {
                put("target_store_id", tenant.storeId)
                put("target_branch_id", tenant.branchId)
                put("target_table_id", tableId)
                put("target_name", name.trim())
                put("target_capacity", capacity)
            }
        ).decodeAs<Boolean>()
    }

    suspend fun archiveStoreTable(
        tenant: VerifiedTenantContext,
        tableId: String
    ): Boolean = withContext(Dispatchers.IO) {
        require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
            "Hanya Owner, Admin, atau Supervisor yang dapat mengelola meja."
        }
        client.postgrest.rpc(
            function = "archive_store_table",
            parameters = buildJsonObject {
                put("target_store_id", tenant.storeId)
                put("target_branch_id", tenant.branchId)
                put("target_table_id", tableId)
            }
        ).decodeAs<Boolean>()
    }

    suspend fun claimStoreTable(
        tenant: VerifiedTenantContext,
        tableId: String,
        orderLocalId: String
    ): Boolean = withContext(Dispatchers.IO) {
        require(tenant.storeId.isNotBlank() && tenant.branchId.isNotBlank())
        client.postgrest.rpc(
            function = "claim_store_table",
            parameters = buildJsonObject {
                put("target_store_id", tenant.storeId)
                put("target_branch_id", tenant.branchId)
                put("target_table_id", tableId)
                put("target_order_local_id", orderLocalId)
            }
        ).decodeAs<Boolean>()
    }

    suspend fun releaseStoreTable(
        tenant: VerifiedTenantContext,
        tableId: String,
        orderLocalId: String
    ): Boolean = withContext(Dispatchers.IO) {
        require(tenant.storeId.isNotBlank() && tenant.branchId.isNotBlank())
        client.postgrest.rpc(
            function = "release_store_table",
            parameters = buildJsonObject {
                put("target_store_id", tenant.storeId)
                put("target_branch_id", tenant.branchId)
                put("target_table_id", tableId)
                put("target_order_local_id", orderLocalId)
            }
        ).decodeAs<Boolean>()
    }

    suspend fun setBranchTimeZone(
        storeId: String,
        branchId: String,
        timeZoneId: String
    ): CloudAccountAccess = withContext(Dispatchers.IO) {
        require(timeZoneId.isNotBlank()) { "Zona waktu wajib diisi." }
        require(runCatching { ZoneId.of(timeZoneId.trim()) }.isSuccess) {
            "Gunakan zona waktu IANA yang valid, misalnya Asia/Makassar."
        }
        client.postgrest.rpc(
            function = "set_branch_timezone",
            parameters = buildJsonObject {
                put("p_store_id", storeId)
                put("p_branch_id", branchId)
                put("p_timezone_id", timeZoneId.trim())
            }
        )
        loadAuthorizedAccess()
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

            val allowedBranches = if (role == StoreRole.OWNER || role == StoreRole.ADMIN) {
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
                branches = allowedBranches.map { AuthorizedBranch(it.id, it.name, it.timeZoneId) }
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
