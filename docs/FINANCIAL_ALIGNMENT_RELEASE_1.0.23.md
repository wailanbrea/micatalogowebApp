# Cierre de alineación financiera — MiCatalogo 1.0.23 (24)

Fecha: 2026-10-05. Repositorios: micatalogo (backend) y micatalogowebApp (Android).

## Auditoría e inconsistencias encontradas
Se compararon rutas, servicios, controllers, DTO, repositorios, Room, navegación, outbox y updater antes de modificar código. La tabla en API_CONTRACT.md registra nombres incorrectos de caja, importes ausentes, recibos descartados, delegación financiera implícita y mínimo de actualización reemplazado por la última versión.

## Cambios backend / API
Caja añade debt_collections_cash, expected_closing_amount y movements_count; conserva nombres web y aliases singulares de Android anterior. Gastos/abonos/movimientos devuelven UUID de confirmación; el abono refresca amount_paid/unpaid_amount para no devolver un modelo desactualizado. Finanzas incluye autorización explícita; cash y expenses tienen middleware independiente. Administradores conservan acceso. No hay cambios de esquema remoto ni seeders.

## Cambios Android / UI
DTO estrictos para importes críticos: una clave ausente no se convierte en cero. Costos/ganancias masked siguen nullable. P&L presenta ventas, descuentos, devoluciones e impuestos informativos; cobertura por ingresos y advertencia de utilidad parcial. Gastos muestran pagado/pendiente y permiten pago inicial parcial o cero, más abonos. Recibos de cobro muestran facturas asignadas, saldo restante y efecto en caja. Menú Gastos independiente. Caja del servidor y Caja local POS se distinguen sin eliminar el flujo local; apertura/cierre remoto requieren conexión. Cuando no se pudo consultar caja, no se inventa una sesión inexistente.

## Room / outbox / permisos
Room 20→21 añade server_response nullable a payment_sync_outbox y operation_outbox; preserva datos anteriores. Finanzas reutiliza OperationOutbox con UUID/payload inmutables, secuencia respecto a ventas y ACK guardado antes de SENT. Cobros mantienen PaymentSyncOutbox y dependencias. HTTP 401/426/429/5xx reintenta; conflictos/validación/permisos bloquean para revisión, sin inventar confirmación. Configuración permite reintentar una operación revisada. Cierre remoto no procede mientras existan colas pendientes de la tienda. finance/cash/expenses se aplican en servidor y navegación; vendedores antiguos con permisos null no reciben finance automáticamente.

## Updater
Se conserva minimum_supported_version_code real, incluido al persistir. installed < minimum: obligatorio; minimum <= installed < latest: opcional antes de iniciar; installed >= latest: sin actualización. Una vez iniciada, la actualización conserva estado pendiente y no permite saltar el flujo al regresar o reiniciar la app. Cancelar el instalador del sistema no desbloquea la app. Un fallo de comprobación sin actualización requerida/iniciada pendiente permite continuar. Esta política responde al documento actual y sustituye el comportamiento anterior que bloqueaba también opcionales.

## Pruebas backend
320 pruebas / 1602 assertions, todas pasan en SQLite :memory:. El guardián de tests impide usar producción. Ejecutadas mediante Pest con entorno de pruebas explícito; la primera ejecución por artisan fue rechazada por el guardián por configuración de entorno, sin operaciones sobre la base remota.

## Pruebas Android
67 pruebas unitarias pasan. 81 instrumentadas pasan en emulador Android 17: 74 de datos/sincronización/migraciones y 7 de interfaz/persistencia/updater. Incluyen migración histórica completa hasta Room 21, preservación de payloads, reintentos financieros con el mismo cuerpo, conflicto 409, recibos persistentes, mínimo opcional conservado, actualización iniciada recuperada y diálogo obligatorio. Los seis fixtures JSON de ambos repositorios coinciden byte a byte.

## E2E aisladas y contract tests
- Caja: 5000 + venta10000 + cobro2000 - gasto1500 - salida500 = 15000; cierre con contado15000 deja diferencia cero. Android presenta RD$15,000.00 con el mismo fixture.
- Gasto10000/pagado4000: P&L reconoce10000, flujo de efectivo4000; abono3000 deja pagado7000/pendiente3000. Replay no duplica abonos y payload distinto devuelve409.
- Deuda3000/cobro1500: FIFO asigna1000 a factura antigua y500 a siguiente; saldo1500, entrada en caja una sola vez.
- Wholesale1200 aceptado;1199 rechazado con price_conflict409 sin alterar stock ni contabilidad. Snapshot sale_mode se conserva.
- Vendedor sin delegación:403 en módulos financieros; finance explícito no otorga expenses ni cash.

Estas son pruebas aisladas de API, persistencia y UI, no operaciones financieras creadas en producción ni una prueba física en el teléfono.

## Release y publicación
APK:1.0.23; versionCode:24; mínimo admitido:23; paquete:com.bsolutions.micatalogo; minSdk:26; no debuggable.
SHA256: f35e780a95489a062555706fe2d70380132226b0ee7a6b3a70e2d5f7e653db99
Tamaño:16459604 bytes.
Certificado SHA256:5a5670decdac3ee1e2fc95503ae65343c3a1f075f62dec835d26125a578d490f.
URL:https://micatalogo.bsolutions.dev/downloads/bspos-1.0.23-contracts.apk
Se verificaron firma y metadatos del artefacto construido y de la descarga pública. Endpoint público corresponde a versión24/mínimo23/hash/tamaño. Se conserva la APK previa y rollback del manifiesto mediante Git; no se generaron backups de carpetas.

## Integridad de producción
Antes/después:11 usuarios,8 tiendas,298 productos,0 pedidos,6 facturas. Coinciden los hashes de todos los identificadores ordenados:
- users:4f708617fb9ec9d83a916b0e2b81440c2e8f48cca55e11fee7c62704f46d9c42
- shops:2c8822e2d00e0506a147c1075af494494ea2905e81e58f9e0e415eef13af7eb3
- products:64dbf33bd76bac4915238e1bc91f0b30faf06066dd8a469cc782fac7509b0855
- orders:4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945
- invoices:9e336c7761c37a1082dcce7ba8b6acea5bc63d2e432459ae3d2051c34e91f06c

## Riesgos restantes / TODO
No quedan cambios funcionales de esta matriz sin implementar. El teléfono no apareció en adb; queda fuera de la evidencia la actualización real en tu dispositivo y su certificado instalado. La caja local existente sigue siendo una sesión separada de la remota; no se presenta como sincronizada. Una cola BLOCKED requiere revisión antes del cierre. Los avisos de SDK duplicado/JDK25 y APIs deprecadas no impidieron las pruebas ni la release; no se modificó el SDK para esta entrega.
