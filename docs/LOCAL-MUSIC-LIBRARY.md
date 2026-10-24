# Música local como tercera fuente de SquareDS

> Informe de una fase anterior. Los criterios actuales de identidad, Información, superposición y Motion se documentan en [LOCAL-MUSIC-CORRECTIONS.md](LOCAL-MUSIC-CORRECTIONS.md).

> La fase posterior de identidades automáticas, carpetas SAF y ajustes de landscape está documentada en [LOCAL-MUSIC-AUTOMATIC-SAF.md](LOCAL-MUSIC-AUTOMATIC-SAF.md), con el APK y la validación actuales.

> Informe de la implementación inicial. Inicio, Novedades, Radio, historial, artistas e insets se refinan en [LOCAL-MUSIC-REFINEMENT.md](LOCAL-MUSIC-REFINEMENT.md), que contiene el estado y la validación actuales.

## Estado de partida y alcance

La intervención parte de `d3b093e45ed1c1ae7000f6e441d771bdb05e4903`, master funcional y sincronizado con origin. Se comprobó que v1.2 ya estaba publicada, no en borrador. Se conservan `v2.6.1-squareds.1`, el respaldo `thor-v2-metadata-functional` y las ramas anteriores.

El trabajo está en `feature/local-music-library`. No se han creado commits, realizado push ni publicado otra Release.

## Auditoría del selector y decisión de arquitectura

Antes había dos valores de `BackendId`: Spotify y YouTube Music. La preferencia `backend` seleccionaba simultáneamente catálogo y motor. `PlaybackService` observaba esa preferencia y reemplazaba el reproductor. El selector existía en la cabecera de Inicio, en Ajustes y en la selección inicial.

Los archivos locales ya se reproducían mediante `LocalPlayerFactory`, dentro de la misma MediaSession. Se detectaban por `local:track:<id>` y se resolvían a MediaStore al abrir el audio. La biblioteca original incluía `local:files`, que leía `LocalLibrary.tracks` y utilizaba sus permisos existentes. No se encontró un explorador SAF previo: el acceso original usa MediaStore y una lista de archivos.

Se introduce `MusicSource` para la selección de exploración, con Spotify, YouTube Music y Música local. `BackendId` sigue teniendo únicamente los dos servicios remotos: no se ha añadido un backend con métodos remotos ficticios para archivos.

La preferencia `music_source` conserva la selección. Las instalaciones anteriores recuperan su servicio desde `backend`; elegir Local conserva el último backend remoto. La selección inicial y los controles originales de Ajustes muestran las tres opciones.

La cabecera original mantiene su ubicación, tamaño, iconografía y acción táctil/enfocable. Al activarla, ofrece las tres fuentes mediante el `GlassChoiceMenu` de Square, dentro de la misma vista. Se elige directamente, sin pasar por otro servicio y detener audio antes de llegar a Local. No hay otro selector persistente, actividad, ventana de aplicación o NavHost.

## Exploración frente a reproducción

Elegir Local cambia las páginas que se exploran y no inicia otra canción. Puede seguir sonando Spotify o YouTube Music hasta que se pulse un archivo. Todas las reproducciones nuevas utilizan el mismo callback `onPlay`, MediaController, MediaSession y `LocalPlayerFactory` que el acceso local anterior.

Los arranques con Local seleccionado construyen directamente el reproductor local existente; no requieren autenticación Spotify. La cola local guardada puede restaurarse por el mecanismo anterior.

Al volver a YouTube Music, o a Spotify cuando su motor ya estaba aparcado, la reproducción local puede continuar durante la exploración. Hay un límite de la arquitectura original: el catálogo nativo de Spotify depende de su motor. Tras un arranque exclusivamente local, abrir Spotify requiere inicializar su reproductor existente y puede pausar la cola local. Su estado se guarda y no se inicia automáticamente otra canción. No se ha creado un segundo reproductor local para ocultar este límite.

Las inserciones de cola comprueban compatibilidad: no se mezclan `local:track:` y canciones remotas. Se informa de que hay que reproducir la canción para cambiar de fuente. El primer archivo añadido a una cola vacía selecciona el reproductor apropiado mediante la ruta original. Aleatorio y repetición se trasladan al entrar en el reproductor local.

La interfaz comprueba el archivo que realmente se reproduce para ocultar Connect y evitar que un estado Connect anterior sustituya los metadatos locales. EQ, efectos y los controles de letras locales no quedan bloqueados por una configuración previa de Spotify Web SDK. No se han cambiado Librespot, el motor YouTube Music ni los proveedores de letras.

