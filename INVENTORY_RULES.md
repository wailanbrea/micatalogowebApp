# BSPOS — REGLAS MAESTRAS DE INVENTARIO Y AUDITORÍA

## 1. EL PRINCIPIO FUNDAMENTAL DE AUDITORÍA

En **BSPOS**, el inventario es un subsistema contable de doble partida simplificado, riguroso y auditable.

> **Regla de Oro:** Está terminantemente prohibido ejecutar una mutación de existencias sin generar simultáneamente un registro inmutable en `inventory_movements`. Jamás existirá una instrucción del tipo `product.stock = X` sin su correspondiente trazabilidad matemática.

Cada unidad de producto en existencia debe tener un origen documentado y verificable. La cantidad actual en cualquier ubicación siempre debe ser demostrable mediante la suma algebraica de todos sus movimientos históricos.

---

## 2. UBICACIONES FÍSICAS DE INVENTARIO (`locationType` / `locationId`)

Para soportar venta en mostrador y distribución en vehículos de ruta, el inventario se segrega por ubicación:

| `locationType` | `locationId` | Descripción Operativa |
| :--- | :--- | :--- |
| `MAIN_WAREHOUSE` | `'MAIN'` | Almacén central del comercio. Aquí ingresan las compras y entradas de proveedores. |
| `ROUTE` | `<UUID de Ruta>` | Vehículo comercial o repartidor en tránsito. Su stock se alimenta de cargas desde el almacén central. |

---

## 3. CATÁLOGO DE TIPOS DE MOVIMIENTO DE INVENTARIO

| Tipo de Movimiento | Signo Físico | Origen / Contexto |
| :--- | :---: | :--- |
| `INITIAL` | `+` (Entrada) | Carga inicial al dar de alta el producto o inventario de apertura. |
| `PURCHASE` | `+` (Entrada) | Recepción de factura de compra de un proveedor formal. |
| `PURCHASE_RETURN`| `-` (Salida) | Devolución física de mercancía averiada o rechazada al proveedor. |
| `SALE` | `-` (Salida) | Despacho por venta directa en mostrador / almacén central. |
| `SALE_RETURN` | `+` (Entrada) | Devolución de un cliente cuyo producto es apto para reventa. |
| `ADJUSTMENT_IN` | `+` (Entrada) | Corrección administrativa positiva (sobrante identificado con motivo). |
| `ADJUSTMENT_OUT` | `-` (Salida) | Corrección administrativa negativa (faltante o error administrativo). |
| `PHYSICAL_COUNT_IN`| `+` (Entrada) | Ajuste por sobrante detectado en un Conteo Físico auditado. |
| `PHYSICAL_COUNT_OUT`|`-` (Salida) | Ajuste por faltante detectado en un Conteo Físico auditado. |
| `ROUTE_LOAD_OUT` | `-` (Salida) | Salida del almacén central para abastecer una ruta de distribución. |
| `ROUTE_LOAD_IN` | `+` (Entrada) | Entrada al inventario móvil de la ruta procedente del almacén central. |
| `ROUTE_SALE` | `-` (Salida) | Venta realizada en la calle desde el stock del vehículo de ruta. |
| `ROUTE_RETURN` | `+` / `-` | Liquidación al final de la jornada: salida de ruta y reingreso a almacén central. |
| `DAMAGED` | `-` (Salida) | Mercancía rota o averiada que se da de baja. |
| `LOSS` | `-` (Salida) | Mercancía extraviada o hurtada. |
| `EXPIRED` | `-` (Salida) | Producto caducado retirado de circulación. |
| `INTERNAL_USE` | `-` (Salida) | Consumo para operaciones internas de la empresa. |
| `TRANSFER_IN` | `+` (Entrada) | Transferencia genérica entrante entre almacenes. |
| `TRANSFER_OUT` | `-` (Salida) | Transferencia genérica saliente entre almacenes. |

---

