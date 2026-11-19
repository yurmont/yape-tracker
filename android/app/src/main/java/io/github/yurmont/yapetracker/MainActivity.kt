package io.github.yurmont.yapetracker

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import io.github.yurmont.yapetracker.core.Money
import io.github.yurmont.yapetracker.core.Movement
import io.github.yurmont.yapetracker.core.MovementSheet
import io.github.yurmont.yapetracker.core.MovementType
import io.github.yurmont.yapetracker.core.XlsxWriter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/** Launcher screen: automatic-registration status, the stored movements, totals and Excel export. */
class MainActivity : Activity() {

    private lateinit var filterGroup: RadioGroup
    private lateinit var totals: TextView
    private lateinit var list: ListView
    private val adapter = MovementAdapter()

    private var filter: MovementType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        filterGroup = findViewById(R.id.filter)
        totals = findViewById(R.id.totals)
        list = findViewById(R.id.list)
        list.adapter = adapter
        list.emptyView = findViewById(R.id.empty)

        filterGroup.setOnCheckedChangeListener { _, checkedId ->
            filter = when (checkedId) {
                R.id.filter_gastos -> MovementType.GASTO
                R.id.filter_ingresos -> MovementType.INGRESO
                else -> null
            }
            refresh()
        }
        findViewById<Button>(R.id.export).setOnClickListener { startExport() }
        findViewById<Button>(R.id.add).setOnClickListener {
            startActivity(Intent(this, ShareActivity::class.java))
        }
        findViewById<Button>(R.id.auto_pay_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.auto_income_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(adapter.getItem(position))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAutomatic()
        refresh()
    }

    /** "Registro automático": one line per source, with "Activar" only when it is off. */
    private fun refreshAutomatic() {
        val canCapture = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        val payOn = canCapture &&
            enabledInSecureList(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, YapeScreenService::class.java)
        findViewById<TextView>(R.id.auto_pay_status).setText(
            when {
                !canCapture -> R.string.auto_pay_old_android
                payOn -> R.string.auto_pay_on
                else -> R.string.auto_pay_off
            },
        )
        findViewById<Button>(R.id.auto_pay_button).visibility =
            if (canCapture && !payOn) View.VISIBLE else View.GONE

        val incomeOn = notificationAccessGranted()
        findViewById<TextView>(R.id.auto_income_status)
            .setText(if (incomeOn) R.string.auto_income_on else R.string.auto_income_off)
        findViewById<Button>(R.id.auto_income_button).visibility = if (incomeOn) View.GONE else View.VISIBLE

        // Warn only about a recent receipt that couldn't be read, so the user can share it instead.
        val log = AutoCaptureLog(this)
        val problem = log.problem?.takeIf { payOn && System.currentTimeMillis() - log.problemAt < PROBLEM_SHOWN_MS }
        findViewById<TextView>(R.id.auto_problem).apply {
            text = problem?.let { getString(R.string.auto_problem, it) }
            visibility = if (problem != null) View.VISIBLE else View.GONE
        }
    }

    private fun refresh() {
        val db = MovementDb.get(this)
        db.fillMissingIncomePersons()
        val items = db.list(filter)
        adapter.items = items
        val spent = items.filter { it.type == MovementType.GASTO }.sumOf { it.amountCents }
        val received = items.filter { it.type == MovementType.INGRESO }.sumOf { it.amountCents }
        totals.text = when (filter) {
            MovementType.GASTO -> getString(R.string.totals_gastos, Money.format(spent), items.size)
            MovementType.INGRESO -> getString(R.string.totals_ingresos, Money.format(received), items.size)
            null -> getString(
                R.string.totals_all, Money.format(received), Money.format(spent), Money.format(received - spent),
            )
        }
    }

    private fun notificationAccessGranted(): Boolean =
        enabledInSecureList("enabled_notification_listeners", YapeNotificationListener::class.java)

    /** Whether one of this app's components is in a ":"-separated list in Settings.Secure. */
    private fun enabledInSecureList(key: String, component: Class<*>): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, key) ?: return false
        val me = ComponentName(this, component)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun confirmDelete(m: Movement) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, describe(m)))
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                MovementDb.get(this).delete(m.id)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- Excel export ----

    private fun startExport() {
        if (adapter.count == 0) {
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val slug = when (filter) {
            MovementType.GASTO -> "gastos"
            MovementType.INGRESO -> "ingresos"
            null -> "movimientos"
        }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(XLSX_MIME)
            .putExtra(Intent.EXTRA_TITLE, "yape-$slug-$day.xlsx")
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    @Deprecated("Framework Activity API; this app has no AndroidX.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && uri != null) export(uri)
    }

    private fun export(uri: Uri) {
        val type = filter
        val sheetName = when (type) {
            MovementType.GASTO -> "Gastos"
            MovementType.INGRESO -> "Ingresos"
            null -> "Movimientos"
        }
        val app = applicationContext
        io.execute {
            val ok = try {
                val movements = MovementDb.get(app).list(type).reversed() // oldest first in the sheet
                val sheet = MovementSheet.build(sheetName, movements, TimeZone.getDefault())
                app.contentResolver.openOutputStream(uri)?.use { XlsxWriter.write(sheet, it) } != null
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                val msg = if (ok) R.string.export_done else R.string.export_failed
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- List ----

    private fun describe(m: Movement): String {
        val sign = if (m.type == MovementType.GASTO) "-" else "+"
        return "$sign S/ ${Money.format(m.amountCents)} · ${m.person ?: m.type.label}"
    }

    private inner class MovementAdapter : BaseAdapter() {
        var items: List<Movement> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        private val timeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_movement, parent, false)
            val m = items[position]
            view.findViewById<TextView>(R.id.item_title).text = m.person ?: m.type.label
            // Without a name, show what the notification said so the entry is still recognisable.
            val detail = m.concepto ?: m.rawText.takeIf { m.person == null }
            view.findViewById<TextView>(R.id.item_subtitle).text =
                listOfNotNull(m.yapeDate ?: timeFormat.format(Date(m.createdAt)), detail).joinToString(" · ")
            view.findViewById<TextView>(R.id.item_amount).apply {
                val expense = m.type == MovementType.GASTO
                text = (if (expense) "-S/ " else "+S/ ") + Money.format(m.amountCents)
                setTextColor(getColor(if (expense) R.color.expense else R.color.income))
            }
            return view
        }
    }

    private companion object {
        const val REQUEST_EXPORT = 1
        const val PROBLEM_SHOWN_MS = 24 * 60 * 60 * 1000L
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val io = Executors.newSingleThreadExecutor()
    }
}
