# ESTADO.md — VibeTube

Última actualización: **2026-11-15** (America/Santiago).

## Hoy, en una mirada

- **Qué es**: cliente de video aparte para Android TV (pedido del usuario: «Haz un app aparte»),
  con el núcleo `MediaServiceCore` de SmartTube (MIT) y la interfaz de VibeM3U. Mockup de
  referencia: `../mockups/cliente-videos/cliente-videos.html` (versión 1, elegida por el usuario).
- **Nombre**: VibeTube (antes VibeVideos). Carpeta `VibeTube`. Paquete, `namespace` y
  `applicationId`: `cl.vibe.tube`. Clase de aplicación: `TubeApp`.
- **Versión**: 0.4.2 (versionCode 6): arreglos de navegación con el control (pestañas, ▲ desde la
  primera fila, teclado de Buscar y tarjeta con foco dentro del margen). Sin emulador: lo prueba el
  revisor. Antes, 0.4.1 (versionCode 5). Entrega 4 de `04-vibetube.md`, en dos partes: publicación
  firmada y actualizador (esta versión, 0.4.1) y HD con SABR (0.5.0, pendiente). Lo de 0.4.0
  (Cuenta, Reproduciendo, Calidad, directos y correcciones) sigue igual; ver abajo.
- **Probado en el emulador por el revisor** (APK de la primera corrección): título en 2 líneas;
  «DESPUÉS · 39:14»; eyebrow y «Seguir sin cuenta» sin recorte; «Subtítulos» atenuado.
- **Probado por el revisor en el emulador**: los directos Meganoticias y CHV Noticias reproducen y
  avanzan (commit 71654d0; CHV en 1080p VP9). Con 36028e8: ◀ ▶ entre acciones (log «moveAction de 0
  a 1», salta «Subtítulos») y la franja de Calidad con CHV (1080p, 5.4 Mbps, 30 fps).
- **Pendiente de prueba del revisor** (octava corrección, sobre 9ef9c14): el orden de fuentes de
  los directos como SmartTube (DASH generado, DASH de YouTube y HLS, tope de 20 s). Antes, con la
  séptima corrección (7a8ffc6 y 9ef9c14), «Suscribirse» sin recorte en Canal y los demás
  recortes de Inicio y Buscar. Sin emulador en esta ronda: lo compiló y probó solo la JVM. Antes,
  con la sexta corrección, quedaban por probar las etiquetas de Calidad y los rótulos de audio, el
  clic fantasma, el reintento de directo y su cierre a los 3 s, y el resto de la entrega 3.
- **Idioma y región**: `es` / `CL`, fijados en `TubeApp.onCreate` antes de cualquier pedido.
- **Repositorio**: `SPxMM3R1/vibetube` en GitHub (rama `main`). Las etiquetas y los releases los
  sube el revisor; la forma de publicar está en «Publicación y actualizador» más abajo. CI de cada
  push en `.github/workflows/android-ci.yml`; publicación en `android-release.yml`.
- **Escala**: las medidas están en unidades del mockup (1280 × 720). `Ui.init` calcula la escala
  de la pantalla real: en una TV 720p de densidad 1 es 1 dp; en el emulador VibeTV (1080p, 320 dpi)
  es 1,5 px por unidad. Así el diseño es igual en cualquier TV. El reproductor también usa
  `Ui.px` (antes usaba `dp`).
- **Pruebas unitarias**: `QualitiesTest` (agrupación de calidades y etiquetas), `ManifestsTest`
  (MPD dinámico a estático; directo con margen de segmentos), `LiveOrderTest`, `AudioLabelsTest` y
  `UpdateRulesTest` (versiones, nombre del APK, descarga y horario del actualizador). Corren con
  `./gradlew :app:testDebugUnitTest`.

## Navegación con el control (0.4.2)

- **Barra de pestañas**: ◀ ▶ solo mueven el foco entre pestañas; no cambian la sección. OK sobre
  una pestaña muestra su sección y deja el foco en la barra. Si el foco se queda 600 ms sobre una
  pestaña, su sección se muestra también sin quitarle el foco a la barra (`DWELL_MS`). Solo ▼ baja
  al contenido. «Cuenta» no es sección: solo OK la abre.
- **▼ desde la barra**: a la última tarjeta (o tecla) con foco de esa sección, o a la primera. Si el
  aviso de versión está visible (solo en Inicio), ▼ va primero al aviso, que está justo debajo de la
  barra; así sigue siendo alcanzable. Es una decisión fuera de la regla «▼ vuelve a la tarjeta».
