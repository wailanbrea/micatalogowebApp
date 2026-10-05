# BSPOS — TODO MAESTRO DE EJECUCIÓN

Estado actual: **FASE 2 EN PROGRESO — PERSISTENCIA COMERCIAL, CAJA E HISTORIAL DE BACKUPS IMPLEMENTADOS**

Última validación: **2026-09-20**. Room v10 contiene catálogo, inventario, documentos, clientes, rutas, cargas, ventas, cobros, devoluciones, caja e historial de backups.
Detalle técnico y resultados: `DEVELOPMENT_STATUS.md`.

> **Regla de Oro:** Ninguna tarea puede marcarse como completada `[x]` hasta que haya sido implementada, probada y validada según la Definition of Done.

---

## FASE 0 — ANÁLISIS Y PLANIFICACIÓN
- [x] Analizar requisitos exhaustivamente.
- [x] Detectar contradicciones o ambigüedades.
- [x] Definir arquitectura general del sistema (`ARCHITECTURE.md`).
- [x] Crear Architectural Decision Records (`DECISIONS.md`).
- [x] Definir mapa de navegación para teléfono y tablet.
- [x] Definir modelo y relaciones de datos (`DATABASE_SCHEMA.md`).
- [x] Definir reglas operativas de inventario (`INVENTORY_RULES.md`).
- [x] Definir reglas financieras y sistema de costos (`COSTING_RULES.md`).
- [x] Definir Design System y pautas UI (`UI_GUIDELINES.md`).
- [x] Identificar riesgos técnicos y mitigaciones.
- [x] Crear plan de proyecto maestro (`PROJECT_PLAN.md`).
- [x] Presentar plan de implementación y obtener aprobación del usuario.

---

## FASE 1 — PROYECTO BASE Y FUNDACIÓN TÉCNICA
- [x] Configurar Gradle con Version Catalog (`libs.versions.toml`).
- [x] Integrar Kotlin Compose, Material 3, Hilt, Room, KSP, Coroutines, Flow.
- [x] Integrar Navigation Compose, DataStore, WorkManager, Coil.
- [x] Crear estructura de paquetes Clean Architecture (`core`, `data`, `domain`, `presentation`).
- [x] Implementar Design System base: `AppColors`, `AppTypography`, `AppSpacing`, `AppShapes`, `AppElevation`.
- [x] Implementar Scaffold responsivo con detección de `WindowSizeClass` (Compact / Medium / Expanded).
- [x] Validar compilación limpia (`BUILD SUCCESSFUL`).

---

## FASE 2 — DATABASE CORE & MIGRATIONS
- [x] Crear `CategoryEntity`.
- [x] Crear `UnitOfMeasureEntity`.
- [x] Crear `ProductEntity`.
- [x] Crear `SupplierEntity`.
- [x] Crear `InventoryStockEntity`.
- [x] Crear `InventoryMovementEntity`.
- [x] Crear `InventoryAdjustmentReasonEntity`.
- [x] Crear `StockEntryEntity` y `StockEntryItemEntity`.
- [x] Crear `PurchaseEntity` y `PurchaseItemEntity`.
- [x] Crear `InventoryCountEntity` e `InventoryCountItemEntity`.
- [x] Crear `CustomerEntity`.
- [x] Crear `RouteEntity` y `RouteCustomerEntity`.
- [x] Crear `RouteLoadEntity` y `RouteLoadItemEntity`.
- [x] Crear `SaleEntity` y `SaleItemEntity`.
- [x] Crear `PaymentEntity` y `PaymentAllocationEntity`.
- [x] Crear `ReturnEntity` y `ReturnItemEntity`.
- [x] Crear `BackupHistoryEntity`.
- [x] Crear `SettingsEntity` / DataStore keys.
  - [x] `allow_negative_stock` y `automatic_backups_enabled`, ambos desactivados por defecto.
- [ ] Implementar TypeConverters (Instant, UUID, BigDecimal/Long).
  - [x] Conversores nullable `Instant` ↔ milisegundos UTC y `UUID` ↔ TEXT; importes `Long` directos sin conversión a coma flotante.
  - [x] Incorporar enums de inventario, ubicación, movimiento, referencia, dirección de ajuste, ventas, cobros, devoluciones, backups y caja como TEXT.
  - [ ] Incorporar los enums que requieran entidades futuras. `BigDecimal` se reserva para cálculos, no almacenamiento monetario.
