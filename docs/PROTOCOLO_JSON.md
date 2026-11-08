> Formato interno. La interfaz presenta «Jornada compartida». El transporte se mantiene protegido como se describe en [Informes protegidos](INFORMES_PROTEGIDOS.md).

# Intercambio diario · workflow.daily.progress v2

Un archivo representa la jornada completa de una fecha, sus catálogos y los datos necesarios para continuarla. No equivale al respaldo completo de la historia o auditoría. Incluye programación futura cuando se comparte esa fecha.

Se conserva la lectura de documentos v1 dentro de la envoltura cifrada. Los nuevos campos opcionales omiten sus valores por defecto para no alterar la representación canónica de documentos v1. El formato del cifrado sigue en versión 1.

- originId identifica el teléfono que genera el archivo; revision es la revisión local.
- journeyId permanece estable al pasar de un teléfono a otro.
- versionVector contiene una revisión por teléfono que ha modificado esta jornada. Importar una copia idéntica no cuenta como edición. Si se combinan catálogos locales adicionales, ese cambio se refleja en la versión.
- day es la fecha del trabajo; generatedAt y startedAt son instantes UTC.
- operators/assets/items conservan UUID y referencias estables. El código de empleado es opcional.
- operations contiene todo lo creado en esa fecha, incluidas tareas. sourceId enlaza una tarea con su actividad; templateKey evita duplicar tareas predeterminadas.
- Las infracciones incorporan nivel, objetivo, resultado real y responsable del registro. Las averías conservan resolvedAt.
- carryOverFaults mantiene la fecha original de las averías anteriores aún en seguimiento durante el día compartido.
- stockOpeningBalances permite continuar existencias sin disponer del historial anterior; stockBalances representa el saldo final de la fecha. Se comprueba su coherencia con los movimientos importados.
- checksum es SHA-256 de la serialización canónica con checksum vacío. No constituye cifrado ni firma del supervisor.

La representación canónica utiliza encodeDefaults=true, el orden del serializer Kotlin y las excepciones @EncodeDefault(NEVER) de campos nuevos opcionales. No ordenar las claves ni recalcular sobre una representación con espacios distinta.

Límites: 4 MiB antes del parser, profundidad 32, 2000 filas por catálogo, 3000 operaciones del día y 3000 averías vinculadas, siempre dentro del límite global de bytes. Se rechazan campos desconocidos, referencias huérfanas, duplicados, fechas imposibles, saldos negativos, valores no finitos y parámetros criptográficos hostiles.

## Carga confirmada de la jornada

| Caso | Resultado |
|---|---|
| Jornada local sin actividad propia | Carga la fecha recibida |
| Mismo contenido y versión causal | Ya cargada, sin duplicados |
| Versión recibida posterior | Actualiza la jornada editable |
| Cambios simultáneos | Revisión explícita antes de sustituir |
| Versión recibida anterior | Revisión explícita; no se aplica silenciosamente |
| Misma versión causal con contenido distinto | Conflicto |
| Archivo de vuelta al teléfono original | Se compara la jornada compartida y se permite continuarla |

La importación es transaccional: valida primero, guarda la versión anterior en auditoría, combina catálogos sin reasignar referencias y actualiza los registros de la fecha. Los saldos iniciales se guardan en una tabla nueva mediante una migración no destructiva. No se borran las operaciones de otras fechas.

Recibir no envía acuses ni mensajes. El usuario pulsa Compartir y elige el destinatario en WhatsApp. Los PDF/JPG son documentos visuales; para continuar datos se usa el archivo protegido.
