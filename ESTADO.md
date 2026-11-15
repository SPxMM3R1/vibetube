# ESTADO.md — VibeTube

Última actualización: **2026-11-14** (America/Santiago).

## Hoy, en una mirada

- **Qué es**: cliente de video aparte para Android TV (pedido del usuario: «Haz un app aparte»),
  con el núcleo `MediaServiceCore` de SmartTube (MIT) y la interfaz de VibeM3U. Mockup de
  referencia: `../mockups/cliente-videos/cliente-videos.html` (versión 1, elegida por el usuario).
- **Nombre**: VibeTube (antes VibeVideos). Carpeta `VibeTube` (antes `VibeVideos`). Paquete,
  `namespace` y `applicationId`: `cl.vibe.tube`. Clase de aplicación: `TubeApp`.
- **Versión**: 0.2.0 (versionCode 2). Entrega 1 de 4 de `04-vibetube.md`: nombre, idioma,
  repositorio local y CI.
- **Idioma y región**: `es` / `CL`, fijados en `TubeApp.onCreate` antes de cualquier pedido.
  Las filas de búsqueda llegan en español («Resultados de búsqueda de…», «Más de 20 minutos»).
- **Repositorio**: solo local. El repositorio público `SPxMM3R1/vibetube`, las etiquetas y los
  releases los crea y publica el revisor. CI en `.github/workflows/android-ci.yml`.
- **Funciona (emulador VibeTV, 2026-11-14)**: compila con JDK 17 (`assembleDebug`); el banner
  «VibeTube» se ve en el launcher (pestaña Apps); búsqueda sin cuenta de «masa madre» con filas
  de miniaturas. La reproducción (Media3 1.4.1 con OSD propio) se probó en 0.1.0 y no se
  volvió a probar en esta entrega.
- **Limitaciones encontradas**:
  - **Calidad**: sin cuenta, YouTube entrega las calidades altas solo por **SABR**; sin DASH ni
    HLS el núcleo solo deja la URL progresiva → **640×360**. SmartTube lee SABR con un módulo
    de su ExoPlayer 2.10.6 propio (`exoplayer-amzn-2.10.6/library/sabr`, MIT). Para HD hay que
    portar ese módulo a Media3 o usar su ExoPlayer.
  - **Inicio vacío sin cuenta**: YouTube responde «Sign in to see the latest videos…». Hace falta
    iniciar sesión (el núcleo trae el inicio por código de TV) o usar Tendencias como portada.
  - La búsqueda usa el teclado del sistema (IME), que tapa parte de las filas. El teclado propio
    llega con la entrega 2 (0.3.0).

## Pendiente

1. Entrega 2 (0.3.0): Inicio, Buscar y Canal según el mockup v1; Inicio sin cuenta con Tendencias.
2. Entrega 3 (0.4.0): Cuenta, Reproduciendo y Calidad.
3. Entrega 4 (0.5.0): HD con SABR. Decidir si se porta el lector SABR a Media3 o se usa el
   ExoPlayer de SmartTube; `release.yml` con APK firmado.
4. Repositorio `SPxMM3R1/vibetube` en GitHub, con tag y release: lo hace el revisor.

## Bitácora (más reciente arriba)

- 2026-11-14 — 0.2.0: renombrado a VibeTube (carpeta, paquete `cl.vibe.tube`, `TubeApp`,
  etiqueta y banner vectorial propio con el texto «VibeTube»). Idioma `es`/`CL` en `TubeApp`.
  `README.md`, `LICENSE` MIT propio («VibeTube contributors») y `NOTICE` con los avisos MIT de
  MediaServiceCore y SharedModules. CI `android-ci.yml`: JDK 17 Temurin, submódulos recursivos,
  `assembleDebug` y APK debug como artefacto. Probado en el emulador VibeTV: se desinstaló
  `cl.vibe.videos`, se instaló `cl.vibe.tube` y la búsqueda «masa madre» llega en español.
- 2026-11-14 — 0.1.0: proyecto nuevo con MediaServiceCore + SharedModules (submódulos),
  Inicio/Búsqueda (`MainActivity`), reproductor Media3 con OSD (`PlayerActivity`) y carga de
  miniaturas sin dependencias (`Images`). Probado en el emulador: búsqueda y reproducción a 360p.
