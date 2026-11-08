package com.hidble.phonekeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteEditorActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PATIENT_ID = "patient_id"
        private const val EXTRA_RECORD_ID = "record_id"
        private const val EXTRA_RECORD_TYPE = "record_type"

        fun intentForNew(context: Context, patientId: Long, type: String): Intent =
            Intent(context, NoteEditorActivity::class.java)
                .putExtra(EXTRA_PATIENT_ID, patientId)
                .putExtra(EXTRA_RECORD_TYPE, type)

        fun intentFor(context: Context, patientId: Long, recordId: Long): Intent =
            Intent(context, NoteEditorActivity::class.java)
                .putExtra(EXTRA_PATIENT_ID, patientId)
                .putExtra(EXTRA_RECORD_ID, recordId)
    }

    private lateinit var repository: PatientRepository
    private var patientId: Long = 0
    private var recordId: Long = 0
    private var patient: Patient? = null
    private var sourceRecord: ClinicalRecord? = null

    private lateinit var typeSpinner: Spinner
    private lateinit var titleInput: EditText
    private lateinit var contentInput: EditText
    private lateinit var characterCount: TextView
    private lateinit var sendButton: Button

    private val recordTypes = listOf(
        "入院记录",
        "首次病程",
        "日常病程",
        "查房记录",
        "会诊记录",
        "操作记录",
        "出院小结"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)
        KeyboardRuntime.initialize(this)
        repository = PatientRepository(this)
        patientId = intent.getLongExtra(EXTRA_PATIENT_ID, 0L)
        recordId = intent.getLongExtra(EXTRA_RECORD_ID, 0L)
        patient = repository.findPatient(patientId)

        typeSpinner = findViewById(R.id.recordTypeSpinner)
        titleInput = findViewById(R.id.recordTitleInput)
        contentInput = findViewById(R.id.contentInput)
        characterCount = findViewById(R.id.characterCount)
        sendButton = findViewById(R.id.sendToComputerButton)

        setupSpinner()
        bindPatient()
        loadRecord()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        refreshConnectionChip()
    }

    override fun onPause() {
        if (hasMeaningfulContent()) saveDraft(showToast = false)
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            this,
            R.layout.item_spinner,
            R.id.spinnerText,
            recordTypes
        )
        adapter.setDropDownViewResource(R.layout.item_spinner)
        typeSpinner.adapter = adapter
        val requestedType = intent.getStringExtra(EXTRA_RECORD_TYPE) ?: "日常病程"
        typeSpinner.setSelection(recordTypes.indexOf(requestedType).coerceAtLeast(2))
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (titleInput.text.isNullOrBlank()) {
                    titleInput.setText(defaultTitle(recordTypes[position]))
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun bindPatient() {
        val value = patient
        val name = value?.displayName ?: "患者"
        findViewById<TextView>(R.id.patientAvatar).text =
            name.firstOrNull()?.toString() ?: "患"
        findViewById<TextView>(R.id.patientName).text = name
        findViewById<TextView>(R.id.patientMeta).text = listOf(
            value?.ward?.ifBlank { "病区未录入" },
            "${value?.bedNo?.ifBlank { "--" } ?: "--"}床",
            value?.diagnosis?.ifBlank { "入院诊断未录入" }
        ).joinToString(" · ")
    }

    private fun loadRecord() {
        sourceRecord = if (recordId > 0) repository.findRecord(recordId) else null
        sourceRecord?.let { record ->
            val index = recordTypes.indexOf(record.type).coerceAtLeast(0)
            typeSpinner.setSelection(index)
            titleInput.setText(record.title)
            contentInput.setText(record.content)
        } ?: run {
            titleInput.setText(defaultTitle(typeSpinner.selectedItem?.toString().orEmpty()))
        }
    }

    private fun setupActions() {
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.saveDraftButton).setOnClickListener {
            saveDraft(showToast = true)
        }
        findViewById<Button>(R.id.templateBasicButton).setOnClickListener {
            insertTemplate("basic")
        }
        findViewById<Button>(R.id.templateSoapButton).setOnClickListener {
            insertTemplate("soap")
        }
        findViewById<Button>(R.id.templateDischargeButton).setOnClickListener {
            insertTemplate("discharge")
        }
        findViewById<TextView>(R.id.connectionChip).setOnClickListener {
            startActivity(Intent(this, TerminalSettingsActivity::class.java))
        }
        sendButton.setOnClickListener { confirmOutput() }
        contentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                characterCount.text = "${s?.length ?: 0} 字"
            }
        })
        characterCount.text = "${contentInput.length()} 字"
    }

    private fun defaultTitle(type: String): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        return when (type) {
            "入院记录" -> "$now 入院记录"
            "首次病程" -> "$now 首次病程记录"
            "日常病程" -> "$now 日常病程记录"
            "查房记录" -> "$now 查房记录"
            "会诊记录" -> "$now 会诊记录"
            "操作记录" -> "$now 操作记录"
            "出院小结" -> "$now 出院小结"
            else -> "$now $type"
        }
    }

    private fun insertTemplate(kind: String) {
        val name = patient?.displayName ?: "患者"
        val diagnosis = patient?.diagnosis?.ifBlank { "（待填写）" } ?: "（待填写）"
        val today = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val template = when (kind) {
            "soap" -> """
                【SOAP 病程记录】
                S（主观）：$name 今日主诉：
                O（客观）：生命体征：T ___℃，P ___次/分，R ___次/分，BP ___/___mmHg；查体：
                A（评估）：目前诊断：$diagnosis；病情变化与评估：
                P（计划）：检查计划：
                治疗计划：
                沟通与注意事项：
                记录时间：$today
            """.trimIndent()
            "discharge" -> """
                【出院小结草稿】
                患者：$name
                入院诊断：$diagnosis
                入院日期：${patient?.admissionDate.orEmpty().ifBlank { "（待填写）" }}
                出院日期：${SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())}
                诊疗经过：
                出院诊断：
                出院情况：
                出院医嘱：
                复诊建议：
                医师签名：${patient?.attending.orEmpty().ifBlank { "（待填写）" }}
            """.trimIndent()
            else -> """
                【病程记录】
                记录时间：$today
                主诉变化：
                现病史/病情变化：
                生命体征：T ___℃，P ___次/分，R ___次/分，BP ___/___mmHg
                查体：
                辅助检查：
                目前诊断：$diagnosis
                分析评估：
                诊疗计划：
                医患沟通及注意事项：
            """.trimIndent()
        }
        val existing = contentInput.text.toString()
        if (existing.isBlank()) {
            contentInput.setText(template)
        } else {
            contentInput.setText("$existing\n\n$template")
        }
        contentInput.setSelection(contentInput.length())
    }

    private fun hasMeaningfulContent(): Boolean =
        contentInput.text.toString().isNotBlank() || titleInput.text.toString().isNotBlank()

    private fun saveDraft(showToast: Boolean): Long {
        if (patientId <= 0) return 0
        val record = ClinicalRecord(
            id = recordId,
            patientId = patientId,
            type = typeSpinner.selectedItem?.toString().orEmpty().ifBlank { "日常病程" },
            title = titleInput.text.toString().trim().ifBlank {
                defaultTitle(typeSpinner.selectedItem?.toString().orEmpty())
            },
            content = contentInput.text.toString(),
            createdAt = sourceRecord?.createdAt ?: 0
        )
        recordId = repository.saveRecord(record)
        sourceRecord = repository.findRecord(recordId)
        if (showToast) Toast.makeText(this, "草稿已保存", Toast.LENGTH_SHORT).show()
        return recordId
    }

    private fun confirmOutput() {
        val terminal = OutputRouter.selected(this)
        if (!OutputRouter.isConnected(this, terminal)) {
            AlertDialog.Builder(this)
                .setTitle("${terminal.displayName} 尚未连接")
                .setMessage("请先在键盘与模型设置中选择并连接输出终端。病例草稿已经保存在本机。")
                .setPositiveButton("去连接") { _, _ ->
                    startActivity(Intent(this, TerminalSettingsActivity::class.java))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("通过 ${terminal.displayName} 发送")
            .setMessage("请确认目标电脑已打开正确的输入窗口。发送后内容会像键盘输入一样写入当前光标位置。")
            .setPositiveButton("开始发送") { _, _ -> sendCurrentRecord() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendCurrentRecord() {
        val content = contentInput.text.toString()
        if (content.isBlank()) {
            Toast.makeText(this, "病例正文为空", Toast.LENGTH_SHORT).show()
            return
        }
        saveDraft(showToast = false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sendButton.isEnabled = false
        sendButton.text = "正在发送…"
        OutputRouter.sendText(this, content) { success, message ->
            runOnUiThread {
                KeyboardRuntime.releaseAll()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                sendButton.isEnabled = true
                sendButton.text = "发送到电脑"
                Toast.makeText(
                    this,
                    if (success) "病例已发送到电脑" else message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshConnectionChip() {
        val terminal = OutputRouter.selected(this)
        val connected = OutputRouter.isConnected(this, terminal)
        val chip = findViewById<TextView>(R.id.connectionChip)
        if (connected) {
            chip.text = "● ${terminal.displayName}"
            chip.setBackgroundResource(R.drawable.bg_status_chip)
            chip.setTextColor(ContextCompat.getColor(this, R.color.clinical_teal))
        } else {
            chip.text = "○ ${terminal.displayName}"
            chip.setBackgroundResource(R.drawable.bg_warning_chip)
            chip.setTextColor(ContextCompat.getColor(this, R.color.clinical_warning))
        }
    }
}
