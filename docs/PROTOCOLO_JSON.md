> Documento del formato interno. No se comparte directamente con las personas: el transporte actual se cifra como se describe en [Informes protegidos](INFORMES_PROTEGIDOS.md).

# Intercambio diario · workflow.daily.progress v1

Un archivo representa el estado completo de **una fecha y un origen**, no un delta ni un respaldo completo. Se envían todos los registros de esa fecha y los catálogos necesarios para interpretarlos sin depender de los del receptor.

Campos superiores, en orden de serialización: format, schemaVersion, originId, supervisor, day, revision, generatedAt, operators, assets, items, operations, stockBalances, checksum.

- format: workflow.daily.progress; schemaVersion: 1.
- originId: UUID persistente por instalación. No es una credencial ni una firma.
- day: fecha ISO del trabajo; generatedAt: instante UTC.
- revision: entero creciente para ese origen/fecha; generado dentro de la transacción de guardado.
- operators/assets/items: identificadores UUID y datos de los catálogos.
- operations: operaciones del día; module determina los campos aplicables. Los identificadores referenciados deben existir en los catálogos incluidos. La combinación día/operador/turno en asistencia y día/tipo en comidas es única.
- stockBalances: saldo al final de esa fecha por artículo, incluyendo movimientos de fechas previas. El JSON diario por sí solo no permite auditar todos esos movimientos anteriores.
- checksum: SHA-256 hexadecimal de la representación UTF-8 generada por DailyCodec con checksum vacío. Esa representación tiene encodeDefaults=true y el orden del serializer Kotlin. No usar un JSON ordenado alfabéticamente ni pretty-print para calcularlo. El receptor decodifica y vuelve a serializar antes de verificar.

Límites: 4 MiB antes del parser; anidamiento hasta 32; hasta 2000 entradas por catálogo y 3000 operaciones. Rechazo de campos desconocidos, fechas imposibles, números no finitos, saldos negativos, identidades duplicadas y referencias huérfanas. La integridad no acredita la identidad del remitente: alguien que conoce el formato puede recalcular el hash.

Recepción confirmada por el usuario, clave (originId, day):

| Caso | Resultado |
|---|---|
| No existe | Nuevo informe recibido |
| Misma revisión y checksum | Ya recibido, sin duplicación |
| Revisión mayor | Actualiza la copia recibida |
| Revisión menor | Rechazo por versión antigua |
| Misma revisión, otro checksum | Rechazo por conflicto |
| Mismo origen del teléfono receptor | Rechazo; no restaura datos propios |

El receptor mantiene sus datos propios intactos. Recibir no produce un acuse automático ni envía nada a nadie. Los PDF son representaciones legibles del mismo documento; no son archivos para importar.
