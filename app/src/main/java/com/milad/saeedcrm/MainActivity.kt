package com.milad.saeedcrm

import android.app.Activity
import android.os.Bundle
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType

class DBHelper(c: android.content.Context) :
    SQLiteOpenHelper(c, "saeed_crm.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT, fullname TEXT, phone TEXT,
                referrer TEXT, weight TEXT, loadtype TEXT,
                kilometer TEXT, price TEXT
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
}

class MainActivity : Activity() {
    private lateinit var db: DBHelper
    private lateinit var root: LinearLayout
    private val fields = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DBHelper(this)
        home()
    }

    private fun setup(title: String) {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(20, 20, 20, 20)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(0xFFF5F5F5.toInt())

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val heading = TextView(this)
        heading.text = title
        heading.textSize = 24f
        heading.typeface = Typeface.DEFAULT_BOLD
        heading.gravity = Gravity.CENTER
        heading.setPadding(8, 20, 8, 25)
        root.addView(heading)
    }

    private fun button(text: String, action: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.textSize = 16f
        root.addView(b)
        b.setOnClickListener { action() }
    }

    private fun home() {
        setup("CRM سعید بهارلو")
        button("ثبت اطلاعات جدید") { form() }
        button("جستجو در سوابق") { search() }
        button("مشاهده تمام سوابق") { showRecords("") }
        button("خروجی Excel (CSV)") { export() }
    }

    private fun form() {
        setup("ثبت اطلاعات جدید")
        fields.clear()

        val labels = listOf(
            "تاریخ", "نام و نام خانوادگی", "شماره تماس",
            "معرف", "وزن بار", "تیپ بار", "کیل بار", "قیمت"
        )

        labels.forEachIndexed { i, label ->
            val t = TextView(this)
            t.text = label
            t.textSize = 15f
            root.addView(t)

            val e = EditText(this)
            e.hint = label
            e.textSize = 16f
            e.setSingleLine(true)
            if (i == 2 || i == 4 || i == 6 || i == 7) {
                e.inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            root.addView(e)
            fields.add(e)
        }

        button("ذخیره اطلاعات") {
            if (fields[1].text.isBlank()) {
                Toast.makeText(this, "نام را وارد کنید", Toast.LENGTH_SHORT).show()
            } else {
                val values = ContentValues()
                val keys = listOf(
                    "date", "fullname", "phone", "referrer",
                    "weight", "loadtype", "kilometer", "price"
                )
                keys.forEachIndexed { i, key ->
                    values.put(key, fields[i].text.toString())
                }
                db.writableDatabase.insert("records", null, values)
                Toast.makeText(this, "اطلاعات ذخیره شد", Toast.LENGTH_LONG).show()
                home()
            }
        }
        button("بازگشت") { home() }
    }

    private fun search() {
        setup("جستجو در سوابق")
        val e = EditText(this)
        e.hint = "نام یا شماره تماس یا معرف"
        root.addView(e)
        button("جستجو") { showRecords(e.text.toString()) }
        button("بازگشت") { home() }
    }

    private fun showRecords(query: String) {
        setup("سوابق اطلاعات")
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM records WHERE fullname LIKE ? OR phone LIKE ? OR referrer LIKE ? ORDER BY id DESC",
            arrayOf("%$query%", "%$query%", "%$query%")
        )

        if (cursor.count == 0) {
            val empty = TextView(this)
            empty.text = "موردی پیدا نشد"
            root.addView(empty)
        }

        while (cursor.moveToNext()) {
            val item = TextView(this)
            item.text = """
                تاریخ: ${cursor.getString(1)}
                نام: ${cursor.getString(2)}
                تماس: ${cursor.getString(3)}
                معرف: ${cursor.getString(4)}
                وزن بار: ${cursor.getString(5)}
                تیپ بار: ${cursor.getString(6)}
                کیل بار: ${cursor.getString(7)}
                قیمت: ${cursor.getString(8)}
            """.trimIndent()
            item.textSize = 16f
            item.setPadding(12, 18, 12, 18)
            root.addView(item)
            val line = View(this)
            line.setBackgroundColor(0xFFBBBBBB.toInt())
            root.addView(line, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
            ))
        }
        cursor.close()
        button("بازگشت") { home() }
    }

    private fun export() {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT date,fullname,phone,referrer,weight,loadtype,kilometer,price FROM records",
            null
        )
        val csv = StringBuilder()
        csv.append("تاریخ,نام,شماره تماس,معرف,وزن بار,تیپ بار,کیل بار,قیمت\n")
        while (cursor.moveToNext()) {
            val row = (0 until 8).joinToString(",") {
                "\"" + (cursor.getString(it) ?: "").replace("\"", "\"\"") + "\""
            }
            csv.append(row).append("\n")
        }
        cursor.close()

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_TITLE, "Saeed-CRM.csv")
        startActivityForResult(intent, 100)
        pendingCsv = csv.toString()
    }

    private var pendingCsv = ""

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let {
                contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(("\uFEFF" + pendingCsv).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "فایل خروجی ذخیره شد", Toast.LENGTH_LONG).show()
            }
        }
    }
}
