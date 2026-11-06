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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonNull
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

@Serializable
data class ProductionInventoryItemDto(
    val id: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("branch_id") val branchId: String,
    val name: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("base_unit") val baseUnit: String,
    @SerialName("on_hand") val onHand: Double,
    @SerialName("minimum_stock") val minimumStock: Double,
    @SerialName("average_unit_cost") val averageUnitCost: Double
)

@Serializable
data class ProductionRecipeDto(
    val id: String,
    val name: String,
    @SerialName("output_item_id") val outputItemId: String,
    @SerialName("yield_quantity") val yieldQuantity: Double,
    val notes: String
)

@Serializable
data class ProductionRecipeItemDto(
    val id: String,
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("inventory_item_id") val inventoryItemId: String,
    val quantity: Double
)

@Serializable
data class ProductionMenuProductDto(val id: String, val name: String)

@Serializable
data class ProductionMenuRecipeItemDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    @SerialName("inventory_item_id") val inventoryItemId: String,
    @SerialName("quantity_per_menu") val quantityPerMenu: Double
)

@Serializable
data class ProductionBatchDto(
    val id: String,
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("output_item_id") val outputItemId: String,
    @SerialName("actual_output_quantity") val actualOutputQuantity: Double,
    @SerialName("total_input_cost") val totalInputCost: Double,
    @SerialName("completed_at") val completedAt: String
)

@Serializable
data class InventoryMovementDto(
    val id: String,
    @SerialName("inventory_item_id") val inventoryItemId: String,
    @SerialName("movement_type") val movementType: String,
    @SerialName("quantity_delta") val quantityDelta: Double,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class ProductionInventoryInsertDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("branch_id") val branchId: String,
    val name: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("base_unit") val baseUnit: String,
    @SerialName("minimum_stock") val minimumStock: Double,
    @SerialName("created_by") val createdBy: String
)

@Serializable
data class ProductionIngredientInputDto(
    @SerialName("inventory_item_id") val inventoryItemId: String,
    val quantity: Double
)

@Serializable
data class MenuIngredientInputDto(
    @SerialName("inventory_item_id") val inventoryItemId: String,
    @SerialName("quantity_per_menu") val quantityPerMenu: Double
)

