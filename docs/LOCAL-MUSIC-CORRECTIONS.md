# Música local: correcciones de identidad, Información, superposición y Motion

## Alcance

Intervención sobre `feature/local-music-library`, HEAD `d3b093e`, conservando todos los cambios pendientes anteriores. Sin commits, push ni Releases. Los informes anteriores describen sus respectivas fases; este informe sustituye sus criterios de dos canciones, viewport reservado y Motion local estricto.

## Identificación con una canción

La condición anterior exigía dos títulos y dos IDs distintos, álbum y duración compatibles. El trabajador, además, descartaba los temas sin álbum o duración antes de consultar el catálogo.

Ahora una canción de título e intérprete exactos normalizados, con relaciones de catálogo coherentes y una identidad inequívoca, basta. El álbum conocido corrobora la identidad; si falta, se usa la evidencia disponible. Las búsquedas siguen acotadas a tres títulos representativos y se espacia el acceso al catálogo. Los títulos adicionales intersectan las identidades plausibles: pueden resolver un homónimo o revelar una contradicción; no se elige por mayoría. Los calificadores live, deluxe y colaboraciones se conservan.

MANUAL y REJECTED siguen prevaleciendo sobre resultados asíncronos. Una identidad automática anterior se conserva durante PENDING para poder contrastarla. Una respuesta contradictoria guarda REVIEW_REQUIRED y conserva la referencia anterior para revisión, ocultando sus imágenes. Una confirmación de la identidad original o una elección manual resuelve la revisión. El guardado de imágenes comprueba también la marca de revisión, evitando que una descarga atrasada la deshaga. Un refresco de la misma identidad conserva los archivos de imagen vigentes.

La firma `evidence-v2` invalida los resultados negativos del algoritmo anterior, sin borrar decisiones manuales ni rechazos. Se conservan las cachés acotadas y sus plazos; el reintento explícito evita reutilizar la respuesta de búsqueda negativa de la sesión.

## Información del artista

Se reutiliza el único icono Info de ArtistHeader. Para artistas locales abre GlassChoiceMenu con Información, estado y las acciones aplicables: asociar/cambiar, quitar, reintentar y rechazar. La decisión manual no ofrece reintento ni rechazo automáticos; el rechazo permite reintentar. Información y estado despliegan el contenido original, junto al estado completo de asociación.

No quedan botones ni estados de asociación bajo las fotos de la cuadrícula. Solo introducir un perfil abre el NameDialog pequeño existente. La asociación manual mantiene los enlaces Spotify/YouTube admitidos previamente. Los créditos de canciones no se modificaron.

El menú reutiliza superficie, animaciones, tipografía y controles Glass. Su ancho es opcional en el componente compartido; los demás menús conservan el valor anterior. En ventanas bajas se limita y desplaza su contenido, se ancla dentro de los insets y permanece por encima de los controles inferiores. Una escena Thor de prioridad 2 concentra su foco y cancela salidas hacia el fondo; BackHandler cierra el menú. No se cambió el mapeo de entradas.

## Causa y corrección de la franja inferior

LocalMusicScreen aplicaba `padding(bottom)` al modificador del grid: reducía su viewport y dejaba el fondo visible como una banda fija. El contentPadding final, en cambio, solo tenía 20 dp. SquareApp mantenía además una medición y un padding exclusivos para local.

Se restaura el viewport completo y se pasa el espacio inferior a contentPadding. Todas las rutas locales usan el mismo listPadding que las remotas: altura de barra, navegación y presencia del mini-reproductor. La altura accesoria se sigue descontando de la medición de la barra; no se suma dos veces. No se cambiaron LiquidBottomTabs, su plegado, los fondos, WindowInsets ni LocalLayoutPolicy.

Inicio, Novedades, Biblioteca y carpetas comparten el grid corregido; álbumes, artistas y playlists usan PlaylistScreen; Buscar conserva su viewport original y recibe el mismo espacio de revelado del foco. Solo en contenido local, las peticiones BringIntoView añaden el espacio tapado por los controles. El valor predeterminado es cero, por lo que las fuentes remotas conservan su comportamiento. Esto permite superposición durante el desplazamiento y visibilidad completa de la última tarjeta al llegar al final.

## Recorrido de Motion local

Se encontraron varios bloqueos independientes:

1. AppleCatalog y el resolutor exigían álbum y duración antes de buscar.
2. El matcher obligaba a una duración dentro de dos segundos y a una edición única.
3. La extracción local dependía de albumFromAttributes, que rechazaba filas sin portada estática aunque pudieran contener vídeo.
4. La carga y la política local exigían Apple como proveedor también en horizontal, mientras que el reproductor remoto ya usa Apple en landscape independientemente del proveedor vertical.