- **▲ desde la primera fila** de Inicio, Suscripciones, En vivo y Biblioteca, desde la fila de
  letras o la primera fila de resultados de Buscar, desde el aviso de versión o desde «Iniciar
  sesión» lleva siempre a la pestaña de la sección visible. Se decide en `MainActivity.navigate`
  (`dispatchKeyEvent`), con `RowsView.inTopRow` y `SearchView.inTopRow`, y no por la geometría de
  Android.
- **Buscar**: el teclado tiene a–z, ñ y 0–9 completos, en filas de 7 (37 teclas), con los números al
  final.
- **Filas**: la fila termina a 64 dp del borde derecho (`rightMargin`). Al tomar el foco, la tarjeta
  pide que se vea también lo que crece (`Tiles.revealGrown`, 14 dp: la miniatura al 106 % más el
  marco de 6), así que no sale de la pantalla.
- **Causas de la 0.4.1 (lo que el revisor vio)**:
  - ▲ desde la primera fila: la búsqueda geométrica de Android no tenía regla para la fila superior;
    en la columna 0 no encontraba la pestaña activa y desde la columna 2 caía en la que quedaba
    encima (Buscar). Causa probable; no reproducida sin emulador.
  - Sección que cambiaba al pasar el foco: en 0.4.1 el único cambio de sección era el clic (OK) de la
    pestaña, y ese clic llevaba además el foco al contenido (`select` → `focusFirst`). No encontré otro
    camino en el código; no reproducida sin emulador.
  - Tarjeta cortada a la derecha: Android solo desplaza hasta que la tarjeta sin crecer cabe en el
    borde (1280 dp = 1920 px); la miniatura al 106 % y el marco sobresalían 12 a 14 dp.
  - Teclado de Buscar: solo tenía 1, 2 y 3 como números.

## Publicación y actualizador (0.4.1)

- **Cómo se publica** (`.github/workflows/android-release.yml`, «Publicar APK»): desde `main`, el
  revisor sube la etiqueta `vX.Y.Z`. El workflow comprueba que `versionName` (en `app/build.gradle`)
  sea `X.Y.Z`, restaura la llave desde el secreto `VIBETUBE_KEYSTORE_BASE64`, corre
  `:app:testDebugUnitTest :app:assembleDebug`, verifica con `apksigner` que el certificado tenga el
  SHA-256 `87b1fb67…eaac`, y publica `VibeTube-vX.Y.Z.apk` con `gh release create --verify-tag
  --generate-notes`. GitHub lo deja como Latest.
- **Llave de firma**: PKCS12, alias `androiddebugkey`, contraseñas `android` (en `build.gradle`).
  Respaldo: `D:\Users\SP4MM3R\Documents\Codex\llaves-firma\vibetube.keystore`. **Nunca cambiarla**:
  el actualizador solo instala un APK firmado con la misma llave que la app ya instalada. Sin la
  variable `VIBETUBE_KEYSTORE_PATH` (en el computador de desarrollo) se compila con la firma debug.
- **Actualizador** (`AppUpdater`, `UpdateRepository`, `UpdateRules`, `UpdateInfo`, `VibeFileProvider`):
  - Al abrir la app (`MainActivity.onCreate`) revisa `releases/latest` como máximo cada 6 horas,
    contadas desde el último intento (también si falló). El resultado se guarda en las preferencias
    `vibetube_update`.
  - **Inicio**: si hay una versión nueva, una línea bajo la barra, «● Hay una versión nueva · X ·
    OK para instalar». OK descarga el APK (muestra el porcentaje), comprueba que sea del paquete
    `cl.vibe.tube`, de `versionCode` mayor y con la misma firma, y abre el instalador de Android
    (`REQUEST_INSTALL_PACKAGES`, FileProvider con `xml/file_paths`). Sin permiso de instalar lleva a
    los ajustes y, al volver, sigue.
  - **Cuenta**, fila «Actualizaciones» al pie: «VibeTube 0.4.1 · revisado hoy a las 16:02 · estás
    al día» y «Buscar ahora» (revisa sin esperar las 6 horas).
  - El APK es el primer archivo del release cuyo nombre empieza por `VibeTube-` y termina en `.apk`.
    La descarga solo se acepta desde `https://github.com/SPxMM3R1/vibetube/releases/download/`.
