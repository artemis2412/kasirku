package com.artemis.kasirku.nativeapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "store_tables", indices = [
    Index(value = ["branchId", "name"], unique = true),
    Index(value = ["storeId", "branchId", "isActive"])
])
data class StoreTableEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val branchId: String,
    val name: String,
    val capacity: Int = 4,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
