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
private data class NativeCatalogCategoryDto(
    val id: String,
    @SerialName("store_id") val storeId: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class NativeCatalogProductDto(
    val id: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("category_id") val categoryId: String? = null,
    val name: String,
    val description: String,
    @SerialName("base_price") val basePrice: Long,
    @SerialName("photo_uri") val photoUri: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class NativeBranchPriceDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("selling_price") val sellingPrice: Long,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class NativeBranchInventoryDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class NativeProductBarcodeDto(
    val id: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("product_id") val productId: String,
    val barcode: String,
    @SerialName("created_at") val createdAt: String
)

data class CatalogCloudSyncSummary(
    val categories: Int,
    val products: Int,
    val branchPrices: Int,
    val inventoryRows: Int,
    val barcodes: Int
)

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


    /**
     * Uploads the current local store catalog and the selected branch's prices/stock.
     * Cloud RLS remains the authority for tenant and role access.
     */
    suspend fun syncCatalogToCloud(
        tenant: VerifiedTenantContext,
        dao: CatalogDao
    ): CatalogCloudSyncSummary = withContext(Dispatchers.IO) {
        require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN)) {
            "Hanya Owner atau Admin yang dapat menyinkronkan katalog."
        }
        require(tenant.storeId.isNotBlank() && tenant.branchId.isNotBlank())
        val products = dao.getProductsForCloudSync(tenant.storeId)
        val categories = dao.getCategoriesForCloudSync(tenant.storeId)
        val prices = dao.getBranchPricesForCloudSync(tenant.branchId)
        val inventory = dao.getBranchInventoryForCloudSync(tenant.branchId)
        val barcodes = dao.getBarcodesForCloudSync(tenant.storeId)

        require(products.all { it.storeId == tenant.storeId }) { "Katalog memuat produk dari toko lain." }
        require(categories.all { it.storeId == tenant.storeId }) { "Katalog memuat kategori dari toko lain." }
        require(prices.all { price -> products.any { it.id == price.productId } }) { "Harga cabang memuat produk yang tidak dikenal." }
        require(inventory.all { row -> row.branchId == tenant.branchId && products.any { it.id == row.productId } }) {
            "Stok memuat produk atau cabang yang tidak sesuai."
        }
        require(barcodes.all { code -> code.storeId == tenant.storeId && products.any { it.id == code.productId } }) {
            "Barcode memuat produk dari toko lain."
        }

        val categoryRows = categories.map { row ->
            NativeCatalogCategoryDto(row.id, row.storeId, row.name, row.sortOrder, row.isActive,
                java.time.Instant.ofEpochMilli(row.updatedAt).toString())
        }
        val productRows = products.map { row ->
            NativeCatalogProductDto(row.id, row.storeId, row.categoryId, row.name, row.description,
                row.basePrice, row.photoUri, row.isActive,
                java.time.Instant.ofEpochMilli(row.createdAt).toString(),
                java.time.Instant.ofEpochMilli(row.updatedAt).toString())
        }
        val priceRows = prices.map { row ->
            NativeBranchPriceDto(tenant.storeId, tenant.branchId, row.productId, row.sellingPrice,
                java.time.Instant.ofEpochMilli(row.updatedAt).toString())
        }
        val inventoryRows = inventory.map { row ->
            NativeBranchInventoryDto(tenant.storeId, tenant.branchId, row.productId, row.quantity,
                java.time.Instant.ofEpochMilli(row.updatedAt).toString())
        }
        val barcodeRows = barcodes.map { row ->
            NativeProductBarcodeDto(row.id, row.storeId, row.productId, row.barcode,
                java.time.Instant.ofEpochMilli(row.createdAt).toString())
        }

        if (categoryRows.isNotEmpty()) client.from("catalog_categories").upsert(categoryRows)
        if (productRows.isNotEmpty()) client.from("catalog_products").upsert(productRows)
        if (priceRows.isNotEmpty()) client.from("branch_product_prices").upsert(priceRows)
        if (inventoryRows.isNotEmpty()) client.from("branch_inventory").upsert(inventoryRows)
        if (barcodeRows.isNotEmpty()) client.from("product_barcodes").upsert(barcodeRows)

        CatalogCloudSyncSummary(categoryRows.size, productRows.size, priceRows.size, inventoryRows.size, barcodeRows.size)
    }

    /**
     * Pulls the canonical store catalog and selected-branch stock into the local cache.
     * Refuses to overwrite local inventory while unsynced sales could still change it.
     */
    suspend fun fetchCatalogFromCloud(
        tenant: VerifiedTenantContext,
        dao: CatalogDao
    ): CatalogCloudSyncSummary = withContext(Dispatchers.IO) {
        require(tenant.storeId.isNotBlank() && tenant.branchId.isNotBlank())
        require(dao.countUnresolvedSaleSync(tenant.storeId, tenant.branchId) == 0) {
            "Masih ada transaksi yang belum tersinkron. Sinkronkan transaksi dahulu agar stok lokal tidak tertimpa."
        }

        val categories = client.from("catalog_categories").select {
            filter { eq("store_id", tenant.storeId) }
        }.decodeList<NativeCatalogCategoryDto>()
        val products = client.from("catalog_products").select {
            filter { eq("store_id", tenant.storeId) }
        }.decodeList<NativeCatalogProductDto>()
        val prices = client.from("branch_product_prices").select {
            filter {
                eq("store_id", tenant.storeId)
                eq("branch_id", tenant.branchId)
            }
        }.decodeList<NativeBranchPriceDto>()
        val inventory = client.from("branch_inventory").select {
            filter {
                eq("store_id", tenant.storeId)
                eq("branch_id", tenant.branchId)
            }
        }.decodeList<NativeBranchInventoryDto>()
        val barcodes = client.from("product_barcodes").select {
            filter { eq("store_id", tenant.storeId) }
        }.decodeList<NativeProductBarcodeDto>()

        require(products.all { it.storeId == tenant.storeId } &&
            categories.all { it.storeId == tenant.storeId }) { "Data Cloud tidak sesuai dengan toko aktif." }
        require(prices.all { it.storeId == tenant.storeId && it.branchId == tenant.branchId } &&
            inventory.all { it.storeId == tenant.storeId && it.branchId == tenant.branchId }) {
            "Data harga atau stok Cloud tidak sesuai dengan cabang aktif."
        }
        val productIds = products.map { it.id }.toSet()
        require(prices.all { it.productId in productIds } &&
            inventory.all { it.productId in productIds } &&
            barcodes.all { it.storeId == tenant.storeId && it.productId in productIds }) {
            "Data Cloud berisi referensi produk yang tidak valid."
        }

        fun epoch(value: String) = java.time.Instant.parse(value).toEpochMilli()
        dao.applyCloudCatalogSnapshot(
            products = products.map {
                ProductEntity(it.id, it.storeId, it.categoryId, it.name, it.description, it.basePrice,
                    it.photoUri, null, it.isActive, epoch(it.createdAt), epoch(it.updatedAt))
            },
            categories = categories.map {
                CategoryEntity(it.id, it.storeId, it.name, it.sortOrder, it.isActive, epoch(it.updatedAt))
            },
            prices = prices.map {
                BranchProductPriceEntity(it.branchId, it.productId, it.sellingPrice, epoch(it.updatedAt))
            },
            inventory = inventory.map {
                BranchInventoryEntity(it.branchId, it.productId, it.quantity, epoch(it.updatedAt))
            },
            barcodes = barcodes.map {
                ProductBarcodeEntity(it.id, it.storeId, it.productId, it.barcode, epoch(it.createdAt))
            }
        )
        CatalogCloudSyncSummary(categories.size, products.size, prices.size, inventory.size, barcodes.size)
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
