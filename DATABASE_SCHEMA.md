# BSPOS — ESQUEMA DETALLADO DE BASE DE DATOS LOCAL (ROOM)

La base de datos SQLite administrada por Room es la única fuente de verdad operativa de **BSPOS**.
Todas las tablas implementan claves primarias basadas en `UUID` (`TEXT`), timestamps inmutables en milisegundos (`INTEGER`), valores monetarios expresados en centavos enteros (`INTEGER / Long`) para evitar cualquier error de coma flotante, e índices estratégicos sobre claves foráneas y campos de filtrado recurrente.

**Estado implementado (2026-09-20):** `AppDatabase` versión 5 contiene `categories`, `units_of_measure`, `products`, `suppliers`, `inventory_stock`, `inventory_movements`, `inventory_adjustment_reasons`, `stock_entries`, `stock_entry_items`, `purchases`, `purchase_items`, `inventory_counts`, `inventory_count_items`, `customers`, `routes`, `route_customers`, `route_loads` y `route_load_items`. Las demás secciones describen el esquema objetivo pendiente de implementación. Los esquemas efectivos generados por Room están en `app/schemas/com.example.bspos.data.local.AppDatabase/{1,2,3,4,5}.json`.

Los IDs se expresan como `UUID` en Kotlin y se convierten explícitamente a `TEXT`. Las marcas de tiempo se expresan como `Instant` y se conservan con precisión de milisegundos. Los montos se almacenan directamente como `Long` en centavos. Los cambios posteriores a v5 requieren incremento de versión, migración que preserve los datos y su prueba; no se utiliza `fallbackToDestructiveMigration`.

---

## 1. CATÁLOGO Y CONFIGURACIÓN BÁSICA

### 1.1 `categories`
Almacena las clasificaciones de productos.
```sql
CREATE TABLE IF NOT EXISTS `categories` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `name` TEXT NOT NULL,
    `description` TEXT,
    `icon` TEXT NOT NULL DEFAULT 'category_default',
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `is_active` INTEGER NOT NULL DEFAULT 1,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `deleted_at` INTEGER
);
CREATE INDEX `idx_categories_is_active` ON `categories` (`is_active`);
```

### 1.2 `units_of_measure`
Unidades físicas (Unidad, Caja, Libra, Kilogramo, Galón, etc.).
```sql
CREATE TABLE IF NOT EXISTS `units_of_measure` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `name` TEXT NOT NULL,
    `abbreviation` TEXT NOT NULL,
    `is_active` INTEGER NOT NULL DEFAULT 1,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `deleted_at` INTEGER
);
```

### 1.3 `products`
Ficha maestra del producto. **Nota:** No contiene la existencia operativa directa; esta se consulta en `inventory_stock`.
```sql
CREATE TABLE IF NOT EXISTS `products` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `name` TEXT NOT NULL,
    `internal_code` TEXT NOT NULL,
    `barcode` TEXT,
    `category_id` TEXT NOT NULL,
    `unit_id` TEXT NOT NULL,
    `description` TEXT,
    `sale_price` INTEGER NOT NULL,        -- Expresado en centavos (ej: RD$35.00 = 3500)
    `wholesale_price` INTEGER,            -- Precio por mayor en centavos
    `average_cost` INTEGER NOT NULL DEFAULT 0, -- Costo promedio ponderado en centavos
    `last_purchase_cost` INTEGER NOT NULL DEFAULT 0, -- Último costo de compra
    `minimum_stock` INTEGER NOT NULL DEFAULT 0,
    `image_path` TEXT,
    `thumbnail_path` TEXT,
    `is_active` INTEGER NOT NULL DEFAULT 1,
    `tracks_expiration` INTEGER NOT NULL DEFAULT 0,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `deleted_at` INTEGER,
    FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (`unit_id`) REFERENCES `units_of_measure`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE UNIQUE INDEX `idx_products_internal_code` ON `products` (`internal_code`);
CREATE INDEX `idx_products_barcode` ON `products` (`barcode`);
CREATE INDEX `idx_products_category_id` ON `products` (`category_id`);
CREATE INDEX `idx_products_unit_id` ON `products` (`unit_id`);
CREATE INDEX `idx_products_is_active` ON `products` (`is_active`);
```

