# Información: diagnóstico, reparación y validación pendiente

## Diagnóstico del código inspeccionado

La pestaña inferior existente se conserva. Se han auditado `SquareApp`, `PlayerScreen`, `PlayerToolsContent`, `CreditsView`, `PlayerMetadata`, `MetadataLinks`, `MetadataDestination`, `ThorMetadataNavigation`, `TrackAlbum`, `MainViewModel`, los controles `pressable`/`thorFocus` y las rutas originales de detalle.

- El encabezado de `PlayerTool.INFO` apuntaba al recurso de Créditos. El encabezado añadido usaba «Artista y álbum», en singular. Ahora son **Información**, **Artistas y álbum** y **Créditos**.
- Información solo dibujaba el álbum cuando `PlaybackState.album` ya contenía texto. La consulta `trackAlbum`/`albumOf` se ejecutaba al pulsar; no actualizaba el nombre mostrado. Con un nombre vacío no había enlace desde el que iniciar esa consulta.
- Los enlaces de artistas solo se habilitaban con los URI del estado de reproducción. No había una resolución de identidades para estados incompletos. La representación de Spotify Connect tampoco contiene álbum ni artistas individuales. El nuevo estado de presentación cubre ese caso sin cambiar Connect ni MediaSession.
- `MetadataLinks` ya utilizaba `pressable`, `combinedClickable` y `thorFocus`. Si faltaba el destino validado, el texto no recibía callback: el recorrido terminaba antes de emitir una solicitud. No se ha demostrado un bloqueo físico de eventos táctiles.
- `MetadataDestination` acepta los URI reales de Spotify y los identificadores InnerTube con letras, números, guion y guion bajo. Las pruebas de formato, tipo y servicio no mostraron que fuera necesario sustituir su validación.
- Arriba se usaba la cola compartida, mientras que abajo se navegaba directamente. El cierre inferior llamaba a `snapTo` sobre el mismo `Animatable` que controla el efecto de `toolsOpen`. Esas dos mutaciones pueden cancelarse entre sí. Se ha eliminado esa competencia y el cierre ahora espera la animación que ya posee el estado de herramientas.
- El receptor anterior permanecía ligado a composición, sin restringirse al ciclo STARTED. También suspendía entre abrir y confirmar una solicitud. Ahora la entrega se confirma inmediatamente después de abrir y una cancelación previa conserva la solicitud.

Estos son defectos identificables en el código y cubiertos por pruebas. No se dispone de una traza física de la canción concreta que produjo el fallo comunicado; el resultado en la AYN Thor sigue pendiente.

## Presentación y resolución

La organización de la pestaña existente queda así:

```
Información
  Artistas y álbum
    Artistas
      Nombres individuales
    Álbum
      Nombre real / Resolviendo álbum… / Álbum no disponible
  ───────────
  Créditos
    Contenido original de Square
```

El nombre conocido permanece visible aunque no haya ID. Solo se habilita un enlace cuando existe una identidad válida del servicio de la canción. Un texto compuesto con varios nombres sin identidades individuales no abre indiscriminadamente al primer artista.

`ResolvedPlayerMetadata` separa los datos mostrados del estado musical. Un efecto con claves de identidad/metadatos inicia la resolución asíncrona; posición, pausa, reproducción y recomposiciones ordinarias no disparan consultas. El cambio de canción cancela el trabajo anterior. Hay un límite de 15 segundos; sin respuesta se conservan los nombres disponibles y los textos sin identidad permanecen informativos.

Spotify se consulta por URI exacto de canción mediante `Catalog.tracks`; la Web API existente es una alternativa opcional para información incompleta. El álbum utiliza también `trackAlbum` y `viewModel.albumOf`. Se verifica la identidad de la canción devuelta.

YouTube Music utiliza `YouTube.queue(videoIds = …)` del cliente InnerTube existente para obtener los metadatos del vídeo/canción exacto. Esta consulta no altera la cola de reproducción. Se conservan los ID propios de artistas y álbumes. Si el catálogo no identifica un álbum, se informa de su ausencia.

No se consulta por coincidencia de nombres para obtener perfiles. Los archivos locales conservan sus nombres y no provocan búsquedas remotas. El método antiguo `artistOf`, que tiene búsquedas por nombre para otros usos, no participa en estos enlaces.

## Comunicación y navegación

Los enlaces superiores y los de Información usan la misma resolución, validación y emisión a `ThorMetadataNavigation`. La cola compartida conserva solicitudes con ID hasta la entrega. Solo la composición inferior consume, cuando su actividad está STARTED y el NavHost original está disponible.

La entrega solicita cerrar herramientas, espera su progreso cerrado, vuelve a comprobar el servicio y abre la página. La cancelación del receptor durante la espera deja la solicitud pendiente para el siguiente receptor. Los destinos de un servicio diferente se rechazan con un registro explícito, sin cambiar automáticamente el servicio musical.

