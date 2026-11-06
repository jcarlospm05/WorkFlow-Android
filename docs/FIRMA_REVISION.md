# Firma de la revisión de controles

La APK 1.0.2 usa el paquete com.workflow.operaciones.review y se instala aparte de com.workflow.operaciones.visual. Las APK anteriores de CI tenían certificados de depuración distintos entre ejecuciones: no permiten actualizarse unas sobre otras. No se requiere desinstalar la app anterior para probar esta corrección.

Se ha creado una clave privada de revisión persistente fuera del repositorio, en /workspace/.workflow-signing. La contraseña tampoco forma parte del código, de los artefactos de GitHub ni de los archivos de distribución. La APK se firma localmente con esa clave tras descargar el resultado de CI; las siguientes revisiones deben usar la misma clave e identificador para permitir actualizaciones.

Conservar ese directorio de firma como material privado del propietario. El ZIP de código no lo incluye. tools/sign-review.sh firma y verifica la APK mediante la herramienta pública apksigner; nunca envía la clave a GitHub. La distribución de producción requiere una configuración de firma y un plan de migración propios.
