package com.hidble.phonekeyboard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PatientListActivity : AppCompatActivity() {

    private lateinit var repository: PatientRepository
    private lateinit var searchInput: EditText
    private lateinit var patientListContainer: LinearLayout
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var loadDemoButton: Button
    private lateinit var settingsButton: Button
    private lateinit var patientScroll: android.widget.ScrollView

    private val patients = mutableListOf<Patient>()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_list)
        KeyboardRuntime.initialize(this)
        repository = PatientRepository(this)

        searchInput = findViewById(R.id.patientSearch)
        patientScroll = findViewById(R.id.patientScroll)
        patientListContainer = findViewById(R.id.patientListContainer)
        emptyState = findViewById(R.id.emptyState)
        emptyTitle = findViewById(R.id.emptyTitleText)
        emptyDescription = findViewById(R.id.emptyDescriptionText)
        loadDemoButton = findViewById(R.id.loadDemoButton)
        settingsButton = findViewById(R.id.settingsButton)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty()
                renderPatients()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        findViewById<Button>(R.id.addPatientButton).setOnClickListener {
            startActivity(PatientDetailActivity.intentFor(this, 0))
        }
        loadDemoButton.setOnClickListener {
            repository.addDemoPatients()
            refresh()
            Toast.makeText(this, "已载入演示患者，仅用于体验界面", Toast.LENGTH_LONG).show()
        }
        settingsButton.setOnClickListener { openSettings() }
        findViewById<Button>(R.id.writingRulesButton).setOnClickListener {
            startActivity(Intent(this, WritingRulesActivity::class.java))
        }
        findViewById<Button>(R.id.navPatientsButton).setOnClickListener {
            patientScroll.smoothScrollTo(0, 0)
        }
        findViewById<Button>(R.id.navRecordsButton).setOnClickListener { showRecentRecords() }
        findViewById<Button>(R.id.navOutputButton).setOnClickListener { openSettings() }
        findViewById<Button>(R.id.navKeyboardButton).setOnClickListener { openKeyboardTool() }

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        findViewById<TextView>(R.id.heroTitle).text = when (hour) {
            in 5..10 -> "早上好，医生"
            in 11..13 -> "中午好，医生"
            in 14..18 -> "下午好，医生"
            else -> "晚上好，医生"
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        patients.clear()
        patients += repository.listPatients()
        val overview = repository.overview()
        findViewById<TextView>(R.id.statTotal).text = overview.total.toString()
        findViewById<TextView>(R.id.statInHospital).text = overview.inHospital.toString()
        findViewById<TextView>(R.id.statAttention).text = overview.needsAttention.toString()
        findViewById<TextView>(R.id.statRecordsToday).text = overview.recordsToday.toString()

        renderPatients()
    }

    private fun renderPatients() {
        val filtered = patients.filter { patient ->
            query.isBlank() || listOf(
                patient.name,
                patient.gender,
                patient.ageLabel
            ).any { it.contains(query, ignoreCase = true) }
        }

        patientListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        filtered.forEach { patient ->
            val item = inflater.inflate(R.layout.item_patient, patientListContainer, false)
            item.findViewById<TextView>(R.id.patientAvatar).text =
                patient.displayName.firstOrNull()?.toString() ?: "患"
            item.findViewById<TextView>(R.id.patientName).text = patient.displayName
            item.findViewById<TextView>(R.id.patientDemographics).text =
                listOf(patient.gender, patient.ageLabel).filter { it.isNotBlank() }.joinToString(" · ")
            item.findViewById<TextView>(R.id.patientLocation).text =
                listOf(
                    patient.ward.ifBlank { "病区未录入" },
                    "${patient.bedNo.ifBlank { "--" }}床",
                    "病案号 ${patient.patientNo.ifBlank { "未录入" }}"
                ).joinToString(" · ")
            item.findViewById<TextView>(R.id.patientLocation).visibility = View.GONE
            item.findViewById<TextView>(R.id.patientDiagnosis).visibility = View.GONE
            item.findViewById<TextView>(R.id.patientAllergy).visibility = View.GONE
            item.findViewById<TextView>(R.id.patientRecordCount).text =
                "${repository.recordCount(patient.id)} 份文书"

            val status = item.findViewById<TextView>(R.id.patientStatus)
            status.text = patient.status.ifBlank { "在院" }
            val (background, textColor) = when (patient.status) {
                "已出院" -> R.drawable.bg_warning_chip to R.color.clinical_warning
                "待出院" -> R.drawable.bg_danger_chip to R.color.clinical_danger
                else -> R.drawable.bg_status_chip to R.color.clinical_teal
            }
            status.setBackgroundResource(background)
            status.setTextColor(ContextCompat.getColor(this, textColor))
            item.setOnClickListener {
                startActivity(PatientDetailActivity.intentFor(this, patient.id))
            }
            patientListContainer.addView(item)
        }

        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (patients.isEmpty()) {
            emptyTitle.text = "还没有患者记录"
            emptyDescription.text = "从新增患者开始建立住院资料和病例文书。\n也可以先载入演示数据体验流程。"
            loadDemoButton.visibility = View.VISIBLE
        } else {
            emptyTitle.text = "没有匹配的患者"
            emptyDescription.text = "换一个姓名、床号或诊断关键词试试。"
            loadDemoButton.visibility = View.GONE
        }
    }

    private fun showRecentRecords() {
        val records = repository.recentRecords()
        if (records.isEmpty()) {
            Toast.makeText(this, "暂无病例文书", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = records.map { (patient, record) ->
            "${patient.displayName} · ${record.title.ifBlank { record.type }}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("最近病例文书")
            .setItems(labels) { _, index ->
                val (patient, record) = records[index]
                startActivity(NoteEditorActivity.intentFor(this, patient.id, record.id))
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun openKeyboardTool() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, TerminalSettingsActivity::class.java))
    }
}
