# GTV Life

App para **Google TV / Android TV** con un menú principal de módulos:

- **Reloj**: reloj grande a pantalla completa. Si activas la opción en la configuración, **atenúa el brillo** (siempre o solo en un horario nocturno). Con el botón **OK** del mando puedes atenuar o restaurar el brillo a mano. El reloj se desplaza un poco cada minuto para no dejar marcas en la pantalla.
- **Calendario**: los eventos de **hoy** (y de mañana en la vista detallada) de tus calendarios, leídos desde sus **direcciones secretas iCal** (Google Calendar, iCloud, Outlook…). Admite eventos recurrentes, excepciones y eventos de todo el día.
- **Tiempo**: el tiempo de hoy en tu ubicación, **si va a llover y a qué horas**, previsión por horas y para mañana (datos de [Open‑Meteo](https://open-meteo.com), sin API key).

## Configuración con el móvil (QR)

La primera vez que abres la app (o desde **⚙ Configuración**) aparece un **código QR** en la TV:

1. Conecta el móvil a la **misma Wi‑Fi** que la TV.
2. Escanea el QR con la cámara: se abre una página web servida por la propia TV.
3. Busca tu ciudad, pega las direcciones iCal de tus calendarios (con el botón **Probar** compruebas que funcionan) y ajusta el reloj y los módulos.
4. Pulsa **Guardar**: la TV se actualiza sola.

Los datos (incluidas las direcciones secretas iCal) **solo se guardan en la TV**. El QR lleva un código aleatorio que cambia cada vez que abres la pantalla, así que nadie más de tu red puede cambiar la configuración sin ver la TV.

## Actualizaciones automáticas

Cada vez que se abre la app, consulta el **último lanzamiento** de este repositorio en GitHub. Si su versión es mayor que la instalada, descarga el APK adjunto y lo instala:

- La primera vez Android pedirá permiso para que GTV Life **instale aplicaciones desconocidas** (la app te lleva a ese ajuste) y una confirmación de instalación.
- A partir de ahí, en Android 12 o superior las actualizaciones pueden instalarse sin preguntar; en versiones anteriores solo hay que pulsar «Instalar».
- El repositorio tiene que ser **público** para que la TV pueda consultar los lanzamientos sin credenciales.

## Publicar una versión

1. En GitHub: **Releases › Draft a new release**.
2. Crea una etiqueta con el formato **`v1.2.3`** (la versión debe ser mayor que la anterior) y pulsa **Publish release**.
3. El workflow [`Release APK`](.github/workflows/release.yml) compila el APK firmado y lo adjunta al lanzamiento como `GTV-Life-v1.2.3.apk` (tarda unos minutos).

### Firma de los APK (hacerlo una sola vez)

Android solo instala una actualización si está firmada con **la misma clave** que la versión instalada, así que el workflow necesita una clave fija guardada en los *secrets* del repositorio:

```bash
keytool -genkeypair -v -keystore gtv-life.jks -alias gtvlife \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 gtv-life.jks > gtv-life.jks.b64   # en macOS: base64 -i gtv-life.jks
```

En **Settings › Secrets and variables › Actions** crea:

| Secret | Valor |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | contenido de `gtv-life.jks.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | contraseña del keystore |
| `ANDROID_KEY_ALIAS` | `gtvlife` |
| `ANDROID_KEY_PASSWORD` | contraseña de la clave (la misma si no pusiste otra) |

Guarda `gtv-life.jks` en un lugar seguro: si la pierdes, habrá que desinstalar la app y volver a instalarla a mano.

## Primera instalación en la TV

La primera vez hay que instalar el APK a mano (después se actualiza sola):

- Con la app **Downloader** (o similar) en la TV, abre la URL del APK del último lanzamiento, o
- con ADB: `adb connect IP_DE_LA_TV && adb install GTV-Life-v1.0.0.apk`.

## Desarrollo

Proyecto Android nativo en Kotlin (minSdk 26, targetSdk 35).

```bash
./gradlew testDebugUnitTest assembleDebug
```

Las compilaciones locales tienen la versión `0.0.0` y no buscan actualizaciones. El repositorio que se consulta para las actualizaciones se toma de `GITHUB_REPOSITORY` en CI (o de `-PupdateRepo=owner/repo`).
