# BSPOS — ESTADO DE DESARROLLO

## Corte de ejecución: 2026-09-20

**Fase actual: 2 — Database Core & Migrations (parcial, Room v10).**

La revisión encontró una aplicación base Compose con navegación y componentes visuales,
inyección Hilt y dependencias configuradas, pero sin entidades, DAOs ni base Room.
El bloque implementado inicia la persistencia que exige el plan antes de avanzar a la UI de catálogo.

## Bloque implementado y validado

- `data/local/AppDatabase.kt`: Room v4 con catálogo, proveedores, inventario, documentos, clientes y rutas.
- `data/local/entity/`: columnas, defaults SQL, auditoría y borrado lógico según el esquema.
- `products.category_id` y `products.unit_id`: FK con `ON DELETE RESTRICT` y `ON UPDATE CASCADE`.
- Código interno de producto único; índices en ambas FK, código de barras y estado activo.
- `core/database/DatabaseConverters.kt`: UUID como TEXT e Instant como milisegundos UTC.
  Los importes son Long; no se persiste BigDecimal ni Double para dinero.
- `data/local/dao/`: observación con Flow, consulta por ID, inserción que aborta ante conflictos,
  actualización y borrado lógico. La ordenación de categorías usa `@Transaction`.
- `core/database/AppDatabaseTransactor.kt`: ejecución atómica con `Room.withTransaction`.
- `domain/model/`, `domain/repository/`, `data/mapper/`, `data/repository/`:
  modelos puros, contratos, mappers bidireccionales y repositorios inyectables.
- `data/local/CatalogLocalDataSource.kt`: acceso local entre repositorios y DAOs.
- `app/schemas/com.example.bspos.data.local.AppDatabase/1.json`: esquema exportado por KSP.
- Corrección del manifiesto: cámara opcional para que la ausencia de este hardware no excluya dispositivos.

## Inventario Base

- `inventory_stock`: saldo por producto y ubicación con índice único. El almacén central usa siempre `MAIN`; una ruta usa un UUID canónico.
- `inventory_movements`: Kardex con saldo anterior/nuevo, costos, referencia documental y motivo. Triggers SQL rechazan `UPDATE` y `DELETE`.
- `InventoryDao.recordMovements()`: transacción que valida aritmética `Long`, signo, referencias, motivos/notas, saldo negativo y escrituras obsoletas.
- El saldo y su movimiento se escriben juntos. Un fallo en cualquier fila revierte todo el lote; las salidas no modifican el CPP del producto.
- `MIGRATION_1_2` añade las tablas, FK, índices y triggers sin modificar los registros v1. La migración y callback se registran en Hilt.
- Se añadieron modelos, mappers, fuentes locales, repositorios y bindings de Hilt para stock, Kardex y motivos.

La prueba de migración crea el esquema Room v1 y abre la base con Room v4, que ejecuta la cadena real de migraciones y valida el esquema. No usa `MigrationTestHelper`: Room 2.8.5 presenta un `AbstractMethodError` con `kotlinx.serialization` 1.7.3, fijado por AndroidX en este proyecto.

## Documentos De Inventario

- Room v3 añade entradas manuales, compras y conteos físicos con sus líneas e índices.
- Las entradas permiten proveedor nulo con `SET NULL`; las compras exigen proveedor con `RESTRICT`.
  Las líneas se eliminan únicamente al eliminar su encabezado y nunca al eliminar un producto referenciado.
- Los DAOs insertan encabezado y líneas en una sola transacción. Validan documentos no vacíos,
  productos sin duplicar, cantidades/costos, subtotales y total de compra.
- Los conteos comienzan en `DRAFT`, exigen una diferencia exacta por línea y sólo permiten
  `DRAFT → IN_PROGRESS/CANCELLED` e `IN_PROGRESS → COMPLETED/CANCELLED`.
- `MIGRATION_2_3` es aditiva. La apertura de una base v1 valida la cadena completa
  `v1→v2→v3` y preserva los datos anteriores.
