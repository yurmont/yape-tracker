# Yape Tracker: design notes

How the Android app in `android/` captures Yape payments, and why it works this way.

## Goal

Record Yape payments on the same Android phone, with no servers or accounts:

- **Expenses (gastos):** on Yape's "¡Yapeaste!" confirmation screen the user taps **Compartir** and picks
  Yape Tracker. Yape shares **one image** (PNG/JPG) of the receipt. The app runs on-device OCR, extracts the
  data, shows it for confirmation and saves it.
- **Incomes (ingresos):** a `NotificationListenerService` reads Yape's "… te envió un pago por S/ X"
  notifications and saves them.
- Everything is stored in a local SQLite database and exported to Excel (.xlsx) on demand.

## Why the share-image flow

Yape sends no notification when you pay (only for received payments and promos), and MacroDroid's
screen-content triggers never fired on Yape: Yape doesn't expose its text to accessibility. Sharing the
receipt is the reliable source for expenses.

### Automatic capture (test feature)

Yape does allow screenshots of the receipt, so `YapeScreenService` (an AccessibilityService limited to Yape's
package, off by default) uses `takeScreenshot()` (Android 11+) instead of reading text:

- Events from Yape are debounced (700 ms to settle, at most 2.5 s, ≥1.2 s between captures, the system limit
  is ~1 s).
- The screenshot is a hardware bitmap that never touches storage; it is OCR'd with `ReceiptParser`.
- It saves only a "¡Yapeaste!" with amount **and** a new operation number, so it never duplicates the share flow.
- `AutoCaptureLog` counts events/captures/saves and keeps the last result, shown on the main screen, to tell
  whether Yape's events reach the service on a given phone.

Trade-offs: Android 13+ requires "Allow restricted settings" for sideloaded apps, and Play Protect is stricter
with accessibility apps.

## Receipt image content (from a real screenshot)

Top to bottom, the shared image contains roughly:

```
yape (logo)                       JOSÉ QUIÑONES (decorative banner, ignore)
¡Yapeaste!                        Compartir
S/ 0.40                           ("S/" and "0.40" may be separate OCR blocks, large font)
Ronald Yur*                       (recipient, masked with *)
19 nov. 2026 | 01:31 p. m.
CÓDIGO DE SEGURIDAD   3 5 8       (three separate digits)
DATOS DE LA TRANSACCIÓN
Nro. de celular       *** *** 691
Destino               Yape
Nro. de operación     19087981
Más en Yape … (ads, ignore)
```

Labels and values sit in two columns, so OCR may return all labels first and then all values.
The parser must not rely on line order between columns.

### Parsing rules (`parser/ReceiptParser.kt`)
- **Amount:** `S/\s*(\d{1,6}[.,]\d{2})` on the joined text. Fallback: the first token matching `^\d{1,6}[.,]\d{2}$`.
  The decimal separator is `.`; a comma is thousands (`1,250.50`).
- **Recipient:** prefer a line ending with `*`; otherwise the first line after the amount that has letters,
  is not a known label and is not a date.
- **Date/time:** `\d{1,2} [a-z]{3}\.? \d{4}` and `\d{1,2}:\d{2} [ap]\. ?m\.`
- **Operation number:** digits after "operación"; fallback: the longest standalone digit run with 7–12 digits.
- **Security code:** optional, three single digits after "CÓDIGO DE SEGURIDAD".
- If "¡Yapeaste!" is not found, show a warning but still allow manual entry.

## App

- Kotlin, minSdk 26, targetSdk 35, no AndroidX. Dependencies: ML Kit Text Recognition (bundled Latin model).
- `ShareActivity`: `ACTION_SEND` + `image/*` → OCR → parse → editable form (amount, recipient, date, operation,
  **Concepto**) → **Guardar**. Opened from the main screen without an image, it is a manual-entry form.
- **Duplicates:** a repeated operation number shows "Ya registrado" and asks before saving again.
- `YapeNotificationListener`: only Yape's package (`Config.YAPE_PACKAGES`, unconfirmed on a real phone).
  Saves a notification only when it has an amount **and** an income phrase ("te envió", "te yapeó", "recibiste"…),
  so promos are skipped. Identical texts within 2 minutes are saved once.
- `MainActivity`: list with Todos / Gastos / Ingresos filter, totals, **Exportar a Excel** (system "save" picker),
  **Agregar gasto**, long-press to delete, and a prompt to grant notification access.
- `core/XlsxWriter.kt` writes the .xlsx by hand (zip of XML parts); `core/MovementSheet.kt` defines the columns
  and the total row.

## Build and distribution

- Pure JUnit tests for the parsers, money handling and the .xlsx writer, including the two-column OCR order.
- GitHub Actions builds and tests on every push. On `main`, with the keystore secrets set, it publishes a
  signed APK as a GitHub Release (`releases/latest`). The fixed key lets updates install over the previous
  version without losing data.

## Acceptance

1. Share a real "¡Yapeaste!" receipt → the app shows the correct amount, recipient and operation number → Guardar → it appears in the list.
2. Sharing the same receipt again warns that it is already registered.
3. A real incoming Yape payment is saved automatically, and promos are ignored.
4. Exporting opens in Excel with the right rows and a correct total.
