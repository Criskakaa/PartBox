package com.example.partmanager

import android.content.ContentResolver
import android.net.Uri
import java.util.Locale

object CsvUtils {

    private val HEADERS = listOf(
        "唯一编号",
        "大类",
        "类型",
        "规格",
        "长度",
        "数量",
        "存放位置",
        "备注"
    )

    fun exportToCsv(
        resolver: ContentResolver,
        uri: Uri,
        parts: List<Part>
    ) {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(HEADERS.joinToString(","))
        sb.append("\n")

        for (part in parts) {
            sb.append(escape(part.uniqueCode)).append(',')
            sb.append(escape(part.category)).append(',')
            sb.append(escape(part.type)).append(',')
            sb.append(escape(part.spec)).append(',')
            sb.append(escape(part.length)).append(',')
            sb.append(escape(part.quantity.toString())).append(',')
            sb.append(escape(part.location)).append(',')
            sb.append(escape(part.remark)).append('\n')
        }

        resolver.openOutputStream(uri)?.use { output ->
            output.write(sb.toString().toByteArray(Charsets.UTF_8))
        } ?: throw java.io.IOException("无法写入文件")
    }

    fun importFromCsv(
        resolver: ContentResolver,
        uri: Uri,
        db: DatabaseHelper
    ): Int {
        var text = resolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw java.io.IOException("无法读取文件")

        if (text.startsWith('\uFEFF')) {
            text = text.substring(1)
        }

        val rows = parseCsv(text)
            .filter { row -> row.any { it.isNotBlank() } }

        if (rows.isEmpty()) {
            return 0
        }

        val first = rows[0]
        val lowerHeader = first.map { it.trim().lowercase(Locale.ROOT) }
        val hasHeader = lowerHeader.any {
            it == "唯一编号" ||
                it == "unique_code" ||
                it == "code" ||
                it.contains("唯一编号")
        }

        var codeIdx = 0
        var categoryIdx = 1
        var typeIdx = 2
        var specIdx = 3
        var lengthIdx = 4
        var quantityIdx = 5
        var locationIdx = 6
        var remarkIdx = 7

        if (hasHeader) {
            codeIdx = indexOr(lowerHeader, 0, listOf("唯一编号", "unique_code", "code"))
            categoryIdx = indexOr(lowerHeader, 1, listOf("大类", "category"))
            typeIdx = indexOr(lowerHeader, 2, listOf("类型", "type"))
            specIdx = indexOr(lowerHeader, 3, listOf("规格", "spec"))
            lengthIdx = indexOr(lowerHeader, 4, listOf("长度", "length"))
            quantityIdx = indexOr(lowerHeader, 5, listOf("数量", "quantity"))
            locationIdx = indexOr(lowerHeader, 6, listOf("存放位置", "位置", "location"))
            remarkIdx = indexOr(lowerHeader, 7, listOf("备注", "remark"))
        }

        var count = 0
        val startIndex = if (hasHeader) 1 else 0

        for (i in startIndex until rows.size) {
            val row = rows[i]

            val code = row.getOrNull(codeIdx)?.trim().orEmpty()
            if (code.isEmpty()) {
                continue
            }

            val quantity = row.getOrNull(quantityIdx)
                ?.trim()
                ?.toIntOrNull()
                ?.coerceAtLeast(0)
                ?: 0

            val existing = db.findByUniqueCode(code)

            val part = if (existing != null) {
                existing.copy(
                    category = row.getOrNull(categoryIdx)?.trim().orEmpty(),
                    type = row.getOrNull(typeIdx)?.trim().orEmpty(),
                    spec = row.getOrNull(specIdx)?.trim().orEmpty(),
                    length = row.getOrNull(lengthIdx)?.trim().orEmpty(),
                    quantity = quantity,
                    location = row.getOrNull(locationIdx)?.trim().orEmpty(),
                    remark = row.getOrNull(remarkIdx)?.trim().orEmpty(),
                    imageFile = existing.imageFile
                )
            } else {
                Part(
                    id = 0L,
                    uniqueCode = code,
                    category = row.getOrNull(categoryIdx)?.trim().orEmpty(),
                    type = row.getOrNull(typeIdx)?.trim().orEmpty(),
                    spec = row.getOrNull(specIdx)?.trim().orEmpty(),
                    length = row.getOrNull(lengthIdx)?.trim().orEmpty(),
                    quantity = quantity,
                    location = row.getOrNull(locationIdx)?.trim().orEmpty(),
                    remark = row.getOrNull(remarkIdx)?.trim().orEmpty(),
                    imageFile = ""
                )
            }

            if (existing != null) {
                db.updatePart(part)
            } else {
                db.insertPart(part)
            }

            count++
        }

        return count
    }

    private fun indexOr(
        headers: List<String>,
        defaultIndex: Int,
        aliases: List<String>
    ): Int {
        val found = headers.indexOfFirst { header ->
            aliases.any { alias -> header == alias || header.contains(alias) }
        }
        return if (found < 0) defaultIndex else found
    }

    private fun escape(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun parseCsv(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < content.length) {
            val ch = content[i]

            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length && content[i + 1] == '"') {
                        field.append('"')
                        i += 2
                    } else {
                        inQuotes = false
                        i += 1
                    }
                } else {
                    field.append(ch)
                    i += 1
                }
                continue
            }

            when (ch) {
                '"' -> {
                    inQuotes = true
                    i += 1
                }
                ',' -> {
                    row.add(field.toString())
                    field = StringBuilder()
                    i += 1
                }
                '\n' -> {
                    row.add(field.toString())
                    rows.add(row)
                    row = mutableListOf()
                    field = StringBuilder()
                    i += 1
                }
                '\r' -> {
                    i += 1
                    if (i < content.length && content[i] == '\n') {
                        i += 1
                    }
                    row.add(field.toString())
                    rows.add(row)
                    row = mutableListOf()
                    field = StringBuilder()
                }
                else -> {
                    field.append(ch)
                    i += 1
                }
            }
        }

        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }

        return rows
    }
}
