# MiCatalogo — estado de desarrollo

Corte vigente: 2026-10-05. Historial previo en Git, no en snapshots contradictorios.

- Repo Android: micatalogowebApp, paquete com.bsolutions.micatalogo, Room 21.
- Pública: 1.0.24 (25); mínimo admitido 23. Web/API y APK publicadas y verificadas.
- POS, catálogo, inventario, rutas, clientes, crédito/cobros, devoluciones y caja local
  conservan almacenamiento Room, centavos Long y colas idempotentes.
- Web/API Laravel es autoridad del catálogo compartido y contabilidad remota.
- Finanzas/caja/gastos tienen permisos independientes; costos masked nullable.
- Gastos permiten pago parcial/abonos; receipts/allocations/saldos guardados antes de SENT.
- Caja remota requiere red; cierre espera colas vacías; caja POS offline es independiente.
- Updater conserva mínimo real y bloqueo persistente después de iniciar instalación.

## Importador adaptativo

Archivo XLSX/XLS/CSV/TXT se envía a Laravel, que detecta hoja/fila/mapping y valida filas.
Android muestra originales, confianza, razones, ejemplos, ignoradas, warnings y conteos.
Puede cambiar hoja/fila sin editar Excel; atributos adicionales son opt-in.
Nombre/precio obligatorios; source-column no puede reutilizarse para distintos campos.
Dinero de importación ahora es String decimal en DTO y dominio; no Double ni cálculo local.
EAN/UPC/SKU siguen strings con ceros. Confirmación envía session_id y opciones, nunca filas.
Cupo de productos nuevos separado de actualizaciones; backend decide bajo lock.
Tras confirmar se sincroniza catálogo Room. No cambia versión ni esquema Room.

## Verificación

- 70 pruebas unitarias Android, incluidos tres contratos nuevos de importación.
- E2E Retrofit real en emulador con Laravel local SQLite aislado: libro anónimo fila 8,
  segunda hoja, mapping visible en Compose, confirmación y replay sin duplicar.
- 96 pruebas instrumentadas pasando: persistencia, ventas, cobros, caja, catálogo, importador y updater.
- Backend asociado: 343 pruebas / 1745 aserciones; build web Vite correcto.
- APK descargada desde la URL pública: hash, firma, paquete y versión verificados;
  mismo certificado que la release anterior. Manifiesto público: 1.0.24 (25), mínimo 23.
- No instalada en teléfono físico; no se afirma prueba de actualización en él.
- VPS: Composer/Vite/cachés correctos; XLSX fila 8 y códigos con ceros verificados.
- Conteos e identificadores comerciales sin cambios antes/después del despliegue.
- Referencia de release financiera vigente: docs/FINANCIAL_ALIGNMENT_RELEASE_1.0.23.md.

## Límites

Importación requiere conexión y sesión server-side, no una simulación offline.
ODS/PDF/imágenes/Word no admitidos. Pérdida previa de ceros/precisión Excel genera aviso,
no reconstrucción inventada. Límites/arquitectura y QA detallados en el repo backend:
docs/INVENTORY_IMPORT_ARCHITECTURE.md. Publicación documentada en docs/INVENTORY_IMPORT_RELEASE_1.0.24.md.