## Conservación del acceso local original

`LocalLibrary.kt` permanece idéntico al estado inicial, comprobado por SHA-256. Se mantiene la entrada `local:files`, el método `openLocalFiles`, su consulta, lista, acciones de reproducción y solicitud original de permisos. Spotify y YouTube Music conservan ese acceso sin tener que seleccionar la nueva fuente.

La biblioteca organizada añade otra presentación de las mismas URI físicas. No mueve, renombra, modifica ni copia audio. Álbumes, artistas, géneros, carpetas y playlists solo agrupan o referencian esas identidades. El control para encolar impide mezclas incompatibles desde ambos accesos. Las identidades locales conocidas abren páginas locales; no se buscan perfiles remotos por nombres de archivos.

## Índice y persistencia

`LocalLibraryIndexer` consulta MediaStore fuera del hilo principal. Conserva ID, URI de contenido, nombre de archivo, carpeta/volumen, ruta cuando Android la proporciona, tamaño, MIME, incorporación, modificación y generación. Lee título, artista, álbum, ALBUMARTIST, disco, pista, género, año/fecha y duración con `MediaMetadataRetriever`, manteniendo los valores de MediaStore si no puede leer una etiqueta.

La política incremental compara tamaño, modificación, generación y una firma de los campos de MediaStore. Solo abre las etiquetas de archivos nuevos o cambiados. Detecta eliminaciones comparando las filas actuales con el índice anterior y elimina duplicados por identidad. La reindexación manual vuelve a leer etiquetas e invalida la revisión de las portadas.

Una instancia de `LocalMusicRepository` pertenece a la aplicación y se comparte entre actividades. Un ContentObserver con debounce detecta cambios; las pestañas leen el índice y no lanzan recorridos de archivos cada vez que se abren. El inicio y las mutaciones utilizan bloqueos separados para no perder cambios de playlists durante un escaneo.

Se sigue el almacenamiento JSON que ya utiliza el proyecto, sin incorporar Room ni cambiar dependencias. `local-music/index.json`, en los archivos privados de la aplicación, se escribe mediante archivo temporal, sincronización y sustitución atómica. Contiene canciones, referencias de playlists y asociaciones visuales explícitas. Se conserva entre reinicios.

Se reutilizan READ_MEDIA_AUDIO o READ_EXTERNAL_STORAGE según Android, sin nuevos permisos de acceso general, imágenes, root o Shizuku. Rechazar audio deja disponibles Spotify y YouTube Music.

## Vistas y navegación

Las rutas originales del NavHost inferior y convencional se adaptan según la fuente:

- **Inicio:** álbumes y canciones añadidos recientemente, artistas, playlists y acceso a toda la colección.
- **Novedades:** canciones locales por fecha de incorporación, con ordenación seleccionable.
- **Radio:** mezcla real de la colección mediante aleatorio; no simula emisoras ni recomendaciones remotas.
- **Biblioteca:** Álbumes, Artistas, Canciones, Géneros, Carpetas y Playlists.
- **Buscar:** conserva `SearchScreen` y consulta el índice por título, artista, álbum y archivo, también sin internet. Las consultas locales se interceptan antes del resolutor de enlaces o búsqueda remota. Los resultados se actualizan si finaliza un escaneo mientras se busca.

Las tarjetas usan `Artwork`, tipografía y controles enfocables existentes. Canciones y carpetas utilizan el `TrackRow` original. La navegación por carpetas conserva la jerarquía; Atrás/B sube al padre. La fuente y las categorías no duplican la barra inferior.

Álbumes, artistas, géneros y playlists se abren en `PlaylistScreen`, con el historial original. No se inicia una búsqueda remota para resolver un contexto local.

## Álbumes, artistas y metadatos

Los álbumes usan ALBUMARTIST cuando existe; en su ausencia aprovechan la identidad de MediaStore o una clave que incluye artista y carpeta. No se agrupan únicamente por título. Las canciones se ordenan por disco y pista. Las recopilaciones conservan sus intérpretes individuales y el artista del álbum; este último se muestra en la cabecera local sin enlazar erróneamente al primer invitado.

La página mantiene portada grande, artista, año disponible, canciones, duración total y los controles originales de reproducción/aleatorio. La vista Canciones permite ordenar por título, artista, álbum, incorporación y duración.