- **Pruebas JVM**: `UpdateRulesTest` (orden de versiones, nombre del APK, dirección de descarga y
  horario de revisión).
- **Sin probar en el emulador** (pedido del usuario): el aviso, «Buscar ahora», la descarga y la
  instalación las prueba el revisor.

## Entrega 3 (0.4.0): lo que hay

- **Inicio sin cuenta**: la primera fila es la primera sección con videos, en este orden:
  Música, Noticias, Deportes y Videojuegos (el rótulo toma el nombre de la que se use). «Tendencias»
  ya no aparece: YouTube la retiró en 2025 (`getTrendingObserve` vuelve vacía). La segunda fila es
  «En vivo ahora». Las filas quedan en top 340 y 535.
- **Directos**: orden de fuentes como en SmartTube (`VideoLoaderController`), decidido por el
  revisor el 2026-11-15. El HLS de este cliente se queda colgado en algunos directos (Meganoticias
  «Siempre Juntos», `iezDsZhw9Mw`, se quedaba en «CARGANDO» más de 60 s). Ahora:
  (1) DASH generado desde los formatos con `LiveSource` (por URI `vibetube://mpd/<id>`), solo si
  hay formatos DASH y `getStartTimeMs() > 0` (SmartTube lo salta sin esa hora); (2) DASH propio de
  YouTube por URL (`getDashManifestUrl()`, con `DashMediaSource` normal), si `containsDashUrl()`;
  (3) HLS (`getHlsManifestUrl()`); (4) el OSD dice «Este directo no se pudo abrir» y vuelve atrás
  a los 3 s. El HLS de YouTube solo funciona bien con el cliente iOS (lo comenta tubehaus en su
  servidor), por eso va al final. El orden lo decide `LiveOrder` (clase pura con prueba
  `LiveOrderTest`). Sin lista de URLs para directos: si no hay ninguna fuente de las tres, falla.
  - **Tope de arranque**: 20 s desde cada `prepare()` sin primer cuadro (`onRenderedFirstFrame`) ni
    error; si hay otra fuente en el orden, pasa a ella (log `Directo: sin cuadro en 20 s, pruebo
    HLS`); si no, el paso (3). Al salir de la pantalla el tope se suspende y, al volver, se rearma
    si aún no hubo cuadro. Cambiar de fuente no reproduce por sí solo: conserva la pausa.
  - **Reintento**: ante error, un reintento con `player.prepare()` (como antes). Si también falla,
    se pasa a la fuente siguiente del orden (decisión propia: «mantén el reintento» se leyó como
    un reintento por fuente); si no hay otra, el paso (3).
  - **Log**: cada fuente que arranca escribe `Directo por DASH`, `Directo por DASH de YouTube` o
    `Directo por HLS` (tag VibeTube).
    El DASH registra `Directo: start=… startTimeMs=… segUs=… ast=…` y cada HTTP fallido el `sq`.
    El DASH dinámico se ajusta con `Manifests.toLive(mpd, ahora, startTimeMs)`:
    `availabilityStartTime` queda anclado a la hora del primer segmento del núcleo
    (`getStartTimeMs()`) más `LIVE_EDGE_MARGIN_SEGMENTS` (3) segmentos; sin esa hora (solo tolerancia: la app no llega ahí), se
    calcula desde «ahora» con tope de 24 h. Se quita el `SegmentTimeline`;
    `suggestedPresentationDelay` es `PT20S`. Los videos normales siguen con DASH estático
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
  muestra cuál. Se aplica con `TrackSelectionOverride`. Audio: las pistas del reproductor, rotuladas
  con idioma, códec y kbps (`AudioLabels`, p. ej. «Español · AAC · 128 kbps»); si se repiten, la
  segunda lleva «· 2». Una etiqueta de pantalla que pisaría la de la pantalla mayor queda oculta
  (`Qualities.labelFits`, con 4 dp de separación); la que tiene el foco siempre muestra la suya.
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
- **Sin recorte al tomar el foco**: un `Ui.option` que crece 1,07× se corta si es el primer hijo de
  un contenedor que lo envuelve. Por eso los padres de las filas tienen `setClipChildren(false)` y
  `setClipToPadding(false)` (Canal: cabecera, fila de acciones y «Suscribirse»; Buscar: columna,
  teclas, fila de Espacio/Borrar/Dictar y sugerencias) y las opciones alineadas a la izquierda
  llevan el tag `anchor_left` (Canal, Buscar, aviso «Iniciar sesión» de Inicio). Las pestañas del
  Inicio no se recortan: tienen el eyebrow o otra pestaña a la izquierda, y el margen de 34 las
  absorbe.
