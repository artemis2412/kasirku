package com.artemis.kasirku.nativeapp.data

data class VerifiedTenantContext(
    val userId: String,
    val storeId: String,
    val storeName: String,
    val role: StoreRole,
    val branchId: String,
    val branchName: String,
    val branchTimeZoneId: String? = null
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
    OWNER, ADMIN, SUPERVISOR, CASHIER;
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

interface TenantContextProvider {
    fun current(): VerifiedTenantContext?
}
