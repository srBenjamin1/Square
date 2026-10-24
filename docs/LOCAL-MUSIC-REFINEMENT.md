# Refinamiento de Música local

> Informe de una fase anterior. Los criterios actuales de identidad, Información, superposición y Motion se documentan en [LOCAL-MUSIC-CORRECTIONS.md](LOCAL-MUSIC-CORRECTIONS.md).

> La fase posterior de identidades automáticas, carpetas SAF y ajustes de landscape está documentada en [LOCAL-MUSIC-AUTOMATIC-SAF.md](LOCAL-MUSIC-AUTOMATIC-SAF.md), con el APK y la validación actuales.

## Estado y diagnóstico

Trabajo sobre `feature/local-music-library`, con HEAD `d3b093e`. Al empezar existían cambios locales de la implementación inicial; se conservaron. Se revisaron commits, etiquetas, diff y `LOCAL-MUSIC-LIBRARY.md`. No se crearon commits, no se hizo push y no se modificaron respaldos ni Releases.

El problema principal era de presentación: la pantalla organizada usaba una cuadrícula y controles propios para casi todas las pestañas, con poca diferencia entre Inicio y Novedades. Radio local era una mezcla aleatoria. Las tarjetas y páginas de artistas podían recurrir a portadas de álbum, incluso en resultados de búsqueda y navegación desde canciones.

La cabecera **ya llamaba al Header original** de Inicio. Se conserva esa implementación, con AppLockup, selector, avatar/Ajustes y GlassChoiceMenu. Ahora forma parte del contenido desplazable de Inicio local: no es una etiqueta o selector nuevo. El selector inicial y Ajustes conservan las tres fuentes y sus preferencias existentes.

También se detectó que el servicio ya medía escuchas mediante ListeningSampler, pero atribuía los archivos locales a la última cuenta remota. RecentStore, por su parte, recibía actualizaciones visuales de metadatos desde Compose: no sirve como contador de escuchas cualificadas.

## Componentes reutilizados

- `HomeScreen.Header`, `LocalMusicHeader`, `AppLockup` y `GlassChoiceMenu`.
- `Heading`, `PlaylistTile`, `TrackTile`, `ArtistTile` y `ArtistCell` de Inicio, expuestos dentro del módulo.
- `LibraryTitle`, extraído de la cabecera original sin cambiar su diseño, y `GridTile` de Biblioteca.
- `FilterChip`, `LiquidButton`, `TrackRow`, Artwork, NameDialog, ConfirmDialog y el editor local existente.
- `PlaylistScreen`, `ArtistHeader`, HeroArt y el bus de navegación de metadatos.
- `ThorLazyVerticalGrid`, `ThorLazyRow`, `ThorFocusScene` y el foco/pressable de los componentes originales.

No hay otra actividad, NavHost, MediaSession, cola, motor ni backend remoto ficticio.

## Inicio, Novedades y Biblioteca

**Inicio** muestra carruseles compactos de canciones, álbumes y artistas escuchados recientemente, más escuchado con ventanas de 7 días/30 días/todo el historial conservado, playlists y archivos incorporados. Los encabezados abren la colección completa mediante «Ver todo» y Atrás/B devuelve a la vista anterior. Las listas completas conservan filas, menú y reproducción originales.

Las playlists se ordenan por la última escucha cualificada cuyo contexto identifica esa playlist. Las listas sin uso conocido mantienen su orden; abrir una lista no se presenta como escucharla.

Sin escuchas se muestra un único mensaje breve y se priorizan álbumes/canciones disponibles. Las secciones sin datos no reservan carruseles vacíos. Se conservan los accesos rápidos a las seis categorías.

**Novedades** muestra álbumes, canciones y artistas incorporados, con fechas reales de MediaStore. `LocalAdditions` calcula las agrupaciones por las fechas de sus canciones. No usa el año del disco como fecha de incorporación ni denomina estos archivos lanzamientos nuevos. Los archivos sin DATE_ADDED conocido siguen accesibles en Biblioteca; no se inventa una fecha.

**Biblioteca** usa el título original, conteo real, filtros originales, cuadrículas de álbumes/playlists y retratos de artistas, filas de canciones con ordenación, géneros, jerarquía de carpetas y gestión de playlists. La reindexación pasa al botón secundario de Biblioteca y conserva también el menú contextual existente. Los cambios de carpeta reinician su contenedor de scroll para evitar posiciones heredadas de otra carpeta.

Los detalles locales ya no muestran «Añadir a biblioteca» remoto: su estado `saved` es null, que es la capacidad opcional prevista por el componente original.