## 4. INVARIANTE MATEMÁTICA FUNDAMENTAL

Para cualquier producto \(P\) en una ubicación \(L\) en el instante de tiempo \(T\):

$$\text{Stock Actual}(P, L) = \sum_{m \in \text{Movimientos}(P, L)} \text{Cantidad}(m)$$

Donde:
$$\text{Nuevo Stock} = \text{Stock Anterior} + \text{Cantidad Movimiento}$$

Adicionalmente, en cada registro de `inventory_movements` se almacenan obligatoriamente:
- `previous_quantity`: Existencia física inmediatamente anterior a la operación.
- `quantity`: Variación (+/-).
- `new_quantity`: Resultado final después de la operación.
- `unit_cost`: Costo unitario promedio ponderado vigente al momento de la transacción.
- `total_cost`: `quantity * unit_cost`.

---

## 5. REGLAS POR MÓDULO OPERATIVO

### 5.1 Inventario Inicial
- Al registrar un nuevo producto, el operador puede ingresar una existencia inicial con su costo unitario.
- Se genera una transacción Room que inserta el registro en `inventory_stock` e inserta un movimiento `INITIAL` con `previous_quantity = 0`, `new_quantity = cantidad_inicial`.
- Se establece `average_cost = costo_ingresado` y `last_purchase_cost = costo_ingresado`.

### 5.2 Entradas Manuales vs. Compras
- **Entrada Manual (`StockEntry`):** Recepción de mercancía de emergencia o sin comprobante fiscal. El proveedor es opcional. Genera movimiento `PURCHASE` o `ADJUSTMENT_IN`.
- **Compra Formal (`Purchase`):** Vinculada a un proveedor registrado, con número de factura física, desglose de impuestos y descuentos.
- Ambas actualizan el **Costo Promedio Ponderado** y el stock físico de forma atómica en `MAIN_WAREHOUSE`.

### 5.3 Ajustes de Inventario y Mermas
- No se permite registrar un ajuste sin asociar un motivo obligatorio (`InventoryAdjustmentReason`).
- Toda merma (dañados, rotos, vencidos, robo) genera su movimiento tipificado (`DAMAGED`, `LOSS`, `EXPIRED`), descontando stock y capturando el costo de la pérdida.

### 5.4 Conteo Físico
1. **DRAFT / IN_PROGRESS:** El operador cuenta los productos en físico en el local.
2. **Comparación:** Se calcula `difference = physical_quantity - system_quantity`.
3. **Confirmación:**
   - Si `difference > 0`: Se inserta movimiento `PHYSICAL_COUNT_IN` por la diferencia positiva.
   - Si `difference < 0`: Se inserta movimiento `PHYSICAL_COUNT_OUT` por la diferencia negativa.
   - Si `difference == 0`: No se altera el inventario.
4. El conteo pasa a estado `COMPLETED` y queda congelado para auditoría.

### 5.5 Kardex y Navegabilidad
- La pantalla de Kardex muestra el historial cronológico completo de un producto.
- Cada línea de Kardex permite tocar y abrir un diálogo/pantalla con los metadatos completos y un enlace directo al documento de origen (Factura de venta, Compra, Carga de Ruta, Ajuste).

### 5.6 Control de Stock Mínimo, Agotados y Stock Negativo
- **Estado Normal:** `quantity > minimum_stock`.
- **Stock Bajo:** `0 < quantity <= minimum_stock` (badge naranja en dashboard y POS).
- **Sin Stock:** `quantity == 0` (badge rojo).
- **Stock Negativo:** `quantity < 0`.
- **Parámetro Operativo:** `allow_negative_stock` (Configurable en Ajustes, por defecto `false`).
  - Si es `false`: El POS y los despachos bloquean la operación si `solicitado > disponible`.
  - Si es `true`: El POS permite la venta, generando un saldo negativo que deberá compensarse con entradas posteriores.

---

## 6. LOGÍSTICA DE RUTAS COMERCIALES

