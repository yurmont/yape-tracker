# yurmont-yape

Registra automáticamente en una Google Sheet los yapeos que **recibes** y los que **haces**, desde un celular Android. Solo usa apps gratuitas: **MacroDroid** (versión gratis, hasta 5 macros; aquí se usan 2) y Google Sheets.

```
Te yapean ──► notificación de Yape ──► MacroDroid (macro 1) ─┐
                                                             ├─ HTTP POST ─► Apps Script ─► Google Sheet
Yapeas ─────► pantalla "¡Yapeaste!" ─► MacroDroid (macro 2) ─┘                              ├─ Raw      (todo lo recibido)
                                                                                            ├─ Gastos   (lo que pagaste)
                                                                                            └─ Ingresos (lo que recibiste)
```

Yape muestra una notificación cuando te pagan, pero aparentemente no cuando pagas tú. Por eso los gastos se capturan leyendo la pantalla de constancia con el permiso de accesibilidad de Android.

## 1. Google Sheet + Apps Script

1. Crea una Google Sheet nueva (por ejemplo "Finanzas personales").
2. **Extensiones → Apps Script**. Reemplaza el contenido de `Code.gs` por el de [Code.gs](Code.gs).
3. Cambia `SECRET` por una cadena larga y aleatoria y guarda.
4. Opcional: selecciona `testParse` y pulsa **Ejecutar** para ver el parser en el registro (la primera vez pide permisos).
5. **Implementar → Nueva implementación → Aplicación web**
   - Ejecutar como: **Yo**
   - Quién tiene acceso: **Cualquier persona** (lo protege el `SECRET`)
6. Copia la **URL de la aplicación web** (`https://script.google.com/macros/s/…/exec`). Al abrirla en el navegador debe mostrar `ok`.

> Cada vez que cambies el script: **Implementar → Administrar implementaciones → Editar → Nueva versión**. Si no, la URL sigue ejecutando el código anterior.

## 2. Ingresos: macro 1 (notificaciones)

**Trigger:** Eventos del dispositivo → **Notificación** → Notificación recibida
- Aplicación: **Yape**
- Contenido: **Cualquiera**
- Concede a MacroDroid el *acceso a notificaciones*.

**Acción:** Conectividad → **Petición HTTP**
- Método: **POST**, URL: la de la aplicación web
- Cuerpo: **Form data** con estos campos:

| Clave  | Valor |
|--------|-------|
| token  | tu `SECRET` |
| source | `notificacion` |
| title  | `[notification_title]` (Magic text → título de la notificación) |
| text   | `[notification]` (Magic text → texto de la notificación) |

## 3. Gastos: macro 2 (pantalla de constancia)

Crea una variable global de tipo texto, por ejemplo `yape_pantalla` (MacroDroid → Variables).

**Trigger:** **Screen content** ([wiki](https://macrodroidforum.com/wiki/index.php/Trigger:_Screen_content))
- Aplicación: **Yape** (no uses "Todas las aplicaciones", consume más batería)
- Texto a detectar: `*Yapeaste*` (con comodines), al **aparecer**
- Frecuencia de lectura: **1–2 s**
- Concede a MacroDroid el permiso de **accesibilidad** cuando lo pida.

**Acciones** (en orden):
1. **Read Screen Content** → guardar el texto en `yape_pantalla`
2. **Petición HTTP**: método **POST**, URL de la aplicación web, cuerpo **Form data**:

| Clave  | Valor |
|--------|-------|
| token  | tu `SECRET` |
| source | `pantalla` |
| text   | `[lv=yape_pantalla]` (Magic text → variable `yape_pantalla`) |

**Restricción (recomendada):** en las opciones de la macro, evita que se repita en menos de **10 s**, porque la pantalla se redibuja varias veces. El script además descarta el mismo pago durante 3 minutos.

El texto de pantalla puede llegar con un bloque por línea; el script lo convierte a `a | b | c` antes de leerlo.

**Si no detecta nada:** algunas apps no dejan leer su texto. Prueba el trigger **Screenshot Content** ([wiki](https://macrodroidforum.com/wiki/index.php/Trigger:_Screenshot_Content)), que lee la pantalla con OCR. Es menos confiable, pero sirve como respaldo.

## 4. Batería

Ajustes → Apps → MacroDroid → Batería → **Sin restricciones**. Si no, Android puede cerrarla y se pierden movimientos.

## 5. Calibrar con datos reales

El parser se escribió con textos de ejemplo, no con pantallas reales de Yape. Después de tus primeros movimientos:

1. Abre la hoja **Raw** y mira el texto exacto que llegó y cómo se clasificó.
2. Si un monto o un destinatario sale mal, ajusta `EXPENSE_PATTERNS` / `INCOME_PATTERNS` o `screenParty_()` en el script y publica una **nueva versión**.

## Limitaciones conocidas

- **Cambios en Yape:** si Yape cambia el texto o el diseño de la constancia, la captura de gastos deja de funcionar hasta ajustar el filtro. Si un gasto no aparece en Raw, revisa esto primero.
- **Historial:** abrir el detalle de un yapeo antiguo puede mostrar "Yapeaste" y registrarlo como gasto nuevo. Bórralo de la hoja si pasa.
- **Pagos repetidos:** dos pagos con el mismo monto a la misma persona en menos de 3 minutos (`DEDUP_SECONDS`) se registran una sola vez.
- **Accesibilidad:** con ese permiso, MacroDroid puede leer todo lo que hay en pantalla. Mantén el filtro solo en la app Yape. Si Yape muestra advertencias o se niega a abrir con el servicio activo, desactívalo y usa otra fuente (correo o SMS).
- **Categoría y Nota:** en Gastos se llenan a mano, o se pueden agregar reglas al script (destinatario → categoría).