No se muestran colas recientes: SavedQueue conserva la cola actual para reanudación, no un historial real de colas. No se crean copias ni un historial paralelo de colas.

## Historial: origen, conteo y persistencia

Se amplía **ListeningStore**, no se introduce otro almacén de historial. La nueva fuente LOCAL usa `events:local-files` en las preferencias existentes `square_listening`. Su flujo se comparte entre actividades y no depende de qué catálogo se esté explorando.

El **mismo grabador del servicio** identifica la fuente por el URI realmente reproducido. Seleccionar otra fuente de exploración no reasigna una escucha local a Spotify o YouTube. El sampler existente mide tiempo audible con reloj monotónico y excluye pausas, buffering y saltos de posición. No se registran nuevas escuchas desde Compose ni al conectar un controlador.

Regla conservada de Square: cualificar al escuchar la mitad de la duración, con máximo de 240 segundos; si la duración es desconocida, 30 segundos. Pulsar Play no cualifica un evento. Pausa, reanudación y cambios de metadatos mantienen su ID; una transición real, incluida repetición, inicia otro evento. Los guardados periódicos actualizan ese mismo ID. La UI puede tardar hasta el siguiente guardado de unos diez segundos en reflejar una escucha.

Se conservan contexto, tiempo medido y los campos originales. `lastAudibleAt` solo avanza con tiempo audible; así reanudar puede actualizar la recencia sin sumar otra reproducción. Los conteos por ventana se asignan al comienzo de cada evento, no a la fecha del archivo. Las agrupaciones resuelven canciones, álbumes y artistas contra el índice actual y omiten archivos ausentes.

Lectura, recuperación, serialización y escritura se ejecutan fuera del hilo principal. La carga inicial del flujo también evita decodificar el historial en el hilo de interfaz.

**Compatibilidad:** se recuperan los eventos `local:track:` que versiones anteriores dejaron bajo la cuenta Spotify o YouTube. Se copian al canal LOCAL con el mismo ID, conservando la medición más completa y sin duplicarlos tras reiniciar. Los registros originales remotos no se borran ni reescriben para realizar esa recuperación. Sus vistas estadísticas excluyen esos archivos físicos, evitando sumarlos como reproducciones remotas. Cerrar sesión conserva primero el historial local recuperable.

**Retención local:** hasta 365 días y 20 000 eventos, incluidos eventos parciales. Los límites se aplican a la recuperación y escritura; las consultas también respetan el límite temporal. «Todo el historial» se refiere al historial local conservado; la interfaz indica «En este dispositivo · hasta 365 días». No se recortan los historiales remotos anteriores. Las pruebas comprueban límites, duplicados, eventos parciales, pausa, reanudación, repetición y recuperación idempotente.

## Barra, mando, insets y scroll

`SourceTabs` es la política compartida por los destinos visibles, el indicador de LiquidBottomTabs y L1/R1:

- Spotify/YouTube: Inicio, Novedades, Radio, Biblioteca, Buscar.
- Local: Inicio, Novedades, Biblioteca, Buscar.

Se condiciona Radio en los dos registros de la barra flotante: pestañas expandidas y registro usado al plegarla. `tabsCount` usa la lista visible, redistribuyendo el ancho. Al entrar en Local se vuelve a Inicio; también se corrige una ruta Radio restaurada cuando ya se está en Local. La implementación remota de Radio permanece.

L1/R1 recorre y envuelve esa lista. No se cambian D-pad, sticks, A/B, L2/R2, START/SELECT ni el cambio inmediato entre tacto y mando. La barra conserva el plegado original: tocar su pestaña compacta permite desplegar los destinos.

La altura medida de la barra flotante ya incluía el mini-reproductor. Se normaliza restando ese accesorio antes de reservarlo una sola vez, se respeta el inset de navegación y se añade margen de separación. Se mantiene la reserva estable prevista por Square mientras la barra se pliega. Sin un elemento de reproducción no se reserva la altura del mini-reproductor.

En las vistas locales organizadas, el viewport termina por encima de los controles flotantes. Esto permite que el desplazamiento hasta el foco considere esa zona visible y que las últimas tarjetas puedan quedar totalmente por encima de los controles. Los detalles compartidos conservan su scroll original y reciben la reserva inferior corregida. No se cambia resolución ni orientación.

## Artistas, fotografías, logotipos y portadas

La foto principal procede del perfil Spotify explícito consultado mediante su API autenticada o del perfil de artista YouTube resuelto por el backend existente. Los recursos editoriales complementarios y **los logotipos son de AppleCatalog**, cuyo sistema original usa coincidencia exacta de nombre del perfil remoto. No se atribuyen los logotipos a Spotify ni se inventa un identificador Apple compartido entre servicios.

