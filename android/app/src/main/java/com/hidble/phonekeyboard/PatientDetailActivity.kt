package com.hidble.phonekeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PatientDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PATIENT_ID = "patient_id"

        fun intentFor(context: Context, patientId: Long): Intent =
            Intent(context, PatientDetailActivity::class.java)
                .putExtra(EXTRA_PATIENT_ID, patientId)
    }

    private lateinit var repository: PatientRepository
    private var patientId: Long = 0
    private var patient: Patient? = null

    private lateinit var nameInput: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var ageInput: EditText
    private lateinit var recordsContainer: LinearLayout
    private lateinit var dischargePatientButton: Button
    private var draftCleanerWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_detail)
        repository = PatientRepository(this)
        patientId = intent.getLongExtra(EXTRA_PATIENT_ID, 0L)

        bindViews()
        setupSpinners()
        setupActions()
        loadPatient()
    }

    override fun onResume() {
        super.onResume()
        refreshConnectionChip()
        refreshRecords()
        refreshDischargeButton()
    }

    override fun onDestroy() {
        draftCleanerWebView?.destroy()
        draftCleanerWebView = null
        super.onDestroy()
    }

    private fun bindViews() {
        nameInput = findViewById(R.id.nameInput)
        genderSpinner = findViewById(R.id.genderSpinner)
        ageInput = findViewById(R.id.ageInput)
        recordsContainer = findViewById(R.id.recordsContainer)
        dischargePatientButton = findViewById(R.id.dischargePatientButton)
    }

    private fun setupSpinners() {
        val adapter = ArrayAdapter(
            this,
            R.layout.item_spinner,
            R.id.spinnerText,
            listOf("男", "女", "其他", "未填写")
        )
        adapter.setDropDownViewResource(R.layout.item_spinner)
        genderSpinner.adapter = adapter

    }

    private fun setupActions() {
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.savePatientButton).setOnClickListener { savePatient() }
        dischargePatientButton.setOnClickListener { dischargePatient() }
        findViewById<Button>(R.id.addRecordButton).setOnClickListener {
            if (patientId <= 0) {
                Toast.makeText(this, "请先保存患者基本信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentPatient = patient ?: repository.findPatient(patientId)
            if (currentPatient != null) {
                startActivity(MedicalRecordWorkspaceActivity.intentFor(this, currentPatient))
            }
        }
        findViewById<TextView>(R.id.connectionChip).setOnClickListener {
            startActivity(Intent(this, TerminalSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.deletePatientButton).setOnClickListener {
            if (patientId <= 0) {
                finish()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("删除患者资料？")
                .setMessage("将同时删除该患者的全部本地病例文书，删除后无法在 App 内恢复。")
                .setPositiveButton("确认删除") { _, _ ->
                    repository.deletePatient(patientId)
                    GeneratedDocumentStore.delete(this, patientId)
                    clearWorkspaceDrafts(patientId) {
                        Toast.makeText(this, "患者及全部本地文书已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun clearWorkspaceDrafts(patientId: Long, onFinished: () -> Unit) {
        val webView = WebView(this)
        draftCleanerWebView = webView
        var completed = false
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (completed) return
                completed = true
                val inputKey = "careflow-record-inputs-$patientId"
                val documentsKey = "careflow-record-documents-$patientId"
                view.evaluateJavascript(
                    "localStorage.removeItem('$inputKey');" +
                        "localStorage.removeItem('$documentsKey');'cleared'"
                ) {
                    view.destroy()
                    if (draftCleanerWebView === view) draftCleanerWebView = null
                    onFinished()
                }
            }
        }
        webView.loadUrl(
            "file:///android_asset/medical-record-workspace.html" +
                "?patientId=$patientId"
        )
    }

    private fun loadPatient() {
        patient = if (patientId > 0) repository.findPatient(patientId) else null
        patient?.let { value ->
            nameInput.setText(value.name)
            genderSpinner.setSelection(
                listOf("男", "女", "其他", "未填写")
                    .indexOf(value.gender.ifBlank { "未填写" })
                    .coerceAtLeast(0)
            )
            ageInput.setText(value.ageLabel.removeSuffix("岁"))
            bindHeader(value)
        } ?: run {
            genderSpinner.setSelection(3)
            bindHeader(Patient(name = "新患者"))
        }
        refreshRecords()
        refreshDischargeButton()
    }

    private fun bindHeader(value: Patient) {
        val displayName = value.displayName
        findViewById<TextView>(R.id.detailAvatar).text =
            displayName.firstOrNull()?.toString() ?: "患"
        findViewById<TextView>(R.id.detailName).text = displayName
        findViewById<TextView>(R.id.detailMeta).text = listOf(
            value.gender,
            value.ageLabel
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun savePatient() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            nameInput.error = "请填写患者姓名"
            nameInput.requestFocus()
            return
        }
        val age = ageInput.text.toString().trim()
        val ageValue = age.toIntOrNull()
        if (ageValue == null || ageValue !in 0..130) {
            ageInput.error = "请填写 0–130 岁之间的有效年龄"
            ageInput.requestFocus()
            return
        }
        val existing = patient
        val saved = Patient(
            id = patientId,
            patientNo = existing?.patientNo.orEmpty(),
            name = name,
            gender = genderSpinner.selectedItem?.toString().orEmpty(),
            ageText = "${ageValue}岁",
            birthDate = existing?.birthDate.orEmpty(),
            phone = existing?.phone.orEmpty(),
            ward = existing?.ward.orEmpty(),
            bedNo = existing?.bedNo.orEmpty(),
            admissionDate = existing?.admissionDate.orEmpty(),
            diagnosis = existing?.diagnosis.orEmpty(),
            allergy = existing?.allergy.orEmpty(),
            status = existing?.status.orEmpty().ifBlank { "在院" },
            attending = existing?.attending.orEmpty(),
            department = existing?.department.orEmpty(),
            createdAt = existing?.createdAt ?: 0
        )
        patientId = repository.savePatient(saved)
        patient = repository.findPatient(patientId)
        patient?.let { bindHeader(it) }
        Toast.makeText(this, "患者资料已保存", Toast.LENGTH_SHORT).show()
        refreshRecords()
        refreshDischargeButton()
    }

    private fun refreshDischargeButton() {
        val current = patient
        when {
            patientId <= 0 || current == null -> {
                dischargePatientButton.text = "请先保存患者资料"
                dischargePatientButton.isEnabled = false
            }
            current.status == "已出院" -> {
                dischargePatientButton.text = "已出院"
                dischargePatientButton.isEnabled = false
            }
            else -> {
                dischargePatientButton.text = "办理出院"
                dischargePatientButton.isEnabled = true
            }
        }
    }

    private fun dischargePatient() {
        val current = patient ?: repository.findPatient(patientId)
        if (patientId <= 0 || current == null) {
            Toast.makeText(this, "请先保存患者资料", Toast.LENGTH_SHORT).show()
            return
        }
        if (current.status == "已出院") {
            Toast.makeText(this, "该患者已经办理出院", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("办理出院？")
            .setMessage("办理后患者状态将改为“已出院”，并计入出院病例统计。患者和已保存文书仍会保留。")
            .setPositiveButton("确认出院") { _, _ ->
                repository.savePatient(current.copy(status = "已出院"))
                patient = repository.findPatient(patientId)
                patient?.let { bindHeader(it) }
                refreshDischargeButton()
                Toast.makeText(this, "患者已办理出院", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshRecords() {
        if (!::recordsContainer.isInitialized) return
        refreshConnectionChip()
        recordsContainer.removeAllViews()
        if (patientId <= 0) {
            findViewById<TextView>(R.id.recordCountLabel).text = "保存患者后可新建文书"
            findViewById<TextView>(R.id.noRecordsText).visibility = LinearLayout.VISIBLE
            return
        }
        val records = GeneratedDocumentStore.list(this, patientId)
        findViewById<TextView>(R.id.recordCountLabel).text = "${records.size} 份记录"
        findViewById<TextView>(R.id.noRecordsText).visibility =
            if (records.isEmpty()) LinearLayout.VISIBLE else LinearLayout.GONE
        val inflater = LayoutInflater.from(this)
        records.forEach { record ->
            val item = inflater.inflate(R.layout.item_clinical_record, recordsContainer, false)
            item.findViewById<TextView>(R.id.recordType).text = outputTitle(record.type)
            item.findViewById<TextView>(R.id.recordTime).text =
                android.text.format.DateFormat.format("MM-dd HH:mm", record.updatedAt).toString()
            item.findViewById<TextView>(R.id.recordTitle).text =
                record.title.ifBlank { outputTitle(record.type) }
            item.findViewById<TextView>(R.id.recordPreview).text =
                record.preview.ifBlank { "已完成病历，点击进入查看" }
            item.setOnClickListener {
                val currentPatient = patient ?: repository.findPatient(patientId) ?: return@setOnClickListener
                startActivity(
                    MedicalRecordWorkspaceActivity.intentFor(
                        this,
                        currentPatient,
                        record.type,
                        record.id
                    )
                )
            }
            item.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("删除这份文书？")
                    .setMessage(record.title.ifBlank { outputTitle(record.type) })
                    .setPositiveButton("删除") { _, _ ->
                        GeneratedDocumentStore.deleteOne(this, patientId, record.id)
                        refreshRecords()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
            recordsContainer.addView(item)
        }
    }

    private fun outputTitle(type: String): String = when (type) {
        "admission" -> "入院记录"
        "first" -> "首次病程记录"
        "progress" -> "普通病程记录"
        "preop-discussion" -> "术前讨论"
        "preop-summary" -> "术前小结"
        "operation" -> "手术记录"
        "consent" -> "手术同意书"
        "discharge" -> "出院小结"
        "difficult" -> "疑难病例讨论"
        else -> type
    }

    private fun refreshConnectionChip() {
        val terminal = OutputRouter.selected(this)
        val connected = OutputRouter.isConnected(this, terminal)
        val chip = findViewById<TextView>(R.id.connectionChip)
        when {
            connected -> {
                chip.text = "● ${terminal.displayName}"
                chip.setBackgroundResource(R.drawable.bg_status_chip)
                chip.setTextColor(ContextCompat.getColor(this, R.color.clinical_teal))
            }
            else -> {
                chip.text = "○ ${terminal.displayName}"
                chip.setBackgroundResource(R.drawable.bg_warning_chip)
                chip.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
            }
        }
    }
}
