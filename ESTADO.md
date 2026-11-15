# ESTADO.md — VibeTube

Última actualización: **2026-11-15** (America/Santiago).

## Hoy, en una mirada

- **Qué es**: cliente de video aparte para Android TV (pedido del usuario: «Haz un app aparte»),
  con el núcleo `MediaServiceCore` de SmartTube (MIT) y la interfaz de VibeM3U. Mockup de
  referencia: `../mockups/cliente-videos/cliente-videos.html` (versión 1, elegida por el usuario).
- **Nombre**: VibeTube (antes VibeVideos). Carpeta `VibeTube`. Paquete, `namespace` y
  `applicationId`: `cl.vibe.tube`. Clase de aplicación: `TubeApp`.
- **Versión**: 0.4.0 (versionCode 4). Entrega 3 de 4 de `04-vibetube.md`: Cuenta, Reproduciendo y
  Calidad, más las dos correcciones de la entrega 2 (Tendencias y directos) y la segunda ronda de
  correcciones de esta misma versión (directos, clic fantasma y ◀ ▶; ver abajo).
- **Probado en el emulador por el revisor** (APK de la primera corrección): título en 2 líneas;
  «DESPUÉS · 39:14»; eyebrow y «Seguir sin cuenta» sin recorte; «Subtítulos» atenuado.
- **Probado por el revisor en el emulador** (commit 71654d0): los directos Meganoticias y CHV
  Noticias reproducen y avanzan; CHV en 1080p VP9.
- **Pendiente de prueba del revisor** (cuarta corrección, sobre 71654d0): ◀ ▶ entre acciones y en la
  franja de Calidad, el clic fantasma, el reintento de directo y su cierre a los 3 s, y el resto de
  la entrega 3. Lo compiló y probó solo la JVM; no se usó el emulador en esta ronda.
- **Idioma y región**: `es` / `CL`, fijados en `TubeApp.onCreate` antes de cualquier pedido.
- **Repositorio**: solo local. El repositorio público `SPxMM3R1/vibetube`, las etiquetas y los
  releases los crea y publica el revisor. CI en `.github/workflows/android-ci.yml`.
- **Escala**: las medidas están en unidades del mockup (1280 × 720). `Ui.init` calcula la escala
  de la pantalla real: en una TV 720p de densidad 1 es 1 dp; en el emulador VibeTV (1080p, 320 dpi)
  es 1,5 px por unidad. Así el diseño es igual en cualquier TV. El reproductor también usa
  `Ui.px` (antes usaba `dp`).
- **Pruebas unitarias**: `QualitiesTest` (agrupación de calidades y etiquetas) y `ManifestsTest`
  (MPD dinámico a estático; directo con margen de segmentos). Corren con
  `./gradlew :app:testDebugUnitTest`.

## Entrega 3 (0.4.0): lo que hay

- **Inicio sin cuenta**: la primera fila es la primera sección con videos, en este orden:
  Música, Noticias, Deportes y Videojuegos (el rótulo toma el nombre de la que se use). «Tendencias»
  ya no aparece: YouTube la retiró en 2025 (`getTrendingObserve` vuelve vacía). La segunda fila es
  «En vivo ahora». Las filas quedan en top 340 y 535.
- **Directos**: en este orden. (1) HLS si `getHlsManifestUrl()` viene. (2) DASH dinámico por URI
  (`vibetube://mpd/<id>`) con `LiveSource`: cada pedido del manifiesto vuelve a llamar a
  `createMpdStreamObservable()`, y los segmentos van por HTTP. El MPD se ajusta con
  `Manifests.toLive(mpd, ahora, startTimeMs)`: `availabilityStartTime` queda anclado a la hora del
  primer segmento del núcleo (`getStartTimeMs()`) más `LIVE_EDGE_MARGIN_SEGMENTS` (3) segmentos;
  sin esa hora (respaldo), se calcula desde «ahora» con tope de 24 h. Se quita el
  `SegmentTimeline`; `suggestedPresentationDelay` es `PT20S`. Cada directo registra una línea
  `Directo: start=… startTimeMs=… segUs=… ast=…` y cada HTTP fallido registra el parámetro `sq`. (3) Si el directo falla, un solo reintento con
  `player.prepare()` (vuelve a pedir el manifiesto); si vuelve a fallar, el OSD dice «Este directo
  no se pudo abrir» y vuelve atrás a los 3 s. Los videos normales siguen con DASH estático
  (`Manifests.toStatic`).
