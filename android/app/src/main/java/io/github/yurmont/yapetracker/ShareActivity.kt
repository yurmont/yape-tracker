package io.github.yurmont.yapetracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.yurmont.yapetracker.core.Money
import io.github.yurmont.yapetracker.core.Movement
import io.github.yurmont.yapetracker.core.MovementType
import io.github.yurmont.yapetracker.parser.EntryFormatter
import io.github.yurmont.yapetracker.parser.Receipt
import io.github.yurmont.yapetracker.parser.ReceiptParser
import java.io.IOException

/**
 * Target of Yape's "Compartir": reads the receipt image, lets the user confirm, saves it on the phone.
 * Opened without an image (from "Agregar gasto"), it is a blank form for manual entry.
 */
class ShareActivity : Activity() {

    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var warning: TextView
    private lateinit var amount: EditText
    private lateinit var recipient: EditText
    private lateinit var dateTime: EditText
    private lateinit var operation: EditText
    private lateinit var concepto: EditText
    private lateinit var save: Button
    private lateinit var ocrToggle: TextView
    private lateinit var ocrText: TextView

    private var duplicateConfirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        warning = findViewById(R.id.warning)
        amount = findViewById(R.id.amount)
        recipient = findViewById(R.id.recipient)
        dateTime = findViewById(R.id.date_time)
        operation = findViewById(R.id.operation)
        concepto = findViewById(R.id.concepto)
        save = findViewById(R.id.save)
        ocrToggle = findViewById(R.id.ocr_toggle)
        ocrText = findViewById(R.id.ocr_text)

        save.setOnClickListener { onSave() }
        ocrToggle.setOnClickListener {
            ocrText.visibility = if (ocrText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // After a rotation the fields keep their text; don't overwrite edits with a second OCR pass.
        if (savedInstanceState != null || intent.action != Intent.ACTION_SEND) {
            showReady()
            ocrToggle.visibility = View.GONE
            return
        }
        val uri = sharedImageUri(intent)
        if (uri == null) {
            showReady()
            showWarning(getString(R.string.warning_no_image))
            return
        }
        recognize(uri)
    }

    private fun sharedImageUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) return null
        val extra = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return extra ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun recognize(uri: Uri) {
        status.setText(R.string.reading)
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: IOException) {
            showReady()
            showWarning(getString(R.string.warning_unreadable))
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { result ->
                val lines = linesInReadingOrder(result)
                ocrText.text = lines.joinToString("\n")
                fill(ReceiptParser.parse(lines))
            }
            .addOnFailureListener {
                showReady()
                showWarning(getString(R.string.warning_unreadable))
            }
    }

    /** OCR lines sorted top to bottom, then left to right. The parser copes with any order anyway. */
    private fun linesInReadingOrder(result: Text): List<String> =
        result.textBlocks
            .flatMap { it.lines }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .map { it.text }

    private fun fill(r: Receipt) {
        amount.setText(r.amount.orEmpty())
        recipient.setText(r.recipient.orEmpty())
        dateTime.setText(r.dateTime.orEmpty())
        operation.setText(r.operation.orEmpty())
        showReady()
        val op = r.operation
        when {
            op != null && MovementDb.get(this).operationExists(op) ->
                showWarning(getString(R.string.warning_already_sent, op))
            !r.isYapeaste -> showWarning(getString(R.string.warning_not_yapeaste))
        }
        if (r.amount == null) amount.requestFocus() else concepto.requestFocus()
    }

    private fun showReady() {
        progress.visibility = View.GONE
        status.setText(R.string.check_and_save)
        save.isEnabled = true
    }

    private fun showWarning(message: String) {
        warning.text = message
        warning.visibility = View.VISIBLE
    }

    private fun onSave() {
        val cents = Money.parseCents(amount.text.toString())
        if (cents == null) {
            amount.error = getString(R.string.error_amount)
            amount.requestFocus()
            return
        }
        val db = MovementDb.get(this)
        val op = operation.text.toString().trim()
        if (op.isNotEmpty() && !duplicateConfirmed && db.operationExists(op)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_duplicate_title)
                .setMessage(getString(R.string.dialog_duplicate_message, op))
                .setPositiveButton(R.string.dialog_duplicate_send) { _, _ ->
                    duplicateConfirmed = true
                    onSave()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val person = recipient.text.toString().trim().ifEmpty { null }
        val yapeDate = dateTime.text.toString().trim().ifEmpty { null }
        val note = concepto.text.toString().trim().ifEmpty { null }
        val summary = EntryFormatter.expense(
            amount = Money.format(cents).replace(",", ""),
            recipient = person,
            dateTime = yapeDate,
            operation = op,
            concepto = note,
        )
        db.insert(
            Movement(
                type = MovementType.GASTO,
                amountCents = cents,
                person = person,
                yapeDate = yapeDate,
                operation = op.ifEmpty { null },
                concepto = note,
                rawText = summary,
                createdAt = System.currentTimeMillis(),
            ),
        )
        Toast.makeText(applicationContext, R.string.toast_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