---

## 2. PROVEEDORES Y COMPRAS

### 2.1 `suppliers`
Entidad para proveedores comerciales.
```sql
CREATE TABLE IF NOT EXISTS `suppliers` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `name` TEXT NOT NULL,
    `contact_name` TEXT,
    `phone` TEXT,
    `email` TEXT,
    `address` TEXT,
    `tax_id` TEXT,                        -- RNC o Cédula
    `notes` TEXT,
    `is_active` INTEGER NOT NULL DEFAULT 1,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `deleted_at` INTEGER
);
CREATE INDEX `idx_suppliers_tax_id` ON `suppliers` (`tax_id`);
```

### 2.2 `purchases`
Documento formal de compra de mercancía a proveedores.
```sql
CREATE TABLE IF NOT EXISTS `purchases` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `supplier_id` TEXT NOT NULL,
    `document_number` TEXT NOT NULL,      -- Número de comprobante interno
    `invoice_number` TEXT,                -- Factura física del proveedor
    `date` INTEGER NOT NULL,
    `subtotal` INTEGER NOT NULL,          -- Centavos
    `discount` INTEGER NOT NULL DEFAULT 0,
    `tax` INTEGER NOT NULL DEFAULT 0,
    `total` INTEGER NOT NULL,
    `notes` TEXT,
    `status` TEXT NOT NULL DEFAULT 'COMPLETED', -- COMPLETED, CANCELLED
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    FOREIGN KEY (`supplier_id`) REFERENCES `suppliers`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_purchases_supplier_id` ON `purchases` (`supplier_id`);
CREATE INDEX `idx_purchases_date` ON `purchases` (`date`);
```

### 2.3 `purchase_items`
Líneas de detalle de compra.
```sql
CREATE TABLE IF NOT EXISTS `purchase_items` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `purchase_id` TEXT NOT NULL,
    `product_id` TEXT NOT NULL,
    `quantity` INTEGER NOT NULL,
    `unit_cost` INTEGER NOT NULL,         -- Costo de compra unitario en centavos
    `discount` INTEGER NOT NULL DEFAULT 0,
    `tax` INTEGER NOT NULL DEFAULT 0,
    `subtotal` INTEGER NOT NULL,
    FOREIGN KEY (`purchase_id`) REFERENCES `purchases`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_purchase_items_purchase_id` ON `purchase_items` (`purchase_id`);
CREATE INDEX `idx_purchase_items_product_id` ON `purchase_items` (`product_id`);
```

### 2.4 `stock_entries` y `stock_entry_items`
Entradas directas de mercancía al almacén sin requerir proveedor formal.
```sql
CREATE TABLE IF NOT EXISTS `stock_entries` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `document_number` TEXT NOT NULL,
    `supplier_id` TEXT,
    `date` INTEGER NOT NULL,
    `notes` TEXT,
    `status` TEXT NOT NULL DEFAULT 'COMPLETED',
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`supplier_id`) REFERENCES `suppliers`(`id`) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS `stock_entry_items` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `entry_id` TEXT NOT NULL,
    `product_id` TEXT NOT NULL,
    `quantity` INTEGER NOT NULL,
    `unit_cost` INTEGER NOT NULL,
    `subtotal` INTEGER NOT NULL,
    FOREIGN KEY (`entry_id`) REFERENCES `stock_entries`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
```

---

## 3. SUBSISTEMA DE INVENTARIO AUDITABLE

