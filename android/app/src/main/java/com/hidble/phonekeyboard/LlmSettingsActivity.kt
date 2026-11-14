package com.hidble.phonekeyboard

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LlmSettingsActivity : AppCompatActivity() {

    private lateinit var providerSpinner: Spinner
    private lateinit var tokenInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var baseUrlText: TextView
    private lateinit var providerHint: TextView
    private lateinit var modelListStatus: TextView
    private lateinit var testButton: Button
    private lateinit var fetchModelsButton: Button
    private lateinit var saveButton: Button
    private lateinit var prefs: android.content.SharedPreferences
    private var selectingFetchedModel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_settings)

        prefs = getSharedPreferences(LlmPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        providerSpinner = findViewById(R.id.llmProviderSpinner)
        tokenInput = findViewById(R.id.llmTokenInput)
        modelInput = findViewById(R.id.llmModelInput)
        modelSpinner = findViewById(R.id.llmModelSpinner)
        baseUrlText = findViewById(R.id.llmBaseUrlText)
        providerHint = findViewById(R.id.llmProviderHint)
        modelListStatus = findViewById(R.id.llmModelListStatus)
        testButton = findViewById(R.id.llmTestButton)
        fetchModelsButton = findViewById(R.id.llmFetchModelsButton)
        saveButton = findViewById(R.id.llmSaveButton)

        val providers = LlmProviders.list
        providerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            providers.map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val savedProvider = prefs.getString(LlmPrefs.KEY_PROVIDER, null)
        providerSpinner.setSelection(LlmProviders.indexOf(savedProvider ?: providers.first().id))
        tokenInput.setText(prefs.getString(LlmPrefs.KEY_API_KEY, "").orEmpty())
        modelInput.setText(prefs.getString(LlmPrefs.KEY_MODEL, "").orEmpty())
        refreshPreset(overwriteModel = false)

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshPreset(overwriteModel = true)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!selectingFetchedModel) {
                    val model = parent?.selectedItem?.toString().orEmpty()
                    if (model.isNotBlank()) modelInput.setText(model)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.llmBackButton).setOnClickListener { finish() }
        fetchModelsButton.setOnClickListener { fetchModels() }
        testButton.setOnClickListener { testConnection() }
        saveButton.setOnClickListener { saveAndFinish() }
    }

    private fun currentProvider(): LlmProvider = LlmProviders.list[providerSpinner.selectedItemPosition]

    private fun refreshPreset(overwriteModel: Boolean) {
        val provider = currentProvider()
        baseUrlText.text = "接口：${provider.baseUrl.trimEnd('/')}"
        if (overwriteModel || modelInput.text.isBlank()) modelInput.setText(provider.defaultModel)
        providerHint.text = provider.hint
        providerHint.visibility = if (provider.hint.isBlank()) View.GONE else View.VISIBLE
        tokenInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        modelSpinner.visibility = View.GONE
        modelListStatus.text = "尚未获取"
        modelListStatus.setTextColor(getColor(R.color.text_hint))
    }

    private fun fetchModels() {
        val provider = currentProvider()
        val key = tokenInput.text.toString().trim()
        if (key.isBlank()) {
            Toast.makeText(this, "请先填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        fetchModelsButton.isEnabled = false
        fetchModelsButton.text = "获取中..."
        modelListStatus.text = "正在连接 ${provider.displayName}…"
        modelListStatus.setTextColor(getColor(R.color.accent))
        lifecycleScope.launch {
            runCatching { LlmClient.listModels(provider, key) }
                .onSuccess { models ->
                    if (models.isEmpty()) {
                        modelSpinner.visibility = View.GONE
                        modelListStatus.text = "接口未返回模型，可直接填写模型 ID"
                        modelListStatus.setTextColor(getColor(R.color.clinical_warning))
                    } else {
                        selectingFetchedModel = true
                        modelSpinner.adapter = ArrayAdapter(
                            this@LlmSettingsActivity,
                            android.R.layout.simple_spinner_item,
                            models
                        ).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        val current = modelInput.text.toString().trim()
                        val index = models.indexOf(current)
                        if (index >= 0) modelSpinner.setSelection(index)
                        selectingFetchedModel = false
                        modelSpinner.visibility = View.VISIBLE
                        modelListStatus.text = "已获取 ${models.size} 个模型，点击选择"
                        modelListStatus.setTextColor(getColor(R.color.clinical_teal))
                    }
                }
                .onFailure {
                    modelSpinner.visibility = View.GONE
                    modelListStatus.text = it.message ?: "获取失败"
                    modelListStatus.setTextColor(getColor(R.color.clinical_danger))
                }
            fetchModelsButton.isEnabled = true
            fetchModelsButton.text = "获取模型列表"
        }
    }

    private fun testConnection() {
        val provider = currentProvider()
        val key = tokenInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        if (model.isBlank()) {
            Toast.makeText(this, "请先选择或填写模型", Toast.LENGTH_SHORT).show()
            return
        }
        if (key.isBlank()) {
            Toast.makeText(this, "请填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        testButton.isEnabled = false
        testButton.text = "测试中..."
        lifecycleScope.launch {
            val ok = runCatching {
                LlmClient.chat(provider, key, model, listOf("user" to "请只回复：OK"))
            }.onFailure {
                Toast.makeText(this@LlmSettingsActivity, "连接失败：${it.message}", Toast.LENGTH_LONG).show()
            }.isSuccess
            if (ok) Toast.makeText(this@LlmSettingsActivity, "连接成功", Toast.LENGTH_LONG).show()
            testButton.isEnabled = true
            testButton.text = "测试模型连接"
        }
    }

    private fun saveAndFinish() {
        val provider = currentProvider()
        val key = tokenInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        if (key.isBlank()) {
            tokenInput.error = "请填写 API Key"
            tokenInput.requestFocus()
            return
        }
        if (model.isBlank()) {
            modelInput.error = "请选择或填写模型"
            modelInput.requestFocus()
            return
        }
        prefs.edit()
            .putString(LlmPrefs.KEY_PROVIDER, provider.id)
            .putString(LlmPrefs.KEY_API_KEY, key)
            .putString(LlmPrefs.KEY_MODEL, model)
            .apply()
        Toast.makeText(this, "已保存：${provider.displayName}（$model）", Toast.LENGTH_SHORT).show()
        finish()
    }
}
