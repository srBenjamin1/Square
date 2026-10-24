# Biblioteca: cierre general al navegar

> Registro de la primera fase en emulador. La validación física posterior, la segunda corrección de cambio de fuente y el APK vigente están en [Validación en AYN Thor](THOR-LIBRARY-VALIDATION.md). Los recuentos y SHA de este documento corresponden al primer APK.

Fecha: 2026-10-24. Rama: `feature/local-music-library`. Se preservaron los cambios pendientes anteriores. No se crearon commits, push ni Release.

## Excepción y reproducción

No había AYN Thor conectada. Se reprodujo el fallo del APK anterior en un emulador temporal, seleccionando sucesivamente Música local, Spotify y YouTube Music y tocando Biblioteca, sin abrir antes Buscar. Las tres ejecuciones terminaron con la misma excepción:

```text
java.lang.IllegalArgumentException: Navigation destination that matches route library cannot be found in the navigation graph ComposeNavGraph(0x0) startDestination={Destination(0x78d845ec) route=home}
    at androidx.navigation.NavController.navigate(NavController.kt:2447)
    at dev.lelonio.square.ui.SquareAppKt.switchTab(SquareApp.kt:4566)
```

[Stack traces anteriores a la corrección](validation/LIBRARY-CRASH-BEFORE.log). Se capturaron tres cierres en procesos distintos, uno por fuente.

## Causa real y corrección mínima

Al añadir LocalThorObscuredBottom al contenido de Buscar, el cierre de CompositionLocalProvider quedó situado después de la declaración `composable(Routes.LIBRARY)`. Biblioteca pasó a declararse dentro de la lambda de composición de Buscar, en vez de registrarse durante la construcción del NavHost. En el grafo inicial existían los demás destinos, pero faltaba `library`; navegar hasta él arrojaba IllegalArgumentException antes de componer LibraryScreen o LocalMusicScreen. Esto explica el fallo común a las tres fuentes, incluso con colecciones vacías.

La corrección de producción mueve una única llave en `SquareApp.kt`: cierra el proveedor al terminar SearchScreen y deja Biblioteca al mismo nivel que Inicio, Buscar y los demás destinos. No se modifican filtros, índices, datos ni componentes visuales. Se conserva el espacio de revelado del foco local dentro de Buscar.

Se consultaron AGENTS.md, Graphify (`query`, `path`, `explain`) y el código real. El grafo permitió localizar SquareApp, LibraryScreen, LocalMusicScreen, LibraryTitle, LibraryViewStore y MainViewModel. La comprobación del alcance de las lambdas en el código, junto al stack trace, identificó la regresión. Se contrastó el registro con HEAD `d3b093e`, donde Biblioteca ya era un destino hermano de Buscar. No se revirtieron mejoras.

## Archivos

- `app/src/main/java/dev/lelonio/square/ui/SquareApp.kt`: única corrección de producción, un cierre añadido y otro eliminado.
- `app/src/test/java/dev/lelonio/square/data/local/LibraryNavigationRegistrationTest.kt`: cuatro regresiones del registro independiente de destinos.
- `tools/local-visuals-smoke/LibrarySmoke.java`: apertura real de Biblioteca mediante el runner instrumentado existente.
- `tools/local-visuals-smoke/VisualSmoke.java`, `build.sh`, `AndroidManifest.xml`: reutilización de utilidades y registro del runner auxiliar, sin nuevas dependencias ni cambios de Gradle de la app.
- `tools/local-visuals-smoke/run-library.py`: matriz ejecutable con salida validada; el código de salida de `am instrument` no basta para darla por pasada.
- Este informe, evidencias de validación y salidas de Graphify actualizadas.

## Verificación

Las cuatro regresiones JVM fallaron con el código defectuoso y pasaron tras la corrección. Detectan destinos declarados dentro del contenido de otra pantalla e incluyen la ruta Biblioteca en las tres fuentes, independientemente de sus datos. [Resultado anterior](validation/LIBRARY-REGRESSION-RED.txt).

