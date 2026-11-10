package com.hidble.phonekeyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Patient(
    val id: Long = 0,
    val patientNo: String = "",
    val name: String = "",
    val gender: String = "",
    val ageText: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val ward: String = "",
    val bedNo: String = "",
    val admissionDate: String = "",
    val diagnosis: String = "",
    val allergy: String = "",
    val status: String = "在院",
    val attending: String = "",
    val department: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val displayName: String
        get() = name.ifBlank { "未命名患者" }

    val ageLabel: String
        get() = ageText.ifBlank { ageFromBirthDate(birthDate) }
}

data class ClinicalRecord(
    val id: Long = 0,
    val patientId: Long = 0,
    val type: String = "日常病程",
    val title: String = "",
    val content: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val preview: String
        get() = content.replace(Regex("\\s+"), " ").trim().take(90)
}

data class PatientOverview(
    val total: Int,
    val inHospital: Int,
    val needsAttention: Int,
    val recordsToday: Int
)

/**
 * 患者资料只保存在应用私有数据库。
 * 文本字段使用 Android Keystore 中的 AES/GCM 密钥加密后写入 SQLite，系统备份同时关闭。
 */
class PatientRepository(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "inpatient_care.db"
        private const val DB_VERSION = 2
        private const val TABLE_PATIENTS = "patients"
        private const val TABLE_RECORDS = "clinical_records"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PATIENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                patient_no TEXT NOT NULL,
                name TEXT NOT NULL,
                gender TEXT NOT NULL,
                age_text TEXT NOT NULL DEFAULT '',
                birth_date TEXT NOT NULL,
                phone TEXT NOT NULL,
                ward TEXT NOT NULL,
                bed_no TEXT NOT NULL,
                admission_date TEXT NOT NULL,
                diagnosis TEXT NOT NULL,
                allergy TEXT NOT NULL,
                status TEXT NOT NULL,
                attending TEXT NOT NULL,
                department TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_RECORDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                patient_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(patient_id) REFERENCES $TABLE_PATIENTS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_records_patient ON $TABLE_RECORDS(patient_id, updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_PATIENTS ADD COLUMN age_text TEXT NOT NULL DEFAULT ''")
        }
    }

    fun listPatients(): List<Patient> {
        val result = mutableListOf<Patient>()
        readableDatabase.query(
            TABLE_PATIENTS,
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.toPatient()
            }
        }
        return result
    }

    fun findPatient(id: Long): Patient? {
        readableDatabase.query(
            TABLE_PATIENTS,
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toPatient() else null
        }
    }

    fun savePatient(patient: Patient): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("patient_no", ClinicalCrypto.encrypt(patient.patientNo))
            put("name", ClinicalCrypto.encrypt(patient.name))
            put("gender", ClinicalCrypto.encrypt(patient.gender))
            put("age_text", ClinicalCrypto.encrypt(patient.ageText))
            put("birth_date", ClinicalCrypto.encrypt(patient.birthDate))
            put("phone", ClinicalCrypto.encrypt(patient.phone))
            put("ward", ClinicalCrypto.encrypt(patient.ward))
            put("bed_no", ClinicalCrypto.encrypt(patient.bedNo))
            put("admission_date", ClinicalCrypto.encrypt(patient.admissionDate))
            put("diagnosis", ClinicalCrypto.encrypt(patient.diagnosis))
            put("allergy", ClinicalCrypto.encrypt(patient.allergy))
            put("status", ClinicalCrypto.encrypt(patient.status))
            put("attending", ClinicalCrypto.encrypt(patient.attending))
            put("department", ClinicalCrypto.encrypt(patient.department))
            put("updated_at", now)
        }
        return if (patient.id > 0) {
            writableDatabase.update(
                TABLE_PATIENTS,
                values,
                "id = ?",
                arrayOf(patient.id.toString())
            )
            patient.id
        } else {
            values.put("created_at", now)
            writableDatabase.insertOrThrow(TABLE_PATIENTS, null, values)
        }
    }

    fun deletePatient(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(TABLE_RECORDS, "patient_id = ?", arrayOf(id.toString()))
            writableDatabase.delete(TABLE_PATIENTS, "id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun listRecords(patientId: Long): List<ClinicalRecord> {
        val result = mutableListOf<ClinicalRecord>()
        readableDatabase.query(
            TABLE_RECORDS,
            null,
            "patient_id = ?",
            arrayOf(patientId.toString()),
            null,
            null,
            "updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.toRecord()
            }
        }
        return result
    }

    fun findRecord(id: Long): ClinicalRecord? {
        readableDatabase.query(
            TABLE_RECORDS,
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toRecord() else null
        }
    }

    fun saveRecord(record: ClinicalRecord): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("patient_id", record.patientId)
            put("type", ClinicalCrypto.encrypt(record.type))
            put("title", ClinicalCrypto.encrypt(record.title))
            put("content", ClinicalCrypto.encrypt(record.content))
            put("updated_at", now)
        }
        return if (record.id > 0) {
            writableDatabase.update(
                TABLE_RECORDS,
                values,
                "id = ?",
                arrayOf(record.id.toString())
            )
            record.id
        } else {
            values.put("created_at", now)
            writableDatabase.insertOrThrow(TABLE_RECORDS, null, values)
        }
    }

    fun deleteRecord(id: Long) {
        writableDatabase.delete(TABLE_RECORDS, "id = ?", arrayOf(id.toString()))
    }

    fun recordCount(patientId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_RECORDS WHERE patient_id = ?",
            arrayOf(patientId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun recentRecords(limit: Int = 20): List<Pair<Patient, ClinicalRecord>> {
        val records = mutableListOf<Pair<Patient, ClinicalRecord>>()
        listPatients().forEach { patient ->
            listRecords(patient.id).forEach { record ->
                records += patient to record
            }
        }
        return records.sortedByDescending { it.second.updatedAt }.take(limit)
    }

    fun overview(): PatientOverview {
        val patients = listPatients()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val todayStart = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(today)?.time ?: 0L
        val recordsToday = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_RECORDS WHERE updated_at >= ?",
            arrayOf(todayStart.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        return PatientOverview(
            total = patients.size,
            inHospital = patients.count { it.status == "在院" || it.status == "待出院" },
            needsAttention = patients.count {
                it.diagnosis.isBlank() || it.allergy.isBlank() || it.attending.isBlank()
            },
            recordsToday = recordsToday
        )
    }

    fun addDemoPatients() {
        if (listPatients().isNotEmpty()) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val p1 = savePatient(
            Patient(
                patientNo = "DEMO-001",
                name = "演示患者 A",
                gender = "男",
                birthDate = "1958-03-12",
                phone = "13800000001",
                ward = "内科一病区",
                bedNo = "08",
                admissionDate = today,
                diagnosis = "社区获得性肺炎",
                allergy = "青霉素过敏",
                status = "在院",
                attending = "演示医生",
                department = "呼吸内科"
            )
        )
        savePatient(
            Patient(
                patientNo = "DEMO-002",
                name = "演示患者 B",
                gender = "女",
                birthDate = "1972-09-21",
                phone = "13800000002",
                ward = "内科一病区",
                bedNo = "12",
                admissionDate = today,
                diagnosis = "2 型糖尿病",
                allergy = "否认药物过敏",
                status = "待出院",
                attending = "演示医生",
                department = "内分泌科"
            )
        )
        saveRecord(
            ClinicalRecord(
                patientId = p1,
                type = "首次病程",
                title = "首次病程记录",
                content = "【演示内容，不可用于真实诊疗】\n患者因发热、咳嗽入院。\n初步诊断：社区获得性肺炎。\n诊疗计划：完善检查，评估感染指标，按规范给予治疗并观察病情变化。"
            )
        )
    }

    private fun android.database.Cursor.toPatient(): Patient = Patient(
        id = getLong(getColumnIndexOrThrow("id")),
        patientNo = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("patient_no"))),
        name = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("name"))),
        gender = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("gender"))),
        ageText = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("age_text"))),
        birthDate = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("birth_date"))),
        phone = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("phone"))),
        ward = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("ward"))),
        bedNo = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("bed_no"))),
        admissionDate = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("admission_date"))),
        diagnosis = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("diagnosis"))),
        allergy = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("allergy"))),
        status = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("status"))),
        attending = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("attending"))),
        department = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("department"))),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
    )

    private fun android.database.Cursor.toRecord(): ClinicalRecord = ClinicalRecord(
        id = getLong(getColumnIndexOrThrow("id")),
        patientId = getLong(getColumnIndexOrThrow("patient_id")),
        type = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("type"))),
        title = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("title"))),
        content = ClinicalCrypto.decrypt(getString(getColumnIndexOrThrow("content"))),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
    )
}

object ClinicalCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "inpatient_care_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    fun decrypt(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            val packed = Base64.decode(value, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_BYTES)
            val body = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_BITS, iv)
            )
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

fun ageFromBirthDate(birthDate: String): String {
    val formats = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyy年MM月dd日")
    val birth = formats.firstNotNullOfOrNull { format ->
        runCatching { SimpleDateFormat(format, Locale.CHINA).parse(birthDate) }.getOrNull()
    } ?: return "年龄未录入"
    val years = ((System.currentTimeMillis() - birth.time) / 31_556_952_000L).toInt()
    return if (years in 0..130) "${years}岁" else "年龄待核对"
}