- Estos documentos todavía no cambian stock ni CPP por sí mismos. Las fases 7 y 9 crearán los
  casos de uso atómicos que los combinarán con movimientos de Kardex.

## Clientes Y Rutas

- Room v4 añade clientes, rutas y `route_customers` mediante una migración aditiva v3→v4.
- `route_customers` es la fuente única de asignación y orden: un cliente puede estar sin ruta o en exactamente una ruta. Los índices impiden dos rutas por cliente y dos posiciones iguales dentro de una ruta.
- Se omiten `customers.route_id` y `customers.visit_order` del esquema implementado para evitar una representación duplicada y divergente.
- Las asignaciones sólo aceptan clientes y rutas activos. Reasignar mueve la fila existente en una transacción; la reordenación usa posiciones temporales para intercambiar valores sin romper la restricción única.
- Los clientes tienen borrado lógico. Al eliminar físicamente una ruta se eliminan sus asignaciones, no los clientes.

## Cargas De Ruta

- Room v5 añade `route_loads` y `route_load_items` mediante una migración aditiva v4→v5.
- Una carga inicia en `OPEN`; sólo puede pasar una vez a `SETTLED` o `CANCELLED`.
- Cada producto aparece una sola vez por carga, con cantidad positiva y `unitCostSnapshot` no negativo. El costo queda congelado aunque cambie el CPP del producto.
- La creación de encabezado y líneas es transaccional. Por ahora el documento no modifica stock: la fase 12 lo combinará atómicamente con `ROUTE_LOAD_OUT` en MAIN y `ROUTE_LOAD_IN` en la ruta.

## Ventas Y Cobros

- Room v6 añade `sales` y `sale_items`; Room v7 añade `payments` y `payment_allocations` mediante migraciones aditivas.
- Las ventas capturan precio, descuentos, impuestos y `unitCostSnapshot` por línea. Las líneas, el encabezado y sus totales se crean en una sola transacción.
- Una venta a crédito exige cliente y saldo pendiente; el número de factura es único.
- Un recibo exige importe positivo, folio único y al menos una aplicación. Las aplicaciones deben sumar exactamente el recibo, no pueden repetir factura y sólo aceptan una venta completada del mismo cliente por un importe no mayor al saldo pendiente registrado.
- Los cobros no actualizan aún el saldo de la factura: el futuro caso de uso de cuentas por cobrar deberá aplicar recibo, asignaciones y saldo de venta en una única transacción.

## Devoluciones

- Room v8 añade `returns` y `return_items` mediante una migración aditiva v7→v8.
- Cada devolución apunta a una venta completada y conserva producto, precio de reembolso y costo histórico de la línea original.
- Las líneas de un documento no pueden repetir una línea vendida, superar la cantidad vendida menos devoluciones anteriores ni alterar el costo congelado. El total debe igualar la suma de cantidad por precio de reembolso.
- Por ahora la devolución no reintegra stock ni ajusta saldos: el futuro caso de uso combinará este documento con los movimientos de Kardex apropiados.

## Historial De Backups

- Room v9 añade `backup_history` mediante una migración aditiva v8→v9.
- Guarda metadatos auditables de cada respaldo o exportación: archivo, ruta, tamaño, tipo, resultado, hash SHA-256 opcional y fecha.
- El DAO rechaza rutas/nombres vacíos, tamaños negativos y hashes distintos de 64 caracteres hexadecimales. No expone borrado ni reemplazo del historial.
- Esta tabla no crea, mueve, restaura ni borra archivos. La operación real de backup y su política de retención siguen pendientes de requisitos explícitos.

## Sesiones De Caja

- Room v10 añade `cash_sessions` y `cash_movements` mediante una migración aditiva v9→v10.
- Sólo se permite una sesión abierta. La apertura exige monto inicial no negativo; cada movimiento exige monto positivo, motivo y una sesión abierta.
- Al cerrar, el esperado se calcula con monto inicial más ingresos, ventas y cobros, menos gastos. El cierre persiste esperado, monto contado y diferencia; no admite movimientos ni un segundo cierre posterior.
- Las ventas y cobros todavía no registran automáticamente movimientos de caja. Esa integración exige un caso de uso comercial atómico posterior.

