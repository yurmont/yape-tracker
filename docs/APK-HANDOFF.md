# Handoff: build the yape-tracker Android app (APK)

> **Update (2026-11-19):** the app in `android/` stores expenses and incomes **locally on the phone** (SQLite)
> and exports them to Excel (.xlsx), instead of sending them to the Google Form below. It has no INTERNET
> permission. The Google Form / Sheet setup stays documented in the README as the non-app alternative.

Instructions for continuing this project with Claude Code on a PC that has Android tooling
(Android Studio / Android SDK + JDK 17+). Paste the prompt at the bottom into Claude Code,
opened in a clone of this repo.

## Goal

Register Yape payments in a Google Sheet automatically from an Android phone:

- **Expenses (gastos):** on Yape's "¡Yapeaste!" confirmation screen the user taps **Compartir** and
  picks this app. Yape shares **one image** (PNG/JPG) of the receipt. The app runs on-device OCR,
  extracts the data, shows it for confirmation, and sends it to a Google Form.
- **Incomes (ingresos), phase 2:** a `NotificationListenerService` reads Yape notifications
  ("… te envió un pago por S/ X") and sends them to the same form. This replaces the MacroDroid macro.

## What was tried and why the APK exists

| Approach | Result |
|---|---|
| Yape push notification when you pay | Yape does not send one (only for received payments and promos) |
| MacroDroid "Contenido de pantalla" (accessibility) trigger on Yape | Never fires |
| MacroDroid "Contenido de la captura de pantalla" (OCR) trigger | Never fires, not even on WhatsApp |
| MacroDroid notification trigger for incomes | **Works** (real Yape notifications reach the form) |
| Yape "Compartir" on the receipt | **Works**: share sheet says "1 imagen" |

Reading Yape's screen live is blocked for any app, so do **not** build an AccessibilityService for expenses.
The share-image flow is the design.

## Backend (already working, do not change)

A Google Form linked to a Google Sheet (`Respuestas de formulario 1`). Submitting is a plain HTTP request:

```
GET or POST https://docs.google.com/forms/d/e/1FAIpQLSc_sySbv4Ch69HeKVACBk3yWWCJZdMalhekJm-K-O6P0euXqQ/formResponse
  entry.561707462 = origen   (e.g. "compartir", "notificacion")
  entry.352548649 = texto
```

Verified: a GET with URL-encoded query params returns "Se ha registrado tu respuesta" and adds a row.
The form must stay public ("anyone with the link"), no login, no email collection.

**Texto format** (keeps `formulas.txt` working: Tipo/Monto/Persona columns):

```
Yapeaste S/ 0.40 a Ronald Yur* | 19 nov. 2026 01:31 p. m. | op 19087981 | <concepto opcional>
```

For incomes keep Yape's own notification text, e.g. `Yape! Ana te envió un pago por S/ 1`.

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

### Parsing rules
- **Amount:** `S/\s*(\d{1,6}[.,]\d{2})` on the joined text. Fallback: the first token matching `^\d{1,6}[.,]\d{2}$`.
  The decimal separator is `.`; a comma is thousands (`1,250.50`).
- **Recipient:** first line after the amount line that has letters, is not a known label, and is not a date.
  Usually ends with `*`.
- **Date/time:** `\d{1,2} [a-z]{3}\.? \d{4}` and `\d{1,2}:\d{2} [ap]\. ?m\.`
- **Operation number:** digits after "operación"; fallback: the longest standalone digit run with 7–12 digits.
- **Security code:** optional, three single digits after "CÓDIGO DE SEGURIDAD".
- If "¡Yapeaste!" is not found, show a warning but still allow manual entry.

## App spec (phase 1: expenses)

- Kotlin, minSdk 26, targetSdk = latest installed. Keep dependencies minimal.
- **ML Kit Text Recognition, bundled Latin model** (`com.google.mlkit:text-recognition`), on-device, no network needed for OCR.
- `ShareActivity` with an intent filter for `ACTION_SEND` and `image/*`, app label "Yape Tracker".
- Flow: receive URI → OCR → parse → show a small form prefilled with amount, recipient, date, operation number,
  plus an empty **Concepto** field → **Guardar** button → HTTP request to the form (background thread) →
  toast "Registrado" → finish.
- **Duplicates:** store sent operation numbers in SharedPreferences. If one repeats, warn ("Ya registrado") and ask before sending again.
- **Offline:** if the request fails, keep it in a small local queue and retry on the next app open (simple JSON in SharedPreferences is enough).
- `MainActivity` (launcher): shows status (pending queue, last sent) and short usage instructions.
- Put the form URL and entry IDs in one `Config.kt`.

## Phase 2: incomes
- `NotificationListenerService` filtered to Yape's package (find it on the phone; likely `com.bcp.innovacxion.yapeapp`).
- Send `origen=notificacion`, `texto=<title> <text>` only when the text contains an amount (`S/`). Skip promos.
- Deduplicate identical texts within 2 minutes.
- In MainActivity, add a button that opens notification-access settings.
- After this works, the user disables the MacroDroid macros.

## Build and distribution
- Pure unit tests for the parser (JUnit), using OCR-like text fixtures, including the two-column order case.
- GitHub Actions workflow: build `assembleDebug` (or a signed release) on push to `main`, run unit tests,
  and publish the APK as a GitHub Release asset (for example tag `latest`), so the user downloads it on the phone.
- Use a **fixed signing key** (keystore in a repo secret) so updates install over the previous version
  without uninstalling.
- The repo is private: downloading the APK on the phone requires being logged in to GitHub.

## Acceptance
1. Share a real "¡Yapeaste!" receipt → the app shows the correct amount, recipient and operation number → Guardar → a row appears in the sheet with `origen=compartir`.
2. Sharing the same receipt again warns that it is already registered.
3. Phase 2: a real incoming Yape payment creates a row automatically, and promos are ignored.

---

## Prompt to paste into Claude Code

> Read `docs/APK-HANDOFF.md` in this repo and build the Android app it describes, in a new `android/` folder.
> Start with phase 1 (receive the shared Yape receipt image → ML Kit OCR → parse → confirm → send to the Google Form).
> Write the parser as pure Kotlin with unit tests covering the receipt layout in the doc, including the case where labels and values come back in separate columns.
> Check which JDK, Android SDK platforms and Gradle versions are installed here, and use versions compatible with them.
> Build the debug APK locally and fix any build errors. Then add a GitHub Actions workflow that builds and tests on push and publishes the APK as a GitHub Release asset with a fixed signing key.
> Update README.md with how to install the APK and use "Compartir → Yape Tracker".
> When phase 1 works, do phase 2 (NotificationListenerService for incomes).
> Ask me before creating repo secrets, releases, or anything outside this repo.
