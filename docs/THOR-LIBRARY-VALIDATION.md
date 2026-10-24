# Biblioteca: validación física en AYN Thor

2026-10-24. Rama `feature/local-music-library`, HEAD `d3b093e`. Sin commits, push ni Release. Se conservaron los cambios pendientes anteriores.

## Resultado y correcciones

Biblioteca abre con Spotify, YouTube Music y Música local en la Thor conectada por ADB, Android 13/API 33, con ambas pantallas encendidas. Se instaló el APK mediante actualización `adb install --no-incremental -r`, sin borrar los datos ni instalar fixtures o runners en el dispositivo físico.

El registro anterior de la Thor confirma `IllegalArgumentException: Navigation destination that matches route library cannot be found`. La causa era una llave mal colocada: Biblioteca quedaba declarada dentro de la composición de Buscar. Se corrigió el registro como destino hermano en `SquareApp.kt`. [Excepción original](validation/THOR-LIBRARY-ORIGINAL.txt), [diagnóstico inicial](LIBRARY-CRASH-FIX.md).

La prueba física descubrió además que cambiar desde una fuente remota a local en la misma actividad devolvía a Inicio al tocar Biblioteca. `LiquidBottomTabs` retenía el callback y rango de cinco pestañas: el índice local de Biblioteca se interpretaba como Radio y la protección local redirigía a Inicio. No era otro cierre. La corrección añade `key(visibleTabs)` alrededor del control y `visibleTabs` a `FloatingTabBar.contentKey`, recreando sus estados recordados al cambiar el conjunto de rutas. No se modificó la implementación compartida de LiquidBottomTabs.

El único archivo de producción tocado por estas dos correcciones es `app/src/main/java/dev/lelonio/square/ui/SquareApp.kt`. Se ampliaron `LibraryNavigationRegistrationTest.kt`, `LibrarySmoke.java`, `run-library.py` y la documentación del runner. Los ajustes auxiliares anteriores en VisualSmoke, manifest y build.sh permiten ejecutar las pruebas sin dependencias nuevas en la aplicación. No se cambiaron versiones, firma, actualizador ni servicios de reproducción para esta reparación.

## Comprobaciones físicas

Pruebas mediante tacto ADB en la pantalla inferior, con el reproductor en la superior:

| Fuente | Datos reales observados | Navegación |
| --- | --- | --- |
| Spotify | 503 elementos en la primera fase; 427 álbumes confirmados también con el APK final | Biblioteca, filtro Álbumes, cuadrícula/lista y detalle de álbum |
| YouTube Music | 8 playlists; filtro Álbumes vacío | Biblioteca, filtros y desplazamiento |
| Música local | 30 canciones, 3 álbumes | Biblioteca, detalle y retorno del álbum, filtros Artistas y Canciones |

Con el APK final se comprobaron Spotify → local y YouTube Music → local sin reiniciar el proceso. Ambos abren Biblioteca local correctamente. [Captura tras Spotify](screenshots/library-thor/v2-local-after-spotify.png), [tras YouTube](screenshots/library-thor/v2-local-after-youtube.png), [Spotify](screenshots/library-thor/v2-spotify-albums.png), [YouTube](screenshots/library-thor/v2-youtube-library.png), [álbum local](screenshots/library-thor/v2-local-album-detail.png).

El PID 5540 permaneció vivo durante toda la matriz final; su buffer de crash no contiene FATAL EXCEPTION. [Buffer final](validation/THOR-LIBRARY-LATEST-CRASH-BUFFER.txt). Se dejó la app en Inicio de Spotify, restaurando la fuente original. No se ordenó reproducir ni se borraron canciones, cuentas o playlists. Se retiró el XML temporal de UiAutomator.

## Pruebas automatizadas

Ejecutado satisfactoriamente:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

312 pruebas JVM, cero fallos/errores/omitidas. Cinco pruebas estructurales cubren registro de destinos y recreación de pestañas; no sustituyen la ejecución de la interfaz.

Seis casos instrumentados en emulador desechable: las tres fuentes con bibliotecas vacías y con contenido. Se comprueban apertura, filtros, navegación y reapertura tras Buscar. Las fuentes remotas usan estados Ready sintéticos, no autenticación real. [Resultados finales](validation/LIBRARY-LATEST-INSTRUMENTED.txt).

Un séptimo caso instrumentado cambia YouTube → local en la misma actividad: [falló antes de la segunda corrección](validation/LIBRARY-SOURCE-SWITCH-RED.txt) y [pasó después](validation/LIBRARY-SOURCE-SWITCH-GREEN.txt). La aserción exige el encabezado real de Biblioteca, evitando confundirlo con la etiqueta de la barra.

Graphify actualizado con `graphify update .`: 10 252 nodos y 27 655 relaciones, 391 comunidades. Se contrastaron sus relaciones con el código. `git diff --check` correcto.

## Hallazgo independiente pendiente

Durante los primeros arranques/restauraciones de la sesión física aparecieron dos cierres distintos, en los procesos 31062 y 31145, con el primer APK corregido. Android rechazó iniciar PlaybackService desde segundo plano: `BackgroundServiceStartNotAllowedException`, envuelta en `RuntimeException: Cannot create an instance of class MainViewModel`.

El stack pasa por `PlaybackService.connect` (PlaybackService.kt:2141) llamado desde el constructor de MainViewModel (MainViewModel.kt:2363). Esa llamada ya existe en HEAD, antes de estas correcciones. [Extracto del registro](validation/THOR-BACKGROUND-START.txt). No se ha establecido un disparador reproducible completo ni se atribuye el fallo al firmware. No se modificó ese flujo por estar fuera de la reparación de Biblioteca.

La matriz final de Biblioteca en primer plano no produjo cierres, pero no acredita que este problema de arranque esté resuelto. Tampoco constituye una certificación completa de mando, Motion Artwork, letras o todos los escenarios de arranque/dual-screen. Las colecciones totalmente vacías se verificaron en emulador; físicamente se verificó el filtro vacío de álbumes de YouTube, conservando las bibliotecas reales. Publicación detenida.

## APK ARM64 instalado

- [app-arm64-v8a-debug.apk](../app/build/outputs/apk/debug/app-arm64-v8a-debug.apk), 53 321 176 bytes.
- SHA-256: `32e6589fbb12fbddeb5bfb59b4db8f286b1a3477cfedf45db8cb26e9e83dde04`.
- El APK extraído de la instalación física coincide con este SHA.
- Versión `2.6.1-squareds.1`, código 58, paquete `dev.lelonio.square`, ARM64, SDK mínimo 26/objetivo 35.
- Certificado SHA-256 sin cambios: `25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c`.

Secuencia de APK: original `20c151…` → primer arreglo `f21c02…` → final `32e658…`. La prueba de cambio de fuente fallaba en `f21c02…` y pasa en el final.