## Configuración Persistente

- `DataStore<Preferences>` usa el archivo `bspos_settings` como singleton inyectado por Hilt.
- `SettingsRepository` expone `AppSettings` mediante `Flow` y actualiza cada clave con `edit`, sin sobrescribir preferencias no relacionadas.
- Las únicas claves implementadas tienen valor seguro por defecto: `allow_negative_stock=false` y `automatic_backups_enabled=false`.
- El control efectivo de stock negativo y la planificación de backups aún deben consumir estas preferencias desde sus casos de uso o workers respectivos.

## Venta Atómica

- `CompleteSaleUseCase` inserta encabezado y líneas de venta, congela el CPP vigente por producto y crea en la misma transacción la salida de Kardex y actualización de stock desde MAIN o la ruta asignada.
- Usa `allow_negative_stock` desde DataStore. Si el inventario no alcanza, no queda factura, movimiento ni modificación de saldo.
- Una venta `CASH` puede recibir una sesión abierta y registra un movimiento de caja con el total. Pagos, crédito y devoluciones todavía requieren casos de uso atómicos propios para actualizar saldos financieros.

## Cobro Atómico

- `RecordPaymentUseCase` crea recibo y asignaciones, actualiza `paid_amount`/`pending_amount` de cada factura y registra caja para efectivo, todo dentro de `AppDatabaseTransactor`.
- Las aplicaciones no pueden superar el saldo original menos las aplicaciones anteriores; un rechazo revierte recibo, asignaciones, saldos y caja.

### Contratos de lectura y escritura

- `observeAll()` incluye registros inactivos y excluye los eliminados lógicamente.
- `findById()` conserva acceso a registros eliminados para resolver referencias históricas.
- `update()` y `softDelete()` devuelven false cuando no se modifica ninguna fila.
- Los DAOs no exponen borrado físico ni usan `REPLACE`, que podría borrar filas referenciadas.
- `reorder()` rechaza IDs duplicados o inexistentes y revierte toda la ordenación ante fallo.
- Los catálogos empiezan vacíos. Los datos presentes en las pruebas son fixtures de una base en memoria.
- Estos son repositorios base; las validaciones comerciales y los casos de uso corresponden
  a sus fases posteriores. El producto no contiene existencia operativa.

## Verificación ejecutada

| Comprobación | Resultado |
| --- | --- |
| `:app:assembleDebug` | BUILD SUCCESSFUL; APK debug generado |
| `:app:testDebugUnitTest` | 16 pruebas, 0 fallos; incluye 10 de conversores |
| `:app:assembleDebugAndroidTest` | APK de pruebas compilado |
| `:app:connectedDebugAndroidTest` | 64 pruebas, 0 fallos, 0 errores, 0 omitidas; venta y cobro atómicos, configuración DataStore, devoluciones, historial de backups, caja y migración encadenada v1→v2→v3→v4→v5→v6→v7→v8→v9→v10 |
| `:app:lintDebug` | BUILD SUCCESSFUL tras corregir cámara opcional; 0 errores, 18 advertencias |

Pruebas instrumentadas ejecutadas en el emulador Pixel 10 Pro XL con Android 17.
Cada prueba de persistencia utiliza `Room.inMemoryDatabaseBuilder`, sin abrir `bspos.db`.
La ejecución combinada de instrumentación y lint terminó inicialmente con error por el
manifiesto; el XML de instrumentación confirma que sus 13 pruebas finalizaron correctamente.
Después se repitieron compilación y lint con resultado satisfactorio.

