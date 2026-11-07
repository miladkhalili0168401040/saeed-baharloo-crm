package com.milad.saeedcrm

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import java.util.Calendar
import java.util.Locale

private const val GREEN = 0xFF244B3B.toInt()
private const val CREAM = 0xFFF7F3E8.toInt()
private const val GOLD = 0xFFC5A45B.toInt()
private const val INK = 0xFF26332D.toInt()

class DBHelper(c: android.content.Context) : SQLiteOpenHelper(c, "saeed_crm.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT, fullname TEXT, phone TEXT, referrer TEXT,
            weight TEXT, loadtype TEXT, kilometer TEXT, price TEXT,
            logistic_code TEXT DEFAULT '', initial_weight TEXT DEFAULT '',
            warehouse_weight TEXT DEFAULT '', product_type TEXT DEFAULT '',
            product_kil TEXT DEFAULT '', description TEXT DEFAULT ''
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN logistic_code TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE records ADD COLUMN initial_weight TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE records ADD COLUMN warehouse_weight TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE records ADD COLUMN product_type TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE records ADD COLUMN product_kil TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE records ADD COLUMN description TEXT DEFAULT ''")
            db.execSQL("UPDATE records SET initial_weight=COALESCE(weight,''), product_type=COALESCE(loadtype,''), product_kil=COALESCE(kilometer,'')")
        }
    }
}

