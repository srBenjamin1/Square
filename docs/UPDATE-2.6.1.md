# SquareDS integración de Square 2.6.1

SquareDS incorpora las correcciones de Square 2.6.1 y consulta exclusivamente las releases del fork. La entrega es `2.6.1-squareds.1`, build Android `58`, para validación física en AYN Thor. No se han creado commits ni publicado cambios.

## Auditoría y protección

- HEAD inicial: `dff51735073b56cc4fc6c12b1f6bf1da3c1d50d5`, etiqueta `thor-v1.1-spatial-navigation`.
- Referencia funcional: `thor-v1-controls`, commit `155304e5a9c3c69969368a54a89a050df921e8ba`, antecesor de HEAD. Se conservan las modificaciones posteriores de README, capturas y mando.
- Respaldo existente verificado: `backup/pre-square-2.6.1`, en HEAD inicial.
- Rama de trabajo existente: `update/upstream-2.6.1`, inicialmente en el mismo commit que `master`.
- `origin/master` apunta a `d17a72c`, un commit adicional de README. No se ha sobrescrito ni incorporado ese cambio remoto a ciegas.
- `origin` está configurado como `https://github.com/srBenjamin1/Square.git`, el nombre anterior del fork. Se conserva la configuración local; al publicar, comprobar que el destino efectivo es SquareDS.
- `upstream`: `https://github.com/Lelonio/Square.git`. Su HEAD y `v2.6.1` apuntan a `17ed2b87974cf6fec9c531ef3f1438448bb2b729`.
- Tag upstream `v2.6.0`: `dfe171d8c23f040d8261b5bcfb9b0a7ef12f7822`.
- `git merge-base HEAD v2.6.1` no encuentra base común. Se compararon `v2.6.0..v2.6.1` y se aplicaron parches de archivos concretos, comprobados antes con `git apply --check`; no hubo merge de historias.
- El estado inicial no tenía cambios en archivos versionados. `.directory` ya existía sin seguimiento y se conserva.

## Cambios integrados

