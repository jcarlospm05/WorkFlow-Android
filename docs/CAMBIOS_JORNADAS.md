# Jornadas continuables y reportes visuales

Alcance autorizado por el usuario el 6 de noviembre de 2026. Se mantiene la identidad existente de la aplicación y se fijan las muestras de docs/reportes/ como guía visual de los documentos. No hay datos de demostración en main ni carga inicial de registros.

1. El archivo compartido carga la fecha recibida en las tablas de trabajo y abre el inicio de esa jornada, con datos editables.
2. Los listados tienen copia al portapapeles y envío como texto por WhatsApp, incluidos cena, almuerzo, asistencia, ausencias y combustible pendiente.
3. Cada infracción tiene documento individual PDF/JPG/texto con fecha, operador, motivo, nivel, supervisor, reincidencias, objetivo/real y descripción.
4. Cada módulo dispone de reporte y visualización dentro de la aplicación.
5. Los eventos individuales se pueden consultar y compartir aparte del informe general.
6. El intercambio incluye las tareas, sus estados y sus relaciones con las actividades. Permite volver al teléfono original sin duplicar la jornada.
7. Iniciar jornada muestra las seis actividades habituales seleccionables; solo las marcadas generan tareas. Iniciar dos veces no duplica actividades.
8. Comidas, combustible y programación generan tareas vinculadas. La tarea de seguimiento de taller aparece con las averías.
9. Los reportes aceptan fechas inclusivas, semana/mes y filtros por operador/equipo/estado cuando corresponden. Incluyen domingos y días libres registrados, sin inventar compensatorios.
10. Una avería abierta marca al equipo como inactivo temporal; permanece así mientras haya otra avería abierta. La resolución se conserva para el historial.
11. La interfaz usa «Cierre del día» y muestra los pendientes. Una jornada cerrada necesita reapertura para modificar sus operaciones.
12. Compartir es manual. La opción de ofrecer compartir después de guardar está en Más y viene desactivada.
13. La sesión sigue abierta al cambiar de aplicación y se revoca al apagar/bloquear la pantalla o terminar el proceso. La autenticación se presenta automáticamente; reintentar solo es una opción si se cancela o falla.
14. Los formularios viven dentro de la navegación inferior. Cambiar de pestaña conserva el borrador y muestra cómo retomarlo, sin pedir que se envíe un reporte.
15. Todos los reportes siguen la identidad visual de WorkFlow: logo y fuentes originales, paleta fija, tarjetas redondeadas, sombras suaves, tablas y gráficos obtenidos de los datos filtrados. Un mismo motor produce PDF, JPG y vista previa.

## Integridad del intercambio

La protección existente AES-256-GCM y PBKDF2-HMAC-SHA256 permanece intacta. El usuario comparte una «jornada» sin tener que conocer su formato interno. El código no incorpora una clave universal. La copia interna permanece en almacenamiento privado; solo se comparten archivos protegidos.

La jornada usa una identidad estable y versiones por teléfono. Una actualización posterior puede continuar automáticamente tras confirmación de la fecha; una versión anterior o con cambios simultáneos exige revisar la sustitución. No se mezcla silenciosamente una jornada ajena con cambios propios. La versión previa se conserva en auditoría antes de una sustitución confirmada. Los catálogos se combinan por identificador y se rechazan colisiones de nombres/códigos, sin renombrar registros ni reasignar sus relaciones.

Los saldos iniciales de artículos viajan con el día para continuar movimientos aunque el receptor no tenga el historial anterior. La migración de SQLite 1→2 añade una tabla de saldos iniciales; conserva las tablas y registros anteriores, sin reconstrucción destructiva. Los archivos de la primera versión siguen siendo legibles; las versiones antiguas de la app no interpretan las nuevas funciones y ambos teléfonos deben actualizarse.

## Verificación

Pruebas JVM: intercambio de ida y vuelta, versiones simultáneas/anteriores, plantillas elegidas, tareas vinculadas, filtros, listados con nombres y fechas, domingos y documentos vacíos.

Pruebas Android: carga efectiva en SQLite, continuación en dos repositorios aislados, saldos sin doble conteo, averías y estado de equipos, navegación con borrador, seguridad del transporte y renderización de los 22 modelos a PDF/JPG con el logo original. Los registros sintéticos están exclusivamente en los conjuntos de pruebas.

Los resultados de compilación y dispositivos se registran aparte al terminar la ejecución. El envío entre cuentas reales de WhatsApp requiere verificación en los teléfonos del equipo.