La ruta local ahora busca por título e intérprete exactos. El álbum exacto tiene prioridad sobre la duración; una duración compatible desempata entre candidatos equivalentes, pero su ausencia o diferencia no descarta por sí sola una canción. No se necesita el álbum completo en almacenamiento ni una relación de artista para obtener Motion si ya se identifica el álbum. La fotografía del artista sí requiere su identidad de catálogo.

Se consultan por ID hasta cuatro álbumes plausibles. Más candidatos permanecen ambiguos. Las ediciones pueden compartir una animación: solo se conservan las URLs de cada orientación que todas compartan. Si difieren todas, se mantiene la portada estática. Los campos de vídeo se leen independientemente de las portadas. No se reemplaza la portada incrustada ni se modifica el archivo de audio.

MainViewModel entrega únicamente metadatos de vídeo al estado existente. PlayerVisualPolicy mantiene la prioridad 1:1 en horizontal, la alternativa vertical, la visibilidad y la exclusión con videoclip. PlayerScreen y MotionCover siguen siendo los existentes. El interruptor de animación y offline bloquean las consultas nuevas y la reproducción local remota; en vertical se conserva la elección de proveedor. Al cambiar orientación o conectividad se reevalúa el resultado.

La caché positiva guarda metadatos, no un vídeo descargado: offline usa la portada estática. Los fallos de red no se guardan como ausencia definitiva. Los negativos se reutilizan siete días y quedan invalidados por cambios de tags o versión de evidencia.

Los logs de depuración bajo `SquareDSLocalVisual` distinguen METADATA_READY, SEARCH_RESULTS, MISSING_METADATA, NO_MATCH, AMBIGUOUS, VIDEO_METADATA, ALBUM_IDENTIFIED, NO_MOTION, MOTION_AVAILABLE, CACHE_HIT, ANIMATION_DISABLED, OFFLINE, BLOCKED_VISUAL_MODE, LOAD_ERROR y FIRST_FRAME. Solo contienen una clave hash, cantidades y un código numérico de error. No incluyen nombres, rutas, URLs, tokens ni mensajes de excepciones. Los eventos del MotionCover compartido identifican el recurso por hash; también pueden aparecer para otras fuentes en debug.