El ciclo de inventario en ruta asegura que la mercancía móvil esté perfectamente controlada:

```text
Almacén Central (MAIN)
       │
       ▼ [Carga de Ruta: ROUTE_LOAD_OUT en MAIN / ROUTE_LOAD_IN en RUTA]
Vehículo de Ruta (ROUTE_X)
       │
       ├─► [Venta a Clientes: ROUTE_SALE en RUTA]
       ├─► [Devolución de Clientes: SALE_RETURN en RUTA]
       ├─► [Mercancía averiada: DAMAGED en RUTA]
       │
       ▼ [Retorno de Ruta al finalizar la jornada]
Conciliación Matemática (Liquidación):
   Cargado Inicial
 - Ventas Realizadas
 + Devoluciones Clientes
 - Mermas Reportadas
 = Stock Teórico a Retornar
       │
       ▼ Comparación con Stock Físico Devuelto
Diferencia = Físico Devuelto - Stock Teórico
       │
       ▼ Reingreso a Almacén Central: ROUTE_RETURN (Stock RUTA = 0, Stock MAIN += Retornado)
```

Si existe discrepancia en el retorno, la diferencia queda registrada como faltante/sobrante atribuido a la liquidación de la ruta.

---

## 7. DEVOLUCIONES DE VENTA Y REINTEGRO DE STOCK

Cuando un cliente devuelve una unidad en el POS:
- La UI presenta obligatoriamente la opción: **"¿El producto vuelve al inventario?"**
  - **SÍ:** Se incrementa el stock de la ubicación correspondiente generando un movimiento `SALE_RETURN`.
  - **NO (Producto roto, caducado o inservible):** El stock de producto disponible **NO** se incrementa; en su lugar, se puede generar un registro de merma (`DAMAGED`) con trazabilidad hacia la devolución.
- La devolución ajusta el balance del cliente (si fue a crédito) o genera una nota de crédito / devolución en efectivo.

---

## 8. VALORIZACIÓN FINANCIERA DEL INVENTARIO

> **Regla Crítica:** La valorización de existencias de un producto **NUNCA** se calcula sobre su precio de venta, sino estrictamente sobre su costo promedio ponderado.

$$\text{Valor del Inventario de un Producto} = \text{quantity} \times \text{average\_cost}$$

$$\text{Valor Total del Inventario de la Empresa} = \sum_{p \in \text{Productos}} (\text{quantity}(p) \times \text{average\_cost}(p))$$

---

## 9. VALIDACIÓN FORMAL DE CASOS CRÍTICOS (A - G)

| Caso | Acción | Stock Anterior | Operación | Stock Resultante | Movimiento Registrado |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **A: Inventario Inicial** | Alta de producto | 0 | +50 | **50** | `INITIAL (+50)` |
| **B: Compra** | Recepción de factura | 50 | +20 | **70** | `PURCHASE (+20)` |
| **C: Venta Mostrador** | Factura cliente | 70 | -5 | **65** | `SALE (-5)` |
| **D: Devolución Venta** | Cliente regresa 2 | 65 | +2 | **67** | `SALE_RETURN (+2)` |
| **E: Conteo Físico** | Auditoría física | 67 | -2 | **65** | `PHYSICAL_COUNT_OUT (-2)` |
| **F: Logística Ruta** | Carga a vehículo | 500 (Main) | -100 | **400 (Main) / 100 (Ruta)**| `ROUTE_LOAD_OUT (-100)` / `IN (+100)` |
| | Venta en calle | 100 (Ruta) | -15 | **85 (Ruta)** | `ROUTE_SALE (-15)` |
| | Retorno a almacén| 85 (Ruta) | -85 | **485 (Main) / 0 (Ruta)** | `ROUTE_RETURN (+85 en Main)` |
| **G: Costo Promedio** | Compra 10 a $120 sobre 10 a $100 | 10 | +10 | **20** | Nuevo `average_cost = $110.00` |
