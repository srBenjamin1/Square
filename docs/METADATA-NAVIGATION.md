# SquareDS navegación desde metadatos y créditos

La rama `update/upstream-2.6.1` incorpora enlaces independientes de artista y álbum en el reproductor superior y en información/créditos. En modo Thor las acciones envían una solicitud al navegador inferior; no navegan en el NavController superior ni cambian la sesión de reproducción. La compilación y las 127 pruebas JVM pasan; la interacción en el dispositivo queda pendiente de validación.

## Diagnóstico

La tarjeta horizontal `SongCard` mostraba el artista como texto plano y no mostraba el álbum. El reproductor convencional tenía una línea de artistas con hit testing por posición de caracteres, pero sin destinos de foco independientes. Si un nombre no aportaba URI, el fallback podía abrir el primer artista. El título convencional ya reutilizaba la resolución de álbum, pero otras acciones de artista navegaban por el controlador local de la instancia de `SquareApp`.

`CreditsView` mostraba los roles, personas, subroles y fuentes del proveedor sin acciones de navegación. La vista de créditos se reutiliza tanto en el stage de información como en las herramientas inferiores. Se expone también mediante el icono original de información en la cabecera superior, sin añadir una barra de navegación.

El puente `ThorAlbumNavigation` ya trasladaba álbumes mediante un único `StateFlow<CatalogPlaylist?>`. No diferenciaba servicio/tipo y podía reemplazar una solicitud pendiente por otra. Se amplía y sustituye por `ThorMetadataNavigation`, sin cambiar actividades ni crear otro NavHost.

## Identidades y navegación

`MetadataDestination` contiene tipo (`ARTIST` o `ALBUM`), URI, servicio (`SPOTIFY` o `YOUTUBE_MUSIC`), nombre y portada opcional. Su fábrica valida el prefijo, el tipo y el alfabeto del identificador, y exige correspondencia con el servicio de la canción. La validación comprueba el formato y la procedencia del modelo; la existencia remota de la página sigue dependiendo del catálogo y de la red.

Las solicitudes de `MetadataNavigationQueue` añaden un ID creciente y permanecen en orden hasta que el consumidor las confirma por ID. Pulsaciones rápidas no sustituyen las anteriores. Confirmar una solicitud no borra las siguientes; volver a solicitar una página ya visitada crea otra solicitud independiente.

Solo la instancia inferior (`showExpandedPlayer=false`) consume el puente. Espera a que su NavHost tenga una entrada, cierra las herramientas y pone su expansión en cero, y llama al helper existente `openPlaylist` con el destino. Utiliza el historial de páginas de `MainViewModel` y el back stack de Navigation existentes. No fuerza expansión del reproductor convencional ni toca el estado de expansión superior.

Si el usuario ha cambiado de servicio antes de recibir una solicitud, el consumidor la descarta en vez de entregar un URI al backend equivocado. No cambia el servicio musical ni interrumpe la canción para resolver la navegación.

En modo convencional se usa el mismo destino validado, se contrae el reproductor como antes y se navega en el NavHost de esa ventana. Los helpers de reproducción, encolado y transporte no participan en estas acciones.

## Artista y álbum

Cada elemento de `PlaybackState.artists` conserva su nombre y su URI independientes. Un artista invitado sin URI no hereda el del primero. Los homónimos con IDs diferentes permanecen como destinos diferentes. Para metadatos antiguos sin lista estructurada se conserva la identidad primaria ya presente en `artistUri`; no se divide una cadena de nombres ni se deducen otras identidades.

Los enlaces usan la tipografía y colores existentes, con `pressable` y contorno Liquid Glass adaptado al contenedor del texto. Cada artista y el álbum tienen sus propias áreas de tacto, foco y activación; la separación entre nombres no abre otro artista. El álbum se añade discretamente debajo de los artistas de la tarjeta horizontal. La acción de la portada no cambia.

