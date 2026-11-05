package com.artemis.kasirku.nativeapp.data

/**
 * Identity context accepted by tenant-scoped repositories.
 *
 * This value must only be created by the authentication/session layer after it
 * has verified the Supabase user, active store membership, store record, and
 * the user's access to the selected active branch.
 *
 * Do not construct this from editable preferences, local PIN role labels, or
 * hard-coded demo IDs. Until a verified session exists, tenant data operations
 * must remain unavailable.
 */
data class VerifiedTenantContext(
    val userId: String,
    val storeId: String,
    val storeName: String,
    val role: StoreRole,
    val branchId: String,
    val branchName: String
) {
    init {
        require(userId.isNotBlank()) { "Akun belum terverifikasi." }
        require(storeId.isNotBlank()) { "Toko belum terverifikasi." }
        require(storeName.isNotBlank()) { "Nama toko belum tersedia." }
        require(branchId.isNotBlank()) { "Cabang belum terverifikasi." }
        require(branchName.isNotBlank()) { "Nama cabang belum tersedia." }
    }
}

enum class StoreRole {
    OWNER,
    ADMIN,
    SUPERVISOR,
    CASHIER;

    companion object {
        fun fromCloud(value: String): StoreRole = when (value.trim().lowercase()) {
            "owner" -> OWNER
            "admin" -> ADMIN
            "supervisor" -> SUPERVISOR
            "cashier" -> CASHIER
            else -> throw IllegalArgumentException("Peran akun Cloud tidak dikenali.")
        }
    }

    fun canViewCostAndProfit(): Boolean = this == OWNER
}

/**
 * Implemented by the authentication layer. A null value means no fully
 * verified Cloud identity/store/branch is currently available.
 */
interface TenantContextProvider {
    fun current(): VerifiedTenantContext?
}
