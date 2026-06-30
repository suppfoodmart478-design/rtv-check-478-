package com.store478.rtvcheck.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream

private const val DB_NAME = "items.db"
private const val DB_VERSION = 1

class ItemDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val dbPath: String = context.getDatabasePath(DB_NAME).path

    init {
        copyDatabaseIfNeeded()
    }

    private fun copyDatabaseIfNeeded() {
        val dbFile = File(dbPath)
        if (dbFile.exists()) return

        dbFile.parentFile?.mkdirs()
        context.assets.open(DB_NAME).use { input ->
            FileOutputStream(dbFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // No-op: database is pre-populated from assets
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Replace bundled DB on version bump (re-copy from assets)
        context.deleteDatabase(DB_NAME)
        copyDatabaseIfNeeded()
    }

    fun openReadable(): SQLiteDatabase {
        return SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
    }

    /**
     * Looks up an item by UPC (barcode) or SKU.
     * Tries UPC first (most common scan path), falls back to SKU.
     */
    fun findByUpcOrSku(query: String): ItemResult? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val db = openReadable()
        try {
            db.rawQuery(
                "SELECT upc, sku, description, status, supplier_no, supplier_name, keterangan " +
                    "FROM items WHERE upc = ? LIMIT 1",
                arrayOf(trimmed)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.toItemResult()
                }
            }

            db.rawQuery(
                "SELECT upc, sku, description, status, supplier_no, supplier_name, keterangan " +
                    "FROM items WHERE sku = ? LIMIT 1",
                arrayOf(trimmed)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.toItemResult()
                }
            }
        } finally {
            db.close()
        }
        return null
    }

    private fun android.database.Cursor.toItemResult(): ItemResult {
        return ItemResult(
            upc = getString(getColumnIndexOrThrow("upc")),
            sku = getString(getColumnIndexOrThrow("sku")),
            description = getString(getColumnIndexOrThrow("description")),
            status = getString(getColumnIndexOrThrow("status")),
            supplierNo = getString(getColumnIndexOrThrow("supplier_no")),
            supplierName = getString(getColumnIndexOrThrow("supplier_name")),
            keterangan = getString(getColumnIndexOrThrow("keterangan"))
        )
    }
}
