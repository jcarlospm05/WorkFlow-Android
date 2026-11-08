# WorkFlow 1.4 — reglas de operación y reportes

Se conserva la marca, los colores, Nunito y las áreas seguras de Android.

- Cada tarea pertenece a un módulo. La agenda abre ese módulo; allí se realiza el registro y se revisan sus tareas. Las actividades generales viven en Gestiones. Las tareas automáticas de comida, combustible y taller conservan sus vínculos con los registros.
- Los módulos muestran el número de pendientes arriba a la derecha. Un registro y su tarea vinculada cuentan una vez. Continuar jornada abre Operación; desde allí se accede a tareas pendientes.
- Las listas muestran filas compactas: nombre, dato o estado y acción. El estado siempre tiene texto; rojo señala incidencias. Personal y equipos usan el mismo criterio. Equipos activos abre solo los activos y permite cambiar a Todos o En taller.
- Asistencia mantiene una decisión por persona y fecha. Sin marcar contiene solo personas pendientes. Presentes y Ausencias/libres muestran las decisiones y permiten corregirlas sin duplicar al operador. Los turnos de datos anteriores no crean decisiones contradictorias en los reportes.
- Comidas abre la lista guardada. Modificar lista abre la selección; Guardar lista vuelve al listado. Copiar y compartir texto permanecen disponibles por módulo.
- Existencias, artículos y Bajo mínimo se retiran del flujo. Los tipos y registros antiguos permanecen en el almacenamiento y en el protocolo para no romper archivos anteriores; no se incluyen en las consultas ni en los nuevos reportes generales.
- Reportes usa un catálogo de filas y resultados tabulados. Las tablas anchas se desplazan horizontalmente. Los PDF/JPG mantienen logo, tipografía y colores, con encabezado reducido, tablas más densas y letra de tabla de 11 puntos.
- Cualquier consulta puede exportar tablas CSV con los mismos filtros y fechas. CSV tiene una tabla por archivo: el informe general/cierre genera un archivo por módulo con actividad. Comidas incluye una fila por persona. Se usa UTF-8 con BOM, fechas ISO, decimales con punto y protección contra fórmulas de hojas de cálculo.
- Compartir progreso envía solo la jornada protegida; Compartir informe envía PDF y CSV; Compartir cierre envía PDF, CSV y jornada protegida. Compartir cierre no cierra el día por sí solo.
- La jornada protegida contiene todos los datos editables, incluidas las tareas y su módulo. Su extensión .workflow permite asociarla a WorkFlow y preserva el cifrado autenticado AES-GCM. Android controla la elección de la aplicación predeterminada. Se registran Abrir con y Recibir para el formato WorkFlow, archivos genéricos y MIME de JSON; archivos ajenos o manipulados se rechazan antes de cargarse.
- Los controles tienen un mínimo táctil de 48 dp y etiquetas cortas; las acciones extensas ocupan todo el ancho. Las alarmas siguen siendo opcionales.
- La APK empieza vacía: todos los datos de validación viven exclusivamente en fuentes de prueba.
