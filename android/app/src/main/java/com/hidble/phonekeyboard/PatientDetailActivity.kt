package com.hidble.phonekeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** 新建或编辑患者的三字段表单。 */
class PatientDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PATIENT_ID = "patient_id"

        fun intentFor(context: Context, patientId: Long): Intent =
            Intent(context, PatientDetailActivity::class.java)
                .putExtra(EXTRA_PATIENT_ID, patientId)
    }

    private lateinit var repository: PatientRepository
    private lateinit var nameInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var genderGroup: RadioGroup
    private var patientId: Long = 0L
    private var existingPatient: Patient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_detail)
        repository = PatientRepository(this)
        patientId = intent.getLongExtra(EXTRA_PATIENT_ID, 0L)
        existingPatient = if (patientId > 0) repository.findPatient(patientId) else null

        nameInput = findViewById(R.id.nameInput)
        ageInput = findViewById(R.id.ageInput)
        genderGroup = findViewById(R.id.genderGroup)
        findViewById<TextView>(R.id.formTitle).text =
            if (patientId > 0) "编辑基本信息" else "新建患者"

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.cancelPatientButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.savePatientButton).setOnClickListener { savePatient() }
        prefillPatient()
    }

    private fun prefillPatient() {
        val patient = existingPatient ?: return
        nameInput.setText(patient.name)
        ageInput.setText(patient.ageLabel.removeSuffix("岁"))
        when (patient.gender) {
            "男" -> genderGroup.check(R.id.genderMale)
            "女" -> genderGroup.check(R.id.genderFemale)
            "其他" -> genderGroup.check(R.id.genderOther)
        }
    }

    private fun selectedGender(): String = when (genderGroup.checkedRadioButtonId) {
        R.id.genderMale -> "男"
        R.id.genderFemale -> "女"
        R.id.genderOther -> "其他"
        else -> ""
    }

    private fun savePatient() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            nameInput.error = "请填写患者姓名"
            nameInput.requestFocus()
            return
        }
        val gender = selectedGender()
        if (gender.isBlank()) {
            Toast.makeText(this, "请选择患者性别", Toast.LENGTH_SHORT).show()
            return
        }
        val ageText = ageInput.text.toString().trim()
        val age = ageText.toIntOrNull()
        if (age == null || age !in 0..130) {
            ageInput.error = "请填写 0–130 岁之间的有效年龄"
            ageInput.requestFocus()
            return
        }

        val current = existingPatient
        val saved = Patient(
            id = patientId,
            patientNo = current?.patientNo.orEmpty(),
            name = name,
            gender = gender,
            ageText = "${age}岁",
            birthDate = current?.birthDate.orEmpty(),
            phone = current?.phone.orEmpty(),
            ward = current?.ward.orEmpty(),
            bedNo = current?.bedNo.orEmpty(),
            admissionDate = current?.admissionDate.orEmpty(),
            diagnosis = current?.diagnosis.orEmpty(),
            allergy = current?.allergy.orEmpty(),
            status = current?.status.orEmpty().ifBlank { "在院" },
            attending = current?.attending.orEmpty(),
            department = current?.department.orEmpty(),
            createdAt = current?.createdAt ?: 0L
        )
        repository.savePatient(saved)
        Toast.makeText(
            this,
            if (patientId > 0) "患者资料已更新" else "患者资料已保存",
            Toast.LENGTH_SHORT
        ).show()
        setResult(RESULT_OK)
        finish()
    }
}