### 3.1 `inventory_stock`
Existencia física agrupada por producto y ubicación (Almacén Central o Ruta de distribución).
```sql
CREATE TABLE IF NOT EXISTS `inventory_stock` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `product_id` TEXT NOT NULL,
    `location_type` TEXT NOT NULL,        -- 'MAIN_WAREHOUSE', 'ROUTE'
    `location_id` TEXT NOT NULL,          -- 'MAIN' o UUID de la ruta
    `quantity` INTEGER NOT NULL DEFAULT 0,
    `reserved_quantity` INTEGER NOT NULL DEFAULT 0,
    `updated_at` INTEGER NOT NULL,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE UNIQUE INDEX `idx_inventory_stock_unique` ON `inventory_stock` (`product_id`, `location_type`, `location_id`);
CREATE INDEX `idx_inventory_stock_product_id` ON `inventory_stock` (`product_id`);
CREATE INDEX `idx_inventory_stock_location` ON `inventory_stock` (`location_type`, `location_id`);
```

### 3.2 `inventory_movements`
Bitácora inmutable de auditoría (Kardex general). No se borra ni modifica ninguna fila.
```sql
CREATE TABLE IF NOT EXISTS `inventory_movements` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `product_id` TEXT NOT NULL,
    `location_type` TEXT NOT NULL,
    `location_id` TEXT NOT NULL,
    `movement_type` TEXT NOT NULL,        -- INITIAL, PURCHASE, SALE, SALE_RETURN, etc.
    `quantity` INTEGER NOT NULL,          -- Positivo (entrada) o negativo (salida)
    `previous_quantity` INTEGER NOT NULL,
    `new_quantity` INTEGER NOT NULL,
    `unit_cost` INTEGER NOT NULL DEFAULT 0, -- Centavos
    `total_cost` INTEGER NOT NULL DEFAULT 0,
    `reference_type` TEXT,                -- 'SALE', 'PURCHASE', 'STOCK_ENTRY', 'ADJUSTMENT', 'ROUTE_LOAD', 'COUNT'
    `reference_id` TEXT,                  -- UUID del documento origen
    `reason_id` TEXT,                     -- UUID de inventory_adjustment_reasons
    `notes` TEXT,
    `created_at` INTEGER NOT NULL,
    `created_by` TEXT,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_movements_product_created` ON `inventory_movements` (`product_id`, `created_at` DESC);
CREATE INDEX `idx_movements_reference` ON `inventory_movements` (`reference_type`, `reference_id`);
CREATE INDEX `idx_movements_location` ON `inventory_movements` (`location_type`, `location_id`);
```

### 3.3 `inventory_adjustment_reasons`
Catálogo de motivos auditables para ajustes manuales y mermas.
```sql
CREATE TABLE IF NOT EXISTS `inventory_adjustment_reasons` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `name` TEXT NOT NULL,
    `direction` TEXT NOT NULL,            -- 'IN' (Entrada), 'OUT' (Salida), 'BOTH'
    `is_active` INTEGER NOT NULL DEFAULT 1
);
```

### 3.4 `inventory_counts` y `inventory_count_items`
Documentos de conteo físico periódico.
```sql
CREATE TABLE IF NOT EXISTS `inventory_counts` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `started_at` INTEGER NOT NULL,
    `completed_at` INTEGER,
    `notes` TEXT,
    `status` TEXT NOT NULL DEFAULT 'DRAFT' -- 'DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
);

CREATE TABLE IF NOT EXISTS `inventory_count_items` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `count_id` TEXT NOT NULL,
    `product_id` TEXT NOT NULL,
    `system_quantity` INTEGER NOT NULL,
    `physical_quantity` INTEGER NOT NULL,
    `difference` INTEGER NOT NULL,        -- physical_quantity - system_quantity
    FOREIGN KEY (`count_id`) REFERENCES `inventory_counts`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_count_items_count_id` ON `inventory_count_items` (`count_id`);
```

