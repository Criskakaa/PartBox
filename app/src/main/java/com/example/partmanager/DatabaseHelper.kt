package com.example.partmanager

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "parts.db"
        private const val DB_VERSION = 1
        const val TABLE_PARTS = "parts"
        const val COL_ID = "_id"
        const val COL_UNIQUE_CODE = "unique_code"
        const val COL_CATEGORY = "category"
        const val COL_TYPE = "part_type"
        const val COL_SPEC = "spec"
        const val COL_LENGTH = "length"
        const val COL_QUANTITY = "quantity"
        const val COL_LOCATION = "location"
        const val COL_REMARK = "remark"
        const val COL_IMAGE_FILE = "image_file"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PARTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UNIQUE_CODE TEXT NOT NULL UNIQUE,
                $COL_CATEGORY TEXT NOT NULL DEFAULT '',
                $COL_TYPE TEXT NOT NULL DEFAULT '',
                $COL_SPEC TEXT NOT NULL DEFAULT '',
                $COL_LENGTH TEXT NOT NULL DEFAULT '',
                $COL_QUANTITY INTEGER NOT NULL DEFAULT 0,
                $COL_LOCATION TEXT NOT NULL DEFAULT '',
                $COL_REMARK TEXT NOT NULL DEFAULT '',
                $COL_IMAGE_FILE TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_parts_filter ON $TABLE_PARTS(
                $COL_CATEGORY, $COL_TYPE, $COL_SPEC, $COL_LENGTH
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 第一版，无升级逻辑
    }

    fun insertPart(part: Part): Long {
        return writableDatabase.insertOrThrow(TABLE_PARTS, null, toValues(part))
    }

    fun updatePart(part: Part): Int {
        return writableDatabase.update(
            TABLE_PARTS,
            toValues(part),
            "$COL_ID = ?",
            arrayOf(part.id.toString())
        )
    }

    fun updateQuantity(id: Long, quantity: Int) {
        val cv = ContentValues()
        cv.put(COL_QUANTITY, quantity)
        writableDatabase.update(
            TABLE_PARTS,
            cv,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun deletePart(id: Long) {
        writableDatabase.delete(TABLE_PARTS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun findById(id: Long): Part? {
        val cursor = readableDatabase.query(
            TABLE_PARTS,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return try {
            if (cursor.moveToFirst()) rowToPart(cursor) else null
        } finally {
            cursor.close()
        }
    }

    fun findByUniqueCode(code: String): Part? {
        val cursor = readableDatabase.query(
            TABLE_PARTS,
            null,
            "$COL_UNIQUE_CODE = ?",
            arrayOf(code),
            null,
            null,
            null
        )
        return try {
            if (cursor.moveToFirst()) rowToPart(cursor) else null
        } finally {
            cursor.close()
        }
    }

    fun getAllParts(): List<Part> {
        return queryParts(null, emptyList())
    }

    fun queryParts(
        category: String?,
        type: String?,
        spec: String?,
        length: String?,
        searchTerms: List<String>
    ): List<Part> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        addEqual(clauses, args, COL_CATEGORY, category)
        addEqual(clauses, args, COL_TYPE, type)
        addEqual(clauses, args, COL_SPEC, spec)
        addEqual(clauses, args, COL_LENGTH, length)

        val searchableColumns = listOf(
            COL_UNIQUE_CODE,
            COL_CATEGORY,
            COL_TYPE,
            COL_SPEC,
            COL_LENGTH,
            COL_LOCATION,
            COL_REMARK
        )

        for (term in searchTerms) {
            val like = "%$term%"
            val termClause = searchableColumns.joinToString(" OR ") { "$it LIKE ?" }
            clauses.add("($termClause)")
            repeat(searchableColumns.size) {
                args.add(like)
            }
        }

        return queryParts(
            clauses.joinToString(" AND "),
            args
        )
    }

    private fun queryParts(selection: String?, args: List<String>): List<Part> {
        val list = mutableListOf<Part>()
        val cursor = readableDatabase.query(
            TABLE_PARTS,
            null,
            selection,
            args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null,
            null,
            "$COL_ID DESC"
        )
        try {
            while (cursor.moveToNext()) {
                list.add(rowToPart(cursor))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun getCategories(): List<String> {
        return distinctValues(COL_CATEGORY, null, emptyList())
    }

    fun getTypes(category: String?): List<String> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!category.isNullOrEmpty()) {
            clauses.add("$COL_CATEGORY = ?")
            args.add(category)
        }
        return distinctValues(COL_TYPE, clauses, args)
    }

    fun getSpecs(category: String?, type: String?): List<String> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!category.isNullOrEmpty()) {
            clauses.add("$COL_CATEGORY = ?")
            args.add(category)
        }
        if (!type.isNullOrEmpty()) {
            clauses.add("$COL_TYPE = ?")
            args.add(type)
        }
        return distinctValues(COL_SPEC, clauses, args)
    }

    fun getLengths(category: String?, type: String?, spec: String?): List<String> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!category.isNullOrEmpty()) {
            clauses.add("$COL_CATEGORY = ?")
            args.add(category)
        }
        if (!type.isNullOrEmpty()) {
            clauses.add("$COL_TYPE = ?")
            args.add(type)
        }
        if (!spec.isNullOrEmpty()) {
            clauses.add("$COL_SPEC = ?")
            args.add(spec)
        }
        return distinctValues(COL_LENGTH, clauses, args)
    }

    private fun distinctValues(
        column: String,
        whereClauses: List<String>?,
        whereArgs: List<String>
    ): List<String> {
        val result = mutableListOf<String>()
        val where = whereClauses?.joinToString(" AND ")
        val cursor = readableDatabase.query(
            true,
            TABLE_PARTS,
            arrayOf(column),
            where,
            whereArgs.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null,
            null,
            "$column COLLATE NOCASE",
            null
        )
        try {
            while (cursor.moveToNext()) {
                val value = cursor.getString(0)
                if (!value.isNullOrEmpty()) {
                    result.add(value)
                }
            }
        } finally {
            cursor.close()
        }
        return result
    }

    private fun addEqual(
        clauses: MutableList<String>,
        args: MutableList<String>,
        column: String,
        value: String?
    ) {
        if (!value.isNullOrEmpty()) {
            clauses.add("$column = ?")
            args.add(value)
        }
    }

    private fun rowToPart(cursor: Cursor): Part {
        return Part(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            uniqueCode = cursor.getString(cursor.getColumnIndexOrThrow(COL_UNIQUE_CODE)) ?: "",
            category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)) ?: "",
            type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE)) ?: "",
            spec = cursor.getString(cursor.getColumnIndexOrThrow(COL_SPEC)) ?: "",
            length = cursor.getString(cursor.getColumnIndexOrThrow(COL_LENGTH)) ?: "",
            quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY)),
            location = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCATION)) ?: "",
            remark = cursor.getString(cursor.getColumnIndexOrThrow(COL_REMARK)) ?: "",
            imageFile = cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_FILE)) ?: ""
        )
    }

    private fun toValues(part: Part): ContentValues {
        return ContentValues().apply {
            put(COL_UNIQUE_CODE, part.uniqueCode)
            put(COL_CATEGORY, part.category)
            put(COL_TYPE, part.type)
            put(COL_SPEC, part.spec)
            put(COL_LENGTH, part.length)
            put(COL_QUANTITY, part.quantity)
            put(COL_LOCATION, part.location)
            put(COL_REMARK, part.remark)
            put(COL_IMAGE_FILE, part.imageFile)
        }
    }
}
