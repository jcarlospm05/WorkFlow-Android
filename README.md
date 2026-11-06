# WorkFlow Android — base nativa 1.0.0-dev

Proyecto nuevo Kotlin + Jetpack Compose + Room/SQLite. Separado del código Flutter v0.2 auditado. La APK de desarrollo se compiló correctamente en GitHub Actions, ejecución 42090575402; quedan pendientes las pruebas en dispositivo y la distribución de producción.

## Qué implementa

- Interfaz clara con logo vectorial único, paleta fija petróleo/turquesa/ámbar y navegación Hoy, Agenda, Operación, Reportes, Más. Respeta insets del sistema, cámara y teclado.
- Personal editable y desactivable, equipos con estado y artículos con unidad/mínimo.
- Asistencia por operador/turno real, tardanza, libres y EXT; el total de presentes cuenta personas distintas.
- Cena/almuerzo con selección independiente de asistencia y una solicitud editable por día/tipo.
- Combustible por equipo y operador opcional; averías con seguimiento; tickets con motivo/estado/descripción; planificación, posiciones TRH/Reefer/Taller y libres.
- Entradas/salidas de existencias con saldo entre jornadas y comprobación transaccional de no negatividad por fecha.
- Daily planner con tareas iniciales y tareas de viernes para fin de semana. Los horarios iniciales son orientativos; las tareas se editan y completan manualmente. No inventa marcaciones ni envía notificaciones.
- Cierre con explicación de pendientes, reapertura para corregir y registro local de cambios.
- JSON diario versionado actualizado con cada guardado. PDF diario multipágina con todas las secciones y nombres.
- Compartir JSON o PDF por WhatsApp/WhatsApp Business, con selector Android como alternativa. El usuario confirma destinatario y envío. No usa APIs privadas ni automatiza la interfaz de WhatsApp.
- Recepción del JSON mediante selector de archivos o Abrir/Compartir desde otra app, vista previa y confirmación. Informes recibidos separados y de solo lectura. Reimportación idempotente; versiones antiguas y conflictos rechazados.
- Bloqueo con credencial/biometría del sistema, pantalla protegida de capturas, archivos privados y FileProvider acotado a cache/shared.

## Cómo usar en dos teléfonos

1. Configura bloqueo de pantalla de Android. Instala la **misma nueva versión** en ambos teléfonos y desbloquea WorkFlow.
2. En Más configura el nombre del responsable y los catálogos. No se cargan nombres reales ni datos de ejemplo automáticamente.
3. Registra asistencia, comida, combustible, etc. Cada guardado conserva una revisión del documento diario en SQLite y actualiza `files/daily/WorkFlow_AAAA-MM-DD.json` con escritura atómica.
4. La opción «Ofrecer compartir tras registrar cambios» está activada al iniciar la sesión; se puede desactivar. El archivo siempre se actualiza aunque se desactive esa oferta. Pulsa Compartir y selecciona JSON o PDF por WhatsApp.
5. En el segundo teléfono abre el JSON con WorkFlow o entra en Reportes → Recibir archivo JSON. Revisa supervisor/origen/fecha/revisión y confirma. Reportes muestra el informe recibido y permite ver todo su contenido y compartirlo de nuevo.
6. Para ver progreso posterior debes enviar e importar la nueva revisión. No hay comunicación en tiempo real ni confirmación automática de recepción. El PDF se abre en cualquier visor PDF.

## Arquitectura

- `core`: modelos serializables, reglas de dominio, protocolo JSON estricto, política de revisiones, resumen común al visor y PDF; pruebas JVM.
- `app/data`: Room, DAO, repositorio con Mutex y transacciones, historial, documentos propios y recibidos separados.
- `app/ui`: tema y logo únicos, ViewModel y pantallas Compose, formularios reutilizables.
- `app/export`: PDF con Canvas de Android y compartición mediante URI temporal de solo lectura.
- `MainActivity`: insets, entrada de archivos content:// y autenticación del sistema.

Room guarda el documento diario en la misma transacción que la operación. La base es la fuente de verdad: si el archivo privado no puede escribirse, se vuelve a materializar al leer/compartir. Si ese paso de archivo falla por almacenamiento, la interfaz indica que los datos sí se guardaron en SQLite y queda pendiente actualizar el archivo; no se debe repetir el alta. Compartir genera el archivo desde la revisión guardada en SQLite. No se borran operaciones físicamente. Una corrección de stock anterior regenera las revisiones ya existentes desde esa fecha.

