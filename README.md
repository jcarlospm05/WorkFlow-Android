# WorkFlow Android

Aplicación nativa de operación diaria, personal, equipos, existencias y agenda. Identidad visual consistente con logo y paleta WorkFlow y tipografía Nunito Sans.

## Revisión 1.1.0 UX

- Instalación sin registros de negocio ni actividades precargadas. Los datos sintéticos solo pertenecen a las pruebas automatizadas.
- Altas consecutivas sin salir del formulario; código de empleado opcional; catálogos buscables.
- Indicadores del inicio que abren sus módulos y conservación de navegación y borradores al volver de otra aplicación.
- Informes protegidos para consultar en otro WorkFlow y documentos PDF para leer. La interfaz no muestra detalles del formato interno o de la base de datos.
- Transporte con AES-256-GCM y PBKDF2-HMAC-SHA256; clave acordada por el equipo, almacenada con Android Keystore en cada teléfono. SHA-256 por sí solo no cifra datos.

[Protocolo y límites de seguridad](docs/INFORMES_PROTEGIDOS.md). Ambos teléfonos deben actualizarse para los nuevos informes protegidos; los archivos antiguos sin cifrado deben volver a compartirse.

## Arquitectura

Kotlin, Jetpack Compose 1.8, Room/SQLite. `core` contiene modelos, reglas de negocio, documento interno, intercambio protegido e importación idempotente. `app/data` mantiene transacciones, historial, archivos atómicos y custodia de claves; `app/ui` presenta formularios, catálogos, informes y agenda; `app/export` genera archivos de intercambio y PDF multipágina y los comparte por WhatsApp o el selector Android.

La base de datos es la fuente de verdad. Cada cambio actualiza el documento del día; si falta espacio para una copia privada, el registro permanece guardado. Los informes recibidos no sobrescriben los datos propios. El documento compartido representa el día y no es un respaldo completo de toda la historia ni de la auditoría.

## Compilación y verificación

JDK 17, Gradle 8.9, SDK Android 35. Ejecutar `gradle :core:test :app:lintDebug :app:assembleDebug`. Las pruebas Android están en `app/src/androidTest` y se ejecutan en Android 10 y 15. `tools/check_project.py` solo realiza comprobaciones estáticas; no sustituye compilar o ejecutar las pruebas.

La revisión de depuración usa `com.workflow.operaciones.ux` y se instala junto a las APK anteriores. La APK final entregada se firma con la clave local persistente; no confundirla con el artefacto de CI firmado con la clave temporal de depuración. [Firma de revisión](docs/FIRMA_REVISION.md).

Para distribución de producción, configurar WORKFLOW_KEYSTORE_PATH, WORKFLOW_STORE_PASSWORD, WORKFLOW_KEY_ALIAS y WORKFLOW_KEY_PASSWORD fuera del código. Los secretos y almacenes de claves no se incluyen en GitHub ni en los paquetes de código.

## Límites

No hay servidor ni sincronización automática: Android y WhatsApp requieren que la persona elija destinatario y confirme el envío. La protección de archivos de WorkFlow usa la frase del equipo; no equivale a firma digital individual. El PDF de lectura y la base interna no usan ese cifrado de intercambio. Sigue pendiente comprobar el envío entre dos teléfonos físicos con WhatsApp y validar la distribución de producción.