### 3.5 `inventory_lots` (Opcional por producto)
Soporte para trazabilidad por lote y fecha de vencimiento.
```sql
CREATE TABLE IF NOT EXISTS `inventory_lots` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `product_id` TEXT NOT NULL,
    `lot_number` TEXT NOT NULL,
    `expiration_date` INTEGER NOT NULL,
    `quantity` INTEGER NOT NULL,
    `unit_cost` INTEGER NOT NULL,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX `idx_inventory_lots_product` ON `inventory_lots` (`product_id`, `expiration_date` ASC);
```

---

## 4. CLIENTES, RUTAS Y LOGÍSTICA DE DISTRIBUCIÓN

### 4.1 `customers`
Ficha del cliente o comercio afiliado.
```sql
CREATE TABLE IF NOT EXISTS `customers` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `business_name` TEXT NOT NULL,
    `owner_name` TEXT,
    `phone` TEXT,
    `whatsapp` TEXT,
    `address` TEXT,
    `reference` TEXT,
    `tax_id` TEXT,                        -- RNC / Cédula
    `visit_days` TEXT,                    -- Días serializados e.g. "LUN,MIE,VIE"
    `credit_limit` INTEGER NOT NULL DEFAULT 0, -- Centavos (0 = sin límite o sin crédito)
    `balance` INTEGER NOT NULL DEFAULT 0, -- Saldo adeudado actual en centavos
    `latitude` REAL,
    `longitude` REAL,
    `notes` TEXT,
    `is_active` INTEGER NOT NULL DEFAULT 1,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `deleted_at` INTEGER
);
CREATE INDEX `idx_customers_balance` ON `customers` (`balance`);
```

### 4.2 `routes` y `route_customers`
Rutas comerciales de reparto y preventa.
```sql
CREATE TABLE IF NOT EXISTS `routes` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `name` TEXT NOT NULL,
    `code` TEXT NOT NULL,
    `description` TEXT,
    `is_active` INTEGER NOT NULL DEFAULT 1,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS `route_customers` (
    `route_id` TEXT NOT NULL,
    `customer_id` TEXT NOT NULL,
    `visit_order` INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (`route_id`, `customer_id`),
    FOREIGN KEY (`route_id`) REFERENCES `routes`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`customer_id`) REFERENCES `customers`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE UNIQUE INDEX `idx_route_customers_customer_id` ON `route_customers` (`customer_id`);
CREATE UNIQUE INDEX `idx_route_customers_visit_order` ON `route_customers` (`route_id`, `visit_order`);
```

`route_customers` es la única fuente de verdad para la asignación y orden de visita. Un cliente puede estar sin ruta o en exactamente una ruta. Se omiten `customers.route_id` y `customers.visit_order` para evitar dos representaciones divergentes.

### 4.3 `route_loads` y `route_load_items`
Documento de carga de mercancía despachada a un vehículo de ruta.
```sql
CREATE TABLE IF NOT EXISTS `route_loads` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `route_id` TEXT NOT NULL,
    `date` INTEGER NOT NULL,
    `status` TEXT NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'SETTLED', 'CANCELLED'
    `notes` TEXT,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`route_id`) REFERENCES `routes`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS `route_load_items` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `route_load_id` TEXT NOT NULL,
    `product_id` TEXT NOT NULL,
    `quantity` INTEGER NOT NULL,
    `unit_cost_snapshot` INTEGER NOT NULL,
    FOREIGN KEY (`route_load_id`) REFERENCES `route_loads`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_route_load_items_load` ON `route_load_items` (`route_load_id`);
