# BSPOS — PLAN DE PROYECTO MAESTRO

## 1. VISIÓN Y ALCANCE DEL PRODUCTO

**BSPOS** es una solución comercial de Punto de Venta (POS), Facturación, Gestión de Inventario Auditable, Clientes con Cuentas por Cobrar y Rutas Comerciales de Distribución para dispositivos Android (teléfonos y tablets).

- **Desarrollador Oficial:** BSolutions.Dev
- **Marca y Créditos:** "Creada por BSolutions.Dev" (con enlaces configurables a sitio web e Instagram oficial).
- **Modelo Comercial:** BSPOS Offline — Licencia de pago único por dispositivo (Lifetime), 100% autónomo, sin mensualidad obligatoria ni dependencia de servidor backend en su versión inicial.
- **Preparación de Futuro:** Arquitectura lista para sincronización con BSPOS Cloud en futuras versiones (Clean Architecture, Repositorios con abstracción LocalDataSource y RemoteDataSource, identificadores UUID, timestamps universales y soft-deletes).

---

## 2. METODOLOGÍA DE DESARROLLO Y FASES DE EJECUCIÓN

El desarrollo se organiza en 24 fases rigurosas e incrementales. Cada fase debe cumplir con la **Definition of Done (DoD)** antes de avanzar a la siguiente.

**Estado de ejecución (2026-09-20):** fase 2 en progreso; primer bloque de persistencia de catálogo y proveedores implementado y probado. Consultar `TODO.md` y `DEVELOPMENT_STATUS.md` para el alcance verificado.

### Fase 0: Análisis, Especificación y Arquitectura
- Documentación técnica exhaustiva (`PROJECT_PLAN.md`, `TODO.md`, `ARCHITECTURE.md`, `DATABASE_SCHEMA.md`, `INVENTORY_RULES.md`, `COSTING_RULES.md`, `UI_GUIDELINES.md`, `DECISIONS.md`).
- Modelado formal de datos, relaciones de inventario, flujo de compras, costo promedio ponderado, inventario de ruta y transacciones críticas.
- Validación con el usuario antes de programar código de negocio.

### Fase 1: Proyecto Base y Fundación Técnica
- Configuración de `libs.versions.toml`, Gradle y plugins necesarios (Hilt, Room, KSP, Compose Compiler, Navigation Compose, WorkManager, DataStore, Coil).
- Definición del Design System base (Colors, Typography, Spacing, Shapes, Elevation, Icons).
- Estructura de paquetes Clean Architecture (`core`, `data`, `domain`, `presentation`).
- Scaffold responsivo y adaptativo (`WindowSizeClass` para Compact, Medium, Expanded).
- Verificación: `BUILD SUCCESSFUL`.

### Fase 2: Database Core & Migrations (Fase Actual)
- Definición de todas las entidades Room con claves primarias UUID, tipos monetarios en centavos (`Long`), índices optimizados y foreign keys con integridad referencial.
- Configuración de `AppDatabase`, TypeConverters (`Instant`, `UUID`, enums) y DAOs transaccionales.
- Repositorios iniciales con mappers bidireccionales Entity <-> Domain.
- Tests unitarios de Room en memoria para verificar integridad referencial y transacciones.

### Fase 3: Categorías y Unidades de Medida
- CRUD completo de Categorías (nombre, descripción, icono, orden, isActive).
- CRUD completo de Unidades de Medida (nombre, abreviatura, isActive).
- UI responsiva para selección y administración.
- Validación de que no existan categorías ni unidades hardcodeadas.

### Fase 4: Catálogo de Productos y Manejo de Imágenes
- CRUD de Productos (código interno, código de barras, categoría, unidad, precios venta/mayorista, costos, stock mínimo).
- Subsistema de almacenamiento de imágenes en disco privado (`imagePath`, `thumbnailPath`), redimensión, compresión y eliminación segura del archivo anterior.
- Buscador reactivo por nombre, SKU y código de barras. Filtros por categoría y estado.

### Fase 5: Proveedores
- CRUD de Proveedores (nombre, contacto, teléfono, RNC/cédula, dirección, notas, isActive).
- Historial de compras y abastecimientos vinculados.

### Fase 6: Inventario Base y Kardex
- Implementación del principio de inventario auditable: `InventoryStock` por ubicación (`MAIN_WAREHOUSE`, `ROUTE`).
- Registro atómico de `InventoryMovement` para cada variación.
- Inicialización de stock con trazabilidad (`INITIAL`).
- Alertas de stock bajo y sin stock. Control de stock negativo configurable.
- Pantalla de Kardex por producto con filtros de fechas y navegación al documento origen.

### Fase 7: Entradas de Inventario y Compras
- Entradas manuales (`StockEntry` y `StockEntryItem`) sin proveedor obligatorio.
- Módulo de compras (`Purchase` y `PurchaseItem`) con proveedor, comprobante y desglose de costos.
- Cálculo automático y atómico de Costo Promedio Ponderado (CPP).
- Transacción atómica integral (Compra + Items + Movimientos + Stock + CPP).

