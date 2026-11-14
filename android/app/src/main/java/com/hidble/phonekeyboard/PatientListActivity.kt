package com.hidble.phonekeyboard

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

class PatientListActivity : AppCompatActivity() {

    private lateinit var repository: PatientRepository
    private lateinit var searchInput: EditText
    private lateinit var patientListContainer: LinearLayout
    private lateinit var emptyState: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var loadDemoButton: Button
    private lateinit var patientScroll: android.widget.ScrollView
    private lateinit var connectionStatusDot: View
    private lateinit var connectionStatusText: TextView
    private lateinit var connectionStatusDetail: TextView
    private lateinit var patientCountChip: TextView

    private val patients = mutableListOf<Patient>()
    private var query = ""
    private val statusHandler = Handler(Looper.getMainLooper())
    private var draftCleanerWebView: WebView? = null
    private val refreshConnection = object : Runnable {
        override fun run() {
            refreshConnectionStatus()
            statusHandler.postDelayed(this, 1_200L)
        }
    }

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
        connectionStatusDot = findViewById(R.id.connectionStatusDot)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        connectionStatusDetail = findViewById(R.id.connectionStatusDetail)
        patientCountChip = findViewById(R.id.patientCountChip)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty()
                renderPatients()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        findViewById<View>(R.id.addPatientButton).setOnClickListener {
            startActivity(PatientDetailActivity.intentFor(this, 0L))
        }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.connectionStatusButton).setOnLongClickListener {
            openConnectionSettings()
            true
        }
        loadDemoButton.setOnClickListener {
            repository.addDemoPatients()
            refresh()
            Toast.makeText(this, "已载入演示患者，仅用于体验界面", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        statusHandler.removeCallbacks(refreshConnection)
        statusHandler.post(refreshConnection)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(refreshConnection)
        super.onPause()
    }

    override fun onDestroy() {
        statusHandler.removeCallbacksAndMessages(null)
        draftCleanerWebView?.destroy()
        draftCleanerWebView = null
        super.onDestroy()
    }

    private fun refresh() {
        patients.clear()
        patients += repository.listPatients()
        patientCountChip.text = "● ${patients.size} 位"
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
                listOf(patient.gender, patient.ageLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "资料待完善" }
            item.findViewById<TextView>(R.id.patientRecordCount).text =
                "${GeneratedDocumentStore.list(this, patient.id).size} 份文书"

            val status = item.findViewById<TextView>(R.id.patientStatus)
            status.text = patient.status.ifBlank { "在院" }
            val (background, textColor) = when (patient.status) {
                "已出院" -> R.drawable.bg_warning_chip to R.color.clinical_warning
                "待出院" -> R.drawable.bg_danger_chip to R.color.clinical_danger
                else -> R.drawable.bg_status_chip to R.color.clinical_teal
            }
            status.setBackgroundResource(background)
            status.setTextColor(ContextCompat.getColor(this, textColor))

            item.setOnClickListener { openWorkspace(patient.id) }
            item.setOnLongClickListener {
                showPatientActions(patient)
                true
            }
            patientListContainer.addView(item)
        }

        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (patients.isEmpty()) {
            emptyTitle.text = "还没有患者记录"
            emptyDescription.text = "点击右下角加号新建患者。"
            loadDemoButton.visibility = View.VISIBLE
        } else {
            emptyTitle.text = "没有匹配的患者"
            emptyDescription.text = "换一个姓名、性别或年龄试试。"
            loadDemoButton.visibility = View.GONE
        }
        if (filtered.isEmpty()) patientScroll.smoothScrollTo(0, 0)
    }

    private fun openWorkspace(patientId: Long) {
        val patient = repository.findPatient(patientId)
        if (patient == null) {
            Toast.makeText(this, "患者资料不存在，请刷新后重试", Toast.LENGTH_SHORT).show()
            refresh()
            return
        }
        startActivity(MedicalRecordWorkspaceActivity.intentFor(this, patient))
    }

    private fun showPatientActions(patient: Patient) {
        val dialog = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.dialog_patient_actions, null)
        content.findViewById<TextView>(R.id.patientActionsTitle).text = patient.displayName
        content.findViewById<Button>(R.id.editPatientAction).setOnClickListener {
            dialog.dismiss()
            startActivity(PatientDetailActivity.intentFor(this, patient.id))
        }
        content.findViewById<Button>(R.id.deletePatientAction).setOnClickListener {
            dialog.dismiss()
            confirmDeletePatient(patient)
        }
        content.findViewById<Button>(R.id.cancelPatientAction).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(content)
        dialog.show()
    }

    private fun confirmDeletePatient(patient: Patient) {
        AlertDialog.Builder(this)
            .setTitle("彻底删除 ${patient.displayName}？")
            .setMessage("该患者的全部基本信息、已保存病例文书和本机工作台草稿都会被删除，且无法在 App 内恢复。")
            .setPositiveButton("彻底删除") { _, _ -> deletePatientCompletely(patient.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deletePatientCompletely(patientId: Long) {
        repository.deletePatient(patientId)
        GeneratedDocumentStore.delete(this, patientId)
        patients.removeAll { it.id == patientId }
        patientCountChip.text = "● ${patients.size} 位"
        renderPatients()
        clearWorkspaceDrafts(patientId) {
            refresh()
            Toast.makeText(this, "患者及全部本机资料已删除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearWorkspaceDrafts(patientId: Long, onFinished: () -> Unit) {
        val webView = WebView(this)
        draftCleanerWebView = webView
        var completed = false
        val finish = {
            if (!completed) {
                completed = true
                webView.destroy()
                if (draftCleanerWebView === webView) draftCleanerWebView = null
                onFinished()
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (completed) return
                val inputKey = "careflow-record-inputs-$patientId"
                val documentsKey = "careflow-record-documents-$patientId"
                view.evaluateJavascript(
                    "localStorage.removeItem('$inputKey');" +
                        "localStorage.removeItem('$documentsKey');'cleared'"
                ) { finish() }
            }

            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                finish()
            }
        }
        statusHandler.postDelayed(finish, 2_500L)
        webView.loadUrl(
            "file:///android_asset/medical-record-workspace.html?patientId=$patientId"
        )
    }

    private fun refreshConnectionStatus() {
        val terminal = OutputRouter.selected(this)
        val connected = OutputRouter.isConnected(this, terminal)
        val color = ContextCompat.getColor(
            this,
            if (connected) R.color.connected else R.color.disconnected
        )
        connectionStatusDot.backgroundTintList = ColorStateList.valueOf(color)
        connectionStatusText.text = if (connected) "已连接" else "未连接"
        connectionStatusDetail.text = terminal.displayName.ifBlank { "长按配置" }
    }

    private fun openConnectionSettings() {
        startActivity(Intent(this, ConnectionActivity::class.java))
    }
}
