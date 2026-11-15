# ESTADO.md — VibeTube

Última actualización: **2026-11-15** (America/Santiago).

## Hoy, en una mirada

- **Qué es**: cliente de video aparte para Android TV (pedido del usuario: «Haz un app aparte»),
  con el núcleo `MediaServiceCore` de SmartTube (MIT) y la interfaz de VibeM3U. Mockup de
  referencia: `../mockups/cliente-videos/cliente-videos.html` (versión 1, elegida por el usuario).
- **Nombre**: VibeTube (antes VibeVideos). Carpeta `VibeTube` (antes `VibeVideos`). Paquete,
  `namespace` y `applicationId`: `cl.vibe.tube`. Clase de aplicación: `TubeApp`.
- **Versión**: 0.3.0 (versionCode 3). Entrega 2 de 4 de `04-vibetube.md`: Inicio, Buscar y Canal
  según el mockup v1.
- **Idioma y región**: `es` / `CL`, fijados en `TubeApp.onCreate` antes de cualquier pedido.
- **Repositorio**: solo local. El repositorio público `SPxMM3R1/vibetube`, las etiquetas y los
  releases los crea y publica el revisor. CI en `.github/workflows/android-ci.yml`.
- **Escala**: las medidas están en unidades del mockup (1280 × 720). `Ui.init` calcula la escala
  de la pantalla real: en una TV 720p de densidad 1 es 1 dp; en el emulador VibeTV (1080p, 320 dpi)
  es 1,5 px por unidad. Así el diseño es igual en cualquier TV.
- **Funciona (emulador VibeTV, 2026-11-15)**:
  - Inicio sin cuenta: pestañas, reloj, detalle del video marcado, «EN VIVO AHORA» y la fila
    «TENDENCIAS EN CHILE» (ver limitaciones). Foco con marco, eyebrow con elipsis.
  - Buscar: teclado propio de 30 teclas (a–z, ñ, 1–3), cursor, «Espacio · Borrar · Dictar»,
    sugerencias con el prefijo en negrita, búsqueda 600 ms después de escribir y rejilla de 3.
  - Canal (tecla Menú sobre una tarjeta): nombre, acciones, «MÁS RECIENTES» y «MÁS VISTOS».
    «Ocultar en esta TV» quita el canal de Inicio al volver (se rehace la pantalla).
  - En vivo: filas por grupo. Suscripciones y Biblioteca sin cuenta: aviso y «Iniciar sesión»
    (muestra «Pronto»).
  - Reproducción de videos que no son en vivo (ver «Reproductor» abajo).
- **Limitaciones encontradas**:
  - **Tendencias vacías**: `getTrendingObserve()` (cliente WEB, `FEtrending`) no entrega grupos
    para esta cuenta; el núcleo registra «groups are null or empty» y termina sin datos. La fila
    muestra «Sin videos por ahora.». Falta decidir qué poner en esa fila (ver «Pendiente»).
  - **Inicio sin cuenta solo tiene directos**: lo único que devuelve el núcleo sin sesión son
    transmisiones en vivo. Por eso el Inicio no puede mostrar un video grabado sin cuenta.
  - **Directos no reproducen**: `getFormatInfoObserve` de un directo entrega DASH «dynamic» y
    Media3 lo rechaza (`checkArgument` en `DashMediaSource.Factory.createMediaSource`). Sin HLS
    para esos IDs no hay otra ruta. Es el mismo error de `PlayerActivity` de 0.1.0.
  - **SABR**: algunos videos solo llegan por SABR; el reproductor muestra «Este video solo llega
    por SABR…» (entrega 4).
  - **Calidad**: sin cuenta, YouTube entrega las calidades altas solo por **SABR**; sin DASH ni
    HLS el núcleo solo deja la URL progresiva → **640×360**. Para HD hay que portar el módulo SABR
    de SmartTube a Media3 (entrega 4) o usar su ExoPlayer.
  - **Canal sin datos de cabecera**: `getChannelObserve` no entrega suscriptores ni descripción del
    canal; el encabezado muestra solo el nombre.
  - **Búsquedas sin canal**: algunos resultados no traen `channelId`; al abrir Canal se pide la
    metadata del video. «Ocultar» no filtra esos resultados (no se conoce su canal).

## Decisiones de la entrega 2 (0.3.0)

- **Medidas**: unidades del mockup escaladas al píxel real (`Ui.px`, `Ui.size`). No se usa `dp`.
- **Teclado**: 30 teclas (a–z, ñ, 1–3) como pide la spec; el mockup tenía 28 (cortaba «2» y «3»).
- **Tendencias en el Inicio** y **En vivo ahora**: la portada sin cuenta toma el primer grupo de
  cada una. Con cuenta: «SEGUIR VIENDO» (historial) y luego cada grupo de `getHomeObserve`.
