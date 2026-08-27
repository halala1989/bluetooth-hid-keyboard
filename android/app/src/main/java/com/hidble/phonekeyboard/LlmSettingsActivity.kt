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

/**
 * 模型设置二级页面：选择提供方 -> 自动填入预设模型 -> 只填 Token 即可使用。
 */
class LlmSettingsActivity : AppCompatActivity() {

    private lateinit var providerSpinner: Spinner
    private lateinit var tokenInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var baseUrlText: TextView
    private lateinit var testButton: Button
    private lateinit var saveButton: Button
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_settings)

        prefs = getSharedPreferences(LlmPrefs.PREFS_NAME, Context.MODE_PRIVATE)

        providerSpinner = findViewById(R.id.llmProviderSpinner)
        tokenInput = findViewById(R.id.llmTokenInput)
        modelInput = findViewById(R.id.llmModelInput)
        baseUrlText = findViewById(R.id.llmBaseUrlText)
        testButton = findViewById(R.id.llmTestButton)
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
        tokenInput.setText(prefs.getString(LlmPrefs.KEY_API_KEY, "") ?: "")
        modelInput.setText(prefs.getString(LlmPrefs.KEY_MODEL, "") ?: "")

        // 首次进入：保留已保存的模型，没有则用预设
        refreshPreset(overwriteModel = false)

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshPreset(overwriteModel = true)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        testButton.setOnClickListener { testConnection() }
        saveButton.setOnClickListener { saveAndFinish() }
    }

    private fun currentProvider(): LlmProvider = LlmProviders.list[providerSpinner.selectedItemPosition]

    private fun refreshPreset(overwriteModel: Boolean) {
        val p = currentProvider()
        baseUrlText.text = "API 地址：${p.baseUrl.trimEnd('/')}/chat/completions"
        if (overwriteModel || modelInput.text.isBlank()) {
            modelInput.setText(p.defaultModel)
        }
        tokenInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        tokenInput.hint = "sk-..."
    }

    private fun testConnection() {
        val p = currentProvider()
        val token = tokenInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        if (model.isEmpty()) {
            Toast.makeText(this, "请先填写模型名", Toast.LENGTH_SHORT).show()
            return
        }
        if (token.isEmpty()) {
            Toast.makeText(this, "请填写 API Token", Toast.LENGTH_SHORT).show()
            return
        }
        testButton.isEnabled = false
        testButton.text = "测试中..."
        Toast.makeText(this, "正在测试连接...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val ok = try {
                LlmClient.chat(p, token, model, listOf("user" to "请只回复：OK"))
                true
            } catch (e: Exception) {
                Toast.makeText(this@LlmSettingsActivity, "连接失败：${e.message}", Toast.LENGTH_LONG).show()
                false
            }
            if (ok) {
                Toast.makeText(this@LlmSettingsActivity, "连接成功", Toast.LENGTH_LONG).show()
            }
            testButton.isEnabled = true
            testButton.text = "测试连接"
        }
    }

    private fun saveAndFinish() {
        val p = currentProvider()
        val token = tokenInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, "请填写 API Token", Toast.LENGTH_SHORT).show()
            return
        }
        if (model.isEmpty()) {
            Toast.makeText(this, "请填写模型名", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit()
            .putString(LlmPrefs.KEY_PROVIDER, p.id)
            .putString(LlmPrefs.KEY_API_KEY, token)
            .putString(LlmPrefs.KEY_MODEL, model)
            .apply()
        Toast.makeText(this, "已保存：${p.displayName}（$model）", Toast.LENGTH_SHORT).show()
        finish()
    }
}