Se ejecutó el comando requerido:

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
BUILD SUCCESSFUL
311 pruebas, 0 fallos, 0 errores, 0 omitidas
```

Matriz instrumentada completada sobre el APK corregido, en un emulador temporal Android API 37, con la interfaz convencional en vertical:

| Fuente | Biblioteca vacía | Con contenido de prueba | Navegación comprobada |
| --- | --- | --- | --- |
| Música local | Pasa; índice vacío | Pasa; FLAC sintético indexado por MediaStore | Filtros, apertura y retorno del álbum, visita a Buscar y reapertura de Biblioteca por Inicio |
| Spotify | Pasa; Ready con cero elementos | Pasa; Ready con playlist sintética | Recuentos, filtros Álbumes/Playlists, grid/list y reapertura tras Buscar |
| YouTube Music | Pasa; Ready con cero elementos | Pasa; Ready con playlist sintética | Recuentos, filtros Álbumes/Playlists, grid/list y reapertura tras Buscar |

Son seis casos con tres comprobaciones completadas por caso; los resultados se validan por marcadores PASS, no solo por el código de salida del proceso. En las fuentes remotas se inyecta el estado Ready de prueba únicamente en memoria mediante el runner auxiliar: no se usan cuentas reales ni se altera producción para simular una sesión. El índice local, su ruta, el NavHost y las pantallas son los reales. El FLAC contiene silencio y metadatos de prueba.

[Resultados instrumentados](validation/LIBRARY-INSTRUMENTED-RESULTS.txt). [Comprobación de logcat posterior](validation/LIBRARY-CRASH-AFTER.txt): sin entradas AndroidRuntime:E ni nuevos FATAL EXCEPTION tras la corrección.

Se verificó también el diff: respecto al inicio de esta intervención, producción solo cambia la posición de esa llave en SquareApp. LibraryScreen, LocalMusicScreen, MainViewModel y las mejoras de artistas, Motion, SAF, playlists, reproducción, letras, cristal y mando conservan su código anterior. No se modificaron versiones, firma, SDK, Librespot, Connect, YouTube, MediaSession ni actualizador.

Graphify se actualizó con `graphify update .` (AST, sin LLM): 10 248 nodos y 27 647 relaciones. El comando informó de cambios en las comunidades y de etiquetas semánticas previas que requieren relabeling para nombres nuevos; la extracción de código terminó correctamente. No se solicitó una nueva clasificación con LLM. Se consultó y contrastó el código real porque el grafo no sustituye el análisis del alcance de las lambdas.

El emulador se cierra tras guardar la evidencia; no había ningún dispositivo físico conectado.

## APK ARM64

- Ruta: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
- Tamaño: 53 321 217 bytes.
- SHA-256: `f21c02892d6fdd2ca969ba95dacf3807e81923685f1cc7631d8c30b667e7183a`.
- Paquete `dev.lelonio.square`, versión `2.6.1-squareds.1`, código 58, ABI `arm64-v8a`, SDK mínimo 26, objetivo 35, compilación 37.
- Firma verificada sin cambios: SHA-256 del certificado `25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c`.

## Capturas

- [Música local vacía](screenshots/library-crash-fix/library-local-empty.png) y [con álbum](screenshots/library-crash-fix/library-local-populated.png).
- [Spotify vacía](screenshots/library-crash-fix/library-spotify-empty.png) y [con playlist de prueba](screenshots/library-crash-fix/library-spotify-populated.png).
- [YouTube Music vacía](screenshots/library-crash-fix/library-youtube_music-empty.png) y [con playlist de prueba](screenshots/library-crash-fix/library-youtube_music-populated.png).


## Validación física pendiente

Instalar el APK ARM64 en AYN Thor y entrar directamente en Biblioteca, sin abrir antes Buscar, con cada fuente. Probar colecciones vacías y las reales, filtros, vistas, detalles y retorno; repetir en ambas pantallas y con tacto/mando. Las pruebas remotas instrumentadas usan estados Ready de prueba; no sustituyen la verificación de las bibliotecas de cuentas reales. No se afirma una prueba física de Thor ni de servicios autenticados.

La regresión demostrada se limita al registro de Biblioteca. No se atribuyen fallos a otras pestañas. Se detiene la publicación hasta completar la validación física solicitada.