Cobertura: conversión de IDs, fechas y enums; lectura/escritura de todos los campos, dinero Long, FK, índices únicos, soft-delete, ordenación atómica, rollback y Flow. Inventario añade saldo igual a suma del Kardex, aislamiento por ubicación, concurrencia optimista, límites negativos, motivos auditables, inmutabilidad SQL, filtros de fechas, sobreflujo y preservación v1.

Reportes locales:
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/outputs/androidTest-results/connected/debug/`
- `app/build/reports/lint-results-debug.html`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

Las advertencias de lint restantes corresponden a versiones de dependencias, recursos de plantilla,
etiqueta redundante, calificador de recursos y posición de un parámetro Modifier existente.
El entorno también informa advertencias de JDK/Kotlin y ubicaciones duplicadas del SDK;
no impidieron las verificaciones de este bloque.

## Siguiente bloque dentro de la fase 2

1. Implementar el trabajo real de backup, casos de uso comerciales atómicos e interfaces de la fase funcional según `TODO.md`.
2. Definir claves de configuración, ampliar mappers/repositorios y probar integridad,
   atomicidad y preservación de datos de cada migración.
3. Cerrar la fase 2 sólo cuando todo el alcance esté implementado y validado.

### Ambigüedades documentales a resolver con los bloques afectados

- El esquema objetivo incluye tablas de lotes y caja que no tienen tareas explícitas de entidad en fase 2.
- La regla general de auditoría UUID/timestamps/soft-delete no aparece uniformemente en todos
  los documentos y tablas hijas del SQL objetivo; el Kardex, además, debe ser inmutable.
- La asignación de clientes a ruta quedó normalizada en `route_customers`; las futuras consultas deben usar esa tabla como fuente de verdad.
- `credit_limit = 0` está descrito como «sin límite o sin crédito»; requiere una única semántica.
- La carga de rutas aparece con nombres `TRANSFER_IN/OUT` y `ROUTE_LOAD_IN/OUT`;
  debe definirse la nomenclatura operativa antes de implementar sus movimientos.

La fase 2 permanece abierta; este corte valida únicamente el bloque detallado arriba.
## Alineación financiera Web/API/Android — 2026-10-05

- Contrato oficial y fixtures: docs/API_CONTRACT.md y docs/api-contracts, replicados y probados por serialización en Android.
- Caja conserva aliases web y Android <=1.0.22; nombres canónicos plurales, deuda, egresos, conteo y cierre esperado reales. Campos monetarios críticos no inventan ceros.
- Impuestos informativos fuera de utilidad; descuentos, devoluciones y cobertura por ingresos. Costos y utilidades masked permanecen nullable.
- Gastos permiten pago inicial parcial y abonos; ACK refresca saldo. Cobros conservan allocations, saldo autoritativo y efecto en caja.
- Permisos finance/cash/expenses independientes en API y Android. Finance requiere delegación explícita para vendedores; null heredado no concede acceso nuevo.
- Android Room 21: recibos nullable en payment_sync_outbox y operation_outbox; migración no destructiva. Payload/UUID estable, estados PENDING/SENT/BLOCKED y orden existente respecto a ventas.
- Apertura/cierre remoto requieren conexión; cierre espera colas de la tienda vacías. Caja local POS permanece separada y explícita, no representa una sesión del servidor.
- Updater conserva el mínimo real: opcional antes de iniciar; obligatorio por mínimo o proceso ya iniciado persistente. Mínimo publicado se conserva en 23.
- Pruebas backend: 320 casos / 1602 assertions en SQLite :memory:. Matriz nueva: caja 5000+10000+2000-1500-500=15000; gasto 10000/pagado4000 y abono3000; deuda3000/cobro1500/FIFO/saldo1500; permisos e idempotencia. Wholesale continúa validando snapshot y price_conflict.

Release verificada: 1.0.23 (24), mínimo admitido 23. Android: 67 pruebas unitarias y 81 instrumentadas en emulador. Publicación, integridad y límites de evidencia: docs/FINANCIAL_ALIGNMENT_RELEASE_1.0.23.md.
- No hay migraciones backend ni cambios de datos de producción en esta entrega.