Los artistas se agrupan normalizando espacios y mayúsculas, incluyendo los ALBUMARTIST conocidos. No se separan nombres por comas, barras o ampersands. El texto ARTIST se conserva como lo entrega Android; no se inventan artistas individuales a partir de separadores ambiguos.

Los metadatos de las canciones se convierten al `CatalogTrack` original. Incluyen URI locales estables de artista y álbum para que los nombres del reproductor y de Información utilicen el mismo bus inferior de navegación. Los destinos locales validan tipo y formato y no pueden convertirse en destinos Spotify o YouTube por coincidencia de nombres.

## Portadas y páginas de artistas

Las portadas se cargan bajo demanda con un Fetcher del Coil existente, compartido por tarjetas, detalles, reproductor y mini-reproductor. La prioridad es portada incrustada, imagen asociada accesible en la carpeta, portada de otra canción del álbum, imagen de álbum de MediaStore y alternativa original de Square. Los archivos sin álbum conocido no toman prestadas portadas de otros archivos sin identificar.

La caché de portadas está limitada y las imágenes se decodifican con los tamaños solicitados por los componentes originales. Las portadas no se sustituyen por imágenes remotas. Las claves incorporan cambios del archivo y de los miembros del álbum; el reindexado manual renueva las revisiones visuales.

Las páginas de artistas locales usan el `ArtistHeader` original: foto principal, proporciones, logotipo y tratamiento tipográfico de Square. El proveedor de esos logotipos es AppleCatalog, no una suposición sobre Spotify. No se añaden proveedores ni imágenes artificiales.

Desde la vista Artistas se puede **Asociar imagen del artista** proporcionando explícitamente un perfil Spotify o una identidad/enlace de artista YouTube. Se consulta esa identidad mediante los mecanismos ya existentes, se conserva la asociación y se reutilizan sus recursos visuales y el proveedor original. No se inicia una asociación automática por el nombre local. Las etiquetas del archivo no se reemplazan por los datos del catálogo.

La identidad se guarda antes de descargar complementos visuales. Las imágenes se almacenan en segundo plano y se aprovecha la caché Coil existente. La página no espera esas descargas para funcionar. Offline se priorizan los archivos visuales guardados, después el recurso local. El logotipo opcional que falla vuelve al tratamiento tipográfico original; no deja vacío el nombre.

## Playlists locales

La categoría Playlists permite crear, renombrar y eliminar. **Gestionar playlist** muestra sus referencias y permite quitar canciones y moverlas arriba o abajo con controles de tacto y mando. Añadir canciones o un conjunto utiliza la hoja original **Añadir a una playlist**, dirigida a las listas locales cuando la fuente es Local. Estas operaciones funcionan offline.

Las playlists guardan URI, no copias de audio. Un archivo eliminado desaparece de los resultados reproducibles y se omite al construir la cola, pero su referencia permanece en la playlist y se identifica como no disponible al gestionarla. Se puede quitar o reordenar. Eliminar una playlist conserva los archivos.

## Letras, mando y modos de pantalla

Las letras conservan íntegramente `LyricsLibrary`, los proveedores, consulta automática, sincronización y traducción existentes. Todos los recorridos de audio local llegan al mismo reproductor y proporcionan título, artista y duración al mismo flujo. No se ha añadido un lector LRC ni se condiciona la reproducción a tener letras o conexión.

La cabecera, filtros y tarjetas heredan la escena de foco original de la página; el menú de fuentes y los diálogos utilizan las escenas apropiadas. Se reutilizan `pressable`, `thorFocus`, listas Thor y los diálogos existentes. No se cambian D-pad, sticks, A/B, L1/R1, L2/R2, START o SELECT.

En Thor, las páginas pertenecen al navegador inferior y el reproductor superior sigue permanente. En modo convencional se usan las mismas rutas y el reproductor expandible original. No se ha creado otro NavHost, actividad o MediaSession.

## Verificación automatizada

Comando ejecutado:

```
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

Resultado: **BUILD SUCCESSFUL**, **201 pruebas JVM, 0 fallos, 0 errores y 0 omitidas**; 48 casos nuevos sobre las 153 pruebas anteriores.

APK ARM64 debug:

`/home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

- Paquete: `dev.lelonio.square`.
- Versión: `2.6.1-squareds.1`, código 58.
- Bibliotecas nativas: únicamente `arm64-v8a`.
- Firma verificada con `apksigner`; certificado SHA-256 compatible con v1.2: `25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c`.
- SHA-256 del archivo: `1b00a7d750c46faecc8127ed70cdad751e21e14a89a336b04b30cd997b0ca013`.

