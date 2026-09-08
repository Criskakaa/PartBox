package com.example.partmanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import java.io.File

class PartAdapter(
    private val context: Context,
    items: List<Part>
) : BaseAdapter() {

    private val parts = mutableListOf<Part>().apply { addAll(items) }
    private val inflater = LayoutInflater.from(context)

    private val imageCache = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun updateList(newList: List<Part>) {
        parts.clear()
        parts.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = parts.size

    override fun getItem(position: Int): Part? {
        return if (position in parts.indices) parts[position] else null
    }

    override fun getItemId(position: Int): Long {
        return parts.getOrNull(position)?.id ?: 0L
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_part, parent, false)
        val part = parts[position]

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvCode = view.findViewById<TextView>(R.id.tvCode)
        val tvQty = view.findViewById<TextView>(R.id.tvQty)
        val tvMeta = view.findViewById<TextView>(R.id.tvMeta)
        val imgPart = view.findViewById<ImageView>(R.id.imgPart)

        tvTitle.text = listOf(part.category, part.type, part.spec, part.length)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        tvCode.text = "编号：${part.uniqueCode}"
        tvQty.text = "数量：${part.quantity}"

        val meta = mutableListOf<String>()
        if (part.location.isNotBlank()) meta.add("位置：${part.location}")
        if (part.remark.isNotBlank()) meta.add("备注：${part.remark}")
        tvMeta.text = meta.joinToString(" | ")

        loadImage(part.imageFile, imgPart)

        return view
    }

    private fun loadImage(fileName: String, imageView: ImageView) {
        if (fileName.isBlank()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        val key = file.absolutePath

        val cached = imageCache.get(key)
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            return
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 100 &&
            bounds.outHeight / (sample * 2) >= 100
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        if (bitmap != null) {
            imageCache.put(key, bitmap)
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }
}
