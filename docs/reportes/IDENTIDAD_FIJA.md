# Referencia visual fija de reportes · WorkFlow

Aprobada por el usuario el 6 de noviembre de 2026. Las 22 imágenes y el PDF en referencias/ fijan la estructura visual de los reportes: logo original, Nunito Sans 400/500/600, colores de docs/IDENTIDAD.md, tarjetas redondeadas, sombras suaves, tablas, indicadores y gráficos calculados con datos reales. No se modifica el resto de la identidad de la app.

Las referencias contienen exclusivamente datos ilustrativos. Viven fuera de app/src y nunca se cargan en la base de datos ni se incluyen como registros de una APK. Los reportes reales usan solo registros creados o importados por el usuario.

Formatos: informe general, cierre del día, agendas semanal/mensual, asistencia, ausencias, domingos, días libres, cena, almuerzos, combustible, taller, historial por equipo, infracciones del día/operador, ticket individual, existencias, programación, posiciones, evento individual y catálogos. Todos admiten PDF/imagen/texto según su contenido; los filtros muestran la fecha o intervalo y el operador/equipo seleccionados.

El archivo para continuar la operación es independiente del documento visual: contiene toda la jornada, conserva tareas y relaciones y se abre como datos editables en WorkFlow. La interfaz no expone JSON ni términos de implementación.
