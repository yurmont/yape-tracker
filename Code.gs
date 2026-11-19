/**
 * yurmont-yape: Yape -> Google Sheet webhook.
 *
 * Two sources post here from the Android phone:
 *   - source=notificacion  (MacroDroid, Notification trigger): Yape push notifications, mainly payments you RECEIVE.
 *   - source=pantalla      (MacroDroid, Screen content trigger): the "¡Yapeaste!" confirmation screen, i.e. payments you MAKE.
 *
 * Everything is logged to "Raw". Payments you make go to "Gastos", payments you receive to "Ingresos".
 * Setup: see README.md.
 */

// Change this to a long random string and use the same value on the phone.
const SECRET = 'CAMBIA-ESTE-TOKEN';

// Same payment seen again within this window is ignored (screen redraws, notification re-posts).
const DEDUP_SECONDS = 180;

const SHEETS = {
  raw: { name: 'Raw', header: ['Recibido', 'Origen', 'Título', 'Texto', 'Clasificación'] },
  gastos: { name: 'Gastos', header: ['Fecha', 'Monto (S/)', 'Destinatario', 'Categoría', 'Nota', 'Origen', 'Texto original'] },
  ingresos: { name: 'Ingresos', header: ['Fecha', 'Monto (S/)', 'Remitente', 'Origen', 'Texto original'] },
};

// Phrases Yape uses when someone pays YOU.
const INCOME_PATTERNS = [/te envi[oó]/i, /te yape[oó]/i, /recibiste/i, /te pag[oó]/i];
// Phrases that indicate a payment you made. Add the wording you see in the Raw sheet if it differs.
const EXPENSE_PATTERNS = [/yapeaste/i, /pagaste/i, /enviaste/i, /pago (realizado|exitoso)/i, /transferiste/i];

function doPost(e) {
  const data = readPayload_(e);
  if (data.token !== SECRET) return json_({ ok: false, error: 'unauthorized' });

  const source = String(data.source || 'notificacion').trim();
  const title = String(data.title || '').trim();
  let text = String(data.text || '').trim();
  if (source === 'pantalla') text = normalizeScreen_(text);
  const full = (title + ' ' + text).trim();
  const now = new Date();

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const parsed = parse_(full, source);
    if (isDuplicate_(parsed, full)) return json_({ ok: true, duplicate: true });

    sheet_(SHEETS.raw).appendRow([now, source, title, text, parsed.kind]);

    if (parsed.kind === 'gasto') {
      sheet_(SHEETS.gastos).appendRow([now, parsed.amount, parsed.party, '', '', source, full]);
    } else if (parsed.kind === 'ingreso') {
      sheet_(SHEETS.ingresos).appendRow([now, parsed.amount, parsed.party, source, full]);
    }
    return json_({ ok: true, kind: parsed.kind, amount: parsed.amount, party: parsed.party });
  } finally {
    lock.releaseLock();
  }
}

/** Quick check from the browser: opening the web app URL should show "ok". */
function doGet() {
  return ContentService.createTextOutput('ok');
}

// Screen text may come one block per line; normalize to "a | b | c" so the parser sees one format.
function normalizeScreen_(text) {
  return text.split(/\s*(?:[\r\n]+|\s\|\s)\s*/).filter(Boolean).join(' | ');
}

function parse_(s, source) {
  // Screen text arrives as "¡Yapeaste! | S/ | 25.00 | Juan P. | ..." so allow separators between S/ and the number.
  const m = s.match(/S\/\.?[\s|]*(\d[\d.,]*)/i);
  if (!m) return { kind: 'sin-monto', amount: '', party: '' };
  const amount = toNumber_(m[1]);

  let kind;
  if (source === 'pantalla') kind = 'gasto'; // The screen macro only fires on the "¡Yapeaste!" screen.
  else if (INCOME_PATTERNS.some((r) => r.test(s))) kind = 'ingreso';
  else if (EXPENSE_PATTERNS.some((r) => r.test(s))) kind = 'gasto';
  else kind = 'gasto'; // amount present but unknown wording: assume you paid; review in Raw.

  const party = source === 'pantalla' ? screenParty_(s, m) : party_(s, kind);
  return { kind: kind, amount: amount, party: party };
}

