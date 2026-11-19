# Yape Tracker

App Android que registra los yapeos que **recibes** y los que **haces**, los guarda en el mismo celular y los exporta a Excel.

- **Gastos:** en la pantalla **"¡Yapeaste!"** toca **Compartir → Yape Tracker**. La app lee la constancia en el celular (OCR, sin internet) y llena monto, destinatario, fecha y número de operación. Agrega un concepto si quieres y toca **Guardar**.
- **Ingresos:** se guardan solos cuando llega la notificación de Yape *"… te envió un pago por S/ X"*. Las promociones se ignoran.
- **Excel:** en la app elige **Todos**, **Gastos** o **Ingresos** y toca **Exportar a Excel**. Se guarda un archivo `.xlsx` (por ejemplo en *Descargas*) con fecha, tipo, monto, importe (+/−), persona, número de operación, concepto y una fila de total.

Los datos se guardan **solo en tu celular**, sin cuentas ni servidores. La app nunca envía tus movimientos a ningún lado.

## Instalar el APK

1. En el celular abre la última versión (si el repositorio es privado, inicia sesión en GitHub antes):
   `https://github.com/yurmont/yape-tracker/releases/latest`
2. Descarga **`yape-tracker.apk`** y ábrelo.
3. Si Android lo pide, permite **Instalar apps desconocidas** para el navegador o el gestor de archivos.
4. Abre **Yape Tracker** y toca **Permitir** en el aviso de notificaciones. Activa *Yape Tracker* en la lista. Sin este permiso, solo se registran los gastos.

Las versiones nuevas se instalan encima de la anterior y conservan los datos, porque todas se firman con la misma clave.

## Usar "Compartir → Yape Tracker"

1. Yapea como siempre. En la constancia **"¡Yapeaste!"** toca **Compartir**.
2. En la lista de apps elige **Yape Tracker**. La primera vez puede estar en *Más*.
3. Revisa los datos. Si la operación ya estaba guardada, verás **"Ya registrado"**, y la app te pide confirmar antes de guardarla otra vez.
4. Toca **Guardar**.

Si la lectura falla, toca **Ver texto leído** para ver lo que reconoció el OCR. Corrige los campos a mano. También puedes registrar un gasto sin constancia con **Agregar gasto**. Mantén presionado un movimiento para borrarlo.

## Gastos automáticos (prueba)

Opcional y apagado por defecto. Registra los gastos **sin Compartir**: cuando aparece la constancia **"¡Yapeaste!"**, la app toma una captura interna, que no se guarda en tu galería. Luego la lee en el celular y guarda el gasto si el número de operación es nuevo. Verás el aviso *"Yape Tracker: registrado S/ …"*.

1. En Yape Tracker, en **Gastos automáticos (prueba)**, toca **Activar**.
2. En Accesibilidad, abre **Apps instaladas** (o *Servicios descargados*) → **Yape Tracker: gastos automáticos** y actívalo.
   - En Android 13 o superior, si sale *"Ajuste restringido"*: **Ajustes → Apps → Yape Tracker → ⋮ → Permitir ajustes restringidos**. Luego actívalo otra vez.
3. Haz un yapeo y espera la constancia unos segundos.

Necesita Android 11 o superior. El servicio solo recibe la pantalla de **Yape**, no la de otras apps, y no envía nada a internet. Si algo falla, la sección muestra cuántos eventos y capturas vio y el último resultado. El Compartir sigue funcionando igual.

## Respaldo

Android incluye la base de datos de la app en la copia de seguridad del teléfono (Google One / Drive), si la tienes activada. Si desinstalas la app sin respaldo, los datos se pierden. Exporta a Excel de vez en cuando.

## Compilar (desarrolladores)

El código está en [`android/`](android/). Con JDK 17 y el Android SDK (plataforma 35):

```bash
cd android && ./gradlew testDebugUnitTest assembleDebug
```

Sin JDK ni SDK instalados, [`android/docker-build.sh`](android/docker-build.sh) hace lo mismo dentro de Docker. GitHub Actions ([`.github/workflows/android.yml`](.github/workflows/android.yml)) corre los tests en cada push. En `main` publica el APK firmado como *Release*, siempre que el repositorio tenga estos secretos:

| Secreto | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | el keystore `.jks` en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | contraseña del keystore |
| `ANDROID_KEY_ALIAS` | alias de la clave |
| `ANDROID_KEY_PASSWORD` | contraseña de la clave |

