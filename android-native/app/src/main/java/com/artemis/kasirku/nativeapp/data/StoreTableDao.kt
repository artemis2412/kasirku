package com.artemis.kasirku.nativeapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreTableDao {
    @Query("SELECT * FROM store_tables WHERE storeId = :storeId AND branchId = :branchId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeActiveTables(storeId: String, branchId: String): Flow<List<StoreTableEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(table: StoreTableEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tables: List<StoreTableEntity>)

    @Query("UPDATE store_tables SET name = :name, capacity = :capacity, updatedAt = :updatedAt WHERE id = :id AND storeId = :storeId AND branchId = :branchId AND isActive = 1 AND occupiedSaleId IS NULL")
    suspend fun update(storeId: String, branchId: String, id: String, name: String, capacity: Int, updatedAt: Long): Int

    @Query("UPDATE store_tables SET isActive = 0, updatedAt = :updatedAt WHERE id = :id AND storeId = :storeId AND branchId = :branchId AND isActive = 1 AND occupiedSaleId IS NULL")
    suspend fun archive(storeId: String, branchId: String, id: String, updatedAt: Long): Int

    @Query("UPDATE store_tables SET occupiedSaleId = :saleId, occupiedAt = :occupiedAt, updatedAt = :updatedAt WHERE id = :id AND storeId = :storeId AND branchId = :branchId AND isActive = 1 AND occupiedSaleId IS NULL")
    suspend fun markOccupied(storeId: String, branchId: String, id: String, saleId: String, occupiedAt: Long): Int

    @Query("UPDATE store_tables SET occupiedSaleId = NULL, occupiedAt = NULL, updatedAt = :updatedAt WHERE id = :id AND storeId = :storeId AND branchId = :branchId AND occupiedSaleId = :saleId")
    suspend fun release(storeId: String, branchId: String, id: String, saleId: String, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM store_tables WHERE storeId = :storeId AND branchId = :branchId AND isActive = 1")
    fun observeActiveCount(storeId: String, branchId: String): Flow<Int>
}
