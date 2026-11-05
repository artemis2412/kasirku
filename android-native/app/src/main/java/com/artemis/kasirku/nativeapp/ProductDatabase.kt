package com.artemis.kasirku.nativeapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class KasirkuProduct(
    val id: Long = 0,
    val name: String,
    val category: String,
    val costPrice: Long,
    val sellingPrice: Long,
    val stock: Int,
    val barcode: String,
    val photoUri: String
)

class ProductDatabase(context: Context) :
    SQLiteOpenHelper(context, "kasirku_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                cost_price INTEGER NOT NULL DEFAULT 0,
                selling_price INTEGER NOT NULL DEFAULT 0,
                stock INTEGER NOT NULL DEFAULT 0,
                barcode TEXT NOT NULL DEFAULT '',
                photo_uri TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future database migrations will be added here.
    }

    fun getAllProducts(): List<KasirkuProduct> {
        val products = mutableListOf<KasirkuProduct>()
        readableDatabase.query(
            "products",
            arrayOf("id", "name", "category", "cost_price", "selling_price", "stock", "barcode", "photo_uri"),
            null, null, null, null, "name COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                products.add(
                    KasirkuProduct(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        category = cursor.getString(2),
                        costPrice = cursor.getLong(3),
                        sellingPrice = cursor.getLong(4),
                        stock = cursor.getInt(5),
                        barcode = cursor.getString(6),
                        photoUri = cursor.getString(7)
                    )
                )
            }
        }
        return products
    }

    fun saveProduct(product: KasirkuProduct): Long {
        val values = ContentValues().apply {
            put("name", product.name)
            put("category", product.category)
            put("cost_price", product.costPrice)
            put("selling_price", product.sellingPrice)
            put("stock", product.stock)
            put("barcode", product.barcode)
            put("photo_uri", product.photoUri)
        }
        return if (product.id == 0L) {
            writableDatabase.insert("products", null, values)
        } else {
            writableDatabase.update("products", values, "id = ?", arrayOf(product.id.toString())).toLong()
            product.id
        }
    }

    fun deleteProduct(id: Long) {
        writableDatabase.delete("products", "id = ?", arrayOf(id.toString()))
    }
}