```

---

## 5. VENTAS, FACTURACIÓN Y CUENTAS POR COBRAR

### 5.1 `sales`
Encabezado de la factura o ticket de venta.
```sql
CREATE TABLE IF NOT EXISTS `sales` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `invoice_number` TEXT NOT NULL,       -- Folio correlativo
    `customer_id` TEXT,                   -- NULL si es Consumidor Final
    `route_id` TEXT,                      -- NULL si fue venta en almacén central
    `date` INTEGER NOT NULL,
    `subtotal` INTEGER NOT NULL,          -- Centavos
    `discount` INTEGER NOT NULL DEFAULT 0,
    `tax` INTEGER NOT NULL DEFAULT 0,
    `total` INTEGER NOT NULL,
    `payment_type` TEXT NOT NULL,         -- 'CASH', 'CREDIT', 'CARD', 'TRANSFER', 'MIXED'
    `paid_amount` INTEGER NOT NULL DEFAULT 0,
    `pending_amount` INTEGER NOT NULL DEFAULT 0,
    `status` TEXT NOT NULL DEFAULT 'COMPLETED', -- 'COMPLETED', 'CANCELLED'
    `notes` TEXT,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    FOREIGN KEY (`customer_id`) REFERENCES `customers`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (`route_id`) REFERENCES `routes`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE UNIQUE INDEX `idx_sales_invoice_number` ON `sales` (`invoice_number`);
CREATE INDEX `idx_sales_customer_id` ON `sales` (`customer_id`);
CREATE INDEX `idx_sales_route_id` ON `sales` (`route_id`);
CREATE INDEX `idx_sales_date` ON `sales` (`date`);
CREATE INDEX `idx_sales_status` ON `sales` (`status`);
```

### 5.2 `sale_items`
Líneas de factura. **Regla de oro:** Cada fila contiene `unit_cost_snapshot` para cálculo inmutable de la utilidad histórica.
```sql
CREATE TABLE IF NOT EXISTS `sale_items` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `sale_id` TEXT NOT NULL,
    `product_id` TEXT NOT NULL,
    `quantity` INTEGER NOT NULL,
    `unit_price` INTEGER NOT NULL,        -- Precio de venta cobrado
    `unit_cost_snapshot` INTEGER NOT NULL,-- Costo promedio al momento de la venta
    `discount` INTEGER NOT NULL DEFAULT 0,
    `tax` INTEGER NOT NULL DEFAULT 0,
    `subtotal` INTEGER NOT NULL,
    FOREIGN KEY (`sale_id`) REFERENCES `sales`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_sale_items_sale_id` ON `sale_items` (`sale_id`);
CREATE INDEX `idx_sale_items_product_id` ON `sale_items` (`product_id`);
```

### 5.3 `payments` y `payment_allocations`
Registro de cobros aplicados a clientes y facturas a crédito.
```sql
CREATE TABLE IF NOT EXISTS `payments` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `receipt_number` TEXT NOT NULL,
    `customer_id` TEXT NOT NULL,
    `route_id` TEXT,
    `date` INTEGER NOT NULL,
    `amount` INTEGER NOT NULL,            -- Total cobrado en centavos
    `payment_method` TEXT NOT NULL,       -- 'CASH', 'TRANSFER', 'CHECK', 'CARD'
    `reference` TEXT,                     -- Número de cheque o confirmación bancaria
    `notes` TEXT,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`customer_id`) REFERENCES `customers`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (`route_id`) REFERENCES `routes`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE UNIQUE INDEX `idx_payments_receipt_number` ON `payments` (`receipt_number`);
CREATE INDEX `idx_payments_customer_id` ON `payments` (`customer_id`);
CREATE INDEX `idx_payments_route_id` ON `payments` (`route_id`);
CREATE INDEX `idx_payments_date` ON `payments` (`date`);

CREATE TABLE IF NOT EXISTS `payment_allocations` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `payment_id` TEXT NOT NULL,
    `sale_id` TEXT NOT NULL,
    `allocated_amount` INTEGER NOT NULL,  -- Porción de dinero aplicada a esta factura
    FOREIGN KEY (`payment_id`) REFERENCES `payments`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`sale_id`) REFERENCES `sales`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_allocations_sale_id` ON `payment_allocations` (`sale_id`);
