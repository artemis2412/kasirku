package com.artemis.kasirku.nativeapp.data

/**
 * Read model returned by the branch-scoped catalog query.
 * Extra aliases are intentionally separate from the persisted product entity.
 */
data class BranchCatalogRow(
    val id: String,
    val storeId: String,
    val categoryId: String?,
    val name: String,
    val description: String,
    val basePrice: Long,
    val photoUri: String?,
    val legacySourceId: Long?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val activeSellingPrice: Long,
    val branchQuantity: Int
)