`applicationId`, versión, SDK, NDK, Rust, Gradle y actualizador se mantienen sin cambios.

Las pruebas nuevas cubren la selección/persistencia compatible de tres fuentes, ausencia de backend remoto ficticio, política de reproducción, agrupaciones, recopilaciones, ALBUMARTIST, orden, géneros, etiquetas ausentes, indexación incremental/manual, duplicados y eliminaciones, playlists y persistencia real en disco, búsqueda offline, referencias visuales, caché offline, navegación local y conservación estructural de los recorridos originales. Las pruebas anteriores se mantienen.

Las pruebas de flujo usan las clases de producción de índice, modelos, store, destinos, cola y handler con un host simulado. Los contratos de interfaz no sustituyen una pulsación Android real. No había dispositivo conectado según `adb devices -l`; no se declara validación física ni prueba instrumentada de MediaStore, decodificadores o gestos.

Se compararon por SHA-256 149 archivos protegidos con el estado inicial: sin cambios en el lector local original, fábrica local, créditos, motores Spotify/YouTube, proveedores de letras, AppleCatalog, MotionCover, FullScreenVideo, controles Thor, actualizador, código nativo y configuración de compilación/publicación.

## Límites y validación física pendiente

- Se descubren los archivos indexados por MediaStore y accesibles con los permisos existentes. No se añade selección de árboles SAF ni acceso a carpetas privadas no indexadas; el acceso original tampoco lo ofrecía.
- La lectura de etiquetas y compatibilidad FLAC, MP3, AAC/M4A, Ogg, Opus y WAV depende de las API y decodificadores del dispositivo. Falta probar muestras reales en Thor.
- Las imágenes asociadas de carpeta solo se leen cuando los permisos actuales permiten acceder a ellas. Los recursos remotos de artistas son opcionales; sin identidad explícita o sin recursos disponibles se mantiene el diseño original con alternativa local.
- No se interpreta un ARTIST compuesto mediante separadores ambiguos ni se usan identificadores externos no suministrados por el lector de Android.
- No hay cola mixta. Se mantiene el límite del arranque local y posterior inicialización de Spotify explicado arriba.
- Hay que medir el escaneo inicial y las mutaciones del JSON con una colección grande en la Thor. Los archivos no cambiados no se vuelven a abrir para leer etiquetas en actualizaciones incrementales.

Validar antes de commit:

1. Elegir las tres fuentes con tacto y mando desde Inicio, Ajustes y arranque nuevo; reiniciar y comprobar persistencia. Elegir Local directamente mientras suena cada servicio y comprobar que abrir el selector no cambia el audio.
2. Arrancar sin cuenta ni internet con Local seleccionado. Conceder/denegar permisos y confirmar que los dos servicios siguen accesibles.
3. Usar el acceso local original desde Spotify y YouTube; comparar archivos, reproducción, cola y letras con la nueva fuente.
4. Probar colecciones internas y microSD, formatos y etiquetas reales, álbumes homónimos, ALBUMARTIST, recopilaciones y varios discos.
5. Añadir, editar y eliminar archivos; observar actualización y reindexación manual, portadas y archivos ausentes en playlists.
6. Crear, añadir, renombrar, reordenar, quitar y eliminar playlists; reiniciar y repetir sin internet. Confirmar que no se modifican los archivos.
7. Buscar por título, artista, álbum y archivo en modo avión, incluso mientras termina un escaneo.
8. Asociar explícitamente una imagen/perfil; revisar foto, logotipo, nombre original y caché tras reinicio sin conexión. Probar un nombre sin asociación y un logo no disponible.
9. Reproducir desde álbum, artista, canciones, género, carpetas, playlists y explorador original; probar Play/Pause, Anterior/Siguiente, posición, aleatorio, repetición, EQ, efectos y letras.
10. Verificar navegación de metadatos abajo, Atrás/B e historial, mini-reproductor y reproductor superior permanente. Comprobar la primera pulsación táctil después de usar mando y el acceso al selector desde el foco de la cabecera.
11. Volver a los servicios remotos y verificar credenciales, cola y el caso de Spotify tras arranque exclusivamente local. Confirmar Motion Artwork y videoclip para las fuentes compatibles.
12. Repetir biblioteca, gestos, reproductor expandible y diálogos en modo convencional.

## Referencias de API

