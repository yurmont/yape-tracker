# Yape Tracker

**Yape Tracker** es una app para Android que anota tus yapeos por ti: lo que **pagas** y lo que **te pagan**. Todo queda guardado en tu celular y, cuando quieras, lo pasas a un archivo de **Excel**.

- 💸 **Cuando yapeas**, se anota solo al aparecer la pantalla **"¡Yapeaste!"**.
- 💰 **Cuando te yapean**, se anota solo con la notificación de Yape.
- 📊 **Exportas a Excel** tus gastos, tus ingresos o todo junto, con el total.
- 🔒 **Privado**: tus datos nunca salen del celular. La app no usa internet, no pide cuenta ni contraseña.

> Yape Tracker no es una app oficial de Yape ni del BCP. No puede mover tu dinero: solo lee lo que Yape te muestra.

---

## Qué necesitas

- Un celular **Android 8 o más nuevo** con la app **Yape**.
- Para que los pagos que **haces** se anoten solos: **Android 11 o más nuevo**. En celulares más antiguos se anotan con el botón **Compartir** (ver [Opcional: anotar con Compartir](#opcional-anotar-con-compartir)).

---

## 1. Instalar

1. Desde el celular, abre este enlace y descarga el archivo:
   **https://github.com/yurmont/yape-tracker/releases/latest/download/yape-tracker.apk**
2. Abre el archivo descargado (`yape-tracker.apk`) y toca **Instalar**.
3. Si el celular dice **"Instalar apps desconocidas"**, toca **Configuración**, activa **Permitir de esta fuente** y vuelve atrás.
4. Si aparece **Play Protect** con un aviso de *"app bloqueada"* o *"puede acceder a datos privados"*:
   toca **Más detalles** y luego **Instalar de todos modos**.

   ¿Por qué sale? La app no viene de Play Store y pide leer notificaciones. Es un aviso general, no significa que tenga virus.

---

## 2. Activar los permisos (una sola vez)

Abre **Yape Tracker**. Arriba verás **Registro automático** con dos líneas:

```
Registro automático
Pagos que haces: apagado        ACTIVAR
Pagos que recibes: apagado      ACTIVAR
```

Toca cada **Activar** y sigue los pasos de abajo. Cuando las dos líneas muestren **✓**, ya está todo listo:

```
Registro automático
✓ Pagos que haces
✓ Pagos que recibes
```

### a) Pagos que haces

1. Junto a *Pagos que haces*, toca **Activar**.
2. Entra a **Aplicaciones instaladas** (o *Servicios descargados*) → **Yape Tracker** y activa el interruptor de arriba. Deja apagado el *"Atajo"*.
3. Si el interruptor está **gris** o sale **"Ajuste restringido"**, Android lo bloquea porque la app no viene de Play Store. Para desbloquearlo:
   1. Toca **Aceptar** en el aviso.
   2. Ve a **Ajustes → Aplicaciones → Yape Tracker**.
   3. Toca **⋮** (tres puntos, arriba a la derecha) → **Permitir ajustes restringidos** y confirma con tu PIN o huella.
   4. Vuelve al paso 2 y activa el interruptor.
4. Android mostrará un aviso como *"¿Permitir control total?"*. Es el texto que sale para cualquier app de este tipo. Toca **Permitir**.

¿Qué ve la app con este permiso? **Solo la app Yape.** Cuando aparece "¡Yapeaste!", toma una foto interna de la pantalla, que **no se guarda en tu galería**. La lee en el celular para sacar el monto, la persona y el número de operación, y la descarta.

### b) Pagos que recibes

1. Junto a *Pagos que recibes*, toca **Activar**.
2. En la lista, activa **Yape Tracker** y confirma.

---

## Cómo se usa

Con el registro automático activado, **no tienes que hacer nada**: usa Yape como siempre.

### Cuando yapeas (pagas)

1. Yapea como siempre.
2. Cuando aparezca la pantalla **"¡Yapeaste!"**, quédate en ella unos **3 segundos**.
3. Aparece el mensaje *"Yape Tracker: registrado S/ …"*. Listo.

Cada yapeo se anota **una sola vez**: la app lo reconoce por su **número de operación**.

> Si abres en el historial de Yape el detalle de un yapeo **antiguo**, de antes de instalar la app, puede anotarse como gasto nuevo. Si pasa, bórralo (ver abajo).

### Cuando te yapean (cobras)

No haces nada. Cuando llega la notificación de Yape *"… te envió un pago por S/ …"*, se anota sola con el nombre y el monto. Las promociones de Yape se ignoran.

### Anotar algo a mano

Toca **Agregar gasto**, escribe el monto y lo demás, y toca **Guardar**.

### Borrar un movimiento

**Mantén presionado** el movimiento en la lista y confirma **Borrar**.

---

## Opcional: anotar con Compartir

No lo necesitas si el registro automático funciona. Úsalo cuando:

- tu celular tiene **Android 10 o más antiguo**;
- tienes apagado *Pagos que haces*;
- un pago **no se anotó solo** (la app te lo avisa en *Registro automático*);
- quieres agregar un **Concepto** (por ejemplo *"almuerzo"*) al momento de anotarlo.

Pasos:

1. En la pantalla **"¡Yapeaste!"** de Yape, toca **Compartir**.
2. Elige **Yape Tracker**. La primera vez puede estar en *Más*.
3. Revisa los datos, escribe un **Concepto** si quieres y toca **Guardar**.

Si ese yapeo ya estaba anotado, la app te avisa **"Ya registrado"** y te pregunta antes de guardarlo otra vez.

---

## Exportar a Excel

1. Arriba, elige qué quieres exportar: **Todos**, **Gastos** o **Ingresos**.
2. Toca **Exportar a Excel**.
3. Elige dónde guardarlo (por ejemplo **Descargas**) y toca **Guardar**.

El archivo `.xlsx` se abre en Excel, Google Sheets o WPS. Trae una fila por movimiento y una fila de **Total** al final:

| Columna | Qué es |
|---|---|
| Registrado | Fecha y hora en que se anotó |
| Tipo | Gasto o Ingreso |
| Monto | El monto del yapeo |
| Importe | Igual que Monto, pero **negativo para gastos**: sumándolo obtienes tu saldo |
| Persona | A quién pagaste o quién te pagó |
| Fecha en Yape | La fecha que muestra la constancia |
| Nro. de operación | El número de la constancia |
| Concepto | Lo que escribiste |
| Texto | El texto original de la constancia o la notificación |

---

## Actualizar la app

Descarga e instala el mismo enlace del paso 1. La versión nueva se instala **encima** de la anterior y **conserva tus movimientos**.

---

## Tus datos

- Se guardan **solo en tu celular**. Nadie más los ve.
- La app **no usa internet**. Tampoco tiene cuentas, anuncios ni rastreo.
- Si **desinstalas** la app, los movimientos se borran. Exporta a Excel de vez en cuando como respaldo.
- Si tienes activada la copia de seguridad de Google en tu celular, Android puede incluir los datos de la app.

---

## Problemas frecuentes

| Problema | Solución |
|---|---|
| Play Protect bloquea la instalación | **Más detalles → Instalar de todos modos**. Si no aparece: Play Store → tu foto → Play Protect → ⚙️ → desactiva *Analizar apps*, instala y **vuelve a activarlo**. |
| El interruptor de Yape Tracker en Accesibilidad está gris | Sigue el paso **2a-3**: *Permitir ajustes restringidos*. |
| Un pago que hiciste no se anotó solo | Quédate unos segundos en "¡Yapeaste!". Si la app no pudo leer la constancia, lo avisa en **Registro automático**. Anótalo con [Compartir](#opcional-anotar-con-compartir). |
| Los pagos que recibes no aparecen | Revisa que *Pagos que recibes* tenga **✓** (paso 2b). En Samsung, Xiaomi u Oppo: Ajustes → Apps → Yape Tracker → Batería → **Sin restricciones**. |
| Una línea de Registro automático dice "apagado" | Toca **Activar** junto a ella. A veces Android la apaga después de actualizar la app. |
| Un dato salió mal leído | Bórralo y anótalo de nuevo con [Compartir](#opcional-anotar-con-compartir), donde puedes corregirlo antes de **Guardar** (toca **Ver texto leído** para ver lo que leyó). |
| "No se puede instalar" al actualizar | Tienes una versión firmada de otra forma. Exporta a Excel, desinstala y vuelve a instalar. |

---

## Para desarrolladores

El código está en [`android/`](android/) (Kotlin, sin AndroidX). Los detalles de diseño están en [docs/DESIGN.md](docs/DESIGN.md).

```bash
cd android && ./gradlew testDebugUnitTest assembleDebug
```

Sin JDK ni Android SDK, [`android/docker-build.sh`](android/docker-build.sh) compila dentro de Docker. GitHub Actions ([`.github/workflows/android.yml`](.github/workflows/android.yml)) corre las pruebas en cada push. En `main` publica el APK firmado como *Release* si el repositorio tiene los secretos `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD`.
