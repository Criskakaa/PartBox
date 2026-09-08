package com.example.partmanager

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class EditPartActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "extra_part_id"
    }

    private lateinit var db: DatabaseHelper
    private lateinit var codeInput: EditText
    private lateinit var categoryInput: EditText
    private lateinit var typeInput: EditText
    private lateinit var specInput: EditText
    private lateinit var lengthInput: EditText
    private lateinit var quantityInput: EditText
    private lateinit var locationInput: EditText
    private lateinit var remarkInput: EditText
    private lateinit var imagePreview: ImageView

    private var partId = 0L
    private var oldImageFileName = ""
    private var currentImageFileName = ""

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && !copySelectedImage(uri)) {
            toast("图片读取失败")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_part)

        db = DatabaseHelper(this)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        codeInput = findViewById(R.id.codeInput)
        categoryInput = findViewById(R.id.categoryInput)
        typeInput = findViewById(R.id.typeInput)
        specInput = findViewById(R.id.specInput)
        lengthInput = findViewById(R.id.lengthInput)
        quantityInput = findViewById(R.id.quantityInput)
        locationInput = findViewById(R.id.locationInput)
        remarkInput = findViewById(R.id.remarkInput)
        imagePreview = findViewById(R.id.imagePreview)

        val editId = intent.getLongExtra(EXTRA_ID, 0L)
        if (editId != 0L) {
            val part = db.findById(editId)
            if (part != null) {
                partId = part.id
                bind(part)
            }
        }

        supportActionBar?.title = if (partId == 0L) "新增零件" else "编辑零件"

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            imagePicker.launch("image/*")
        }

        findViewById<Button>(R.id.btnRemoveImage).setOnClickListener {
            val pending = currentImageFileName
            currentImageFileName = ""
            if (pending.isNotEmpty() && pending != oldImageFileName) {
                File(filesDir, pending).delete()
            }
            displayImage()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            savePart()
        }

        val deleteBtn = findViewById<Button>(R.id.btnDelete)
        deleteBtn.setOnClickListener {
            if (partId == 0L) {
                finish()
            } else {
                confirmDelete()
            }
        }

        if (partId == 0L) {
            deleteBtn.text = "返回"
        }
    }

    private fun bind(part: Part) {
        codeInput.setText(part.uniqueCode)
        codeInput.isEnabled = false
        categoryInput.setText(part.category)
        typeInput.setText(part.type)
        specInput.setText(part.spec)
        lengthInput.setText(part.length)
        quantityInput.setText(part.quantity.toString())
        locationInput.setText(part.location)
        remarkInput.setText(part.remark)

        oldImageFileName = part.imageFile
        currentImageFileName = part.imageFile
        displayImage()
    }

    private fun copySelectedImage(uri: Uri): Boolean {
        return try {
            val previousPending = currentImageFileName
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val dest = File(filesDir, fileName)

            val copied = contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
                dest.length() > 0
            } ?: false

            if (!copied) {
                dest.delete()
                return false
            }

            if (previousPending.isNotEmpty() && previousPending != oldImageFileName) {
                File(filesDir, previousPending).delete()
            }

            currentImageFileName = fileName
            displayImage()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun displayImage() {
        if (currentImageFileName.isBlank()) {
            imagePreview.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        val file = File(filesDir, currentImageFileName)
        if (!file.exists()) {
            imagePreview.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 &&
            bounds.outHeight / (sample * 2) >= 512
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        if (bitmap != null) {
            imagePreview.setImageBitmap(bitmap)
        } else {
            imagePreview.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun savePart() {
        val code = codeInput.text.toString().trim()
        if (code.isEmpty()) {
            codeInput.error = "请填写唯一编号"
            codeInput.requestFocus()
            return
        }

        val quantityText = quantityInput.text.toString().trim()
        val quantity = if (quantityText.isEmpty()) {
            0
        } else {
            quantityText.toIntOrNull()
        }

        if (quantity == null) {
            toast("数量必须是整数")
            return
        }
        if (quantity < 0) {
            toast("数量不能为负数")
            return
        }

        if (partId == 0L) {
            if (db.findByUniqueCode(code) != null) {
                toast("该唯一编号已存在")
                return
            }
        }

        val originalPart = if (partId != 0L) db.findById(partId) else null
        if (partId != 0L && originalPart == null) {
            toast("记录不存在")
            finish()
            return
        }

        val finalCode = originalPart?.uniqueCode ?: code

        val part = Part(
            id = partId,
            uniqueCode = finalCode,
            category = categoryInput.text.toString().trim(),
            type = typeInput.text.toString().trim(),
            spec = specInput.text.toString().trim(),
            length = lengthInput.text.toString().trim(),
            quantity = quantity,
            location = locationInput.text.toString().trim(),
            remark = remarkInput.text.toString().trim(),
            imageFile = currentImageFileName
        )

        if (partId == 0L) {
            db.insertPart(part)
        } else {
            db.updatePart(part)
            if (oldImageFileName.isNotEmpty() && oldImageFileName != currentImageFileName) {
                File(filesDir, oldImageFileName).delete()
            }
        }

        setResult(RESULT_OK)
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("删除零件")
            .setMessage("确定删除这个零件吗？删除后无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                db.deletePart(partId)
                if (oldImageFileName.isNotEmpty()) {
                    val file = File(filesDir, oldImageFileName)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                toast("已删除")
                setResult(RESULT_OK)
                finish()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