- [Android: acceso a archivos multimedia compartidos](https://developer.android.com/training/data-storage/shared/media).
- [Android: MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever).
- [Coil 2.7: Fetcher](https://github.com/coil-kt/coil/blob/2.7.0/coil-base/src/main/java/coil/fetch/Fetcher.kt).

## Archivos para revisión

- [app/src/main/java/dev/lelonio/square/SquareApplication.kt](../app/src/main/java/dev/lelonio/square/SquareApplication.kt)
- [app/src/main/java/dev/lelonio/square/data/MetadataDestination.kt](../app/src/main/java/dev/lelonio/square/data/MetadataDestination.kt)
- [app/src/main/java/dev/lelonio/square/data/MusicSource.kt](../app/src/main/java/dev/lelonio/square/data/MusicSource.kt)
- [app/src/main/java/dev/lelonio/square/data/PreferencesStore.kt](../app/src/main/java/dev/lelonio/square/data/PreferencesStore.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalArtistAssociation.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalArtistAssociation.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalArtworkRepository.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalArtworkRepository.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalIndexUpdate.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalIndexUpdate.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalLibraryIndexer.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalLibraryIndexer.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalMusicModels.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalMusicModels.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalMusicRepository.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalMusicRepository.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalMusicStore.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalMusicStore.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalQueuePolicy.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalQueuePolicy.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalSourcePlaybackPolicy.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalSourcePlaybackPolicy.kt)
- [app/src/main/java/dev/lelonio/square/playback/MediaBrowseTree.kt](../app/src/main/java/dev/lelonio/square/playback/MediaBrowseTree.kt)
- [app/src/main/java/dev/lelonio/square/playback/PlaybackService.kt](../app/src/main/java/dev/lelonio/square/playback/PlaybackService.kt)
- [app/src/main/java/dev/lelonio/square/thor/ui/ThorActivity.kt](../app/src/main/java/dev/lelonio/square/thor/ui/ThorActivity.kt)
- [app/src/main/java/dev/lelonio/square/ui/MainActivity.kt](../app/src/main/java/dev/lelonio/square/ui/MainActivity.kt)
- [app/src/main/java/dev/lelonio/square/ui/MainViewModel.kt](../app/src/main/java/dev/lelonio/square/ui/MainViewModel.kt)
- [app/src/main/java/dev/lelonio/square/ui/MetadataNavigationHandler.kt](../app/src/main/java/dev/lelonio/square/ui/MetadataNavigationHandler.kt)
- [app/src/main/java/dev/lelonio/square/ui/SquareApp.kt](../app/src/main/java/dev/lelonio/square/ui/SquareApp.kt)
- [app/src/main/java/dev/lelonio/square/ui/TrackAlbum.kt](../app/src/main/java/dev/lelonio/square/ui/TrackAlbum.kt)
- [app/src/main/java/dev/lelonio/square/ui/components/Artwork.kt](../app/src/main/java/dev/lelonio/square/ui/components/Artwork.kt)
- [app/src/main/java/dev/lelonio/square/ui/components/NameDialog.kt](../app/src/main/java/dev/lelonio/square/ui/components/NameDialog.kt)
- [app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt](../app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt](../app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/local/LocalMusicScreen.kt](../app/src/main/java/dev/lelonio/square/ui/local/LocalMusicScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/onboarding/BackendChoiceScreen.kt](../app/src/main/java/dev/lelonio/square/ui/onboarding/BackendChoiceScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/player/PlayerMetadata.kt](../app/src/main/java/dev/lelonio/square/ui/player/PlayerMetadata.kt)
- [app/src/main/java/dev/lelonio/square/ui/player/PlayerScreen.kt](../app/src/main/java/dev/lelonio/square/ui/player/PlayerScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/player/PlayerToolsContent.kt](../app/src/main/java/dev/lelonio/square/ui/player/PlayerToolsContent.kt)
- [app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt](../app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt)
- [app/src/main/res/values-es/strings.xml](../app/src/main/res/values-es/strings.xml)
- [app/src/main/res/values/strings.xml](../app/src/main/res/values/strings.xml)
- [app/src/test/java/dev/lelonio/square/data/local/LocalArtistArtworkTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalArtistArtworkTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalIndexUpdateTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalIndexUpdateTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalMusicCatalogTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalMusicCatalogTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalMusicNavigationIntegrationTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalMusicNavigationIntegrationTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalMusicPersistenceTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalMusicPersistenceTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalMusicUiContractTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalMusicUiContractTest.kt)
- [docs/LOCAL-MUSIC-LIBRARY.md](../docs/LOCAL-MUSIC-LIBRARY.md)
