# Música local: identidad automática, carpetas SAF y landscape

> Informe de una fase anterior. Los criterios actuales de identidad, Información, superposición y Motion se documentan en [LOCAL-MUSIC-CORRECTIONS.md](LOCAL-MUSIC-CORRECTIONS.md).

## Estado y auditoría

Trabajo en `feature/local-music-library`, con HEAD `d3b093e`. Se conservaron los cambios pendientes de las dos fases anteriores y se leyeron sus informes. No se crearon commits, no se hizo push, no se cambiaron etiquetas ni se publicaron Releases.

La auditoría encontró cuatro límites concretos: la asociación solo aceptaba perfiles manuales; el índice y el resolutor del reproductor suponían MediaStore; `loadNowPlayingArt` excluía expresamente los archivos locales; y el espacio inferior de la biblioteca se reservaba a partir de dimensiones conservadoras incluso con la barra plegada. Los detalles locales también conservaban una cabecera de 512 dp en ventanas horizontales mucho más bajas.

## Fotografías, logotipos e identidad automática

Se mantiene la asociación manual Spotify/YouTube existente. Las fotografías de esos perfiles se consultan por su identidad explícita; los logotipos y recursos editoriales complementarios proceden de AppleCatalog. No se atribuyen los logotipos a Spotify.