CREATE UNIQUE INDEX `idx_allocations_unique` ON `payment_allocations` (`payment_id`, `sale_id`);
```

### 5.4 `returns` y `return_items`
Devoluciones sobre ventas.
```sql
CREATE TABLE IF NOT EXISTS `returns` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `return_number` TEXT NOT NULL,
    `sale_id` TEXT NOT NULL,
    `customer_id` TEXT,
    `date` INTEGER NOT NULL,
    `total_amount` INTEGER NOT NULL,
    `reason` TEXT,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`sale_id`) REFERENCES `sales`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE UNIQUE INDEX `idx_returns_return_number` ON `returns` (`return_number`);
CREATE INDEX `idx_returns_sale_id` ON `returns` (`sale_id`);
CREATE INDEX `idx_returns_customer_id` ON `returns` (`customer_id`);
CREATE INDEX `idx_returns_date` ON `returns` (`date`);

CREATE TABLE IF NOT EXISTS `return_items` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `return_id` TEXT NOT NULL,
    `sale_item_id` TEXT NOT NULL,
    `product_id` TEXT NOT NULL,
    `quantity` INTEGER NOT NULL,
    `refund_price` INTEGER NOT NULL,
    `unit_cost_snapshot` INTEGER NOT NULL,
    `restock_inventory` INTEGER NOT NULL DEFAULT 1, -- 1 si vuelve al inventario, 0 si se descarta (daño)
    FOREIGN KEY (`return_id`) REFERENCES `returns`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (`sale_item_id`) REFERENCES `sale_items`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (`product_id`) REFERENCES `products`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX `idx_return_items_return_id` ON `return_items` (`return_id`);
CREATE INDEX `idx_return_items_sale_item_id` ON `return_items` (`sale_item_id`);
CREATE INDEX `idx_return_items_product_id` ON `return_items` (`product_id`);
CREATE UNIQUE INDEX `idx_return_items_unique` ON `return_items` (`return_id`, `sale_item_id`);
```

---

## 6. SESIONES DE CAJA Y AUDITORÍA DE BACKUP

### 6.1 `cash_sessions` y `cash_movements`
Apertura, cuadre y cierre de turno de caja.
```sql
CREATE TABLE IF NOT EXISTS `cash_sessions` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `opened_at` INTEGER NOT NULL,
    `closed_at` INTEGER,
    `opening_amount` INTEGER NOT NULL,
    `expected_amount` INTEGER,
    `actual_amount` INTEGER,
    `difference` INTEGER,
    `status` TEXT NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'CLOSED'
    `notes` TEXT
);
CREATE INDEX `idx_cash_sessions_status` ON `cash_sessions` (`status`);
CREATE INDEX `idx_cash_sessions_opened_at` ON `cash_sessions` (`opened_at`);

CREATE TABLE IF NOT EXISTS `cash_movements` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `session_id` TEXT NOT NULL,
    `type` TEXT NOT NULL,                 -- 'INCOME', 'EXPENSE', 'SALE', 'PAYMENT_COLLECTION'
    `amount` INTEGER NOT NULL,
    `reason` TEXT NOT NULL,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`session_id`) REFERENCES `cash_sessions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX `idx_cash_movements_session_id` ON `cash_movements` (`session_id`);
CREATE INDEX `idx_cash_movements_created_at` ON `cash_movements` (`created_at`);
```

### 6.2 `backup_history`
Registro de copias de seguridad locales y exportaciones.
```sql
CREATE TABLE IF NOT EXISTS `backup_history` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `file_name` TEXT NOT NULL,
    `file_path` TEXT NOT NULL,
    `size_bytes` INTEGER NOT NULL,
    `type` TEXT NOT NULL,                 -- 'AUTO', 'MANUAL', 'PRE_RESTORE'
    `status` TEXT NOT NULL,               -- 'SUCCESS', 'FAILED'
    `sha256_hash` TEXT,
    `created_at` INTEGER NOT NULL
);
CREATE INDEX `idx_backup_history_created_at` ON `backup_history` (`created_at`);
CREATE INDEX `idx_backup_history_status` ON `backup_history` (`status`);
```
