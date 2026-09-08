package com.example.partmanager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PartAdapter(
    private val onIncrease: (Part) -> Unit,
    private val onDecrease: (Part) -> Unit,
    private val onEdit: (Part) -> Unit,
    private val onDelete: (Part) -> Unit
) : RecyclerView.Adapter<PartAdapter.VH>() {

    private val items = mutableListOf<Part>()

    private val imageCache = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun submitList(newList: List<Part>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_part, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val part = items[position]

        holder.tvTitle.text = listOf(part.category, part.type, part.spec, part.length)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        holder.tvCode.text = "编号：${part.uniqueCode}"
        holder.tvQty.text = "数量：${part.quantity}"

        val meta = mutableListOf<String>()
        if (part.location.isNotBlank()) meta.add("位置：${part.location}")
        if (part.remark.isNotBlank()) meta.add("备注：${part.remark}")
        holder.tvMeta.text = meta.joinToString(" | ")

        loadImage(part.imageFile, holder.imgPart, holder.itemView.context.filesDir)

        holder.itemView.setOnClickListener {
            onEdit(part)
        }
    }

    private fun loadImage(fileName: String, imageView: ImageView, filesDir: File) {
        if (fileName.isBlank()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        val file = File(filesDir, fileName)
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

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvCode: TextView = view.findViewById(R.id.tvCode)
        val tvQty: TextView = view.findViewById(R.id.tvQty)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val imgPart: ImageView = view.findViewById(R.id.imgPart)
    }
}