- **Cuenta** (`AccountActivity`, pestaña «Cuenta» al final): código de dispositivo con la URL y el
  código reales del servicio; espera la confirmación en segundo plano; al confirmar vuelve.
  Con sesión: nombre con punto verde, cuentas guardadas (se elige una), «Agregar otra cuenta» y
  «Cerrar sesión» en rosa. Se llega también desde «Iniciar sesión» (Suscripciones y Biblioteca),
  desde «Me gusta», «Guardar» y «Suscribirse» sin cuenta.
- **Reproduciendo** (`PlayerActivity`): eyebrow «canal · vistas · fecha», título 40, barra cian de
  420 con marcas de capítulo, «Después» con el siguiente sugerido, acciones en una fila de 760:
  Me gusta · cantidad, Guardar, Subtítulos, Calidad, Velocidad, Ir al canal. A la derecha, reloj
  de 64, fecha y la línea técnica, alineada con la barra (corrección de la v1). «Subtítulos» y
  «Velocidad» ciclan con OK. Con cuenta: Me gusta y Guardar cambian, y la posición se guarda cada
  30 s y al salir (`updateHistoryPositionObserve`).
- **Calidad**: franja abajo con pantallas anidadas (una por resolución, la mayor abajo a la
  izquierda; verde con relleno la que suena; blanca la que tiene el foco). Auto por defecto. Si
  hay variantes del mismo alto (1080p60 y 1080p), ◀ ▶ sobre la pantalla cambian cuál; el detalle
  muestra cuál. Se aplica con `TrackSelectionOverride`. Audio: las pistas del reproductor.
- **Canal**: «Suscribirse» sin cuenta lleva a Cuenta; con cuenta se suscribe o se quita.

## Decisiones tomadas fuera de la especificación

- **Cuenta por código**: el núcleo no expone la URL ni `isSignedObserve()` para YouTube (el
  inicio de sesión que las trae, `GoogleSignInService`, no está conectado al contenido). La app
  pide el código con `AuthService.getUserCode()` (URL y código reales) y guarda la cuenta
  replicando la lógica pública de `persistRefreshToken` (`Session.addAccount`). `isSignedObserve()`
  es un `Observable` que envuelve `isSigned()`.
- **Guardar** = agregar o quitar el video de «Ver más tarde» (la primera lista de
  `getPlaylistsInfoObserve`).
- **Me gusta** usa un ítem mínimo con el id del video (`Session.item`): el núcleo solo lee el id.
- **Foco en el OSD**: al empezar no hay foco en ninguna acción. OK pausa; Abajo entra a las
  acciones (y las deja enfocables, `armActions`); arriba sale y las vuelve a dejar sin foco. Así
  Android no le da el foco inicial a «Me gusta».
- **Clic fantasma**: `ClickGuard` descarta en Reproductor, Cuenta y Canal el ACTION_UP de OK (o
  del botón A, o Enter) que llega sin su ACTION_DOWN en esa pantalla: es el UP de la tecla que la
  abrió.
- **◀ ▶ en las acciones**: `moveAction` pasa a la acción vecina enfocable y no adelanta ni atrasa
  el video. Sube desde el foco hasta el hijo directo de la fila y, si ese hijo es un grupo, pide el
  foco a su primer descendiente enfocable. Se atienden en `dispatchKeyEvent` (`handleArrowKey`), antes que las vistas: en el emulador
  no llegaban al `onKeyDown`. Fuera de la fila, ◀ ▶ siguen adelantando y atrasando 10 s.
  Las acciones crecen desde su borde izquierdo (`anchor_left` en `Ui.restyle`), como en el mockup.
- **Calidad**: el ancho de cada pantalla es proporcional a su alto frente a la mayor (mínimo 56).
- **Al confirmar la cuenta** la pantalla se cierra y Inicio se vuelve a armar. Si se entró desde el
  reproductor, vuelve al reproductor.

## Limitaciones conocidas

