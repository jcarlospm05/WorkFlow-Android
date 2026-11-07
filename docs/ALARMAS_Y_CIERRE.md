# Agenda, alarmas y cierre de jornadas

Alcance confirmado por el usuario: avisos con sonido y vibración para tareas, alarma general a las 19:00 y obligación de terminar las tareas y cerrar jornadas anteriores antes de empezar otra. No se crean registros de demostración y se conservan los registros existentes y la identidad visual.

Cada tarea pendiente nueva necesita hora. El usuario configura la hora para tareas automáticas y puede cambiarla en cada tarea o al iniciar la jornada. Las tareas antiguas sin hora se conservan: hay que asignársela antes de continuar operaciones; no se borran ni se completan automáticamente.

La comprobación utiliza jornadas iniciadas o con actividad real; consultar una fecha o preparar tareas futuras no inicia un día ni crea jornadas en días libres. Una fecha anterior cerrada con tareas pendientes sigue bloqueando el inicio posterior. El bloqueo se comprueba dentro de la transacción de datos, también al ejecutar operaciones e importar una jornada iniciada. Se puede volver a completar y cerrar días anteriores. Las averías pueden seguir en taller si la tarea de revisión está completada y el cierre documenta esa situación.

Android debe autorizar notificaciones y alarmas exactas. El canal Agenda y cierre del día debe permitir sonido y vibración y el volumen de alarmas debe estar activo. La app guía a la configuración; respeta No molestar y los ajustes del teléfono. Los avisos usan AlarmManager, se restauran tras reinicio, cambio de hora/zona y actualización, y cancelan los horarios anteriores al reprogramar o completar. Una tarea no se completa al detener el aviso.

La alarma suena/vibra hasta detenerla o durante un máximo de 60 segundos. Abre la fecha correcta de la agenda, conservando el desbloqueo de WorkFlow. La notificación se marca como privada y ofrece una versión pública sin detalles para la pantalla bloqueada. Si no se entregó la alarma de una tarea mientras el teléfono estaba apagado, se avisa al restaurarla; la comprobación de cierre se repite a las 19:00 de cada día mientras siga abierto. El teléfono apagado y las restricciones de Android no se pueden eludir.

Pruebas: reglas puras de jornadas y zona horaria, transacciones sin permisos, cierre con tareas pendientes, planificación futura y archivos recibidos, y pruebas Android con AlarmManager/notificación/servicio reales. Los fixtures y accesos simulados están solo en androidTest; las pruebas de integración de alarmas usan el acceso Android de producción. No hay permisos de Internet ni almacenamiento compartido.