La ruta automática utiliza **la integración AppleCatalog existente**, sus API de catálogo, credencial, restricciones de red y cachés. No se añade un proveedor ni un extractor de páginas. Las búsquedas piden [relaciones de canciones con artistas y álbumes](https://developer.apple.com/documentation/applemusicapi/handling-resource-representation-and-relationships); la fotografía y el logotipo se solicitan después por el ID de artista verificado, no por una nueva búsqueda libre de nombre.

La decisión automática requiere al menos dos títulos locales diferentes y dos identificadores de canción distintos que apunten al mismo artista de catálogo. Cada coincidencia debe conservar título, intérprete, álbum y duración compatible (diferencia máxima de dos segundos). Después se exige también el nombre exacto normalizado del perfil. Se normalizan Unicode, espacios y mayúsculas; no se eliminan calificadores de edición/live/deluxe ni se dividen nombres compuestos. Copias del mismo tema no cuentan como evidencia independiente.

Si aparece otra identidad plausible, el resultado es ambiguo y no se aplica una imagen. La falta de evidencia suficiente produce «Sin coincidencia fiable». Una respuesta temporalmente inaccesible permanece pendiente. Las relaciones múltiples o incompletas tampoco se convierten en una identidad elegida arbitrariamente.

La prioridad es: decisión manual o rechazo guardado, asociación verificada vigente y, finalmente, búsqueda conservadora. Las decisiones se guardan como MANUAL, VERIFIED, PENDING, AMBIGUOUS, NOT_FOUND o REJECTED, junto con firma de evidencia, fecha y generación de reintento. Las asociaciones anteriores sin el nuevo campo de origen siguen siendo manuales.

«Asociar imagen del artista» permite cambiar un perfil. Quitar una asociación guarda el rechazo para impedir que el trabajador la reponga. También se puede rechazar la búsqueda sin imagen y reactivarla con «Reintentar asociación automática». La frontera de persistencia vuelve a comprobar la decisión del usuario, la identidad local y la vigencia de la evidencia antes de aceptar un resultado asíncrono.

Hay un trabajador por aplicación, un artista cada vez y un máximo de tres temas representativos por artista. Se espacia el inicio de operaciones al menos 1,5 segundos y se retrocede 30 segundos ante indisponibilidad. Las decisiones se reutilizan durante siete días; los candidatos tienen una caché acotada de 128 entradas durante 24 horas. El trabajo se cancela al cambiar fuente/conectividad/evidencia y sus resultados obsoletos se descartan.

No se descargan fotografías de toda la colección al indexar. Los recursos visuales se guardan cuando un artista entra en una vista o detalle; hay dos plazas de descarga y deduplicación por artista. Se conserva la caché Coil y la caché privada existente para fotografías y logos. No se hacen solicitudes de catálogo sin conexión; AppleCatalog conserva además su condición previa de red no medida para consultas nuevas.

Sin identidad confirmada se conserva Artwork original y ArtistHeader tipográfico compacto. Nunca se usa una portada de álbum como retrato. Los recursos remotos no reemplazan tags ni canciones locales.

## Carpetas opcionales y comportamiento predeterminado

Ajustes → Reproducción → Sonido incluye **Música local — Carpetas de música**:

- **Toda la música del dispositivo — Predeterminado:** MediaStore conserva el descubrimiento general y SAF añade archivos autorizados que no aparezcan allí.
- **Solo carpetas seleccionadas — Opcional:** filtra el índice visible por la unión de árboles autorizados, incluidas sus subcarpetas. Solo se activa mediante una elección expresa.

Sin carpetas válidas, intentar activar el filtro muestra una explicación y el botón para añadir una carpeta; el modo permanece general. Al quitar la última autorización o detectar que ninguna sigue accesible, se vuelve al modo general y se informa del cambio. Una carpeta válida que realmente no contiene audio puede producir un resultado vacío intencional.

Se usa `ActivityResultContracts.OpenDocumentTree` (ACTION_OPEN_DOCUMENT_TREE) y `takePersistableUriPermission` **solo para lectura**. La UI muestra nombres, URI, accesibilidad, retirada y reindexación. Al volver del selector se revalidan los permisos mediante el ciclo de vida de Ajustes. Al quitar una ubicación se libera su permiso persistente; no se elimina ningún documento.

Android limita la selección de raíces de almacenamiento y de la raíz de Descargas. La interfaz explica que se puede elegir una subcarpeta compatible. No se solicita MANAGE_EXTERNAL_STORAGE, root, Shizuku ni nuevos permisos del manifiesto. Véase [la documentación oficial de SAF](https://developer.android.com/training/data-storage/shared/documents-files).

Los árboles, el modo y su accesibilidad se guardan en el snapshot JSON atómico existente. El modo restrictivo filtra la presentación, no borra el índice general ni las playlists. Las referencias a archivos excluidos o inaccesibles permanecen en las playlists y vuelven a resolverse cuando están disponibles.

## Indexación, duplicados y reproducción

`LocalSafIndexer` recorre DocumentsContract en IO, con cancelación y control de directorios visitados. Consulta nombre, MIME, tamaño y modificación; reutiliza tags de archivos sin cambios y comparte lecturas entre árboles superpuestos. La reindexación explícita relee metadatos e invalida portadas. Los registros SAF ausentes se conservan como metadatos no disponibles, hasta 10 000, para recuperar identidades; no se guardan copias de audio.

La deduplicación usa identidades de documento, rutas de volumen comprobables y, cuando Android lo proporciona, la equivalencia de `MediaStore.getMediaUri`. Esa API no concede acceso adicional; se conserva el permiso del documento autorizado. No se fusionan archivos solo por título, nombre o tamaño. Véase [MediaStore.getMediaUri](https://developer.android.com/reference/android/provider/MediaStore#getMediaUri(android.content.Context,%20android.net.Uri)).

Los IDs `local:track:<id>` anteriores se conservan. Los documentos no indexados tienen un ID negativo estable derivado de autoridad e ID de documento. Si MediaStore encuentra después el mismo archivo, se conserva su identidad anterior. Las carpetas superpuestas aportan varias autorizaciones a un único registro visible.

La única modificación de `LocalPlayerFactory` es su frontera de resolución de URI: consulta el URI autorizado del índice y conserva como alternativa `LocalLibrary.contentUri` para los identificadores anteriores. La espera del índice ocurre en el hilo de carga de la fuente de datos, no en el hilo de interfaz. No se cambian el constructor del reproductor, los decodificadores, audio focus, MediaController, MediaSession ni la cola. La música SAF utiliza el mismo motor local.

El explorador `local:files` y `LocalLibrary.kt` se mantienen intactos. La biblioteca organizada también funciona con permisos SAF aunque READ_MEDIA_AUDIO esté denegado. El lector de portadas puede resolver registros del índice completo, incluso cuando el filtro cambia durante una reproducción.

Los cambios de carpetas durante un escaneo invalidan ese resultado antes de publicarlo; el siguiente escaneo usa las autorizaciones actuales. La búsqueda, las playlists y el historial siguen usando las identidades locales existentes.

## Landscape, padding y scroll

`LocalLayoutPolicy` trabaja con dimensiones de ventana en dp. En ventanas horizontales compactas se reducen la cabecera original, los márgenes de sección y los tamaños de los mismos PlaylistTile, TrackTile y ArtistTile. Portrait y ventanas grandes conservan sus tamaños anteriores. No hay valores basados en la resolución física de la Thor ni otro selector de fuentes.

La barra local se pliega al entrar en una pestaña de una ventana horizontal compacta, conservando su control original para desplegar los cuatro destinos. Radio, el orden y la política L1/R1 de la fase anterior no cambian. Spotify y YouTube mantienen sus controles y sus parámetros originales de tarjetas/cabecera.

La reserva inferior local usa la **altura total realmente medida** de los controles flotantes. No añade de nuevo la altura del mini-reproductor ni impone la altura expandida cuando la barra está compacta. Solo antes de obtener una medida usa las dimensiones conocidas como alternativa. No reserva un mini-reproductor si no existe contenido de reproducción.

Se elimina el recorte adicional de la vista local; sus contenedores Lazy y el viewport restante conservan el desplazamiento y el auto-scroll del foco. Los detalles locales reciben esa misma reserva y limitan su cabecera al espacio horizontal disponible. Mantienen el colapso original y sus controles; la última fila puede desplazarse por encima de la barra. La fórmula anterior de 512 dp sigue siendo la predeterminada para los detalles remotos.

D-pad, sticks, A/B, L1/R1, L2/R2 y START/SELECT no se remapean. Los controles nuevos reutilizan FilterChip/pressable y el selector de documentos del sistema; falta validar el retorno de foco con el mando físico.

## Apple Music Motion Artwork local

El bloqueo original era explícito: `loadNowPlayingArt` limpiaba el estado y salía para cualquier `local:track:`. Ahora consulta una ruta local verificada, cancelable y con caché persistente de hasta 200 resultados. Se utiliza el índice cuando está disponible y, para el explorador original, los metadatos completos de la canción y su duración.

La correspondencia exige título, intérprete, álbum y duración compatibles, relaciones de identidad completas y un único ID de edición de álbum. Al consultar ese álbum se verifican de nuevo su nombre y artista del álbum. Si hay varias ediciones plausibles, duración desconocida/incompatible o datos incompletos, se conserva el fallback estático.

No se expone un identificador Apple/ISRC fiable en el lector de tags actual; por eso no se supone uno a partir de nombres o identificadores MediaStore. Las asociaciones verificadas guardadas se reutilizan solo con la misma firma de metadatos. Una asociación de artista, por sí sola, no prueba qué edición de álbum corresponde a una canción.

Se reutilizan PlayerVisualPolicy y MotionCover. Para archivos locales se exige activar las portadas animadas y elegir Apple Music; no se usa Canvas como sustituto. Landscape prioriza el recurso 1:1 y conserva la alternativa proporcional existente cuando solo hay vídeo vertical. El videoclip activo o en carga excluye Motion Artwork, y la vista oculta no lo instancia.

Los resultados locales contienen exclusivamente los URI de movimiento: `heroUrl` y `coverUrl` permanecen null. Así la portada incrustada sigue siendo la imagen estática y el fallback; no se reemplaza el archivo ni su edición por una portada remota. Offline se desactiva el movimiento local para evitar que el reproductor visual inicie una descarga. Las reglas remotas anteriores siguen pasando sus pruebas.

## Verificación y APK

Comando ejecutado:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

Resultado: **BUILD SUCCESSFUL**, **286 pruebas JVM aprobadas**, sin fallos, errores ni omitidas: los 236 casos anteriores y 50 nuevos. Se actualizó el contrato estructural que exigía el recorte adicional del viewport, retirado por esta solicitud.

APK debug ARM64:

`/home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

- 53 317 770 bytes, exclusivamente bibliotecas `arm64-v8a`.
- Paquete `dev.lelonio.square`, versión `2.6.1-squareds.1`, código 58; minSdk 26, targetSdk 35 y compileSdk 37.
- SHA-256 del APK: `1b514735105aa673d9209973909397eb0d5202fff790fd10dc001c8e45e41ed6`.
- Firma comprobada con apksigner. Certificado SHA-256 compatible con el anterior: `25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c`.
- `git diff --check` sin errores; nada staged.

Sin cambios en versión, manifiesto, Gradle/SDK/NDK/Rust, workflows, actualizador, código nativo, backends, `LocalLibrary.kt`, `MotionCover` ni `HeroBackdrop`. La ampliación de `LocalPlayerFactory` está limitada al resolutor de URI descrito arriba. No se añade otra MediaSession.

La sesión Android fue el AVD existente Medium Phone API 37, abierto temporalmente en modo de solo lectura, con traducción ARM64. Se usaron audios sintéticos y una portada plana de prueba, fuera del producto. La rotación se simuló en el emulador; no se modificó una Thor ni su resolución.

Comprobado en Android:

1. Música local funciona con los tres archivos MediaStore iniciales y sin carpetas configuradas.
2. Intentar activar el filtro vacío mantiene ALL y muestra una explicación.
3. El selector Android concede un árbol persistente que incluye subcarpetas.
4. El modo general combina cuatro archivos sin duplicados: tres de MediaStore y uno bajo `.nomedia`, descubierto exclusivamente mediante SAF.
5. El modo restrictivo muestra los tres archivos autorizados y excluye el archivo externo al árbol.
6. MediaSession confirmó PLAYING, posición creciente y ausencia de error para el archivo SAF con ID negativo.
7. Tras reinstalar/reiniciar, se conservaron árbol y modo SELECTED. Se denegó READ_MEDIA_AUDIO y se pudo abrir y reproducir el álbum SAF mediante su autorización persistente.
8. La portada incrustada del archivo SAF aparece en tarjetas y cabecera sin permiso general de audio.
9. El rechazo manual quedó guardado y siguió rechazado después de reiniciar el trabajador automático.
10. En landscape se revisaron cabecera, filtros, tarjetas, detalle del álbum y desplazamiento de su última fila por encima de los controles.

Además se autorizó el subárbol `sub` mientras ya estaba autorizado su padre: el registro correspondiente tuvo dos autorizaciones y siguió apareciendo una sola vez. Se retiró el padre manteniendo el subárbol, y después la última carpeta: el modo volvió a ALL, reaparecieron los tres archivos MediaStore y se mostró la explicación. Se comprobó que los archivos originales de prueba seguían existiendo. Tras otro reinicio se conservó ALL sin carpetas.

Los casos de coincidencia fiable/homónimos/ediciones y exclusión de dos vídeos se verificaron mediante pruebas JVM del código de producción. **No se declara una reproducción real de Motion Artwork de catálogo ni una descarga real de retrato/logotipo verificado con estos fixtures sintéticos.** No se validó físicamente AYN Thor. La sesión temporal del emulador se cerró al terminar; se descartaron sus datos y se conservaron únicamente las capturas de revisión.

## Límites y pruebas pendientes

- La resolución automática es conservadora y actualmente consulta AppleCatalog; Spotify/YouTube se conservan para asociación manual y recursos ya guardados. Una colección con una sola canción por artista, tags incompletos o versiones distintas puede mantener su fallback hasta una decisión manual.
- Las consultas nuevas de Apple dependen del acceso y las restricciones de red de la integración original, incluidos sus recursos editoriales opcionales. Un proveedor inaccesible no bloquea la música.
- Proveedores SAF distintos que no expongan una equivalencia comprobable no se fusionan por coincidencias de nombres. La lectura y reproducción dependen de lo que el DocumentsProvider permita abrir.
- MediaStore mantiene su ContentObserver. Los cambios exclusivamente SAF se vuelven a comprobar al iniciar/volver a la fuente, al volver a los controles de carpetas o al reindexar; no se incorpora un observador universal de todos los proveedores de documentos.
- Faltan microSD y proveedores reales, colecciones grandes, revocación desde Ajustes del sistema y latencia de servicios con muestras reales.
- Validar en AYN Thor: ambas pantallas, landscape de su panel inferior, foco tras el selector Android, D-pad/sticks/atajos, primeras pulsaciones táctiles, playlists, letras, EQ y efectos mientras se cambia el filtro.
- Validar con artistas/ediciones reales: asociaciones automáticas, homónimos, corrección manual, retrato/logo disponibles o fallidos, caché offline, vídeo 1:1, preferencia desactivada y transición a videoclip.
- Se mantienen los límites previos: no hay cola mixta; inicializar Spotify tras un arranque exclusivamente local puede pausar la cola local existente.

## Archivos y capturas

Estos son los archivos de esta fase; el estado Git incluye también cambios previos que se conservaron.

- [app/src/main/java/dev/lelonio/square/SquareApplication.kt](../app/src/main/java/dev/lelonio/square/SquareApplication.kt)
- [app/src/main/java/dev/lelonio/square/data/AppleCatalog.kt](../app/src/main/java/dev/lelonio/square/data/AppleCatalog.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalMusicModels.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalMusicModels.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalMusicRepository.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalMusicRepository.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalLibraryIndexer.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalLibraryIndexer.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalIndexUpdate.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalIndexUpdate.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalSafIndexer.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalSafIndexer.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalFolderPolicy.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalFolderPolicy.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalVisualMatching.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalVisualMatching.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalVisualResolver.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalVisualResolver.kt)
- [app/src/main/java/dev/lelonio/square/data/local/LocalArtworkRepository.kt](../app/src/main/java/dev/lelonio/square/data/local/LocalArtworkRepository.kt)
- [app/src/main/java/dev/lelonio/square/playback/LocalPlayerFactory.kt](../app/src/main/java/dev/lelonio/square/playback/LocalPlayerFactory.kt)
- [app/src/main/java/dev/lelonio/square/ui/MainViewModel.kt](../app/src/main/java/dev/lelonio/square/ui/MainViewModel.kt)
- [app/src/main/java/dev/lelonio/square/ui/SquareApp.kt](../app/src/main/java/dev/lelonio/square/ui/SquareApp.kt)
- [app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt](../app/src/main/java/dev/lelonio/square/ui/home/HomeScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt](../app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/player/PlayerVisualPolicy.kt](../app/src/main/java/dev/lelonio/square/ui/player/PlayerVisualPolicy.kt)
- [app/src/main/java/dev/lelonio/square/ui/local/LocalMusicScreen.kt](../app/src/main/java/dev/lelonio/square/ui/local/LocalMusicScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/local/LocalLayoutPolicy.kt](../app/src/main/java/dev/lelonio/square/ui/local/LocalLayoutPolicy.kt)
- [app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt](../app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt)
- [app/src/main/java/dev/lelonio/square/ui/settings/LocalMusicFoldersSection.kt](../app/src/main/java/dev/lelonio/square/ui/settings/LocalMusicFoldersSection.kt)
- [app/src/main/res/values/strings.xml](../app/src/main/res/values/strings.xml)
- [app/src/main/res/values-es/strings.xml](../app/src/main/res/values-es/strings.xml)
- [app/src/test/java/dev/lelonio/square/data/local/LocalFolderPolicyTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalFolderPolicyTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalVisualMatchingTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalVisualMatchingTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalPresentationIntegrationTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalPresentationIntegrationTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalIndexUpdateTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalIndexUpdateTest.kt)
- [app/src/test/java/dev/lelonio/square/data/local/LocalRefinementNavigationTest.kt](../app/src/test/java/dev/lelonio/square/data/local/LocalRefinementNavigationTest.kt)
- [docs/LOCAL-MUSIC-AUTOMATIC-SAF.md](../docs/LOCAL-MUSIC-AUTOMATIC-SAF.md)
- [docs/LOCAL-MUSIC-LIBRARY.md](../docs/LOCAL-MUSIC-LIBRARY.md)
- [docs/LOCAL-MUSIC-REFINEMENT.md](../docs/LOCAL-MUSIC-REFINEMENT.md)

Capturas del emulador (datos sintéticos de prueba):

- [album-landscape.png](screenshots/local-auto/album-landscape.png)
- [album-scroll.png](screenshots/local-auto/album-scroll.png)
- [artista-rechazado.png](screenshots/local-auto/artista-rechazado.png)
- [carpetas-seleccionadas.png](screenshots/local-auto/carpetas-seleccionadas.png)
- [inicio-landscape.png](screenshots/local-auto/inicio-landscape.png)
- [saf-sin-permiso-media.png](screenshots/local-auto/saf-sin-permiso-media.png)
- [vuelta-modo-general.png](screenshots/local-auto/vuelta-modo-general.png)