### Fase 8: Ajustes de Inventario y Mermas
- Ajustes de inventario con motivos auditables (`InventoryAdjustmentReason`: dañados, rotos, vencidos, robo, error, uso interno).
- Generación estricta de movimientos (`ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `DAMAGED`, `LOSS`, etc.).

### Fase 9: Conteo Físico de Inventario
- Ciclo de conteo físico (`InventoryCount` e `InventoryCountItem`): Borrador -> En Progreso -> Comparación -> Confirmación.
- Cálculo de discrepancias y generación automática de movimientos de ajuste (`PHYSICAL_COUNT_IN`, `PHYSICAL_COUNT_OUT`).

### Fase 10: Clientes y Crédito
- CRUD de clientes (nombre comercial, propietario, RNC/cédula, teléfono, WhatsApp, dirección, límites de crédito, balance).
- Estado de cuenta y resumen financiero.

### Fase 11: Rutas Comerciales y Visitas
- Creación y administración de Rutas de distribución.
- Asignación de clientes a rutas con orden de visita y días designados.
- Registro de estado de visitas comerciales.

### Fase 12: Inventario de Ruta y Cuadre de Carga
- Documento de Carga de Ruta (`RouteLoad` y `RouteLoadItem`).
- Transferencia atómica de stock: `TRANSFER_OUT` en almacén principal -> `TRANSFER_IN` en almacén de ruta.
- Ventas y devoluciones afectando exclusivamente el stock de la ruta activa.
- Proceso de Retorno de Ruta (`ROUTE_RETURN`) y conciliación matemática (Cargado - Vendido - Dañado - Retornado = Diferencia).

### Fase 13: Módulo de Punto de Venta (POS)
- Pantalla POS para teléfono (Grid 2 columnas, drawer inferior) y tablet (Grid 4 columnas, navigation rail, panel lateral de carrito).
- Selección de cliente (o Consumidor Final).
- Búsqueda en tiempo real y escaneo de código de barras.
- Gestión de carrito reactivo, descuentos por porcentaje o monto, impuestos.
- Captura inmutable de costo histórico (`unitCostSnapshot`) en cada `SaleItem`.
- Transacción completa de venta: Stock validation -> Sale -> SaleItems -> Movements -> Stock update -> Payment / Receivable.

### Fase 14: Cobros y Cuentas por Cobrar
- Registro de cobros a facturas a crédito (`Payment` y `PaymentAllocation`).
- Recibos de cobro y actualización del balance del cliente.

### Fase 15: Devoluciones (Ventas y Compras)
- Devolución de ventas (`Return` y `ReturnItem`): opción de reintegrar o no al stock según estado físico del producto, notas de crédito o reembolso.
- Devolución a proveedores (`PURCHASE_RETURN`) con reducción de stock y registro contable.

### Fase 16: Impresión Térmica Bluetooth (58mm / 80mm)
- Integración de `BluetoothPrinterService` para impresoras térmicas ESC/POS.
- Formatos de impresión de Factura, Recibo de Cobro, Cierre de Caja y Carga de Ruta.

### Fase 17: Dashboard Comercial e Inventario
- KPIs en tiempo real: Ventas del día, Cobros, Cuentas por Cobrar, Productos con Stock Bajo, Agotados, Valorización de Inventario.
- Acciones rápidas adaptativas para teléfono y tablet.

### Fase 18: Reportes Financieros, Operativos y Kardex
- Reporte de ventas, utilidad bruta real (calculada con el snapshot de costos), rotación de productos, cobros, créditos y devoluciones.
- Exportación a PDF / CSV compartibles mediante Storage Access Framework.

### Fase 19: Backup y Restauración Autónoma
- Sistema de respaldo integral: empaquetado `.zip` conteniendo SQLite `bspos.db`, imágenes locales de productos, configuración `settings.json` y `metadata.json`.
- Copias de seguridad automáticas en segundo plano con WorkManager (política de retención 7 diarios, 4 semanales, 3 mensuales).
- Proceso de restauración seguro con validación previa de integridad y respaldo de emergencia automático.

### Fase 20: Configuración Global e Identidad BSolutions.Dev
- Ajustes de negocio (RNC, nombre, teléfono, logo de empresa).
- Parámetros operativos (permitir venta sin stock, control de costos, unidades, rutas).
- Pantalla Acerca de oficial con logo original inalterado de BSolutions.Dev, enlaces institucionales y licencia local.

### Fase 21: Responsive Design y Pruebas Multidispositivo
- Pruebas exhaustivas en tamaños de pantalla Compact (360dp, 400dp), Medium (600dp) y Expanded (800dp+), portrait y landscape.
- Verificación de ausencia de overflows o elementos truncados.

### Fase 22: Polish de UI/UX, Accesibilidad y Microinteracciones
- Transiciones fluidas (`AnimatedVisibility`, `AnimatedContent`), empty states enriquecidos con ilustraciones e instrucciones, snackbars y accesibilidad (contrastes, font scale, tap targets).

### Fase 23: QA Integral, Pruebas de Carga y Auditoría
- Batería de pruebas unitarias e instrumentadas.
- Verificación de todas las invariantes matemáticas y financieras.
- Pruebas de estrés con más de 5,000 productos y 20,000 movimientos.

---

## 3. HITOS CLAVE Y CRITERIOS DE ACEPTACIÓN

1. **Hito Base (Fases 0 - 2):** Compilación limpia, base de datos completamente definida con claves foráneas, tipos seguros y repositorios abstractos.
2. **Hito Catálogo e Inventario (Fases 3 - 9):** Ciclo completo de mercancía: Entrada/Compra -> CPP -> Existencia -> Ajuste -> Conteo Físico -> Kardex sin discrepancias.
3. **Hito Comercial y Rutas (Fases 10 - 15):** POS interactivo de alto rendimiento en teléfono y tablet, Carga de Ruta -> Venta en Calle -> Retorno y Cuadre, Cobros a Crédito y Devoluciones.
4. **Hito Operativo y Cierre (Fases 16 - 24):** Impresión Bluetooth térmica funcional, Backups automáticos en ZIP con imágenes, Dashboard KPI preciso y Producto listo para distribución comercial.
