# AGENTS.md — VibeTube

Cómo trabajar en este repositorio. Léelo junto con `ESTADO.md`.

- Responde y documenta en español.
- App de Android TV en Java (vistas Android, sin Compose) con el lenguaje de diseño «Flotante
  minimalista» de VibeM3U (`../VibeM3U/DISENO.md`): texto que flota sobre el video, una sola capa
  de degradado, foco = blanco en negrita + línea fina, verde #5BE9A0 = en uso/en vivo, cian
  #00B8D9 = avance/activo, resorte `PathInterpolator(0.34, 1.42, 0.64, 1)`.
- Todo cambio visual: primero mockup y esperar «aplícalo» del usuario.
- El núcleo de YouTube es `MediaServiceCore` + `SharedModules` de yuliskov (SmartTube, MIT),
  como submódulos git en la raíz. No editar su código: se actualizan con
  `git submodule update --remote`. YouTube cambia su API seguido; si algo deja de cargar, lo
  primero es actualizar los submódulos.
- Idioma y región: `es` / `CL`, fijados en `TubeApp.onCreate` con `LocaleManager`, antes de
  cualquier pedido al núcleo. Paquete Java y `applicationId`: `cl.vibe.tube`.
- Herramientas fijas por el núcleo: Gradle 7.5, AGP 7.4.2, Kotlin 1.8.10, **JDK 17**
  (`JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"`; el JBR de Android Studio
  es JDK 25 y Gradle 7.5 no corre con él). compileSdk 34, minSdk 21.
- Compilar: `./gradlew.bat :app:assembleDebug`. Probar siempre en el emulador VibeTV y cerrarlo
  al terminar (`adb emu kill`). Usar `adb -s emulator-5554`. Nunca ADB contra la TV Hisense real.
- Sin marca ni logo de YouTube en la app.
- Git: commits locales en español, terminados en la línea de coautoría del modelo que los escribe.
  No hacer push, tags ni releases: los hace el revisor. Nunca `--amend` ni force push, y no borrar
  releases.
- Publicar (lo hace el revisor): desde `main`, subir la etiqueta `vX.Y.Z` que coincida con
  `versionName` de `app/build.gradle`. El workflow `android-release.yml` firma con el secreto
  `VIBETUBE_KEYSTORE_BASE64`, comprueba el certificado y publica `VibeTube-vX.Y.Z.apk` como release
  Latest; el actualizador de la app lo lee desde ahí. La llave es la de `llaves-firma\vibetube.keystore`
  (respaldo fuera del repo) y nunca se cambia. Para compilar la firma de publicación en local, definir
  `VIBETUBE_KEYSTORE_PATH` con la ruta del respaldo; sin esa variable se usa la firma debug.
- Al terminar cualquier cambio, actualiza `ESTADO.md` (y este archivo si cambió la forma de
  trabajar) en el mismo commit.
