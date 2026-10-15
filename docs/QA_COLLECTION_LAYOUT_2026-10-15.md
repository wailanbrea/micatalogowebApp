# Registrar cobro — revisión móvil

Cambio Android local; no modifica backend, datos comerciales, firma, versión ni la APK publicada1.0.112.

## Problema y solución

La tarjeta anterior colocaba el importe total junto al cliente y el botón junto al monto editable. En320dp el nombre quedaba comprimido y los controles competían por el ancho. El selector de métodos era una fila horizontal desplazable.

- Márgenes exteriores16dp, interiores16dp y separación vertical12dp.
- Cliente/factura, saldo destacado, importe editable y botón en orden vertical.
- Campo y botón alineados y a todo el ancho; acción con mínimo52dp.
- Efectivo/Transferencia/Cheque/Tarjeta en rejilla de dos columnas, mínimo48dp y sin scroll horizontal.
- Nombres hasta dos líneas, sin competir con los importes.
- Protección frente al teclado en la lista y mensaje de importe superior al saldo.
- Mismo formulario visual reutilizado para factura local y saldo remoto. Se conservan callbacks, moneda, permisos, límites e identidad/idempotencia de los cobros.

## Evidencia

- Reproducción previa:2FAIL/1PASS; ambas fallas demostraron que el botón no quedaba debajo del campo.
- Final:7/7 instrumentadas PASS, cero skips/assumptions,40,03s, emulator-5554 y paquete QA aislado.
- Cobertura:320dp,360dp con fuente130%, importes grandes, métodos visibles/selección, abono200→20.000centavos, cero/exceso bloqueados, procesamiento deshabilitado, cambio de cliente y autorización/revocación sin duplicar cola.
- JVM completa:147PASS. Build debug QA y androidTest exitosas.
- Capturas de componentes inspeccionadas visualmente en `app/build/outputs/qa/collection-before/` y `app/build/outputs/qa/collection-after/`; esta última incluye `qa-results.json`.
- Son pruebas aisladas de componentes y repositorios; no se registraron cobros en producción ni se presenta una captura de componentes como un recorrido completo autenticado de la app.

Incluido en la release 1.0.113 (versionCode 114), solicitada para descarga pública. La publicación no implica instalación ni prueba de actualización en un teléfono físico.