- **Abajo y ATRÁS en Reproductor**: Abajo con el foco fuera de las acciones entra a «Me gusta»
  (`focusActions`). ATRÁS desde la franja de Calidad (`hidePanel`) deja el foco en «Calidad». Ambas
  cosas ya estaban así; no cambiaron.
- **Calidad**: el ancho de cada pantalla es proporcional a su alto frente a la mayor (mínimo 56).
- **Al confirmar la cuenta** la pantalla se cierra y Inicio se vuelve a armar. Si se entró desde el
  reproductor, vuelve al reproductor.

- **Aviso y fila, no diálogo** (pedido del usuario, 2026-11-15): la especificación pedía un diálogo
  «Actualizar · Ahora no»; se hizo una línea en el Inicio («Hay una versión nueva · X · OK para
  instalar») y la fila «Actualizaciones» en Cuenta. Sin «Ahora no»: el aviso queda hasta instalar o
  hasta que la app esté al día.
- **Revisión**: al abrir el Inicio, como máximo cada 6 horas desde el último intento (también
  cuenta si falló). «Buscar ahora» no espera. Si la revisión encuentra una versión mayor, la fila
  dice «hay una versión nueva: X».
- **Asset por prefijo**: se reconoce el APK por `VibeTube-` … `.apk`, no por el nombre exacto.
- **Dependencia nueva**: `androidx.core:core:1.12.0`, solo para el FileProvider del actualizador.
- **Release con `assembleDebug`**: como VibeDLNA, el APK publicado es el debug firmado con la llave
  de VibeTube (no minificado).

## Limitaciones conocidas

- **Sin cuenta, Inicio solo tiene lo que el núcleo entrega sin sesión** (ver arriba).
- **SABR**: los videos que solo llegan por SABR no reproducen (0.5.0).
- **Primera actualización**: 0.4.1 es el primer APK firmado con la llave de VibeTube. Una
  instalación de 0.4.0 firmada con la llave debug del computador no se puede actualizar en sitio:
  hay que desinstalarla antes (se pierde la sesión de cuenta guardada).
- **Subtítulos**: se ciclan las pistas que el reproductor reciba del video. El núcleo no entrega
  subtítulos en el MPD; por eso hoy suelen decir «no hay».
- **Con cuenta** (Inicio con historial, Suscripciones, Biblioteca, Me gusta, Guardar, Suscribirse y
  posición): el código está escrito; la prueba con una cuenta la hace el revisor.
- **Canal sin datos de cabecera**: `getChannelObserve` no entrega suscriptores ni descripción del
  canal.
- **Búsquedas sin canal**: algunos resultados no traen `channelId`.

## Pendiente

1. Prueba del revisor de 0.4.2 en el emulador: ▲ desde la primera fila de cada pantalla con
   pestañas, ◀ ▶ en la barra sin cambiar la sección, la espera de 600 ms, ▼ hacia la última tarjeta,
   el teclado de Buscar completo y la última tarjeta de Música dentro del margen.
2. Entrega 4 (0.5.0): HD con SABR en Media3 (módulo `:sabr`).
3. Prueba del revisor de 0.4.1 en el emulador: aviso en Inicio (OK, descarga, instalación), fila
   «Actualizaciones» con «Buscar ahora», y la publicación con la etiqueta `v0.4.1`. La primera
   actualización de 0.4.0 a 0.4.1 requiere desinstalar antes (ver Limitaciones).
4. Prueba con cuenta real (inicio por código, varias cuentas, Me gusta, Guardar, posición): la hace
   el revisor.

## Bitácora (más reciente arriba)

- 2026-11-15 — Ícono del launcher igual al de las otras Vibe (pedido del usuario: «genera un ícono para el launcher como el de los otros Vibe»): `drawable-nodpi/vibetube_banner.png` (320 × 180, fondo transparente) con el televisor y «Vibe» del banner de VibeDLNA Player y «Tube» en cian `#02C8FD` (Poppins SemiBold, misma altura de minúsculas). Reemplaza al vector `drawable/banner.xml`, que se borró. Va en la 0.4.2.

