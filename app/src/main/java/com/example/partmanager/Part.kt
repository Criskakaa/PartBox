package com.example.partmanager

data class Part(
    val id: Long,
    val uniqueCode: String,
    val category: String,
    val type: String,
    val spec: String,
    val length: String,
    val quantity: Int,
    val location: String,
    val remark: String,
    val imageFile: String
)
