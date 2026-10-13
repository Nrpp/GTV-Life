# GTV Life — notas para Claude

App Android TV (Kotlin, sin Compose). Interfaz en español.

## Flujo de trabajo (preferencia del dueño del repo)
Al terminar cada cambio, sin pedir confirmación:
1. `./gradlew testDebugUnitTest assembleDebug` en verde.
2. Push a la rama de trabajo, abrir PR contra `main`, esperar a que pase el check `build` y fusionarlo (squash).
3. Publicar una versión nueva lanzando el workflow `release.yml` (`workflow_dispatch`, input `version`), subiendo
   la versión semántica (fix → parche, funcionalidad → menor). La TV se actualiza sola desde GitHub Releases.
4. Verificar que el lanzamiento tiene el APK firmado.

## Útil
- Capturas de pantalla sin emulador: `./gradlew testDebugUnitTest -Pscreenshots=true` (salida en `app/build/screenshots/`;
  opciones `-PclockStyle=analog|minimal`, `-Paccent=magenta…`, `-Pmodule=<clave>`).
- Configuración: `data/AppConfig.kt` (validación en `fromJson`) + página del móvil `app/src/main/assets/config.html`.
- Módulos extra: `data/ModuleConfig.kt`, `data/ModuleData.kt`, `ui/modules/ModuleUi.kt`.
