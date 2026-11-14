# ESTADO.md — VibeVideos

Última actualización: **2026-11-14** (America/Santiago).

## Hoy, en una mirada

- **Qué es**: cliente de video aparte para Android TV (pedido del usuario: «Haz un app aparte»),
  con el núcleo `MediaServiceCore` de SmartTube (MIT) y la interfaz de VibeM3U. Mockup de
  referencia: `../mockups/cliente-videos/cliente-videos.html`.
- **Versión**: 0.1.0, prueba de concepto. Solo local: todavía no tiene repositorio en GitHub.
- **Funciona (emulador VibeTV, 2026-11-14)**: compila con el núcleo; búsqueda sin cuenta
  («masa madre» → filas de resultados con miniaturas, fondo del video marcado, foco con
  resorte); reproducción con Media3 1.4.1 y OSD propio (canal, título, barra cian, línea técnica).
- **Limitaciones encontradas**:
  - **Calidad**: sin cuenta, YouTube entrega las calidades altas solo por **SABR**; sin DASH ni
    HLS el núcleo solo deja la URL progresiva → **640×360**. SmartTube lee SABR con un módulo
    de su ExoPlayer 2.10.6 propio (`exoplayer-amzn-2.10.6/library/sabr`, MIT). Para HD hay que
    portar ese módulo a Media3 o usar su ExoPlayer.
  - **Inicio vacío sin cuenta**: YouTube responde «Sign in to see the latest videos…». Hace falta
    iniciar sesión (el núcleo trae el inicio por código de TV) o usar Tendencias/Música como
    portada.
  - Idioma y región del núcleo: en-US (títulos de filas en inglés).

## Pendiente

1. Decidir cómo llegar a HD: portar el lector SABR a Media3 (recomendado: un solo reproductor
   moderno) o usar el ExoPlayer de SmartTube.
2. Inicio sin cuenta: Tendencias como portada, o inicio de sesión por código.
3. Idioma/región es-CL en el núcleo.
4. Repositorio en GitHub (preguntar nombre y visibilidad al usuario).

## Bitácora (más reciente arriba)

- 2026-11-14 — 0.1.0: proyecto nuevo con MediaServiceCore + SharedModules (submódulos),
  Inicio/Búsqueda (`MainActivity`), reproductor Media3 con OSD (`PlayerActivity`) y carga de
  miniaturas sin dependencias (`Images`). Probado en el emulador: búsqueda y reproducción a 360p.
