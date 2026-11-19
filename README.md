# yape-tracker

Registra automáticamente en una Google Sheet los yapeos que **recibes** y los que **haces**, desde un celular Android. No hay que programar nada: es un **Google Form** + **MacroDroid** (gratis) + unas fórmulas.

```
Te yapean ──► notificación de Yape ──► MacroDroid (macro 1) ─┐
                                                             ├─ HTTP POST ─► Google Form ─► Hoja "Respuestas" (texto crudo)
Yapeas ─────► pantalla "¡Yapeaste!" ─► MacroDroid (macro 2) ─┘                                       │
                                                                                                     ▼
                                                                         Hoja "Movimientos" (fórmulas: tipo, monto, persona)
```

Yape muestra una notificación cuando te pagan, pero aparentemente no cuando pagas tú. Por eso los gastos se capturan leyendo la pantalla de constancia (permiso de accesibilidad de Android).

---

## 1. Google Form (en la PC, ~5 min)

1. Entra a [forms.new](https://forms.new). Título: `Yape tracker`.
2. Crea **dos preguntas**, sin marcarlas como obligatorias:
   - `origen` → tipo **Respuesta corta**
   - `texto` → tipo **Párrafo**
3. **Configuración → Respuestas:** desactiva *Recopilar direcciones de correo electrónico* y *Limitar a 1 respuesta*. El formulario no debe pedir iniciar sesión.
4. Pestaña **Respuestas → Vincular con Hojas de cálculo → Crear una hoja de cálculo nueva**. Se crea una hoja con la pestaña `Respuestas de formulario 1`.

### Obtener la URL y los IDs de los campos

5. En el formulario: menú **⋮ → Obtener enlace prellenado**.
6. Escribe `X` en *origen* y `Y` en *texto* → **Obtener vínculo → Copiar vínculo**. Se ve así:

   ```
   https://docs.google.com/forms/d/e/1FAIpQL...xyz/viewform?usp=pp_url&entry.111111111=X&entry.222222222=Y
   ```

7. Anota tres cosas:
   - **URL de envío:** la parte hasta `/viewform`, cambiando `viewform` por `formResponse`:
     `https://docs.google.com/forms/d/e/1FAIpQL...xyz/formResponse`
   - **ID de origen:** `entry.111111111` (el que tenía `X`)
   - **ID de texto:** `entry.222222222` (el que tenía `Y`)

8. **Prueba:** abre en el navegador (con tus valores):

   ```
   https://docs.google.com/forms/d/e/1FAIpQL...xyz/formResponse?entry.111111111=prueba&entry.222222222=Yape!%20Ana%20te%20envi%C3%B3%20un%20pago%20por%20S/%201
   ```

   Debe decir *"Se registró tu respuesta"* y aparecer una fila nueva en la hoja.

## 2. Pestaña "Movimientos" (fórmulas)

En la hoja vinculada, crea una pestaña nueva llamada `Movimientos`. Escribe estos encabezados en la fila 1 y pega cada fórmula en la **fila 2** de su columna. Cada fórmula se extiende sola a todas las filas.

| Col | Encabezado | Fórmula (fila 2) |
|-----|------------|------------------|
| A | Fecha | `=ARRAYFORMULA(IF('Respuestas de formulario 1'!A2:A="",,'Respuestas de formulario 1'!A2:A))` |
| B | Tipo | `=ARRAYFORMULA(IF(A2:A="",,IF('Respuestas de formulario 1'!B2:B="pantalla","Gasto",IF(REGEXMATCH(LOWER('Respuestas de formulario 1'!C2:C),"te envi\|te yape\|recibiste\|te pag"),"Ingreso",IF(REGEXMATCH('Respuestas de formulario 1'!C2:C,"S/"),"Gasto","Otro")))))` |
| C | Monto | `=ARRAYFORMULA(IF(A2:A="",,IFERROR(VALUE(SUBSTITUTE(REGEXEXTRACT('Respuestas de formulario 1'!C2:C,"S/\.?[\s\|]*(\d[\d,]*\.?\d*)"),",","")))))` |
| D | Persona | `=ARRAYFORMULA(IF(A2:A="",,IFERROR(TRIM(REGEXEXTRACT('Respuestas de formulario 1'!C2:C,"([A-ZÁÉÍÓÚÑa-záéíóúñ. ]+?) te (?:envi\|yape\|pag)")),IFERROR(TRIM(REGEXEXTRACT('Respuestas de formulario 1'!C2:C,"(?m)\ba ([^\|\n]+?)(?: por\|\.?$\|\s*\\\|)")),""))))` |
| E | Importe | `=ARRAYFORMULA(IF(B2:B="Ingreso",C2:C,IF(B2:B="Gasto",-C2:C,)))` |
| F | Duplicado | `=ARRAYFORMULA(IF(A2:A2000="",,IF(IFERROR((B2:B2000=B1:B1999)*(C2:C2000=C1:C1999)*(A2:A2000-A1:A1999<3/1440),0),"Sí","")))` |

> En la tabla de GitHub, los `\|` son solo para que se muestre bien. **Copia las fórmulas desde [formulas.txt](formulas.txt)**, que las tiene listas para pegar.

- **Tipo:** todo lo que viene de la pantalla es *Gasto*; las notificaciones con "te envió / te yapeó…" son *Ingreso*.
- **Importe:** positivo para ingresos y negativo para gastos, para sumarlo directo.
- **Duplicado:** marca *Sí* cuando el mismo tipo y monto se repite en menos de 3 minutos (la pantalla se lee varias veces).

**Totales** (por ejemplo en `H1` y `H2`):

```
=SUMIFS(C2:C,B2:B,"Ingreso",F2:F,"<>Sí")
=SUMIFS(C2:C,B2:B,"Gasto",F2:F,"<>Sí")
```

**Si una fórmula da "Error de análisis":** tu hoja usa `;` como separador. Reemplaza las `,` que separan argumentos por `;`. **Si un monto como 25.00 aparece como 2500:** en **Archivo → Configuración → Configuración regional**, elige **Perú** o **Estados Unidos** (punto decimal).

## 3. MacroDroid (en el celular, ~10 min)

Instala **MacroDroid** (gratis, hasta 5 macros; aquí se usan 2). En las dos macros, la acción **Petición HTTP** es igual:
- Método: **POST**
- URL: tu **URL de envío** (`…/formResponse`)
- Cuerpo: **Form data**

### Macro 1: Ingresos (notificaciones)

**Trigger:** Eventos del dispositivo → **Notificación** → Notificación recibida → App: **Yape**, contenido: **Cualquiera**. Concede el *acceso a notificaciones*.

**Acción:** Petición HTTP con:

| Clave | Valor |
|-------|-------|
| `entry.111111111` (tu ID de origen) | `notificacion` |
| `entry.222222222` (tu ID de texto)  | `[notification_title] [notification]` (Magic text) |

### Macro 2: Gastos (pantalla de constancia)

Crea antes una variable global de texto llamada `yape_pantalla` (MacroDroid → Variables).

**Trigger:** **Screen content** ([wiki](https://macrodroidforum.com/wiki/index.php/Trigger:_Screen_content))
- App: **Yape** (no "Todas las aplicaciones")
- Texto: `*Yapeaste*`, al **aparecer**
- Frecuencia de lectura: **1–2 s**
- Concede el permiso de **accesibilidad**.

**Acciones:**
1. **Read Screen Content** → guardar en `yape_pantalla`
2. **Petición HTTP** con:

| Clave | Valor |
|-------|-------|
| `entry.111111111` | `pantalla` |
| `entry.222222222` | `[lv=yape_pantalla]` (Magic text → variable) |

En las opciones de la macro, evita que se repita en menos de **10 s**.

**Si no detecta la pantalla:** prueba el trigger **Screenshot Content** ([wiki](https://macrodroidforum.com/wiki/index.php/Trigger:_Screenshot_Content)), que lee la pantalla con OCR. Es menos confiable, pero sirve de respaldo.

### Batería

Ajustes → Apps → MacroDroid → Batería → **Sin restricciones**. Si no, Android puede cerrarla y se pierden movimientos.

## 4. Probar

1. Pide que te yapeen S/ 1 → debe aparecer en *Movimientos* como **Ingreso**.
2. Yapea S/ 1 → debe aparecer como **Gasto**.
3. Si algo sale mal, mira el texto crudo en `Respuestas de formulario 1` y ajusta las fórmulas de *Movimientos*.

## Limitaciones

- **Cambios en Yape:** si Yape cambia el texto o el diseño de la constancia, los gastos dejan de capturarse hasta ajustar el filtro.
- **Persona:** si en la pantalla el nombre aparece solo, sin "a" delante, la columna Persona queda vacía. Ajusta la fórmula D con el texto real.
- **Historial:** abrir el detalle de un yapeo antiguo puede registrarlo como gasto nuevo. Bórralo de la hoja de respuestas.
- **URL del formulario:** cualquiera que tenga la URL de envío puede agregar filas. No la compartas.
- **Accesibilidad:** con ese permiso, MacroDroid puede leer todo lo que hay en pantalla. Mantén el filtro solo en Yape.

## Alternativa avanzada: Apps Script

[apps-script/Code.gs](apps-script/Code.gs) hace lo mismo con un webhook propio. Separa ingresos y gastos en pestañas, elimina duplicados en el servidor y exige una clave secreta. Requiere publicar el script como aplicación web; las instrucciones están en los comentarios del archivo.