La [release oficial de Square 2.6.1](https://github.com/Lelonio/Square/releases/tag/v2.6.1) corresponde a los commits revisados localmente.

| Cambio | Procedencia | Integración en SquareDS |
| --- | --- | --- |
| Ir al álbum | `067fe5d` | Menú junto a Ir al artista; Spotify puede resolver el álbum si falta el URI y la Web API está configurada, YouTube Music lo ofrece cuando hay URI. Las peticiones desde el reproductor superior pasan al navegador inferior mediante `ThorAlbumNavigation`, sin cambiar de display ni contraer el reproductor superior. También se adapta el enlace de álbum ya existente en el título del reproductor. |
| Portadas nítidas y completas | `5c8fe1d` | Rendiciones grandes de Spotify y mosaicos; fallback al URL original si falla la imagen grande. Offline se usa la imagen disponible. Los encabezados muestran la portada cuadrada completa, con la transición y desenfoque Liquid Glass existentes. Las fotografías específicas de encabezado conservan su proporción. |
| Playlists de YouTube Music offline | `629c6db` | Se consultan las descargas antes de pedir la biblioteca online, y también ante fallo de red. Se conservan las estanterías de archivos locales y canciones descargadas, etiquetas y portadas. |
| Dirección de letras | `b4d0917` | La dirección depende de las letras de la canción, con fallback al idioma del dispositivo para contenido neutro. Se aplica a las vistas clásica y sincronizada; no se trasladan las letras a otra pantalla ni se cambian traducción o tiempos. |
| Metadatos recién añadidos | `0544f35` | Se completan las entradas incompletas por el catálogo, conservando orden, duplicados y fecha de incorporación. Las cachés incompletas no reciben snapshot válido. Se reutiliza la publicación reactiva existente; la adición en lote también refresca la playlist abierta. |
| Login después de actualizar | `91a446e` | Kotlin rechaza credenciales vacías o JSON ilegible. Rust escribe a un temporal, hace `sync_all` y renombra sobre el archivo anterior. No se cambian OAuth ni los backends de Spotify/YouTube. |
| Velocidad y tono | `01c17d9` | Parámetros pendientes coherentes entre controles y callbacks actualizados del slider. Reset limpia el valor arrastrado; se conserva `thorFocus()`. |

No se sustituyeron archivos completos de la adaptación por sus versiones upstream. `MainActivity`, `ThorActivity`, gestión de displays, `PlaybackService`, navegación por mando, README y capturas existentes permanecen sin cambios.

No se incorporaron el número de versión upstream `2.6.1`, su changelog Fastlane como entrega propia, ni los cambios de reintentos/caché de clonación de Bungee: no resuelven un fallo observado en esta compilación. El motor mantiene sus versiones y parches existentes; únicamente se integra la escritura atómica de credenciales solicitada. No se modifican SDK, NDK, Rust ni Gradle del proyecto. En CI, el instalador de NDK se alinea con el `30.0.16248370` que el proyecto ya exigía; antes instalaba `28.2.13676358`, por lo que una máquina nueva fallaría en la comprobación de NDK.

## Diagnóstico y nuevo actualizador

El actualizador original construía `https://api.github.com/repos/Lelonio/Square/releases/latest`, aceptaba APKs de sus assets y comparaba partes numéricas tomando sufijos inválidos como cero. Si faltaba el ABI y tampoco había universal, elegía el primer APK disponible. Con ello podía ofrecer una compilación upstream a un dispositivo Thor.

`UpdatePrompt` ya consultaba automáticamente cada seis horas, además de la opción manual de Ajustes; el comentario antiguo de `Updater` que decía que solo había comprobación manual no describía el comportamiento real. Se conserva esta frecuencia, las notas, la notificación, el servicio de descarga y los permisos del instalador.

Origen único:

- API: `https://api.github.com/repos/srBenjamin1/SquareDS/releases/latest`.
- Enlace en Ajustes: `https://github.com/srBenjamin1/SquareDS/releases`.
- Descargas: únicamente `https://github.com/srBenjamin1/SquareDS/releases/download/<tag>/<asset>`.

Los enlaces a upstream en créditos, documentación, ayuda y metadatos Fastlane no configuran el actualizador y se conservan. Ningún archivo de producción en `update/` contiene un origen de actualización upstream.

La respuesta real de la API se comprobó el 23 de octubre de 2026: la última release estable seguía siendo `thor-v1.1-spatial-navigation`, con `Square-Dual-Screen-v1.1-arm64-v8a.apk`. Los dos tags históricos publicados (`thor-v1-controls` y `thor-v1.1-spatial-navigation`) se reconocen como entregas antiguas y no se vuelven a ofrecer.

### Versiones y assets

- `versionName`: `2.6.1-squareds.1`; base Square `2.6.1`, revisión del fork `1` dentro de esa base.
- `versionCode`: `58`, superior a `57` de los APK anteriores de SquareDS.
- Tag: `v2.6.1-squareds.1`; debe coincidir con el `versionName` del APK, con el prefijo `v` en el tag.
- Comparación numérica de la tupla `(major, minor, patch, revisión SquareDS)`, sin ordenar cadenas. Por ejemplo, revisión `10` supera a `9`, y base `2.10.0` supera a `2.9.0`.
- Se admite una versión instalada antigua como `2.6.0` para la transición. No se admite una versión candidata upstream como `v2.6.1`.
- Canal estable: se ignoran drafts y prereleases. El sufijo `-squareds.N` es una convención propia, no un canal prerelease; publicar con `prerelease=false`.
- No se admiten sufijos adicionales `rc`, versiones malformadas, revisiones cero ni números que desborden el comparador. Un dato inválido se comunica como fallo recuperable.
- Assets: `squareds-2.6.1-squareds.1-arm64-v8a.apk`, `squareds-2.6.1-squareds.1-armeabi-v7a.apk` o `squareds-2.6.1-squareds.1-universal.apk`.
- Se elige el primer ABI compatible de `Build.SUPPORTED_ABIS`, después universal. Nunca se usa un split incompatible como fallback.
- Se exige URL del fork, tag y nombre correspondientes, tamaño positivo y descarga completa. Los errores HTTP, JSON y la falta de un APK compatible pasan a `State.Failed`, sin abrir el instalador.
- Antes de instalar se comprueban `applicationId`, versión anunciada, `versionCode` creciente, versión SquareDS superior y certificado idéntico al instalado. Android mantiene la verificación criptográfica final y la confirmación de instalación. No se implementa rotación de certificados en esta entrega.

## Firma y primera instalación

El APK local anterior tiene SHA-256 `b524385ea22ac269e1224dd4dec6bba1be46af0b664fa6dd983ee65e2d6dc264`, exactamente el hash publicado en la [release Thor v1.1](https://github.com/srBenjamin1/SquareDS/releases/tag/thor-v1.1-spatial-navigation). Su certificado SHA-256 es:

```text
25dc740e92ccfb79abe59ff5b2ac4a7853037d9237ab65b74ab924984e04bb3c
```

Es un certificado Android Debug local. Los builds `release`/`dev` usan `keystore.properties` si existe y, en su ausencia, la configuración debug. La compilación `debug` solicitada utiliza la clave debug local. La compatibilidad solo se puede afirmar para APKs con el certificado comprobado; no se ha leído el APK actualmente instalado en el dispositivo ni comprobado el certificado de la primera release Thor V1.

El workflow tenía fijado el certificado upstream `9f3469...27cf0c`; ahora exige la variable de repositorio `SQUAREDS_SIGNING_CERT_SHA256`, valida todos los splits y no publica si falta o no coincide. Los secretos de firma siguen siendo obligatorios. Usar la misma clave del APK ya instalado es necesario para una actualización in-place; generar una nueva clave no conserva esa compatibilidad.

La primera instalación de esta integración debe ser manual: los binarios anteriores siguen consultando upstream y no pueden descubrir este nuevo origen por sí solos. No desinstalar para sortear un error de firma: eso elimina los datos de la aplicación.

```bash
adb install -r /home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Si Android devuelve `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, detenerse y comparar certificados del APK instalado y del nuevo. No se deshabilita ninguna verificación.

## Validación automatizada y APK

Se ejecutan la compilación solicitada y la suite JVM completa:

```bash
./gradlew :app:assembleDebug --no-configuration-cache
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache
```

Los tests nuevos cubren origen exclusivo, rechazo de assets upstream, selección ARM64 y universal, ausencia de split compatible, versiones numéricas, transición desde `2.6.0`, versiones instaladas/antiguas, tags inválidos, tags Thor históricos, drafts, prereleases, HTTP 403/404/429/500 y JSON inválido. También prueban resolución de álbum Spotify/YouTube, consumo de navegación inferior, URLs de portadas, conservación de playlists offline y estanterías locales, dirección de letras y recuperación de metadatos y credenciales vacías/corruptas.

Estas pruebas comprueban lógica JVM y la compilación de su conexión con la interfaz. Los fallos HTTP usan respuestas simuladas; las pruebas no equivalen a una sesión autenticada contra Spotify/YouTube, una descarga e instalación real de la próxima release ni una prueba de renderizado de Compose en dos pantallas. El código Rust modificado se compila para ambos ABIs; no se simula la muerte del proceso en un dispositivo.

Resultado: ambas ejecuciones terminaron con `BUILD SUCCESSFUL` (4 min 16 s la primera compilación; 31 s la validación final). Suite completa: **99 tests, 0 fallos, 0 errores, 0 omitidos**; 27 tests nuevos y los 72 existentes, incluidas las 23 pruebas de entrada y foco Thor.

`apksigner verify --verbose --print-certs` verifica la firma v2 del APK final y confirma el mismo certificado `25dc740e...04bb3c` del APK Thor v1.1 comprobado. `aapt dump badging` confirma `dev.lelonio.square`, código `58`, nombre `2.6.1-squareds.1` y minSdk `26`. La inspección ZIP confirma que todas sus bibliotecas nativas son `arm64-v8a`, incluida `libsquarecore.so`.

SHA-256 del APK ARM64 final:

```text
2544d09848472f5737d043913556e7aa0a296eb3e9184ecca9a122b9559597e2
```

También pasan `git diff --check`, la comprobación de sintaxis Bash de los seis scripts de los workflows revisados y la ejecución del guard de publicación: acepta el tag SquareDS correcto y rechaza un tag upstream o un repositorio distinto. El workflow completo de GitHub Actions y la compilación release minificada no se han ejecutado; el APK entregado es debug.

Se conservan las advertencias existentes de AGP sobre compileSdk 37, APIs deprecadas y documentación Rust; no se cambiaron herramientas para silenciarlas. Los informes JVM están en `app/build/reports/tests/testDebugUnitTest/index.html`.

APK ARM64: `/home/srbenjamin/Documentos/Squareaynthor/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.

## Validación física pendiente en AYN Thor

No se han realizado pruebas físicas durante esta integración.

1. Instalar con `adb install -r` y confirmar que se conservan cuentas, biblioteca y configuración.
2. Activar modo Thor; comprobar ambas pantallas, reproductor superior fijo y navegación inferior independiente.
3. Abrir Ir al álbum desde canciones Spotify y YouTube Music con álbum disponible; probar también el título del reproductor superior. El álbum debe abrir abajo y la música continuar arriba.
4. Revisar portadas Spotify editoriales, propias, mosaicos y álbumes: nitidez, proporción, bordes completos y transición Liquid Glass. Repetir sin conexión y con una imagen grande no disponible.
5. Reproducir letras inglesas con el sistema en árabe/hebreo, y letras árabes/hebreas con el sistema en español. Comprobar ambos estilos, sincronización, resaltado, traducción y controles; las letras permanecen arriba.
6. Añadir una canción y un lote a una playlist abierta. Confirmar artista, duración y portada sin cerrar ni reiniciar la pantalla; volver a abrir para comprobar caché.
7. Descargar una playlist de YouTube Music, activar modo avión y volver a abrir la aplicación. Comprobar playlist, archivos locales y canciones descargadas, y reproducir desde ellos.
8. Comprobar login de Spotify y YouTube Music después de actualizar; probar cierre y reapertura conservando sesión.
9. Probar foco, D-pad, stick, botones, cola, ecualizador, efectos, velocidad/tono y reset sin regresiones de audio.
10. Ajustes > Acerca de: comprobar únicamente SquareDS. Mientras la última release sea la histórica, debe indicar que no hay actualización.
11. Con una release estable posterior y firmada con la misma clave, comprobar detección, notas, descarga ARM64 y permiso/confirmación Android. La versión instalada o una anterior no debe ofrecerse; tampoco `v2.6.1` upstream.
12. Probar modo avión, error de servidor y release sin APK compatible: mensaje recuperable, sin crash ni instalación equivocada. Revisar logs sin exportar tokens ni credenciales.

## Publicar la siguiente release de SquareDS

1. Validar físicamente este APK y revisar el diff antes de crear un commit. Esta tarea no hace commits, push ni publicación.
2. Para publicar esta integración usar `versionName=2.6.1-squareds.1`, `versionCode=58`, tag `v2.6.1-squareds.1`. Para la siguiente corrección, por ejemplo, `2.6.1-squareds.2` y código `59`. El código debe crecer incluso si cambia la versión base.
3. Comprobar que el remoto y GitHub Actions corresponden a `srBenjamin1/SquareDS`; el workflow rechaza otro repositorio. Conservar las releases antiguas.
4. Configurar `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` y la variable pública `SQUAREDS_SIGNING_CERT_SHA256`. Para mantener compatibilidad con Thor v1.1, importar la clave correspondiente al certificado indicado arriba; no imprimir ni añadir el keystore o contraseñas al repositorio.
5. Se puede ejecutar el workflow manualmente primero: genera artefactos firmados sin publicar una release. Después, con autorización del usuario, crear y subir el tag exacto en la revisión validada. El workflow construye los APK release y crea un borrador en SquareDS.
6. Revisar certificado y contenido de cada APK. El workflow usa los nombres `squareds-<versionName>-<abi>.apk`; conservarlos exactamente. Para una publicación manual del debug validado, copiarlo con ese nombre y verificar de nuevo firma y SHA-256; no adjuntar un APK upstream.
7. Escribir notas propias de SquareDS que indiquen base Square, revisión del fork, código Android, compatibilidad Thor, resultados físicos y hash del APK. Publicar como estable, sin draft ni prerelease, y marcarla como Latest para `/releases/latest`.
8. Comprobar la API pública y probar desde un SquareDS anterior que ya incorpore este nuevo actualizador. La instalación desde los APK anteriores a esta integración sigue siendo manual.

## Archivos modificados

41 archivos modificados o nuevos; `.directory` no forma parte de la entrega.

- `.github/actions/setup/action.yml`
- `.github/workflows/release.yml`
- `app/build.gradle.kts`
- `app/src/main/java/dev/lelonio/square/auth/EngineCredentials.kt`
- `app/src/main/java/dev/lelonio/square/data/OfflineLibrary.kt`
- `app/src/main/java/dev/lelonio/square/data/PlaylistMetadata.kt`
- `app/src/main/java/dev/lelonio/square/thor/ui/ThorAlbumNavigation.kt`
- `app/src/main/java/dev/lelonio/square/ui/MainViewModel.kt`
- `app/src/main/java/dev/lelonio/square/ui/SquareApp.kt`
- `app/src/main/java/dev/lelonio/square/ui/TrackAlbum.kt`
- `app/src/main/java/dev/lelonio/square/ui/components/Artwork.kt`
- `app/src/main/java/dev/lelonio/square/ui/components/HeroBackdrop.kt`
- `app/src/main/java/dev/lelonio/square/ui/glass/LiquidSlider.kt`
- `app/src/main/java/dev/lelonio/square/ui/library/PlaylistScreen.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/LyricsView.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/PlayerPanel.kt`
- `app/src/main/java/dev/lelonio/square/ui/player/PlayerScreen.kt`
- `app/src/main/java/dev/lelonio/square/ui/settings/SettingsScreen.kt`
- `app/src/main/java/dev/lelonio/square/update/ReleasePolicy.kt`
- `app/src/main/java/dev/lelonio/square/update/Updater.kt`
- `app/src/main/res/values-de/strings.xml`
- `app/src/main/res/values-es/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-hi/strings.xml`
- `app/src/main/res/values-it/strings.xml`
- `app/src/main/res/values-ja/strings.xml`
- `app/src/main/res/values-ko/strings.xml`
- `app/src/main/res/values-pt/strings.xml`
- `app/src/main/res/values-ru/strings.xml`
- `app/src/main/res/values-tr/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/dev/lelonio/square/auth/EngineCredentialsTest.kt`
- `app/src/test/java/dev/lelonio/square/data/OfflineLibraryTest.kt`
- `app/src/test/java/dev/lelonio/square/data/PlaylistMetadataTest.kt`
- `app/src/test/java/dev/lelonio/square/ui/LyricsDirectionTest.kt`
- `app/src/test/java/dev/lelonio/square/ui/PlaylistArtworkTest.kt`
- `app/src/test/java/dev/lelonio/square/ui/TrackAlbumTest.kt`
- `app/src/test/java/dev/lelonio/square/update/ReleasePolicyTest.kt`
- `docs/UPDATE-2.6.1.md`
- `native/vendor/README.md`
- `native/vendor/librespot-core/src/cache.rs`