function party_(s, kind) {
  let m;
  if (kind === 'ingreso') {
    // "Juan P. te envió un pago por S/ 10"
    m = s.match(/(?:Yape!?\s*)?([A-ZÁÉÍÓÚÑa-záéíóúñ.\s]+?)\s+te (?:envi[oó]|yape[oó]|pag[oó])/i);
  } else {
    // "... S/ 25.00 a Juan P." / "... a Juan P. por S/ 25"
    m = s.match(/\ba\s+([A-ZÁÉÍÓÚÑ][\wÁÉÍÓÚÑáéíóúñ.\s]*?)(?:\s+por\b|[.,]?\s*$)/);
  }
  return m ? m[1].trim() : '';
}

/**
 * On the confirmation screen the recipient is usually the first text block after the amount
 * that looks like a name (letters only, not a label or a date). Calibrate with the Raw sheet.
 */
function screenParty_(s, amountMatch) {
  const inline = s.match(/\ba\s+([A-ZÁÉÍÓÚÑ][\wÁÉÍÓÚÑáéíóúñ.\s]*?)\s*(?:\||$)/);
  if (inline) return inline[1].trim();

  const after = s.slice(amountMatch.index + amountMatch[0].length).split('|');
  const labels = /^(¡?yapeaste!?|compartir|volver|inicio|ver detalle|c[oó]digo|n[uú]mero|fecha|destino|datos|nro|operaci[oó]n)/i;
  for (const raw of after) {
    const t = raw.trim();
    if (/^[A-Za-zÁÉÍÓÚÑáéíóúñ][A-Za-zÁÉÍÓÚÑáéíóúñ.\s]{2,}$/.test(t) && !labels.test(t)) return t;
  }
  return '';
}

// "1,234.50" -> 1234.5 ; "25,50" -> 25.5
function toNumber_(raw) {
  let v = raw.replace(/[.,]$/, '');
  if (/,\d{1,2}$/.test(v) && v.indexOf('.') === -1) v = v.replace(',', '.');
  else v = v.replace(/,/g, '');
  return Number(v);
}

/**
 * A payment is identified by type + amount + party, not by the full text: the confirmation
 * screen is redrawn several times with slightly different text, and the same payment can arrive
 * from both the notification and the screen. Trade-off: two identical payments to the same person
 * within DEDUP_SECONDS count once.
 */
function isDuplicate_(parsed, full) {
  const id = parsed.kind === 'sin-monto'
    ? 'raw|' + full
    : [parsed.kind, parsed.amount, parsed.party.toLowerCase()].join('|');
  const key = Utilities.base64Encode(Utilities.computeDigest(Utilities.DigestAlgorithm.MD5, id));
  const cache = CacheService.getScriptCache();
  if (cache.get(key)) return true;
  cache.put(key, '1', DEDUP_SECONDS);
  return false;
}

function readPayload_(e) {
  // Accepts form fields (recommended) or a JSON body.
  if (e && e.parameter && e.parameter.token) return e.parameter;
  try {
    return JSON.parse(e.postData.contents);
  } catch (err) {
    return {};
  }
}

function sheet_(def) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sh = ss.getSheetByName(def.name);
  if (!sh) {
    sh = ss.insertSheet(def.name);
    sh.appendRow(def.header);
    sh.setFrozenRows(1);
  }
  return sh;
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** Run this once from the editor to check the parser without the phone. */
function testParse() {
  [
    ['notificacion', 'Yapeaste S/ 25.00 a Juan P.'],
    ['notificacion', 'Yape! María L. te envió un pago por S/ 10'],
    ['notificacion', 'Pagaste S/ 1,250.50 a Tienda Sol por QR'],
    ['notificacion', 'Yape actualizó sus términos'],
    ['pantalla', '¡Yapeaste! | S/ | 25.50 | Juan Pérez | 19 nov. 2026 - 10:30 a. m. | Código de seguridad | 123'],
    ['pantalla', '¡Yapeaste! | S/ 8 | a Bodega Rosita | Nro. de operación | 04512345'],
    ['pantalla', '¡Yapeaste!\nS/\n12.90\nAna Torres\nCódigo de seguridad\n456'],
  ].forEach(([src, s]) => {
    const t = src === 'pantalla' ? normalizeScreen_(s) : s;
    Logger.log(src + ': ' + t + '  ->  ' + JSON.stringify(parse_(t, src)));
  });
}