Se reutiliza una asociación ya guardada para el mismo URI remoto sin volver a buscarla. No se deduce un perfil a partir del nombre local. Se mantiene «Asociar imagen del artista»; en la ruta local se elimina el fallback SpotifyWebArtist, de modo que no se añade scraping para resolver un perfil sin API disponible. Spotify requiere una sesión/token válido para una asociación nueva; YouTube conserva sus capacidades existentes.

La fotografía del perfil explícito tiene prioridad sobre el complemento editorial. Los tags, álbumes y canciones siguen siendo locales. Las páginas usan ArtistHeader original, su logo opcional, transiciones, proporciones y fallback tipográfico. Sin fotografía local asociada se compacta la altura de esa misma cabecera; se evita un área negra de 512 dp sin contenido. Los artistas remotos y los locales con fotografía conservan su altura original.

Se eliminó la alternativa de portada de álbum en cuadrículas, carruseles, búsqueda, detalle y resolución de artista desde una canción. Sin asociación se usa el Artwork generado original de Square en tarjetas y el nombre tipográfico en la cabecera. Si falla un logo sigue visible el nombre.

Se conservan Coil y la caché privada existente, con preferencia por archivos descargados y sin solicitudes cuando OfflineMode está activo. Los detalles reaccionan también a cambios del modo offline. Las nuevas descargas usan nombres que incluyen identidad y URL para que dos asociaciones simultáneas no puedan sobrescribir retratos de perfiles distintos. Las asociaciones previas y sus rutas de caché siguen siendo legibles.

Las portadas de álbum mantienen el Fetcher local y su prioridad anterior: incrustada, imagen accesible de carpeta, otra canción del mismo álbum, MediaStore y alternativa original. No se sustituyen por portadas remotas ni se modifican archivos o etiquetas.

## Validación

El comando obligatorio ejecutado es:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

Resultado final: **BUILD SUCCESSFUL**, **236 pruebas JVM, 0 fallos, 0 errores y 0 omitidas** (201 previas y 35 nuevas).

APK debug ARM64:

`/home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

- 52 608 380 bytes; únicamente bibliotecas `arm64-v8a`.
- `dev.lelonio.square`, `versionCode=58`, `versionName=2.6.1-squareds.1`; minSdk 26, targetSdk 35, compileSdk 37.
- SHA-256: `dfdda3c3cb9c63674ca7245f3cd6efe8f9d83cb5d98ef27ee6513cdda1e97e14`.
- Firma verificada con apksigner; certificado SHA-256: `25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c`, compatible con el APK anterior.
- `git diff --check` sin errores. Sin cambios en configuración de compilación, manifiesto, SDK/NDK/Rust/Gradle, workflows, versión, firma ni actualizador.
- Comparación con la copia de inicio de esta fase: 52 archivos protegidos de backends, código nativo, entrada Thor, LocalLibrary, LocalPlayerFactory y AppleCatalog sin cambios.

Las pruebas JVM cubren la política real de navegación y agregación, además de contratos estructurales de reutilización. Esos contratos no son pruebas de renderizado. Se conservan los 201 casos previos; se actualiza su expectativa de Radio local, que ha sido retirada por esta solicitud.

Se realizaron pruebas de interfaz con adb/uiautomator y capturas en una sesión **temporal y de solo lectura** del AVD existente Medium Phone API 37. El sistema admite traducción ARM64. Se usaron tres audios sintéticos de 65 s (dos FLAC y un MP3) con tags de prueba, únicamente en el almacenamiento temporal del emulador. No se introducen fixtures ni datos ficticios en el producto.

Comprobado en ejecución: selección inicial Local, permiso de audio, indexación, tres archivos agrupados en dos álbumes y dos artistas, cabecera Square, cuatro destinos, apertura/reproducción desde álbum y mini-reproductor, ausencia de historial antes de cualificar, aparición posterior de escuchas/álbumes/artistas reales, persistencia al reinstalar, «Ver todo» y Atrás, Novedades, conteo de Biblioteca, artistas sin portada prestada, selector de tres fuentes y Radio visible en YouTube/oculta al volver a Local.

También se verificó la recuperación en Android con un evento de audio realmente medido: se reprodujo temporalmente su disposición antigua bajo YouTube, se arrancó la versión final dos veces y se comprobó su recuperación local con el mismo ID y tiempo, una sola copia y el registro archivado intacto. No se alteran datos del usuario: todo se hizo dentro del AVD temporal.

Capturas de revisión (artefactos locales, fuera del repositorio):

- [Inicio con historial real de prueba](/tmp/squareds-refine-final-home.png).
- [Novedades](/tmp/squareds-refine-new.png).
- [Biblioteca y artistas](/tmp/squareds-refine-artists-grid.png).
- [Artista sin fotografía: cabecera compacta](/tmp/squareds-refine-final-artist-compact.png).
- [Álbum sin acción remota de guardado](/tmp/squareds-refine-final-album.png).
- [Selector de tres fuentes](/tmp/squareds-refine-source-menu.png).
- [Radio en YouTube](/tmp/squareds-refine-youtube-bar.png).

Las pruebas visuales usaron el idioma inglés del AVD; las nuevas cadenas también están traducidas al español. No se cambiaron orientación ni resolución del emulador.

La sesión temporal del emulador se cerró al terminar, descartando sus datos de prueba. Esto es validación de emulador convencional; no se declara una prueba física de Thor ni de reproducción remota autenticada.

## Pendiente en AYN Thor y límites

1. Pantalla inferior en landscape: últimas tarjetas, reserva inferior con/sin mini-reproductor, plegado y accesibilidad de todos los controles.
2. D-pad, stick izquierdo, A/B, L1/R1 con cuatro/cinco destinos y wrap; L2/R2, START/SELECT; primera pulsación táctil después de mando y auto-scroll al foco.
3. Reproductor superior permanente, metadatos interactivos hacia abajo, cola, letras sincronizadas, EQ y efectos mientras se exploran las páginas nuevas.
4. Spotify autenticado y YouTube autenticado: cambio de fuente desde Radio/Ajustes, conservación de credenciales, Connect, Motion Artwork y videoclip. La lógica de selección desde Radio está cubierta por pruebas JVM; falta el recorrido físico.
5. Asociación de perfiles reales, fotografía/logo disponibles, fallo de logo, cambios rápidos de asociación y caché tras reiniciar sin internet. Los proveedores remotos no se ejercitaron con credenciales en el emulador.
6. Colección real y microSD grande, formatos/portadas embebidas, altas/bajas de archivos, rechazo de permisos y rendimiento de indexación/serialización.
7. Playlists: creación, adición, reordenación, borrado y orden por escuchas reales desde su contexto; reiniciar en modo avión.

Se mantienen los límites anteriores: MediaStore y permisos existentes, sin cola mixta; tras arranque solo local, inicializar el catálogo nativo de Spotify puede pausar la cola local. No se introducen estadísticas anteriores a eventos medidos ni recomendaciones o colas inventadas.

## Archivos de esta fase

- [app/src/main/java/dev/lelonio/square/data/ListeningHistory.kt](../app/src/main/java/dev/lelonio/square/data/ListeningHistory.kt)
- [app/src/main/java/dev/lelonio/square/data/ListeningStore.kt](../app/src/main/java/dev/lelonio/square/data/ListeningStore.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalAdditions.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalAdditions.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalArtworkRepository.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalArtworkRepository.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalListening.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalListening.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalMusicModels.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalMusicModels.kt)
- [app/src/main/java/dev/lelonio/square/playback/PlaybackService.kt](../app/src/main/java/dev/lelonio/square/playback/PlaybackService.kt)
- [app/src/main/java/dev/lelonio/square/ui/MainViewModel.kt](../app/src/main/java/dev/lelonio/square/ui/MainViewModel.kt)
- [app/src/main/java/dev/lelonio/square/ui/SourceTabs.kt](../app/src/main/java/dev/lelonio/square/ui/SourceTabs.kt)
- [app/src/main/java/dev/lelonio/square/ui/SquareApp.kt](../app/src/main/java/dev/lelonio/square/ui/SquareApp.kt)
- [app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt](../app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/library/LibraryScreen.kt](../app/src/main/java/dev/lelonio/square/ui/library/LibraryScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt](../app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/local/LocalMusicScreen.kt](../app/src/main/java/dev/lelonio/square/ui/local/LocalMusicScreen.kt)
- [app/src/main/res/values-es/strings.xml](../app/src/main/res/values-es/strings.xml)
- [app/src/main/res/values/strings.xml](../app/src/main/res/values/strings.xml)
- [app/src/test/java/dev/lelonio/square/data/local/LocalArtistArtworkTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalArtistArtworkTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalListeningTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalListeningTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalMusicUiContractTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalMusicUiContractTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalRefinementNavigationTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalRefinementNavigationTest.kt)
- [docs/LOCAL-MUSIC-LIBRARY.md](../docs/LOCAL-MUSIC-LIBRARY.md)
- [docs/LOCAL-MUSIC-REFINEMENT.md](../docs/LOCAL-MUSIC-REFINEMENT.md)
