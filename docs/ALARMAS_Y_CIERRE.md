# Agenda, alarmas opcionales y cierre de jornadas

Las alarmas son opcionales y están desactivadas por defecto, también al actualizar desde 1.3.0. Se activan voluntariamente en Más → Alarmas de la agenda. Desactivarlas cancela los avisos programados y detiene el aviso activo. La preferencia es local a cada teléfono; recibir una jornada no activa alarmas ni pide permisos.

Se puede iniciar una jornada, guardar y editar tareas, registrar operaciones e importar trabajo sin horas, sin permisos de notificación, sin alarmas exactas y sin configurar sonido o vibración. Una hora solo se valida si se escribe; puede dejarse vacía. La hora predeterminada de tareas automáticas también se puede dejar vacía. Se conservan los horarios, tareas y registros existentes y no se completan tareas automáticamente.

Si el usuario activa los avisos y autoriza sus permisos, se programan las tareas pendientes que tengan hora y el recordatorio de cierre a las 19:00 locales. Las limitaciones de Android solo afectan a la entrega del aviso, nunca al registro o continuación del trabajo. Los avisos respetan el canal, volumen y No molestar del teléfono. Detener el aviso no completa la tarea. El sonido dura hasta detenerlo o un máximo de 60 segundos; la notificación privada abre la fecha correspondiente de la agenda.

Se mantiene la regla independiente de terminar las tareas y cerrar las jornadas anteriores antes de iniciar otra. Consultar fechas y preparar tareas futuras no inicia jornadas ni bloquea días libres. Las averías pueden continuar en taller cuando su revisión está completada y el cierre documenta la situación.

No se cambia el esquema SQLite ni la identidad visual ni se borran datos. No se precargan registros de demostración ni se añaden permisos de Internet o almacenamiento compartido.

Validación: inicio y operaciones con tareas sin hora, edición sin alarma, recepción y continuación sin configurar avisos, rechazo de horas mal escritas, conservación del cierre anterior, cancelación real en AlarmManager al desactivar y funcionamiento de los avisos cuando se activan voluntariamente. Los fixtures solo existen en los tests y sus capturas.