- 2026-11-15 — 0.4.2: arreglos de navegación con el control (sin emulador: lo prueba el revisor).
  Pestañas: ◀ ▶ solo mueven el foco; OK o 600 ms sobre una pestaña muestran la sección sin quitar el
  foco; ▼ baja y vuelve a la última tarjeta con foco; ▲ desde la primera fila lleva a la pestaña
  activa (`MainActivity.navigate`, `showTab`, `focusContent`, `onFocusMoved`; `RowsView.inTopRow`,
  `SearchView.inTopRow`). Buscar: teclado a–z, ñ y 0–9. Filas: margen derecho de 64 y la tarjeta con
  foco pide ver 14 dp más (`Tiles.revealGrown`). `versionCode` 6, `versionName` 0.4.2. Pruebas:
  `:app:assembleDebug` y `:app:testDebugUnitTest` en verde.

- 2026-11-15 — 0.4.1: publicación firmada y actualizador (entrega 4, parte 1; SABR queda para
  0.5.0). Firma: `app/build.gradle` usa `VIBETUBE_KEYSTORE_PATH` si está definida, para debug y
  release; sin ella compila como antes. Workflow `android-release.yml` (etiqueta `v*`, verificación
  del certificado, `gh release create --verify-tag --generate-notes`). Actualizador: `UpdateRules`
  (pura, con `UpdateRulesTest`), `UpdateRepository`, `AppUpdater` (singleton, revisión cada 6 h,
  descarga con porcentaje, verificación de paquete/versión/firma, instalador con FileProvider),
  `UpdateInfo`, `VibeFileProvider` y `xml/file_paths`. Inicio: línea de aviso (`refreshUpdate`).
  Cuenta: fila «Actualizaciones» con «Buscar ahora». Manifiesto: `REQUEST_INSTALL_PACKAGES` y
  provider. `versionCode` 5, `versionName` 0.4.1. Pruebas: `:app:testDebugUnitTest` en verde (6 nuevas
  en `UpdateRulesTest`). Con `VIBETUBE_KEYSTORE_PATH` definida, el debug y el release salen firmados
  con el SHA-256 `87b1fb67…eaac` (verificado con `apksigner`). Sin emulador, por pedido del usuario.

- 2026-11-15 — 0.4.0, octava corrección (sin emulador: lo prueba el revisor). A: el orden de fuentes
  de los directos sigue a SmartTube (`VideoLoaderController`, como en la nota de Directos): DASH
  generado solo con `getStartTimeMs() > 0`, después el DASH de YouTube por URL y al final HLS; si
  no queda ninguna, «Este directo no se pudo abrir». Se quitó el DASH generado sin hora (respaldo
  de `Manifests.toLive` queda solo como tolerancia). `LiveOrder` recibe los formatos, la hora, la
  URL de DASH y el HLS; `LiveOrderTest` cubre `startTimeMs == 0` (salta a la URL de DASH o a HLS) y
  sin URL de DASH. El tope de 20 s y el reintento único no cambian. Pruebas JVM con
  `./gradlew :app:testDebugUnitTest`.

- 2026-11-15 — 0.4.0, séptima corrección (sin emulador: lo prueba el revisor). A: directos en orden
  DASH dinámico y después HLS (`LiveOrder`, con `LiveOrderTest`), tope de 20 s sin primer cuadro
  que pasa a la fuente siguiente, y «Este directo no se pudo abrir» si no queda otra. Ante error se
  reintenta una vez y luego se pasa a la fuente siguiente. El tope se suspende fuera de pantalla.
  Cada fuente registra «Directo por DASH|HLS». B: «Suscribirse» en Canal, filas de Buscar y aviso
  «Iniciar sesión» sin recorte (clip en los padres y `anchor_left`). C: Abajo y ATRÁS en
  Reproductor ya cumplían la especificación; no cambiaron.

- 2026-11-15 — 0.4.0, sexta corrección (sin emulador: lo prueba el revisor). A: en la franja de
  Calidad, una etiqueta de pantalla que pisaría la de la mayor se oculta (`Qualities.labelFits`);
  la que tiene el foco siempre se ve. B: las pistas de audio se rotulan con idioma en español,
  códec y kbps (`AudioLabels`), y las repetidas llevan «· 2». Pruebas JVM nuevas: `AudioLabelsTest`
  y casos de `labelFits` en `QualitiesTest`.

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
