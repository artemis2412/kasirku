package com.artemis.kasirku.nativeapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        ProductBarcodeEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KasirkuRoomDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        @Volatile
        private var INSTANCE: KasirkuRoomDatabase? = null

        fun getInstance(context: Context): KasirkuRoomDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KasirkuRoomDatabase::class.java,
                    "kasirku_room.db"
                ).build().also { INSTANCE = it }
            }
    }
}
