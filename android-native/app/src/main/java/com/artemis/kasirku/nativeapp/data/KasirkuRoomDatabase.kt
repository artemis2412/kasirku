package com.artemis.kasirku.nativeapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * New Room store kept separate from the legacy kasirku_local.db.
 * The legacy SQLiteOpenHelper remains untouched until an explicit,
 * verified import/migration is implemented in a later step.
 */
@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        BranchProductPriceEntity::class,
        BranchInventoryEntity::class,
        ProductBarcodeEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SaleSyncOutboxEntity::class,
        StoreTableEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class KasirkuRoomDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun storeTableDao(): StoreTableDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS sales (id TEXT NOT NULL PRIMARY KEY, storeId TEXT NOT NULL, branchId TEXT NOT NULL, cashierUserId TEXT NOT NULL, subtotal INTEGER NOT NULL, discount INTEGER NOT NULL, total INTEGER NOT NULL, paid INTEGER NOT NULL, change INTEGER NOT NULL, paymentMethod TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sales_storeId_branchId_createdAt ON sales(storeId, branchId, createdAt)")
                database.execSQL("CREATE TABLE IF NOT EXISTS sale_items (id TEXT NOT NULL PRIMARY KEY, saleId TEXT NOT NULL, productId TEXT NOT NULL, productName TEXT NOT NULL, quantity INTEGER NOT NULL, unitPrice INTEGER NOT NULL, lineTotal INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS sale_sync_outbox (saleId TEXT NOT NULL PRIMARY KEY, storeId TEXT NOT NULL, branchId TEXT NOT NULL, status TEXT NOT NULL, attempts INTEGER NOT NULL, lastError TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sale_sync_outbox_status_createdAt ON sale_sync_outbox(status, createdAt)")
                database.execSQL("INSERT OR IGNORE INTO sale_sync_outbox (saleId, storeId, branchId, status, attempts, lastError, createdAt, updatedAt) SELECT id, storeId, branchId, 'PENDING', 0, NULL, createdAt, createdAt FROM sales")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sale_sync_outbox_storeId_branchId_status_createdAt ON sale_sync_outbox(storeId, branchId, status, createdAt)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS store_tables (id TEXT NOT NULL PRIMARY KEY, storeId TEXT NOT NULL, branchId TEXT NOT NULL, name TEXT NOT NULL, capacity INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_store_tables_branchId_name ON store_tables(branchId, name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_store_tables_storeId_branchId_isActive ON store_tables(storeId, branchId, isActive)")
            }
        }

        @Volatile
        private var INSTANCE: KasirkuRoomDatabase? = null

        fun getInstance(context: Context): KasirkuRoomDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KasirkuRoomDatabase::class.java,
                    "kasirku_room.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
            }
    }
}