El álbum reutiliza `trackAlbum` y `viewModel.albumOf`, los helpers de Go to album. Un URI disponible se utiliza directamente. Para Spotify se conserva la alternativa existente de consultar el álbum por el ID de la canción cuando falta el URI; no se busca un álbum por nombre. En YouTube Music se usa el URI que ya proporciona InnerTube. El resultado se valida antes de enviarlo al puente.

Los URIs `spotify:artist:`/`spotify:album:` y `ytmusic:artist:`/`ytmusic:album:` no se mezclan. Las canciones locales y `spotify:local:` no producen destinos remotos por sus etiquetas, incluso si conservan IDs antiguos incoherentes. Los nombres sin identidad quedan visibles sin acción; no se implementa una biblioteca local organizada.

## Créditos e información

Se mantienen roles, nombres, subroles y fuentes de grabación del proveedor original. Las nuevas filas de artista y álbum siguen disponibles durante la carga o cuando no hay créditos adicionales. No se modifica el proveedor ni se hacen consultas nuevas de créditos.

Un compositor o productor solo tiene enlace cuando el proveedor aporta su propio URI de artista válido del mismo servicio. Su rol sigue mostrándose como compositor o productor; no se le convierte en artista principal ni se asocia su nombre a un resultado de búsqueda. Los créditos sin URI permanecen como texto.

El icono ⓘ superior abre el panel de información existente y lo alterna con letras; seleccionar un enlace dentro de él abre la página abajo y mantiene la información y el reproductor arriba. Las herramientas inferiores de Información reciben las mismas acciones y se cierran cuando se abre el destino.

## Tacto y mando

Se reutilizan `pressable`, `thorFocus`, `UpperPlayerControls` y `UpperPlayerFocusScene`. La conexión de entrada superior, antes limitada a canciones con acción de vídeo en landscape, se mantiene ahora también para los metadatos superiores y en ambas orientaciones. No se cambia el mapa de botones ni se crea un dispatcher nuevo.

D-pad y stick utilizan las escenas y controles de foco existentes; A activa el enlace enfocado. El tacto conserva el mecanismo existente de ocultar el contorno sin consumir la primera pulsación. B recibido arriba sigue la navegación contextual inferior cuando no se está viendo un vídeo. Los eventos recibidos directamente por la actividad inferior mantienen su destino inferior. L1/R1, L2/R2, START y SELECT conservan sus órdenes.

## Pruebas y compilación

Comando ejecutado:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

La ejecución final terminó con `BUILD SUCCESSFUL` en 15 segundos: **127 tests, 0 fallos, 0 errores y 0 omitidos**. Se conservan las 111 pruebas anteriores; se añaden 16 pruebas y se adapta la prueba del puente de álbum anterior al nuevo protocolo.

Las pruebas nuevas cubren:

- Destinos Spotify y YouTube con tipo, servicio, URI y portada correctos.
- Rechazo de tipos cruzados, servicios cruzados, URIs ausentes, externos o malformados.
- Conservación de los IDs individuales de artistas múltiples y homónimos; invitado sin URI sin fallback al primero.
- Texto local sin navegación remota y validación de identidades de colaboradores.
- Destinos de artista y álbum independientes.
- Cola de solicitudes antes de tener consumidor, confirmación por ID, ausencia de borrado accidental y orden de solicitudes rápidas.

Las pruebas JVM comprueban modelos y protocolo de entrega; no prueban el renderizado de Compose, eventos físicos de mando, audio ni el back stack real de Android en dos pantallas. Estas comprobaciones corresponden a la validación física siguiente.

También pasan `git diff --check`, `apksigner verify` y la inspección ZIP del ABI. El APK contiene solo `arm64-v8a`, incluida `libsquarecore.so`. Conserva `applicationId=dev.lelonio.square`, `versionName=2.6.1-squareds.1` y `versionCode=58`.

APK:

```text
/home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

SHA-256 del APK:

```text
a1f6c12cf0ea47ffcee09f8c13c368ea870e426690f4920c5c435a0beb91065d
```

Certificado SHA-256:

```text
25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c
```

No se han modificado en esta tarea los cambios anteriores de vídeo/Motion Artwork, sus estados de servicio ni el motor de reproducción. La comparación con el estado al inicio confirma que `SpotifyVideoMode`, `PlaybackService`, `AppleCatalog`, `MotionCover`, `FullScreenVideo`, `ThorInputRouter`, `UpperPlayerControls` y `trackAlbum` conservan sus contenidos previos. Tampoco cambian actividades, `MainViewModel`, MediaSession, Librespot, preferencias, actualizador ni herramientas de compilación. Los cambios nuevos de `PlayerScreen` y `SquareApp` se limitan a los accesos, callbacks, foco y entrega de navegación descritos aquí.

## Validación física pendiente en AYN Thor

La validación física previa de vídeo y reproducción fue indicada por el usuario. Las siguientes pruebas de esta integración no se han realizado en el dispositivo durante esta tarea.

Instalación sobre un APK con el mismo certificado:

```bash
adb install -r /home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

1. Desde la tarjeta superior, tocar cada artista y el álbum. Confirmar página correcta abajo, reproductor fijo arriba y reproducción/cola/posición intactas.
2. Probar artistas múltiples, incluido uno sin URI y homónimos con identidades distintas. Cada enlace debe abrir su ID; los nombres sin ID deben permanecer sin acción.
3. Repetir con Spotify y YouTube Music. Comprobar álbumes y perfiles propios de cada servicio y ausencia de búsquedas por nombre.
4. Abrir ⓘ arriba y los accesos de artista/álbum dentro de créditos. Verificar roles, compositores, productores, subroles y fuentes existentes. Un colaborador sin URI no debe ser interactivo.
5. Probar la recepción abajo desde Inicio, Novedades, Biblioteca y Buscar, y desde otra página de detalle. Atrás/B debe volver a la vista anterior con la misma canción arriba.
6. Repetir con Cola, EQ, Efectos, Información y Ajustes abiertos abajo. Deben cerrarse y dejar la página solicitada, sin destello del reproductor convencional y con mini-reproductor disponible.
7. Enfocar artistas, álbum e identidades de créditos con D-pad y stick; activar con A. Comprobar contornos independientes y que tocar activa inmediatamente y oculta el contorno del mando.
8. Probar B y los atajos L1/R1, L2/R2, START y SELECT con el foco en cada ventana. La navegación inferior y sus herramientas deben conservarse.
9. Probar metadatos sin IDs, archivos locales y `spotify:local:`: texto visible, sin navegación a homónimos ni crash.
10. Comprobar Motion Artwork, Canvas portrait, entrada/salida de videoclip, letras, Spotify Connect, audio, cola, EQ y efectos después de navegar.
11. Repetir los enlaces en modo convencional de una pantalla y en portrait. Deben conservar su navegación habitual y no generar actividades adicionales.

## Archivos de esta tarea

Modificados:

- `app/src/main/java/dev/lelonio/square/ui/SquareApp.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/PlayerScreen.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/PlayerToolsContent.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/CreditsView.kt`
- `app/src/test/java/dev/lelonio/square/ui/TrackAlbumTest.kt`

Añadidos:

- `app/src/main/java/dev/lelonio/square/data/MetadataDestination.kt`
- `app/src/main/java/dev/lelonio/square/thor/ui/ThorMetadataNavigation.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/PlayerMetadata.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/MetadataLinks.kt`
- `app/src/test/java/dev/lelonio/square/data/MetadataDestinationTest.kt`
- `app/src/test/java/dev/lelonio/square/ui/PlayerMetadataTest.kt`
- `app/src/test/java/dev/lelonio/square/thor/ui/MetadataNavigationQueueTest.kt`
- `docs/METADATA-NAVIGATION.md`

Eliminado al generalizar el protocolo: `app/src/main/java/dev/lelonio/square/thor/ui/ThorAlbumNavigation.kt`.

Los cambios sin commit de la tarea anterior y los archivos locales preexistentes se conservan. No se han creado commits, hecho push ni publicado releases.
