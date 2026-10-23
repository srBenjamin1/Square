# SquareDS Motion Artwork y videoclips en horizontal

La rama `update/upstream-2.6.1` recupera Apple Music Motion Artwork en la portada horizontal y permite iniciar y cerrar videoclips desde el reproductor superior. La compilación debug y las 111 pruebas JVM pasan. La reproducción y el mando en AYN Thor requieren validación física.

## Causas identificadas

### Motion Artwork

`coverFade` excluye landscape porque controla la imagen ambiental de `HeroBackdrop`. El reproductor horizontal utiliza otra composición: `SongCard` dibujaba exclusivamente `Artwork`, una imagen estática. Por eso retirar `!sideways` habría mostrado una animación ambiental detrás de la tarjeta, sin resolver el contenedor correcto.

Además, `AppleCatalog.motion()` seleccionaba primero `motionDetailTall` o `motionTallVideo3x4` y retenía un único `motionUrl`; si Apple ofrecía también `motionDetailSquare`, se perdía esa alternativa. La selección guardada de Spotify para portrait también excluía Apple en el punto donde se pasaba el recurso al reproductor.

### Acceso al videoclip

El botón de Spotify estaba en `controlsBlock()`, oculto explícitamente con `!sideways`. Ese bloque no se compone en la distribución horizontal, que usa `SongCard` y la cabecera `TopBar`. La cabecera ya tenía el botón de YouTube Music, pero no recibía la acción de Spotify.

Había dos problemas adicionales: el retorno anticipado a `FullScreenVideo` se aplicaba también a la instancia de `SquareApp` del panel inferior; y esa rama quedaba fuera de los proveedores y escenas de foco Thor. Los eventos recibidos por la actividad superior se reenviaban al dispatcher inferior, por lo que añadir únicamente un icono enfocable no resolvía el acceso con mando.

## Motion Artwork

Se conserva `motionUrl` para portrait y se añade `motionSquareUrl`, con valor predeterminado nulo para poder leer metadatos antiguos. La caché online pasa de `album2` a `album3` para volver a consultar respuestas que habían descartado la variante cuadrada. No cambian las preferencias del usuario.

