# Validación Android

La compilación de la aplicación no incluye bases de datos ni registros sintéticos. Las pruebas de instrumentación crean sus datos dentro del emulador, mediante `androidTest`, y no se distribuyen en la APK para la persona usuaria.

## Pruebas automatizadas

- `core:test`: reglas, documentos internos, importación y envoltura protegida. Comprobar clave incorrecta, cambios de contenido/etiqueta/sal, parámetros maliciosos, límites y ausencia de alternativa sin cifrado.
- `RepositoryTest`: instalación sin datos de negocio, códigos opcionales y unicidad de los asignados, correcciones, cierre de jornada, existencias e importación separada.
- `TransferProtectionTest`: guardar y recuperar la frase con Android Keystore y comprobar que su representación almacenada cambia en cada escritura.
- `UserExperienceTest`: altas consecutivas de personal/equipos/artículos, edición sin duplicados, registros consecutivos sin interrupción para compartir, dashboard, conservación de borradores y recepción protegida sin sustituir datos propios.
- `DialogInsetsTest`: gestos y tres botones de Android con teclado real. Pulsar los controles y comprobar sus límites físicos, incluidos «Guardar y añadir otro».
- `RealScreenshotsTest`: capturas de la interfaz real y logo en un emulador. Sus datos visuales de ejemplo son exclusivos de la prueba.

## Pruebas manuales en dos teléfonos

1. Instalar la APK normal y confirmar que no existen operadores, equipos, artículos, actividades ni informes recibidos precargados.
2. Añadir varios operadores sin código; conservar turno entre altas. Cerrar y regresar a Personal, sin volver al inicio. Editar uno y comprobar que no se duplica.
3. Repetir altas consecutivas de equipos, artículos y operaciones. Un error mantiene los campos y el formulario abierto. La oferta de compartir se presenta al terminar, sin interrumpir una secuencia.
4. Pulsar los cuatro indicadores del dashboard y comprobar su destino; bajo mínimo abre el filtro correspondiente.
5. Abrir un formulario, pasar por el bloqueo de Android y volver. Conservar módulo, desplazamiento y borrador. No guardar frases del equipo en el estado de navegación.
6. Compartir un informe por WhatsApp y WhatsApp Business. Si no están instalados, usar el selector Android. Confirmar destinatario y envío en la aplicación elegida.
7. Configurar la frase en el emisor y abrir el informe en el segundo teléfono con la misma frase. Revisar supervisor/fecha y guardar en Informes recibidos.
8. Recordar la frase y abrir el siguiente informe sin volver a pedirla. Introducir una frase equivocada o modificar un archivo: no mostrar contenido ni alterar datos recibidos o propios.
9. Reabrir la misma actualización sin duplicar; recibir una posterior y rechazar una anterior o un conflicto de igual actualización.
10. Generar y abrir el documento PDF en un lector externo y revisar nombres, listas de comida, cantidades, paginación y textos largos.
11. Comprobar en teléfonos físicos pequeños, escala de fuente grande, gestos, tres botones, teclado y recorte de cámara. Los resultados en emulador no sustituyen esta comprobación.
12. Antes de distribución de producción, revisar firma persistente, actualización desde instalaciones previas y recuperación de archivos antiguos.