- **Canal**: se omiten los grupos que son solo transmisiones en vivo; «MÁS RECIENTES» es el
  primer grupo con videos y «MÁS VISTOS» el segundo. «Videos» enfoca las filas; «En vivo» y
  «Listas» muestran «Pronto».
- **Canal desde una tarjeta**: tecla Menú (probada) o mantener OK (implementada, no probada).
- **Biblioteca sin cuenta**: «Inicia sesión para ver tu biblioteca.» (la spec decía lo mismo que
  Suscripciones; se usó texto propio).
- **Descripción** del detalle: de `getMetadataObserve` del video, pedida 350 ms después de que
  el foco se detiene. «4K» no aparece en la línea tenue (no hay formato sin reproducir).
- **Foco**: una línea (texto) o un marco (miniatura) que viajan con resorte; las tarjetas se
  levantan 6 % con pivote abajo. Los contenedores de filas y resultados no son enfocables.
- **Sin conexión**: el aviso «Sin conexión a internet…» está implementado (`checkNetwork` en
  `onResume`), pero **no se verificó**: en el emulador el modo avión no cortó la red.
- **Con cuenta**: las ramas (Historial, Suscripciones, Biblioteca, Inicio con cuenta) están
  escritas según la spec, **no probadas** (no hay sesión hasta la entrega 3).
- **Error de fila**: «No se pudo cargar. OK para reintentar.» implementado; no se provocó en el
  emulador.

## Reproductor (arreglo en 0.3.0, commit aparte)

- Para videos que no son en vivo, el MPD que entrega el núcleo se marca `type="static"` antes de
  parsearlo (`PlayerActivity.source`). Probado en el emulador: un video de búsqueda («PAN AMASADO
  RÁPIDO…») y uno de un canal («Investigan hallazgo…», 640 × 360 · 30 fps · H.264) reproducen.

## Pendiente

1. Decidir qué poner en la fila «TENDENCIAS EN CHILE» si YouTube no entrega Tendencias
   (hoy: «Sin videos por ahora.»).
2. Reproducción de directos (DASH dinámico o HLS) y de videos sin formato progresivo; hoy fallan.
3. Entrega 3 (0.4.0): Cuenta, Reproduciendo y Calidad. Aquí se prueban las ramas con cuenta.
4. Entrega 4 (0.5.0): HD con SABR. Decidir si se porta el lector SABR a Media3 o se usa el
   ExoPlayer de SmartTube; `release.yml` con APK firmado.
5. Repositorio `SPxMM3R1/vibetube` en GitHub, con tag y release: lo hace el revisor.

## Bitácora (más reciente arriba)

- 2026-11-15 — 0.3.0: Entrega 2. Inicio (pestañas, detalle, dos filas de alto fijo, sin cuenta
  con Tendencias y En vivo), Buscar (teclado propio, sugerencias, búsqueda con retardo, dictado
  con `RecognizerIntent`), Canal (acciones, «MÁS RECIENTES» y «MÁS VISTOS», ocultar canal en
  esta TV). Clases nuevas: `Ui`, `Focus`, `Tiles`, `RowsView`, `SearchView`, `Hidden`,
  `ChannelActivity`. Reproductor: MPD estático para videos que no son en vivo.
  Probado en el emulador VibeTV con capturas en el scratchpad de la sesión (`vibetube-03`).
- 2026-11-14 — 0.2.0: renombrado a VibeTube (carpeta, paquete `cl.vibe.tube`, `TubeApp`,
  etiqueta y banner vectorial propio con el texto «VibeTube»). Idioma `es`/`CL` en `TubeApp`.
  `README.md`, `LICENSE` MIT propio («VibeTube contributors») y `NOTICE` con los avisos MIT de
  MediaServiceCore y SharedModules. CI `android-ci.yml`: JDK 17 Temurin, submódulos recursivos,
  `assembleDebug` y APK debug como artefacto. Probado en el emulador VibeTV: se desinstaló
  `cl.vibe.videos`, se instaló `cl.vibe.tube` y la búsqueda «masa madre» llega en español.
- 2026-11-14 — 0.1.0: proyecto nuevo con MediaServiceCore + SharedModules (submódulos),
  Inicio/Búsqueda (`MainActivity`), reproductor Media3 con OSD (`PlayerActivity`) y carga de
  miniaturas sin dependencias (`Images`). Probado en el emulador: búsqueda y reproducción a 360p.
