package io.github.yurmont.yapetracker

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.yurmont.yapetracker.core.Money
import io.github.yurmont.yapetracker.core.Movement
import io.github.yurmont.yapetracker.core.MovementType
import io.github.yurmont.yapetracker.parser.EntryFormatter
import io.github.yurmont.yapetracker.parser.ReceiptParser

/**
 * Registers expenses without "Compartir". Limited to Yape's package (see res/xml/yape_screen_service.xml):
 * when Yape's screen settles, it takes a screenshot that is never saved to the gallery, reads it with
 * the same OCR as the share flow, and saves a "¡Yapeaste!" receipt whose operation number is new.
 *
 * Yape doesn't expose its text to other apps, so the screenshot is the only reliable source.
 * Needs Android 11+ (takeScreenshot); on older versions it only counts events.
 */
class YapeScreenService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val captureRunnable = Runnable { capture() }
    private lateinit var log: AutoCaptureLog
    private var recognizer: TextRecognizer? = null

    /** When the first not-yet-captured event arrived; caps the debounce on screens that keep animating. */
    private var pendingSince = 0L
    private var lastCaptureAt = 0L
    private var busy = false

    override fun onServiceConnected() {
        log = AutoCaptureLog(this)
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        log.note(getString(R.string.auto_log_connected))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() !in Config.YAPE_PACKAGES) return
        log.countEvent()
        val now = SystemClock.uptimeMillis()
        if (pendingSince == 0L) pendingSince = now
        handler.removeCallbacks(captureRunnable)
        // Wait for the screen to settle, but never more than MAX_WAIT_MS after the first change.
        val delay = if (now - pendingSince >= MAX_WAIT_MS) 0L else SETTLE_MS
        handler.postDelayed(captureRunnable, delay)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.close()
        super.onDestroy()
    }

    private fun capture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            log.note(getString(R.string.auto_log_needs_android_11))
            return
        }
        if (busy) return
        // The system refuses screenshots taken less than ~1 s apart.
        val wait = lastCaptureAt + MIN_INTERVAL_MS - SystemClock.uptimeMillis()
        if (wait > 0) {
            handler.postDelayed(captureRunnable, wait)
            return
        }
        pendingSince = 0L
        lastCaptureAt = SystemClock.uptimeMillis()
        busy = true
        takeYapeScreenshot()
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun takeYapeScreenshot() {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    log.countCapture()
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            ?.let { hw -> hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() } }
                    } finally {
                        result.hardwareBuffer.close()
                    }
                    if (bitmap == null) {
                        done(getString(R.string.auto_log_capture_failed, -1))
                        return
                    }
                    recognize(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    done(getString(R.string.auto_log_capture_failed, errorCode))
                }
            },
        )
    }

    private fun recognize(bitmap: Bitmap) {
        val client = recognizer ?: return done(null)
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text -> done(handleReceipt(text.linesInReadingOrder())) }
            .addOnFailureListener { done(getString(R.string.auto_log_ocr_failed)) }
            .addOnCompleteListener { bitmap.recycle() }
    }

    /** Saves the receipt if it is a complete, new "¡Yapeaste!". Returns what to note in the log. */
    private fun handleReceipt(lines: List<String>): String? {
        val r = ReceiptParser.parse(lines)
        if (!r.isYapeaste) return null // any other Yape screen: nothing to note
        val cents = r.amount?.let(Money::parseCents)
        val op = r.operation
        if (cents == null || op == null) return getString(R.string.auto_log_incomplete)
        val db = MovementDb.get(this)
        if (db.operationExists(op)) return getString(R.string.auto_log_already, op)

        db.insert(
            Movement(
                type = MovementType.GASTO,
                amountCents = cents,
                person = r.recipient,
                yapeDate = r.dateTime,
                operation = op,
                concepto = null,
                rawText = EntryFormatter.expense(
                    amount = Money.format(cents).replace(",", ""),
                    recipient = r.recipient,
                    dateTime = r.dateTime,
                    operation = op,
                    concepto = null,
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        log.countSaved()
        val summary = "S/ ${Money.format(cents)}" + (r.recipient?.let { " · $it" } ?: "")
        Toast.makeText(this, getString(R.string.auto_toast_saved, summary), Toast.LENGTH_LONG).show()
        return getString(R.string.auto_log_saved, summary, op)
    }

    private fun done(message: String?) {
        busy = false
        message?.let(log::note)
    }

    private companion object {
        const val SETTLE_MS = 700L
        const val MAX_WAIT_MS = 2_500L
        const val MIN_INTERVAL_MS = 1_200L
    }
}