La comprobación del 23 de octubre de 2026 de la [página pública de GUTS en Apple Music](https://music.apple.com/us/album/guts/1694386825) y sus manifiestos HLS encontró recursos diferentes: preview cuadrado de 3840 × 3840, con variantes HLS desde 360 × 360 hasta 2160 × 2160; y preview vertical de 2048 × 2732, con variantes cercanas a 3:4. Esta comprobación valida un ejemplo real, no la disponibilidad de animación para todo el catálogo.

En landscape, `PlayerVisualPolicy` elige Apple independientemente de la fuente guardada para portrait, siempre que las portadas animadas estén activadas. Se prioriza `motionDetailSquare`; si no existe, la variante vertical existente se ajusta completa dentro del mismo contenedor mediante `RESIZE_MODE_FIT`. Las proporciones no cambian y los márgenes dejan ver la portada estática. Si no hay recurso o falla su reproducción, queda la imagen estática.

`SongCard` reutiliza `MotionCover`, que ya usaba ExoPlayer silencioso, sin foco de audio y con liberación ligada a la composición. La animación sigue la pausa de la canción y el lifecycle de la ventana. Cambiar de canción sustituye el recurso. No se toca la colocación de letras, sincronización, traducción ni los controles musicales de la tarjeta.

`coverFade` conserva `!sideways`. Spotify Canvas no se consulta ni se compone en landscape. Portrait mantiene la selección original entre Canvas y Apple. Al entrar en videoclip se retira Motion Artwork; al volver se compone otra vez si procede. La instancia inferior no recibe superficies de portada animada ni de vídeo.

## Cambio entre canción y videoclip

Se reutiliza el botón original de `TopBar`, con su material Liquid Glass y los iconos existentes. En horizontal recibe la acción de Spotify cuando hay `videoFileId` o ya se está viendo un vídeo; si no, conserva la acción original de YouTube Music. Una canción Spotify sin videoclip no obtiene un botón de vídeo funcional. El botón portrait de Spotify conserva su ubicación original.

Spotify expone el estado de carga y fallo de la solicitud que ya procesaba el servicio. `PlaybackService` solamente notifica el resultado de esa solicitud; no cambia la obtención del manifest, DRM, el intercambio del reproductor ni la restauración de la cola. Durante la solicitud se muestra progreso y se evita repetirla. Los fallos de solicitud y de reproducción se muestran sin desplazar las letras o cambiar las dimensiones de la portada.

`FullScreenVideo` se usa en el reproductor superior o en el modo convencional, y deja fuera al panel inferior. También se evita que el panel inferior entre en la rama PiP o adjunte una segunda superficie de vídeo. No se inicia otra actividad ni otra sesión musical.

La acción visible de notas musicales vuelve a la canción. En Thor no se ofrece el botón que fuerza orientación portrait; el modo convencional lo conserva. `BackHandler` y B cuando el evento llega a la ventana superior ejecutan la vuelta a canción. Si el foco está en la ventana inferior, B conserva su navegación contextual inferior. Los controles de vídeo permanecen visibles mientras se utiliza el mando.

### Mando

`UpperPlayerControls` registra un destino temporal en `ThorInputRouter` solamente para la ventana superior horizontal que tiene una acción de vídeo. Se reutilizan `ThorInputDispatcher`, `SpatialNavigation`, `ThorFocusScene` y `thorFocus`; no se cambia el mapa de botones ni se crea otro dispatcher. Los eventos recibidos directamente por `ThorActivity` siguen dirigidos a su interfaz inferior. L1/R1, L2/R2, START, SELECT y las órdenes musicales continúan pasando por el mecanismo existente.

La escena nueva de foco se limita al reproductor superior; no se registra una escena invisible en el panel inferior al cerrar sus herramientas. El contorno usa la modalidad global existente y se oculta al volver al tacto. Su comportamiento real entre ventanas necesita comprobarse en el firmware de Thor.

## Servicios y conservación del estado

- **Spotify:** se conserva la detección original de `videoFileId`. `watch(fileId, posición)` entrega la posición al servicio; este aparca el reproductor musical y usa el vídeo existente. `listen(posición)` restaura el reproductor y la cola y busca la posición alcanzada. Se mantienen las limitaciones de disponibilidad, cuenta, DRM y conectividad originales.
- **YouTube Music:** se reutiliza `YouTubeVideoMode.toggle(player)` y el resolver original. Conserva índice, posición e intención de reproducción al parar/preparar la fuente. Si el videoclip oficial no se puede usar, sigue el comportamiento original de usar el vídeo de la canción y mostrar su aviso; no se incorpora otra búsqueda.
- **Archivos locales:** no se añade búsqueda de videoclips ni se cambia su reproducción. Pueden mostrar artwork Apple si el catálogo original lo encuentra por sus metadatos.
- **Motion Artwork:** es decorativo y silencioso; no sustituye al videoclip ni al reproductor musical de la sesión.

## Validación realizada

```bash
./gradlew :app:assembleDebug --no-configuration-cache
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

La compilación solicitada terminó con `BUILD SUCCESSFUL` en 54 segundos. La ejecución final de tests y empaquetado terminó con `BUILD SUCCESSFUL` en 16 segundos: **111 tests, 0 fallos, 0 errores, 0 omitidos**.

Las 12 pruebas nuevas cubren selección cuadrada de Apple, conservación de la fuente portrait, fallback vertical/estático, desactivación y visibilidad, exclusión de Canvas y Motion durante videoclip, exclusión del navegador inferior de pantalla completa, proporciones y recursos Apple inválidos, lectura de metadatos antiguos y solicitudes Spotify de entrada/salida con sus posiciones y estados de carga/error. Las 99 pruebas existentes, incluidas las de entrada y foco Thor, siguen pasando.

También pasan `git diff --check`, la verificación del certificado con `apksigner` y la inspección de ABI del ZIP. El APK contiene solamente `arm64-v8a`, incluida `libsquarecore.so`. Se conserva `dev.lelonio.square`, `versionName=2.6.1-squareds.1` y `versionCode=58`; no se modifica la versión ni el actualizador en esta tarea.

Certificado SHA-256:

```text
25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c
```

SHA-256 del nuevo APK ARM64:

```text
9e87821bd01331c5fa8c6379fa1c727d10a2a36c5f3a7d12954b796dc6bcd784
```

APK: `/home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.

No se han realizado pruebas físicas, reproducción autenticada contra los servicios, medición de decodificadores activos ni pruebas de renderizado/instrumentación en dispositivo. Las pruebas JVM verifican decisiones y solicitudes; la compilación verifica su conexión con los componentes de Compose. No equivalen a validar vídeo, audio, foco ni restauración de cola en Thor.

## Validación física pendiente

Instalar la compilación debug sobre una instalación con el mismo certificado:

```bash
adb install -r /home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

1. En landscape, probar un álbum con Motion Artwork cuadrado, uno con solo recurso vertical y uno sin animación. Confirmar proporción, contención, fallback y ausencia de Canvas.
2. Cambiar de canción, pausar/reanudar, salir/volver a la aplicación y desactivar/reactivar las portadas animadas.
3. En portrait, probar la selección Spotify Canvas y Apple Music y comprobar que la preferencia guardada no cambia al girar.
4. Probar Spotify con y sin `videoFileId`, YouTube Music con videoclip oficial y con fallback, y archivos locales. Confirmar ocultación cuando no hay acción.
5. Activar vídeo desde el botón superior mediante tacto, D-pad, stick izquierdo y A. Comprobar contorno Liquid Glass y su desaparición con tacto.
6. Comprobar carga, error de red/manifest y error de reproducción. Verificar que la acción de volver sigue siendo accesible.
7. En vídeo horizontal, probar reproducir, pausar, buscar posición, anterior y siguiente; volver con notas musicales y con B desde la ventana superior.
8. Comparar cola y posición antes y después. Confirmar que reaparecen las letras, portada/Motion Artwork y controles sin reinicio innecesario.
9. Confirmar que Motion Artwork y Canvas no reproducen vídeo a la vez que el videoclip, mediante observación y perfilado en dispositivo si hace falta.
10. Mientras se ve el vídeo arriba, usar abajo Inicio, Novedades, Radio, Biblioteca, Buscar, mini-reproductor, cola, ecualizador, efectos, información y ajustes. No deben desaparecer ni reiniciarse.
11. Probar B con el foco abajo, cambio entre ventanas, L1/R1, L2/R2, START y SELECT. Sus funciones inferiores deben conservarse.
12. Comprobar reproducción Spotify, YouTube Music y local en el modo convencional, ambas orientaciones, ausencia de crashes y continuidad de audio/vídeo.

## Limitaciones

La disponibilidad de Motion Artwork depende del catálogo, storefront, coincidencia de metadatos y condiciones de red ya usadas por Square. Las descargas antiguas pueden conservar solo la variante vertical; se ajusta dentro de la tarjeta, sin sustituir automáticamente sus metadatos descargados. No se cachea un vídeo nuevo ni se crean proveedores.

La lectura de dimensiones del preview valida la variante cuadrada declarada; el ajuste real de proporción corresponde al reproductor de vídeo existente. Que la compilación acepte un HLS no garantiza que el dispositivo pueda decodificarlo. Ante fallo, se conserva la portada estática.

La conservación efectiva de cola, posición, autenticación, sincronía y foco entre displays requiere las pruebas físicas anteriores. No se han modificado `MainActivity`, `ThorActivity`, preferencias, letras, sistema de actualizaciones, SDK, NDK, Rust ni Gradle. No se han creado commits, hecho push ni publicado releases.

## Archivos modificados

- `app/src/main/java/dev/lelonio/square/backend/spotify/SpotifyVideoMode.kt`
- `app/src/main/java/dev/lelonio/square/data/AppleCatalog.kt`
- `app/src/main/java/dev/lelonio/square/playback/PlaybackService.kt`
- `app/src/main/java/dev/lelonio/square/thor/input/ThorInputRouter.kt`
- `app/src/main/java/dev/lelonio/square/thor/ui/UpperPlayerControls.kt`
- `app/src/main/java/dev/lelonio/square/ui/SquareApp.kt`
- `app/src/main/java/dev/lelonio/square/ui/components/MotionCover.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/FullScreenVideo.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/PlayerScreen.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/PlayerVisualPolicy.kt`
- `app/src/main/res/values-es/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/dev/lelonio/square/backend/spotify/SpotifyVideoModeTest.kt`
- `app/src/test/java/dev/lelonio/square/data/AppleMotionArtworkTest.kt`
- `app/src/test/java/dev/lelonio/square/ui/PlayerVisualPolicyTest.kt`
- `docs/LANDSCAPE-MEDIA.md`