- [ ] Configurar `AppDatabase` con claves foráneas e índices.
  - [x] Room v10, esquemas JSON exportados, Hilt, migraciones v1→v2→v3→v4→v5→v6→v7→v8→v9→v10 y claves/índices de catálogo, inventario, documentos, clientes, rutas, cargas, ventas, cobros, devoluciones, caja e historial de backups.
  - [ ] Incorporar las tablas restantes mediante migraciones versionadas y verificadas.
- [ ] Implementar DAOs con soporte transaccional (`@Transaction`).
  - [x] DAOs de catálogo/proveedores, ordenación transaccional, Kardex/stock, documentos, clientes/rutas, cargas, ventas, cobros, devoluciones, historial de backups y `AppDatabaseTransactor`.
- [ ] Implementar Domain Models y Mappers bidireccionales.
  - [x] Modelos Kotlin y mappers de catálogo, inventario, documentos, clientes, rutas y cargas.
- [ ] Implementar Repositorios base.
  - [x] Contratos de dominio, fuentes locales, implementaciones y bindings Hilt para catálogo, inventario, documentos, clientes, rutas y cargas.
- [ ] Escribir y ejecutar Unit Tests de persistencia Room.
  - [x] Diez pruebas JVM de conversores y 40 pruebas instrumentadas de Room en memoria/migración, ejecutadas en Android 17.
  - [ ] Extender las pruebas a las entidades restantes y migraciones.

---

## FASE 3 — CATEGORÍAS Y UNIDADES
- [ ] Implementar UseCases para Categorías (Crear, Editar, Desactivar, Reactivar, Ordenar, Listar/Buscar).
- [ ] Implementar UseCases para Unidades de Medida (Crear, Editar, Desactivar, Listar).
- [ ] Implementar ViewModel de Categorías y Unidades.
- [ ] Diseñar pantalla y componentes UI para Categorías.
- [ ] Diseñar pantalla y componentes UI para Unidades.
- [ ] Validar que no existan categorías ni unidades hardcodeadas.
- [ ] Pruebas unitarias de UseCases y Repositorios.

---

## FASE 4 — CATÁLOGO DE PRODUCTOS
- [ ] Implementar UseCases para CRUD de Productos.
- [ ] Implementar subsistema local de guardado de imágenes con PhotoPicker y CameraX.
- [ ] Implementar compresión, redimensión y generación de thumbnail en disco privado.
- [ ] Implementar eliminación segura de imagen anterior al reemplazarla o borrar el producto.
- [ ] Implementar ViewModel de Productos con búsqueda reactiva y filtros (categoría, estado, stock bajo).
- [ ] Diseñar Grid de Productos responsivo (2 columnas en teléfono, 3-4 en tablet).
- [ ] Diseñar formulario de creación y edición de producto con validaciones completas.
- [ ] Pruebas unitarias y de integración.

---

## FASE 5 — PROVEEDORES
- [ ] Implementar UseCases para CRUD de Proveedores.
- [ ] Implementar ViewModel de Proveedores.
- [ ] Diseñar listado, buscador y formulario de edición de proveedores.
- [ ] Implementar vista de historial de compras vinculadas por proveedor.
- [ ] Pruebas unitarias.

---

## FASE 6 — INVENTARIO BASE Y KARDEX
- [ ] Implementar UseCase de inventario inicial (`INITIAL`).
- [ ] Implementar repositorio y UseCase de consulta de `InventoryStock` por almacén.
- [ ] Implementar UseCase de registro atómico en `InventoryMovement`.
- [ ] Implementar ViewModel de Existencias con filtros (todos, agotados, bajo stock, disponibles).
- [ ] Diseñar pantalla de Existencias según guía visual.
- [ ] Diseñar pantalla de Kardex por producto con filtrado por rango de fechas.
- [ ] Implementar modal de detalle de movimiento con navegación al documento origen.
- [ ] Implementar control de stock negativo según configuración.
- [ ] Pruebas unitarias exhaustivas de invariantes de stock.

---

## FASE 7 — ENTRADAS DE INVENTARIO Y COMPRAS
- [ ] Implementar módulo de Entrada Manual de Inventario (`StockEntry`).
- [ ] Implementar módulo de Compra formal con proveedor (`Purchase`).
- [ ] Implementar algoritmo transaccional de Costo Promedio Ponderado (CPP).
- [ ] Crear UseCase atómico `RegisterPurchaseUseCase` con Room Transaction.
- [ ] Diseñar interfaz de captura de Entrada y Compra.
- [ ] Validar actualización inmediata en Kardex y Costo Promedio.
- [ ] Pruebas unitarias financieras y de concurrencia.

---

