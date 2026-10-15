# Decants — acceso a productos sin volumen

- Se retira el bloque permanente de productos pendientes de la pantalla principal.
- Accesos en este orden: **Reporte → Sin volumen (cantidad) → Más opciones**.
- Tres botones con igual ancho, visibles juntos, sin desplazar horizontalmente para alcanzar Más opciones.
- Sin volumen abre el mismo componente de ventana inferior utilizado por Reporte.
- Buscador por nombre/código, conteo y existencias; se elimina el límite anterior de ocho productos. Configurar ml cierra la lista y abre el formulario existente, sin duplicar el producto.
- Se conserva la selección por tienda/productos activos y los permisos: ver lista no concede configurar volumen.
- Estado vacío y cierre de ventana incluidos. Sin cambios de API, migraciones o datos de producción.

Pruebas añadidas a DecantsWorkspaceTest: orden/ancho de los accesos, apertura, búsqueda del producto12 por código (fuera del límite anterior), selección, estado vacío/cierre y configuración deshabilitada sin permiso. Se mantienen las seis regresiones previas de fuentes, frascos y cantidades.

Captura del modal inspeccionada visualmente; los primeros botones horizontales dejaban Más opciones cortado, por lo que se corrigieron a tres columnas de igual ancho y se añadió una aserción de dimensiones.

Build debug y androidTest exitosas; JVM147PASS antes del ajuste final de distribución de botones. Instrumentadas finales **9/9PASS, cero omisiones/fallos**,49,959s. Evidencia instrumentada (`qa-results-final.json`) y capturas en `app/build/outputs/qa/decant-unconfigured/`. Captura final inspeccionada: los tres accesos se ven completos y el modal funciona con el teclado abierto.

Incluido junto con Registrar cobro en la release 1.0.113 (versionCode 114), solicitada para descarga pública. Se conserva la APK 1.0.112 anterior. No se instala ni se borran datos de un teléfono físico durante la publicación.