## Compilar

Requiere JDK 17, Android SDK plataforma 35/build-tools, Gradle **8.9** y acceso a repositorios Google/Maven. Abre la carpeta raíz en Android Studio y configura el SDK. Si utilizas CLI, instala Gradle 8.9 y configura ANDROID_HOME o `sdk.dir` en un `local.properties` local.

```sh
gradle :core:test :app:lintDebug :app:assembleDebug --stacktrace
# En Linux/macOS también:
bash tools/build.sh
```

APK de desarrollo esperada: `app/build/outputs/apk/debug/app-debug.apk`.

El identificador de desarrollo es `com.workflow.operaciones.dev`, para no pisar la app Flutter anterior. Este proyecto **no incluye el wrapper binario de Gradle**; utiliza Gradle 8.9 instalado o la configuración de GitHub Actions incluida. El workflow ejecuta tests, lint y compilación sin ignorar fallos; no ha sido disparado ni publicado desde esta sesión.

Pruebas de repositorio en teléfono/emulador:

```sh
gradle :app:connectedDebugAndroidTest
```

Para release configura fuera del código: WORKFLOW_KEYSTORE_PATH, WORKFLOW_STORE_PASSWORD, WORKFLOW_KEY_ALIAS y WORKFLOW_KEY_PASSWORD. Nunca uses firma debug para distribución. No hay claves incluidas. La salida de release no debe publicarse hasta pasar las pruebas en dispositivo y definir la migración desde la app anterior.

## Estado de verificación en esta entrega

Compilación confirmada en GitHub Actions: https://github.com/jcarlospm05/WorkFlow-Android/actions/runs/42090575402

- Commit del código compilado: decfcfcd9793747b8e2ed6fe4b03af7e89268d55, rama codex/workflow-native-apk, PR #1.
- `:core:test`: 13 pruebas ejecutadas, 0 fallos.
- `:app:lintDebug`: 0 errores, 14 advertencias (dependencias/target recientes y reglas de extracción de datos).
- `:app:assembleDebug`: APK generada, versión 1.0.0-dev-debug, Android 8.0 o posterior, paquete com.workflow.operaciones.dev.
- APK: 18.207.765 bytes. SHA-256: fc7e5dd6aeaf4bbc17e494fddb8197fcad37dce19ad324e73a65453802f364ce.
- Archivo ZIP del artefacto descargado: digest contrastado con el publicado por GitHub. APK revisada por CRC y manifiesto binario; sin permiso INTERNET y con allowBackup=false.
- **No se ejecutaron las cuatro pruebas instrumentadas ni se probó en un teléfono/emulador. No es una versión de producción.**

Las comprobaciones estáticas offline se conservan en docs/offline-checks.json. La compilación fue remota; el entorno local sigue sin Gradle ni SDK Android.

## Límites pendientes antes de versión final

- La revisión visual 1.0.1 incluye Nunito Sans real (pesos 400/500/600, subconjunto latino) y su licencia OFL. Se mantiene el logo y la paleta fijados.
- Probar en Android real: insets, tamaño de letra grande, teclado, autorización temporal de archivos, dos teléfonos y WhatsApp.
- Notificaciones programadas, informes por rangos/XLSX/CSV, voz TTS, gráficos, copias completas cifradas/restauración y migración desde ControlOPS quedan fuera de esta primera base. No son funciones terminadas.
- Las averías registran hora y seguimiento, pero no calculan intervalos exactos ni múltiples paradas. Tickets son registros operativos, sin firma jurídica.
- Los JSON se verifican con SHA-256 para detectar corrupción; **no hay firma digital que autentique al remitente**. Un tercero puede crear un nuevo archivo con checksum válido. El usuario debe verificar el origen por un canal conocido. Las revisiones son orden lógico, no sincronización autenticada.
- SQLite está en sandbox, sin cifrado propio SQLCipher. Los JSON/PDF se comparten sin cifrado de archivo. No se consideran respaldos completos: no contienen todo el historial de otros días ni la auditoría.
- No se ha hecho auditoría de CVE sobre dependencias resueltas. No se generó lockfile fingiendo una resolución que no ocurrió. Generar y versionar lockfile/verificación de dependencias en el primer build validado.