## FASE 8 — AJUSTES DE INVENTARIO Y MERMAS
- [ ] Implementar gestión de motivos de ajuste (`InventoryAdjustmentReason`).
- [ ] Implementar UseCase atómico de Ajuste de Inventario (Entrada / Salida).
- [ ] Registrar adecuadamente movimientos (`DAMAGED`, `LOSS`, `EXPIRED`, `INTERNAL_USE`, `ADJUSTMENT_IN/OUT`).
- [ ] Diseñar pantalla de Nuevo Ajuste con validaciones de motivo y notas obligatorias.
- [ ] Pruebas de consistencia de stock tras ajuste.

---

## FASE 9 — CONTEO FÍSICO DE INVENTARIO
- [ ] Implementar ciclo de vida de `InventoryCount` (DRAFT, IN_PROGRESS, COMPLETED, CANCELLED).
- [ ] Implementar cálculo de discrepancias entre stock del sistema y stock contado.
- [ ] Implementar generación automática de movimientos compensatorios (`PHYSICAL_COUNT_IN`, `PHYSICAL_COUNT_OUT`).
- [ ] Diseñar pantalla de Conteo Físico con modo revisión antes de aplicar.
- [ ] Pruebas unitarias del proceso de confirmación de conteo.

---

## FASE 10 — CLIENTES Y CRÉDITO
- [ ] Implementar UseCases para CRUD de Clientes.
- [ ] Implementar validación de RNC/Cédula, límite de crédito y control de balances.
- [ ] Diseñar listado, buscador y formulario de clientes.
- [ ] Diseñar vista de Estado de Cuenta del cliente con historial transaccional.
- [ ] Pruebas unitarias.

---

## FASE 11 — RUTAS COMERCIALES Y VISITAS
- [ ] Implementar CRUD de Rutas comerciales.
- [ ] Asignar clientes a rutas con orden de visita y días de atención.
- [ ] Registrar y visualizar estado de visitas de la ruta activa.
- [ ] Diseñar pantallas de administración de rutas y agenda de visitas.
- [ ] Pruebas unitarias.

---

## FASE 12 — INVENTARIO DE RUTA Y CUADRE
- [ ] Implementar documento de Carga de Ruta (`RouteLoad` y `RouteLoadItem`).
- [ ] Implementar transferencia atómica: Almacén Principal (`TRANSFER_OUT`) -> Ruta (`TRANSFER_IN`).
- [ ] Configurar stock independiente por `locationId = routeId`.
- [ ] Implementar proceso de Retorno de Ruta (`ROUTE_RETURN`) hacia almacén principal.
- [ ] Diseñar pantalla de Cuadre de Inventario de Ruta (Cargado vs. Vendido vs. Mermas vs. Retornado vs. Diferencia).
- [ ] Pruebas unitarias del flujo completo de ruta.

---

## FASE 13 — PUNTO DE VENTA (POS)
- [ ] Implementar arquitectura reactiva del Carrito de Compras en memoria con guardado de borrador.
- [ ] Implementar selector de cliente (Cliente registrado / Consumidor final).
- [ ] Diseñar pantalla POS Mobile (Grid 2 columnas + BottomSheet / Drawer de Carrito).
- [ ] Diseñar pantalla POS Tablet (NavigationRail + Grid 4 columnas + Panel lateral de Carrito fijo).
- [ ] Implementar cálculo en tiempo real de Subtotal, Descuentos e Impuestos.
- [ ] Implementar captura obligatoria de `unitCostSnapshot` en cada `SaleItem`.
- [x] Implementar transacción atómica de venta (validar stock -> Sale -> SaleItems -> Movimientos -> Stock -> caja de efectivo opcional).
- [ ] Integrar pagos/crédito y pruebas de venta a crédito en el caso de uso comercial.

---

## FASE 14 — COBROS Y CUENTAS POR COBRAR
- [x] Implementar UseCase atómico de registro de cobros (`Payment`, `PaymentAllocation`, saldo de venta y caja de efectivo opcional).
- [ ] Permitir abonos parciales o cancelación total de facturas pendientes.
- [ ] Actualizar balance del cliente de forma transaccional.
- [ ] Diseñar pantalla de Cuentas por Cobrar y Cobro Rápido.
- [ ] Pruebas unitarias de actualización de balances.

---

