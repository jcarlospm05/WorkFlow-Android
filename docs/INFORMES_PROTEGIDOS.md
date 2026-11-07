# Informes protegidos y experiencia de uso

La interfaz presenta «Informe protegido» para abrir el progreso en otro WorkFlow y «Documento PDF» para leer o imprimir. No muestra JSON, SQLite, algoritmos, identificadores internos ni detalles del protocolo. La agenda y los catálogos empiezan sin registros precargados.

## Protección del archivo compartido

Los archivos `.workflow` contienen una envoltura binaria versionada: magic WFRP (4 bytes), versión 1 (1 byte), iteraciones PBKDF2 en big endian (4 bytes), sal aleatoria (16 bytes), IV aleatorio (12 bytes), contenido cifrado y etiqueta GCM (16 bytes). No hay nombres, fechas ni registros en claro dentro de la envoltura. El nombre de archivo sí indica la fecha para que la persona identifique el envío.

El contenido canónico interno es el documento DailyCodec; su SHA-256 detecta corrupción. El transporte usa AES-256-GCM con el encabezado autenticado como AAD. SHA-256 no es cifrado: la clave AES se deriva de una frase de 12 a 128 caracteres mediante PBKDF2-HMAC-SHA256 con 600 000 iteraciones y sal nueva en cada envío. El IV también cambia con cada envío. Antes de derivar claves se verifican versión, parámetros y límite de tamaño; el archivo no puede pedir un coste de derivación arbitrario. La etiqueta GCM se valida antes de interpretar el documento.

La frase se acuerda entre el equipo, sin claves universales ni secretos en el APK. El receptor puede recordarla. En cada teléfono se guarda cifrada con una clave AES no exportable de Android Keystore. Las frases no se incluyen en el estado guardado de la interfaz, registros de diagnóstico, respaldos ni archivos compartidos. El conocimiento de la frase identifica al grupo que la conoce; no constituye una firma digital individual del supervisor.

No se acepta un documento sin cifrar como alternativa silenciosa. Los informes de versiones anteriores deben volver a compartirse con la edición actual. Ambos teléfonos necesitan una versión compatible. Cambiar la frase afecta a los próximos envíos: los archivos anteriores siguen usando la frase de su envío.

El documento PDF conserva su función de lectura fuera de WorkFlow y no recibe este cifrado de intercambio. La base privada y el documento interno del día siguen protegidos por el aislamiento y bloqueo del teléfono; no se afirma cifrado propio de toda la base de datos. Tras revisar la fecha, la jornada recibida se carga en las tablas editables y abre el inicio de esa fecha. Se comparan identidad de jornada y versiones por teléfono; los cambios simultáneos o versiones anteriores necesitan revisión antes de sustituir el día. La versión anterior se archiva en auditoría. El archivo incluye tareas, relaciones, saldos iniciales y averías anteriores que siguen en seguimiento; no es un respaldo completo de toda la historia.

## Formularios y navegación

- Código de empleado opcional; nombres de personas no se usan como identificador único. Los códigos asignados siguen siendo únicos. La migración 1→2 conserva los registros y añade saldos iniciales para continuar existencias en otro teléfono.
- «Guardar y añadir otro» mantiene el formulario y turno/tipo elegidos, limpia los campos del siguiente registro y asigna un identificador nuevo. No abre el menú de compartir durante una secuencia. «Guardar y cerrar» vuelve al módulo actual. La edición conserva su identificador y muestra «Guardar cambios».
- Personal, equipos y artículos disponen de listas buscables. Los accesos del dashboard abren asistencia, pendientes por módulo, equipos y existencias bajo mínimo.
- El módulo, el desplazamiento y los borradores se conservan al pasar por el bloqueo o por otra aplicación. Los informes completos no se guardan dentro del Bundle de navegación.
- Las claves se solicitan con lenguaje de negocio y campos protegidos. Los formularios mantienen sus controles dentro del área visible y por encima del teclado.

La barra inferior sigue disponible dentro de los formularios; los borradores se pueden retomar al cambiar de pestaña. Compartir es manual y la oferta posterior al guardado está desactivada inicialmente. La sesión se mantiene al cambiar de aplicación mientras la pantalla sigue desbloqueada y se revoca con el apagado de la pantalla. El lector de Android aparece automáticamente al volver a una sesión bloqueada.\n\nLos PDF, JPG y vistas previas comparten el motor visual definido en [Identidad de reportes](reportes/IDENTIDAD_FIJA.md). El texto de los listados se puede copiar y enviar aparte.\n\n## Verificación

Las pruebas de dominio cubren cifrado/descifrado, claves incorrectas, alteración de contenido/etiqueta/sal, parámetros hostiles, entrada excesiva, rechazo de texto sin cifrar y códigos opcionales. Las pruebas Android usan el proveedor real de Android Keystore, guardados múltiples, navegación, restauración de borradores y recepción protegida con continuación editable, conflictos y preservación de otras fechas. Los datos sintéticos se crean solo dentro de las pruebas de instrumentación; no se incluyen en la APK de la aplicación ni se precargan al instalarla.