data class ProductionCloudSnapshot(
    val items: List<ProductionInventoryItemDto>,
    val recipes: List<ProductionRecipeDto>,
    val recipeItems: List<ProductionRecipeItemDto>,
    val products: List<ProductionMenuProductDto>,
    val menuRecipeItems: List<ProductionMenuRecipeItemDto>,
    val batches: List<ProductionBatchDto>,
    val movements: List<InventoryMovementDto>
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

    suspend fun loadProductionSnapshot(tenant: VerifiedTenantContext): ProductionCloudSnapshot =
        withContext(Dispatchers.IO) {
            val items = client.from("inventory_items").select {
                filter { eq("store_id", tenant.storeId); eq("branch_id", tenant.branchId); eq("is_active", true) }
            }.decodeList<ProductionInventoryItemDto>()
            val recipes = client.from("production_recipes").select {
                filter { eq("store_id", tenant.storeId); eq("branch_id", tenant.branchId); eq("is_active", true) }
            }.decodeList<ProductionRecipeDto>()
            val recipeItems = client.from("production_recipe_items").select {
                filter { eq("store_id", tenant.storeId); eq("branch_id", tenant.branchId) }
            }.decodeList<ProductionRecipeItemDto>()
            val products = client.from("products").select {
                filter { eq("store_id", tenant.storeId); eq("branch_id", tenant.branchId); eq("is_active", true) }
            }.decodeList<ProductionMenuProductDto>()
            val menuItems = client.from("menu_recipe_items").select {
                filter { eq("store_id", tenant.storeId); eq("branch_id", tenant.branchId) }
            }.decodeList<ProductionMenuRecipeItemDto>()
            val batches = client.from("production_batches").select {
                filter { eq("store_id", tenant.storeId); eq("branch_id", tenant.branchId) }
            }.decodeList<ProductionBatchDto>()
            val movements = client.from("inventory_movements").select {
                filter { eq("store_id", tenant.storeId); eq("branch_id", tenant.branchId) }
            }.decodeList<InventoryMovementDto>()
            ProductionCloudSnapshot(items, recipes, recipeItems, products, menuItems, batches, movements)
        }

    suspend fun createProductionInventoryItem(tenant: VerifiedTenantContext, name: String, itemType: String, baseUnit: String, minimumStock: Double) =
        withContext(Dispatchers.IO) {
            require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
                "Akses pengelolaan bahan hanya untuk Owner, Admin, dan Supervisor."
            }
            client.from("inventory_items").insert(
                ProductionInventoryInsertDto(tenant.storeId, tenant.branchId, name.trim(), itemType, baseUnit.trim(), minimumStock, tenant.userId)
            )
        }

    suspend fun adjustProductionStock(tenant: VerifiedTenantContext, itemId: String, quantityDelta: Double, movementType: String, unitCost: Double, note: String) =
        withContext(Dispatchers.IO) {
            require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
                "Akses pengelolaan stok tidak diizinkan."
            }
            client.postgrest.rpc("adjust_inventory_stock", buildJsonObject {
                put("p_store_id", tenant.storeId)
                put("p_branch_id", tenant.branchId)
                put("p_item_id", itemId)
                put("p_quantity_delta", quantityDelta)
                put("p_movement_type", movementType)
                put("p_unit_cost", unitCost)
                put("p_note", note)
                put("p_idempotency_key", java.util.UUID.randomUUID().toString())
            })
        }

    suspend fun saveProductionRecipe(
        tenant: VerifiedTenantContext, recipeId: String?, name: String, outputItemId: String,
        yieldQuantity: Double, notes: String, ingredients: List<ProductionIngredientInputDto>
    ) = withContext(Dispatchers.IO) {
        require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
            "Akses pengelolaan resep tidak diizinkan."
        }
        client.postgrest.rpc("save_production_recipe", buildJsonObject {
            put("p_store_id", tenant.storeId)
            put("p_branch_id", tenant.branchId)
            if (recipeId == null) put("p_recipe_id", JsonNull) else put("p_recipe_id", recipeId)
            put("p_name", name.trim())
            put("p_output_item_id", outputItemId)
            put("p_yield_quantity", yieldQuantity)
            put("p_notes", notes)
            put("p_items", Json.encodeToJsonElement(ListSerializer(ProductionIngredientInputDto.serializer()), ingredients))
        })
    }

    suspend fun completeProduction(tenant: VerifiedTenantContext, recipeId: String, outputQuantity: Double, notes: String) =
        withContext(Dispatchers.IO) {
            require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
                "Akses pencatatan produksi tidak diizinkan."
            }
            client.postgrest.rpc("complete_production_batch", buildJsonObject {
                put("p_store_id", tenant.storeId)
                put("p_branch_id", tenant.branchId)
                put("p_recipe_id", recipeId)
                put("p_output_quantity", outputQuantity)
                put("p_actual_inputs", buildJsonArray { })
                put("p_notes", notes)
            })
        }

    suspend fun replaceMenuRecipe(tenant: VerifiedTenantContext, productId: String, ingredients: List<MenuIngredientInputDto>) =
        withContext(Dispatchers.IO) {
            require(tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)) {
                "Akses pengelolaan resep menu tidak diizinkan."
            }
            client.postgrest.rpc("replace_menu_recipe", buildJsonObject {
                put("p_store_id", tenant.storeId)
                put("p_branch_id", tenant.branchId)
                put("p_product_id", productId)
                put("p_items", Json.encodeToJsonElement(ListSerializer(MenuIngredientInputDto.serializer()), ingredients))
            })
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