## FASE 15 — DEVOLUCIONES
- [ ] Implementar módulo de Devolución de Ventas (`Return` y `ReturnItem`).
- [ ] Incluir selector explícito: "¿El producto vuelve al inventario?" (Sí -> `SALE_RETURN` / No -> Merma o sin movimiento de stock).
- [ ] Manejar ajuste financiero (reembolso en efectivo o nota de crédito al balance del cliente).
- [ ] Implementar Devolución a Proveedor (`PURCHASE_RETURN`).
- [ ] Pruebas unitarias de integridad de inventario y saldos.

---

## FASE 16 — IMPRESIÓN TÉRMICA BLUETOOTH
- [ ] Implementar `BluetoothPrinterService` para conexión SPP con impresoras térmicas ESC/POS.
- [ ] Diseñar formateador de recibos para anchos de 58 mm y 80 mm.
- [ ] Generar tickets para: Factura de Venta, Recibo de Cobro, Comprobante de Carga de Ruta y Cierre de Caja.
- [ ] Implementar pantalla de selección y prueba de impresora Bluetooth en Ajustes.

---

## FASE 17 — DASHBOARD PRINCIPAL
- [ ] Implementar UseCase de agregación de métricas en tiempo real:
  - Ventas de hoy.
  - Cobros del día.
  - Total cuentas por cobrar.
  - Conteo de productos con stock bajo y agotados.
  - Valorización total de inventario a costo promedio.
  - Métricas de visitas de ruta.
- [ ] Diseñar Dashboard responsivo siguiendo fielmente el estilo visual de referencia.

---

## FASE 18 — REPORTES
- [ ] Diseñar y calcular reportes analíticos:
  - Ventas por período y por vendedor/ruta.
  - Utilidad bruta real (Ventas - Costo Snapshot).
  - Inventario actual y valorizado.
  - Kardex exportable.
  - Rotación de inventario y productos más vendidos.
  - Cuentas por cobrar y antigüedad de saldos.
  - Cuadre y liquidación de rutas.
- [ ] Implementar exportación a PDF y CSV con Storage Access Framework.

---

## FASE 19 — SISTEMA DE BACKUPS Y RESTAURACIÓN
- [ ] Implementar generador de archivo `.zip` (`BSPOS_BACKUP_<timestamp>.zip`) conteniendo:
  - `database/bspos.db` (checkpoint WAL previo).
  - `images/products/` (fotografías y thumbnails).
  - `config/settings.json`.
  - `metadata.json` (versión app, versión esquema, timestamp, hash SHA-256).
- [ ] Implementar WorkManager periódico para copias automáticas con política de retención (7 diarios, 4 semanales, 3 mensuales).
- [ ] Implementar flujo seguro de restauración con validación previa de integridad y creación de respaldo de seguridad antes de sustituir la base de datos activa.
- [ ] Pruebas unitarias de empaquetado y descompresión.

---

## FASE 20 — AJUSTES E IDENTIDAD BSOLUTIONS.DEV
- [ ] Configuración general del negocio (nombre comercial, RNC/cédula, dirección, teléfono, pie de factura).
- [ ] Parámetros de inventario (permitir venta sin existencia, alertas de stock mínimo).
- [ ] Ajustes de apariencia (Modo Claro, Oscuro, Sistema).
- [ ] Pantalla "Acerca de":
  - Nombre y versión del producto.
  - Leyenda obligatoria: "Creada por BSolutions.Dev".
  - Logotipo oficial inalterado de BSolutions.Dev.
  - Enlaces oficiales configurables (`BrandLinks.WEBSITE`, `BrandLinks.INSTAGRAM`).
  - Información de licencia de 1 dispositivo por pago único.

---

## FASE 21 — RESPONSIVE DESIGN Y MULTIDISPOSITIVO
- [ ] Validar en emuladores y previews Compact (360dp, 400dp).
- [ ] Validar en Medium (600dp) y Expanded (800dp+).
- [ ] Probar orientación Portrait y Landscape.
- [ ] Verificar ausencia de textos cortados, tap targets pequeños o márgenes rotos.

---

## FASE 22 — UX POLISH Y ACCESIBILIDAD
- [ ] Configurar transiciones fluidas y microinteracciones de feedback háptico.
- [ ] Implementar componentes de estado vacío informativos para cada sección.
- [ ] Revisión de contraste de colores y soporte de accesibilidad (TalkBack, font scaling).

---

## FASE 23 — QA INTEGRAL Y PRUEBAS FINALES
- [ ] Ejecutar matriz completa de pruebas para los Casos Críticos A-G.
- [ ] Validar todas las invariantes matemáticas de stock y finanzas.
- [ ] Prueba de rendimiento con dataset simulado de 5,000 productos y 20,000 movimientos.
- [ ] Preparación final para distribución y entrega.