Las relaciones y atributos extendidos usan la integración existente y la [semántica de Apple Music API](https://developer.apple.com/documentation/applemusicapi/handling-resource-representation-and-relationships). No se añadieron proveedores, scraping ni un reproductor visual nuevo. Las reglas remotas de coincidencia permanecen intactas; AppleCatalog conserva su restricción original de red no medida.

## Verificación

### JVM y compilación

Comando requerido ejecutado con éxito:

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
BUILD SUCCESSFUL
307 tests, 0 failures, 0 errors, 0 skipped
```

Se conservaron las 286 pruebas de la fase anterior, actualizando las expectativas del criterio antiguo, y se añadieron 21 casos. Cubren una/dos/tres canciones, homónimos, corroboración y contradicción, prioridad manual y rechazo, revisión persistente, preservación de archivos cacheados, acciones por estado, integración del Info, foco local, viewport/end padding, relaciones incompletas para Motion, álbum/duración ausentes, prioridad de metadatos, recursos compartidos o diferentes, preferencia/offline, videoclip y comportamiento remoto. Las comprobaciones estructurales de UI se complementaron con ejecución instrumentada; no se presentan como pruebas físicas de vídeo.

### Ejecución en Android

Emulador temporal `Medium_Phone_API_37.0`, lanzado con `-read-only -no-snapshot`, APK ARM64 con traducción sobre x86_64. Los fixtures son tonos sintéticos con una portada azul y tags de prueba. Ningún dispositivo físico fue modificado.

El [runner instrumentado](../tools/local-visuals-smoke/README.md), compilado exclusivamente contra el SDK, comprobó:

- Modo convencional: cuadrícula limpia, menú desde Info original y Back conservando el perfil; tanto horizontal como vertical, tres aserciones por orientación.
- Actividad Thor en el display del emulador: cuadrícula, Info, B, D-Pad/A abriendo la entrada de perfil, stick izquierdo activando esa misma acción y B tras usar el stick; seis aserciones por orientación, horizontal y vertical.
- Grid horizontal con mini-reproductor: desplazamiento hasta el último álbum, comprobando sus límites por encima de los controles flotantes.
- Grid horizontal sin mini-reproductor: se vació exclusivamente la cola sintética mediante una conexión temporal a la MediaSession existente, se comprobó que desaparecía el mini y se verificó el último álbum por encima de la barra.

El runner pasó 20 aserciones contando las ejecuciones en ambas orientaciones y los dos casos de scroll. Se cerró el emulador temporal tras guardar la evidencia.

Las capturas del inicio del grid muestran las tarjetas detrás del cristal hasta el borde de la ventana. Las capturas del final muestran los últimos álbumes completos por encima de los controles. El espacio visible al final corresponde a contenido desplazable, no a un recorte fijo del viewport.

### Catálogo y Motion reales

Se probó el recorrido de la aplicación con tags reales sobre tonos sintéticos, utilizando el AppleCatalog existente:

| Muestra | Resultado observado |
| --- | --- |
| Billie Eilish, una sola canción | VERIFIED persistido y foto real disponible; no había logotipo en esa respuesta. No se necesitó una segunda canción. |
| HIT ME HARD AND SOFT | Álbum identificado; esa respuesta no ofreció Motion, conservando la portada estática. |
| Midnights | Cuatro ediciones plausibles con recursos distintos; AMBIGUOUS y portada estática. |
| eternal sunshine | Dos ediciones con recursos distintos; AMBIGUOUS y portada estática. |
| Future Nostalgia | Un álbum identificado, Motion 1:1 disponible, persistido y entregado al MotionCover existente. La descarga alcanzó MediaCodec. |

En el último caso el emulador falló en `c2.goldfish.h264.decoder` con vídeo AVC 456×456, aunque Media3 indicaba `format_supported=YES`. El error fue del decodificador del emulador, no una ausencia de metadatos ni de Motion. El fallback estático permaneció visible y la sesión de audio siguió en PLAYING con posición avanzando. Las letras siguieron disponibles. **No se obtuvo un primer fotograma válido de ese Motion en el emulador y no se afirma que haya quedado validado físicamente en AYN Thor.** No se alteró el motor visual para compensar el decodificador del entorno de prueba.

La auditoría se completó mediante las solicitudes normales de la app y sus [logs seguros capturados](validation/LOCAL-MOTION-TRACE.txt). No se extrajeron ni reutilizaron credenciales con herramientas externas.

### APK

- Ruta: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
- Tamaño: 53 321 570 bytes.
- SHA-256: `20c151223ba7118b504614a98b7b2e962376e7525746809ce8e86bb9c9a19d59`.
- Paquete `dev.lelonio.square`, código 58, versión `2.6.1-squareds.1`, ABI única `arm64-v8a`, compileSdk 37, targetSdk 35, minSdk 26 conservados.
- Firma verificada, SHA-256 del certificado: `25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c`, igual que el APK anterior.
- Sin cambios en applicationId, versiones, SDK/NDK, Rust, Gradle, dependencias, configuración de firma ni actualizador. MediaStore, SAF, selección de carpetas, historial, LocalPlayerFactory, PlaybackService, PlayerScreen, créditos, EQ y efectos conservan exactamente el contenido de la instantánea anterior a esta intervención.

## Pendiente en AYN Thor y límites

- Verificar vídeo real 1:1 y alternativa vertical, primer fotograma, transiciones y proporciones en el decodificador del dispositivo; letras simultáneas y exclusión con videoclip.
- Validar ambas pantallas, resolución y densidad reales, foco visible en tarjetas de todas las rutas, barra plegada/expandida y primeros toques tras usar el mando. La actividad Thor instrumentada en un solo display no sustituye esa prueba física.
- Comprobar en la colección real tags incompletos, compilaciones, homónimos, estados de revisión, asociación manual y caché offline; las fotos/logos editoriales dependen de lo que Apple publique y del storefront/red disponibles.
- El límite de tres canciones por artista y cuatro álbumes plausibles por Motion evita consultas masivas; metadatos insuficientes o más ediciones mantienen el fallback. La asociación manual conserva sus proveedores previos Spotify/YouTube.
- Las pruebas de ambigüedad, Motion compartido, preferencia desactivada, offline y exclusión de videoclip incluyen casos JVM; no todos esos cruces se reprodujeron físicamente en Android durante esta ejecución. El plegado y expansión originales se preservaron por código y se observó la barra plegada en horizontal y expandida en vertical, sin simular firmware dual-screen.
- No hay vídeo Motion descargado para offline ni una nueva cola mixta. Se conservan los límites de fuentes, SAF y servicios descritos en los informes anteriores.

## Evidencia visual

- [Menú convencional horizontal](screenshots/local-corrections/menu-conventional-landscape.png).
- [Menú convencional vertical](screenshots/local-corrections/menu-conventional-portrait.png).
- [Menú Thor horizontal](screenshots/local-corrections/menu-thor-landscape.png).
- [Menú Thor vertical](screenshots/local-corrections/menu-thor-portrait.png).
- [Contenido detrás de los controles](screenshots/local-corrections/grid-start-landscape.png).
- [Última tarjeta con mini-reproductor](screenshots/local-corrections/grid-end-landscape.png).
- [Inicio sin mini-reproductor](screenshots/local-corrections/grid-start-no-mini.png).
- [Última tarjeta sin mini-reproductor](screenshots/local-corrections/grid-end-no-mini.png).
