package io.github.yurmont.yapetracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.yurmont.yapetracker.core.Movement
import io.github.yurmont.yapetracker.core.MovementType

/** Every expense and income, stored only on this phone. */
class MovementDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "movements.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE movements (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                person TEXT,
                yape_date TEXT,
                operation TEXT,
                concepto TEXT,
                raw_text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX movements_operation ON movements(operation)")
        db.execSQL("CREATE INDEX movements_created ON movements(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(m: Movement): Long = writableDatabase.insert(
        "movements", null,
        ContentValues().apply {
            put("type", m.type.name)
            put("amount_cents", m.amountCents)
            put("person", m.person)
            put("yape_date", m.yapeDate)
            put("operation", m.operation)
            put("concepto", m.concepto)
            put("raw_text", m.rawText)
            put("created_at", m.createdAt)
        },
    )

    fun delete(id: Long) {
        writableDatabase.delete("movements", "_id = ?", arrayOf(id.toString()))
    }

    /** Newest first. `type` null means both expenses and incomes. */
    fun list(type: MovementType?): List<Movement> {
        val (where, args) = if (type == null) null to null else "type = ?" to arrayOf(type.name)
        return readableDatabase.query("movements", null, where, args, null, null, "created_at DESC, _id DESC")
            .use { c -> generateSequence { if (c.moveToNext()) c.toMovement() else null }.toList() }
    }

    fun operationExists(operation: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM movements WHERE operation = ? LIMIT 1", arrayOf(operation),
    ).use { it.moveToFirst() }

    fun recentIncomeExists(text: String, since: Long): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM movements WHERE type = ? AND raw_text = ? AND created_at >= ? LIMIT 1",
        arrayOf(MovementType.INGRESO.name, text, since.toString()),
    ).use { it.moveToFirst() }

    private fun Cursor.toMovement() = Movement(
        id = getLong(getColumnIndexOrThrow("_id")),
        type = MovementType.valueOf(getString(getColumnIndexOrThrow("type"))),
        amountCents = getLong(getColumnIndexOrThrow("amount_cents")),
        person = getStringOrNull("person"),
        yapeDate = getStringOrNull("yape_date"),
        operation = getStringOrNull("operation"),
        concepto = getStringOrNull("concepto"),
        rawText = getString(getColumnIndexOrThrow("raw_text")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val i = getColumnIndexOrThrow(column)
        return if (isNull(i)) null else getString(i)
    }

    companion object {
        @Volatile private var instance: MovementDb? = null

        fun get(context: Context): MovementDb =
            instance ?: synchronized(this) { instance ?: MovementDb(context).also { instance = it } }
    }
}