class MainActivity : Activity() {
    private lateinit var db: DBHelper
    private lateinit var root: LinearLayout
    private var editingId: Long? = null
    private var pendingCsv = ""
    private val labels = listOf("کد لجستیک", "تاریخ", "نام و نام خانوادگی مشتری",
        "وزن اولیه محصول", "وزن موجودی انبار", "تیپ محصول", "کیل محصول",
        "شماره همراه", "توضیحات")
    private val keys = listOf("logistic_code", "date", "fullname", "initial_weight",
        "warehouse_weight", "product_type", "product_kil", "phone", "description")
    private val fields = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DBHelper(this)
        home()
    }

    private fun setup(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 24)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(CREAM)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = "بهارلو | BAHARLOO\n$title"
            textSize = 22f
            setTextColor(GREEN)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(8, 20, 8, 24)
        })
    }

    private fun label(value: String) {
        root.addView(TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(INK)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(4, 9, 4, 5)
        })
    }

    private fun button(value: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = value
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN))
            setOnClickListener { action() }
        })
    }

    private fun input(hint: String, numeric: Boolean = false, value: String = ""): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            setText(value)
            textSize = 16f
            setSingleLine(hint != "توضیحات")
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.WHITE)
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        root.addView(e)
        return e
    }

    private fun home() {
        setup("مدیریت کارگاه بسته‌بندی بادام")
        label("داشبورد مدیریتی")
        val c = db.readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(CAST(REPLACE(product_kil, ',', '.') AS REAL)),0) FROM records", null)
        var count = 0
        var kilos = 0.0
        if (c.moveToFirst()) { count = c.getInt(0); kilos = c.getDouble(1) }
        c.close()
        label("تعداد کل سوابق: " + count)
        label("مجموع کیل محصول: " + String.format(Locale.US, "%.2f", kilos))
        val today = java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US).format(java.util.Date())
        val month = today.substring(0, 7)
        val d = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM records WHERE date=?", arrayOf(today))
        val todayCount = if (d.moveToFirst()) d.getInt(0) else 0
        d.close()
        val m = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM records WHERE date LIKE ?", arrayOf(month + "%"))
        val monthCount = if (m.moveToFirst()) m.getInt(0) else 0
        m.close()
        label("ثبت‌های امروز: " + todayCount + "     |     ثبت‌های این ماه: " + monthCount)
        button("ثبت اطلاعات جدید") { form() }
        button("جستجوی پیشرفته") { advancedSearch() }
        button("مشاهده تمام سوابق") { showRecords(null) }
        button("خروجی Excel / CSV") { export() }
        button("انتقال اطلاعات از نسخه قبلی") { importLegacyCsv() }
        button("پشتیبان‌گیری از اطلاعات") { backup() }
        button("بازیابی نسخه پشتیبان") { restore() }
    }

    private fun form(recordId: Long? = null) {
        editingId = recordId
        val old = if (recordId == null) null else db.readableDatabase.rawQuery(
            "SELECT * FROM records WHERE id=?", arrayOf(recordId.toString()))
        val oldValues = mutableListOf<String>()
        if (old != null && old.moveToFirst()) {
            for (key in keys) {
                val index = old.getColumnIndex(key)
                oldValues.add(if (index >= 0) old.getString(index) ?: "" else "")
            }
        } else repeat(keys.size) { oldValues.add("") }
        old?.close()

        setup(if (recordId == null) "ثبت اطلاعات جدید" else "ویرایش سابقه")
        fields.clear()
        labels.forEachIndexed { i, name ->
            label(name)
            val e = input(name, i == 3 || i == 4 || i == 6, oldValues[i])
            if (i == 1) {
                e.isFocusable = false
                e.setOnClickListener {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(this, { _, year, month, day ->
                        e.setText(String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day))
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }
            }
            if (i == 8) {
                e.setSingleLine(false)
                e.minLines = 3
                e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            fields.add(e)
        }
        button(if (recordId == null) "ذخیره اطلاعات" else "ذخیره تغییرات") {
            if (fields[2].text.isBlank()) {
                Toast.makeText(this, "نام مشتری را وارد کنید", Toast.LENGTH_SHORT).show()
                return@button
            }
            val values = ContentValues()
            keys.forEachIndexed { i, key -> values.put(key, fields[i].text.toString().trim()) }
            if (recordId == null) db.writableDatabase.insert("records", null, values)
            else db.writableDatabase.update("records", values, "id=?", arrayOf(recordId.toString()))
            Toast.makeText(this, "اطلاعات ذخیره شد", Toast.LENGTH_SHORT).show()
            home()
        }
        button("بازگشت") { home() }
    }

    private fun advancedSearch() {
        setup("جستجوی پیشرفته")
        label("از تاریخ (سال/ماه/روز)")
        val fromDate = input("مثلاً 1405/01/01")
        label("تا تاریخ (سال/ماه/روز)")
        val toDate = input("مثلاً 1405/01/31")
        label("از مقدار کیل محصول")
        val fromKilo = input("حداقل مقدار", true)
        label("تا مقدار کیل محصول")
        val toKilo = input("حداکثر مقدار", true)
        label("نام خانوادگی مشتری")
        val surname = input("نام خانوادگی")
        button("اعمال فیلترها") {
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()
            if (fromDate.text.isNotBlank()) { conditions.add("date >= ?"); args.add(fromDate.text.toString().trim()) }
            if (toDate.text.isNotBlank()) { conditions.add("date <= ?"); args.add(toDate.text.toString().trim()) }
            if (fromKilo.text.isNotBlank()) { conditions.add("CAST(REPLACE(product_kil, ',', '.') AS REAL) >= ?"); args.add(fromKilo.text.toString()) }
            if (toKilo.text.isNotBlank()) { conditions.add("CAST(REPLACE(product_kil, ',', '.') AS REAL) <= ?"); args.add(toKilo.text.toString()) }
            if (surname.text.isNotBlank()) { conditions.add("fullname LIKE ?"); args.add("%" + surname.text.toString().trim() + "%") }
            showRecords(if (conditions.isEmpty()) null else conditions.joinToString(" AND "), args.toTypedArray())
        }
        button("بازگشت") { home() }
    }

    private fun showRecords(where: String?, args: Array<String> = emptyArray()) {
        setup("سوابق ثبت‌شده")
        val sql = "SELECT * FROM records" + (if (where.isNullOrBlank()) "" else " WHERE " + where) + " ORDER BY id DESC"
        val cursor = db.readableDatabase.rawQuery(sql, args)
        if (cursor.count == 0) label("سابقه‌ای پیدا نشد")
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
            val details = labels.indices.joinToString("\n") { i ->
                labels[i] + ": " + (cursor.getString(cursor.getColumnIndexOrThrow(keys[i])) ?: "")
            }
            root.addView(TextView(this).apply {
                text = details
                textSize = 15f
                setTextColor(INK)
                setPadding(14, 14, 14, 14)
                setBackgroundColor(Color.WHITE)
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this).apply {
                text = "ویرایش"
                setOnClickListener { form(id) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply {
                text = "حذف"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("حذف سابقه")
                        .setMessage("آیا از حذف این سابقه مطمئن هستید؟")
                        .setNegativeButton("انصراف", null)
                        .setPositiveButton("حذف") { _, _ ->
                            db.writableDatabase.delete("records", "id=?", arrayOf(id.toString()))
                            showRecords(where, args)
                        }.show()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(actions)
            root.addView(View(this).apply { setBackgroundColor(GOLD) },
                LinearLayout.LayoutParams(-1, 2))
        }
        cursor.close()
        button("بازگشت به صفحه اصلی") { home() }
    }

    private fun export() {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT " + keys.joinToString(",") + " FROM records ORDER BY id DESC", null)
        val csv = StringBuilder()
        csv.append(labels.joinToString(",")).append("\n")
        while (cursor.moveToNext()) {
            csv.append((0 until keys.size).joinToString(",") { i ->
                "\"" + (cursor.getString(i) ?: "").replace("\"", "\"\"") + "\""
            }).append("\n")
        }
        cursor.close()
        pendingCsv = csv.toString()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "Baharloo-Records.csv")
        }
        startActivityForResult(intent, 100)
    }

    private fun backup() {
        try {
            db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "Baharloo-Backup.db")
            }
            startActivityForResult(intent, 101)
        } catch (e: Exception) {
            Toast.makeText(this, "آماده‌سازی پشتیبان ناموفق بود: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun importLegacyCsv() {
        AlertDialog.Builder(this)
            .setTitle("انتقال اطلاعات نسخه قبلی")
            .setMessage("ابتدا در برنامه قدیمی بهارلو/CRM از بخش خروجی CSV فایل سوابق را تهیه کنید؛ سپس آن فایل را اینجا انتخاب کنید.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("انتخاب فایل CSV") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, 103)
            }.show()
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"') {
                cell.append('"'); i++
            } else if (ch == '"') {
                quoted = !quoted
            } else if (ch == ',' && !quoted) {
                values.add(cell.toString()); cell.setLength(0)
            } else {
                cell.append(ch)
            }
            i++
        }
        values.add(cell.toString())
        return values
    }

    private fun restore() {
        AlertDialog.Builder(this)
            .setTitle("بازیابی اطلاعات")
            .setMessage("با بازیابی، اطلاعات فعلی برنامه با اطلاعات فایل پشتیبان جایگزین می‌شود. قبل از ادامه از اطلاعات فعلی پشتیبان بگیرید. ادامه می‌دهید؟")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("انتخاب فایل پشتیبان") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, 102)
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            when (requestCode) {
                100 -> {
                    contentResolver.openOutputStream(data.data!!)?.use {
                        it.write(("\uFEFF" + pendingCsv).toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "فایل CSV ذخیره شد", Toast.LENGTH_LONG).show()
                }
                101 -> {
                    val source = getDatabasePath("saeed_crm.db")
                    db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
                    source.inputStream().use { input ->
                        contentResolver.openOutputStream(data.data!!)?.use { output -> input.copyTo(output) }
                    }
                    Toast.makeText(this, "نسخه پشتیبان ذخیره شد", Toast.LENGTH_LONG).show()
                }
                103 -> {
                    val csvText = contentResolver.openInputStream(data.data!!)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("فایل CSV قابل خواندن نیست")
                    val lines = csvText.removePrefix("\uFEFF").split(Regex("\\r?\\n")).filter { it.isNotBlank() }
                    var imported = 0
                    db.writableDatabase.beginTransaction()
                    try {
                        for (line in lines.drop(1)) {
                            val row = parseCsvLine(line)
                            if (row.size < 7) continue
                            val values = ContentValues().apply {
                                put("date", row.getOrElse(0) { "" })
                                put("fullname", row.getOrElse(1) { "" })
                                put("phone", row.getOrElse(2) { "" })
                                put("referrer", row.getOrElse(3) { "" })
                                put("initial_weight", row.getOrElse(4) { "" })
                                put("weight", row.getOrElse(4) { "" })
                                put("product_type", row.getOrElse(5) { "" })
                                put("loadtype", row.getOrElse(5) { "" })
                                put("product_kil", row.getOrElse(6) { "" })
                                put("kilometer", row.getOrElse(6) { "" })
                                put("price", row.getOrElse(7) { "" })
                                put("description", if (row.getOrElse(3) { "" }.isBlank()) "" else "معرف قبلی: " + row[3])
                            }
                            if (values.getAsString("fullname").isNullOrBlank()) continue
                            db.writableDatabase.insert("records", null, values)
                            imported++
                        }
                        db.writableDatabase.setTransactionSuccessful()
                    } finally {
                        db.writableDatabase.endTransaction()
                    }
                    Toast.makeText(this, "$imported سابقه وارد شد", Toast.LENGTH_LONG).show()
                    home()
                }
                102 -> {
                    val temp = java.io.File(cacheDir, "baharloo-restore.db")
                    contentResolver.openInputStream(data.data!!)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("فایل انتخاب‌شده قابل خواندن نیست")
                    val check = SQLiteDatabase.openDatabase(temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    val valid = check.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='records'", null)
                    val hasRecords = valid.moveToFirst()
                    valid.close()
                    check.close()
                    if (!hasRecords) throw IllegalArgumentException("این فایل، پشتیبان معتبر بهارلو نیست")
                    db.close()
                    val target = getDatabasePath("saeed_crm.db")
                    java.io.File(target.path + "-wal").delete()
                    java.io.File(target.path + "-shm").delete()
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                    db = DBHelper(this)
                    db.readableDatabase
                    Toast.makeText(this, "بازیابی انجام شد", Toast.LENGTH_LONG).show()
                    home()
                }
            }
        } catch (e: Exception) {
            db = DBHelper(this)
            Toast.makeText(this, "عملیات ناموفق بود: " + e.message, Toast.LENGTH_LONG).show()
        }
    }
}