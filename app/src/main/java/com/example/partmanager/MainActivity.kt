package com.example.partmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: PartAdapter

    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var tvStatsAll: TextView
    private lateinit var tvStatsFilter: TextView
    private lateinit var searchInput: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerSpec: Spinner
    private lateinit var spinnerLength: Spinner
    private lateinit var recyclerView: RecyclerView

    private var isRefreshing = false

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onDataChanged()
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            CsvUtils.exportToCsv(contentResolver, uri, db.getAllParts())
            toast("CSV 已导出")
        } catch (e: Exception) {
            toast("导出失败：${e.message}")
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val count = CsvUtils.importFromCsv(contentResolver, uri, db)
            toast("导入完成：新增/更新 $count 条")
            onDataChanged()
        } catch (e: Exception) {
            toast("导入失败：${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        tvStatsAll = findViewById(R.id.tvStatsAll)
        tvStatsFilter = findViewById(R.id.tvStatsFilter)
        searchInput = findViewById(R.id.searchInput)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerType = findViewById(R.id.spinnerType)
        spinnerSpec = findViewById(R.id.spinnerSpec)
        spinnerLength = findViewById(R.id.spinnerLength)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = PartAdapter(
            onIncrease = { part ->
                db.updateQuantity(part.id, part.quantity + 1)
                onDataChanged()
            },
            onDecrease = { part ->
                if (part.quantity > 0) {
                    db.updateQuantity(part.id, part.quantity - 1)
                    onDataChanged()
                }
            },
            onEdit = { part ->
                val intent = Intent(this, EditPartActivity::class.java)
                    .putExtra(EditPartActivity.EXTRA_ID, part.id)
                editLauncher.launch(intent)
            },
            onDelete = { part ->
                confirmDelete(part)
            }
        )
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val intent = Intent(this, EditPartActivity::class.java)
            editLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnClearFilters).setOnClickListener {
            clearFilters()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isRefreshing) {
                    refreshData()
                }
            }
        })

        setupSpinnerListeners()
        updateSpinnerOptions()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                startExport()
                true
            }
            R.id.action_import -> {
                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupSpinnerListeners() {
        spinnerCategory.onItemSelectedListener = spinnerListener()
        spinnerType.onItemSelectedListener = spinnerListener()
        spinnerSpec.onItemSelectedListener = spinnerListener()
        spinnerLength.onItemSelectedListener = spinnerListener()
    }

    private fun spinnerListener() = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            if (!isRefreshing) {
                updateSpinnerOptions()
                refreshData()
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun clearFilters() {
        isRefreshing = true
        searchInput.setText("")
        spinnerCategory.setSelection(0)
        spinnerType.setSelection(0)
        spinnerSpec.setSelection(0)
        spinnerLength.setSelection(0)
        isRefreshing = false

        updateSpinnerOptions()
        refreshData()
    }

    private fun updateSpinnerOptions() {
        if (isRefreshing) return

        val wantedCategory = currentValue(spinnerCategory)
        val wantedType = currentValue(spinnerType)
        val wantedSpec = currentValue(spinnerSpec)
        val wantedLength = currentValue(spinnerLength)

        isRefreshing = true
        try {
            setSpinnerItems(spinnerCategory, db.getCategories(), wantedCategory)

            val category = currentValue(spinnerCategory)
            setSpinnerItems(spinnerType, db.getTypes(category), wantedType)

            val type = currentValue(spinnerType)
            setSpinnerItems(spinnerSpec, db.getSpecs(category, type), wantedSpec)

            val spec = currentValue(spinnerSpec)
            setSpinnerItems(
                spinnerLength,
                db.getLengths(category, type, spec),
                wantedLength
            )
        } finally {
            isRefreshing = false
        }
    }

    private fun setSpinnerItems(
        spinner: Spinner,
        values: List<String>,
        preferred: String?
    ) {
        val visibleList = listOf("全部") + values
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            visibleList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val targetIndex = if (preferred == null) {
            0
        } else {
            val index = visibleList.indexOf(preferred)
            if (index < 0) 0 else index
        }
        spinner.setSelection(targetIndex)
    }

    private fun currentValue(spinner: Spinner): String? {
        return if (spinner.selectedItemPosition > 0 &&
            spinner.selectedItemPosition < spinner.count
        ) {
            spinner.getItemAtPosition(spinner.selectedItemPosition)?.toString()
        } else {
            null
        }
    }

    private fun currentSearchTerms(): List<String> {
        val raw = searchInput.text.toString()
        return raw.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun refreshData() {
        val parts = db.queryParts(
            category = currentValue(spinnerCategory),
            type = currentValue(spinnerType),
            spec = currentValue(spinnerSpec),
            length = currentValue(spinnerLength),
            searchTerms = currentSearchTerms()
        )

        adapter.submitList(parts)

        val allParts = db.getAllParts()
        tvStatsAll.text = "全部零件：${countKinds(allParts)} 种，${sumQuantity(allParts)} 件"
        tvStatsFilter.text = "当前结果：${countKinds(parts)} 种，${sumQuantity(parts)} 件"
    }

    private fun onDataChanged() {
        updateSpinnerOptions()
        refreshData()
    }

    private fun countKinds(parts: List<Part>): Int {
        return parts.map {
            "${it.category}\u0000${it.type}\u0000${it.spec}\u0000${it.length}"
        }.distinct().count()
    }

    private fun sumQuantity(parts: List<Part>): Long {
        return parts.sumOf { it.quantity.toLong() }
    }

    private fun confirmDelete(part: Part) {
        AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定删除零件“${part.uniqueCode}”吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                db.deletePart(part.id)
                if (part.imageFile.isNotBlank()) {
                    val file = File(filesDir, part.imageFile)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                onDataChanged()
            }
            .show()
    }

    private fun startExport() {
        if (db.getAllParts().isEmpty()) {
            toast("没有可导出的数据")
            return
        }
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        exportLauncher.launch("parts_$time.csv")
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