- **Sin cuenta, Inicio solo tiene lo que el núcleo entrega sin sesión** (ver arriba).
- **SABR**: los videos que solo llegan por SABR no reproducen (entrega 4).
- **Subtítulos**: se ciclan las pistas que el reproductor reciba del video. El núcleo no entrega
  subtítulos en el MPD; por eso hoy suelen decir «no hay».
- **Con cuenta** (Inicio con historial, Suscripciones, Biblioteca, Me gusta, Guardar, Suscribirse y
  posición): el código está escrito; la prueba con una cuenta la hace el revisor.
- **Canal sin datos de cabecera**: `getChannelObserve` no entrega suscriptores ni descripción del
  canal.
- **Búsquedas sin canal**: algunos resultados no traen `channelId`.

## Pendiente

1. Entrega 4 (0.5.0): HD con SABR en Media3 (módulo `:sabr`) y `release.yml`.
2. Actualizador (entrega 4).
3. Repositorio `SPxMM3R1/vibetube` en GitHub, con tag y release: lo hace el revisor.
4. Prueba con cuenta real (inicio por código, varias cuentas, Me gusta, Guardar, posición): la hace
   el revisor.

## Bitácora (más reciente arriba)

- 2026-11-15 — 0.4.0, quinta corrección (sin emulador: lo prueba el revisor). `isInside` nunca
  subía desde un `View` (un TextView no es `ViewParent`), así que `inActions()` era siempre falso
  y ◀ ▶ no llegaban a `moveAction`. Ahora compara la vista y sube desde `getParent()`. No había
  otros `instanceof ViewParent` en `app/src`.

- 2026-11-15 — 0.4.0, cuarta corrección (sin emulador: lo prueba el revisor). A: ◀ ▶ se atienden en
  `dispatchKeyEvent` (`handleArrowKey`) antes que las vistas, tanto en la fila de acciones
  (`moveAction`, con `Log.d`) como en las variantes de Calidad (`cycleVariant`); se quitaron los
  `OnKeyListener` de la franja y el bloque de ◀ ▶ de `onKeyDown`. B: el revisor probó los directos
  en el emulador con 71654d0: Meganoticias y CHV Noticias reproducen y avanzan (CHV en 1080p VP9).

- 2026-11-15 — 0.4.0, tercera ronda de correcciones (sin emulador: lo prueba el revisor). A: el
  directo ya no supone que el final de la línea de tiempo es el borde. `Manifests.toLive(mpd, ahora,
  startTimeMs)` ancla `availabilityStartTime` a `getStartTimeMs()` del primer segmento más 3
  segmentos; sin esa hora usa el respaldo con tope de 24 h. Se quitó el `SegmentTimeline`.
  `LiveSource` recibe la hora, el número y la duración del segmento, y registra una vez la línea
  `Directo: …`. El log de HTTP suma el `sq`. B: ◀ ▶ entre acciones busca el hijo directo desde el
  foco, y «Me gusta» crece desde su borde izquierdo. Pruebas JVM: 13 de `ManifestsTest`, 6 de
  `QualitiesTest`.
- 2026-11-15 — 0.4.0, segunda ronda de correcciones (sin emulador: lo prueba el revisor). A:
  `Manifests.toLive` con `LIVE_EDGE_MARGIN_SEGMENTS = 3` y `PT20S`; `ManifestsTest` ajustado y
  con una prueba de línea corta; `PlayerActivity` registra el código HTTP y la URI (200 caracteres)
  y reintenta un directo una vez; el reintento se rearma al empezar a sonar. B: `ClickGuard` en
  Reproductor, Cuenta y Canal; las acciones del OSD no tienen foco inicial. C: ◀ ▶ entre acciones
  con `moveAction`. Se entrega junto con la entrega 3, que quedaba sin commit.
- 2026-11-15 — 0.4.0: Entrega 3. Inicio sin Tendencias (primera sección con videos), directos por
  HLS o DASH dinámico con `LiveSource`, pestaña Cuenta con inicio por código y varias cuentas,
  Reproduciendo con el OSD del mockup (760 dp de acciones, línea técnica a la altura de la barra),
  Calidad con pantallas anidadas y `TrackSelectionOverride`. Clases nuevas: `Session`,
  `AccountActivity`, `LiveSource`, `Qualities`, `Manifests`. `PlayerActivity` reescrita.
  `RowsView.Section` y `Row.loadSection`. Pruebas JVM de `Qualities` y `Manifests`.
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