El NavHost original usa una ruta común de detalle; `MainViewModel` distingue el contenido por URI. No se ha añadido otro NavHost ni otra actividad. Se ha reconocido también `ytmusic:album:` como álbum en esa clasificación. Las consultas de nombre/imagen de Spotify se restringen a URI Spotify.

La navegación reutiliza `openPlaylist`, el historial de páginas y las acciones Atrás/B existentes. La pantalla superior no consume solicitudes ni colapsa como parte de este flujo. No se invocan operaciones de reproducción, posición o cola al abrir los metadatos.

Los registros DEBUG bajo `SquareMetadata` incluyen `METADATA_CLICK`, `METADATA_RESOLVED`, `METADATA_REQUEST`, `METADATA_RECEIVED`, `METADATA_NAVIGATE`, `METADATA_COMPLETE` y `METADATA_ERROR`. Contienen fases, tipo, servicio, cantidades e ID secuencial de solicitud; no imprimen nombres, URI ni credenciales. No se emiten por recomposición.

## Archivos de esta intervención

Respecto al estado encontrado al comenzar esta solicitud:

- `ui/player/ResolvedPlayerMetadata.kt`: nuevo estado y resolución de presentación por identidad.
- `ui/player/PlayerMetadata.kt`: evita enlazar una lista de nombres al primer artista.
- `ui/player/TrackInformationView.kt`: nombre real y estados del álbum dentro de la sección existente.
- `ui/player/PlayerToolsContent.kt`: encabezado Información y metadatos resueltos.
- `ui/player/PlayerScreen.kt`: conecta los mismos metadatos con Información y los nombres superiores.
- `ui/SquareApp.kt`: resolución asíncrona, emisión común y consumo inferior con ciclo de vida.
- `ui/MetadataNavigationHandler.kt`: espera de cierre y trazas de apertura/rechazo.
- `ui/TrackAlbum.kt`: un URI con nombre vacío ya no impide resolver el nombre.
- `ui/MainViewModel.kt`: consulta exacta de canción y clasificación/aislamiento de destinos.
- `thor/ui/ThorMetadataNavigation.kt`: entrega que conserva solicitudes canceladas antes de navegar.
- `backend/youtube/YouTubeBackend.kt`: consulta de metadatos por ID con el cliente existente.
- `res/values/strings.xml`, `res/values-es/strings.xml`: encabezados y estados informativos.
- `ResolvedPlayerMetadataTest.kt`, `MetadataDeliveryIntegrationTest.kt`: nuevas pruebas.
- Este documento.

`CreditsView.kt`, `FullScreenVideo.kt` y `MotionCover.kt` permanecen idénticos, byte a byte, al estado encontrado en esta intervención. No se han modificado los proveedores de créditos, motores de reproducción, SDK, NDK, Rust ni Gradle. Se conservan los cambios anteriores que ya estaban en el árbol de trabajo.

## Pruebas y compilación

Comando requerido:

```
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

Resultado: BUILD SUCCESSFUL. **153 pruebas JVM, 0 fallos, 0 errores, 0 omitidas**. Se mantienen las pruebas existentes y se añaden once casos para resolución/presentación, identidades, archivos locales y entrega. Una prueba utiliza un `Animatable` real de Compose con reloj de frames para comprobar que artistas y álbumes se abren después del cierre; otra cancela el receptor durante el cierre y confirma la entrega posterior.

Las pruebas de historial utilizan un host simulado. No son una validación visual, de pulsación real, ni del NavController Android en el dispositivo. `adb devices -l` no muestra dispositivos conectados; no se han añadido dependencias ni cambiado Gradle para montar otro entorno de interfaz.

APK ARM64 debug: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`. Se ha comprobado que contiene únicamente bibliotecas de `arm64-v8a`. La firma debug se verifica correctamente.

SHA-256 del APK entregado: `9784fed8e14acccaf995c58fd87dd80d76df38d9c7dc2c38bae671300d8618c0`.

## Validación pendiente en AYN Thor

1. Comprobar los tres encabezados y el nombre real del álbum, incluida una canción de una cola antigua o de Spotify Connect con metadatos iniciales incompletos.
2. Pulsar cada artista y el álbum desde Información y desde la tarjeta superior. Confirmar el perfil/álbum seleccionado y que las solicitudes se reciben abajo.
3. Verificar cierre de herramientas, mini-reproductor, historial y regreso Atrás/B desde una pestaña y desde otro detalle.
4. Probar tacto, primera pulsación tras usar mando, D-pad, stick y A. Confirmar que los créditos no adquieren enlaces ni foco interactivo.
5. Repetir con Spotify, YouTube Music, artistas múltiples, canciones sin álbum identificable y archivos locales.
6. Interrumpir/reanudar la actividad inferior durante el cierre y cambiar de servicio con solicitudes pendientes.
7. Confirmar continuidad de audio, posición y cola, reproductor superior permanente, Motion Artwork, videoclip, letras, Connect, EQ y efectos.

No se han creado commits, realizado push ni publicado una Release. El trabajo se detiene para validación física.
