# BSPOS — REGLAS MAESTRAS DE COSTOS, PRECIOS Y FINANZAS

## 1. REPRESENTACIÓN MONETARIA DE ALTA PRECISIÓN

Para eliminar cualquier riesgo de imprecisión por redondeo en coma flotante (`Float` / `Double`):

1. **Moneda Base:** Todas las magnitudes de dinero (precios, costos, subtotales, descuentos, impuestos, pagos, balances) se modelan en el dominio y en la base de datos como enteros de 64 bits (`Long`) que representan **centavos**.
   - Ejemplo: `RD$ 35.00` se almacena como `3500L`.
   - Ejemplo: `RD$ 24,750.50` se almacena como `2475050L`.
2. **Cálculos Aritméticos Complejos:** Para prorrateos, promedios ponderados y porcentajes de descuento/impuesto se utiliza `BigDecimal` con escala definida a 4 decimales internos y modo de redondeo `RoundingMode.HALF_EVEN` (redondeo bancario), convirtiendo finalmente al centavo más cercano al persistir.

---

## 2. COSTO PROMEDIO PONDERADO (CPP / WEIGHTED AVERAGE COST)

BSPOS utiliza el método de **Costo Promedio Ponderado Móvil** para valorar el costo real de adquisición de la mercancía.

### 2.1 Fórmula Matemática General
Cuando se recibe una nueva compra o entrada de mercancía para un producto \(P\):

$$\text{Nuevo CPP} = \frac{(\text{Stock Actual} \times \text{CPP Anterior}) + (\text{Cantidad Comprada} \times \text{Costo Unitario de Compra})}{\text{Stock Actual} + \text{Cantidad Comprada}}$$

### 2.2 Tratamiento de Casos Especiales

1. **Stock Actual = 0:**
   - Si no hay existencia previa, el nuevo costo promedio es idéntico al costo unitario de la compra entrante:
     $$\text{Nuevo CPP} = \text{Costo Unitario de Compra}$$
2. **Stock Actual < 0 (Stock Negativo):**
   - Si el sistema permitió ventas sin existencia previa, las unidades compradas primero compensan el déficit sin que el stock negativo distorsione la ponderación de las unidades restantes:
     $$\text{Nuevo CPP} = \text{Costo Unitario de Compra}$$
3. **Cantidad Comprada a Costo 0 (Donaciones o Bonificaciones):**
   - Si se ingresan unidades con costo unitario 0, el nuevo costo promedio disminuye de acuerdo con la fórmula estándar, reflejando el menor costo unitario global.
4. **Ventas y Salidas de Inventario:**
   - Las ventas, mermas, consumos internos y transferencias **NO** alteran el Costo Promedio Ponderado del producto; únicamente reducen la cantidad de stock valorizada.

---

## 3. FOTOGRAFÍA FINANCIERA DEL COSTO DE VENTA (`unitCostSnapshot`)

> **Regla Crítica de Contabilidad Histórica:** Cada registro de línea de factura (`SaleItem`) captura obligatoriamente una copia inmutable del costo promedio ponderado vigente al momento exacto de la emisión de la venta: `unitCostSnapshot`.

### Justificación Técnica:
Si un producto se vende hoy con un costo de `RD$ 20.00` y precio de `RD$ 35.00`, y el próximo mes una nueva compra eleva el costo promedio a `RD$ 28.00`:
- La venta realizada hoy debe seguir reportando un costo de `RD$ 20.00` y una utilidad de `RD$ 15.00`.
- **Prohibición:** Nunca debe recalcularse la utilidad de ventas históricas usando el costo promedio actual del catálogo. El reporte financiero debe basarse única y exclusivamente en el campo `sale_items.unit_cost_snapshot`.

---

## 4. CÁLCULO DE UTILIDAD Y MÁRGENES FINANCIEROS

### 4.1 Utilidad Bruta por Línea de Venta
Para una línea \(i\) de un ticket de venta:

$$\text{Ingreso Neto}_i = (\text{Cantidad}_i \times \text{Precio Unitario}_i) - \text{Descuento}_i$$

$$\text{Costo Total Mercancía}_i = \text{Cantidad}_i \times \text{unit\_cost\_snapshot}_i$$

$$\text{Utilidad Bruta}_i = \text{Ingreso Neto}_i - \text{Costo Total Mercancía}_i$$

### 4.2 Margen Bruto Porcentual
$$\text{Margen Bruto } (\%) = \left( \frac{\text{Utilidad Bruta}}{\text{Ingreso Neto}} \right) \times 100$$

### 4.3 Utilidad en Devoluciones
Cuando una venta genera una devolución (`Return`):
- Si el producto se reintegra al inventario (`restock_inventory = 1`), la utilidad se ajusta proporcionalmente en el período contable correspondiente a la fecha de la devolución.
- Si el producto devuelto se descarta por daño o merma (`restock_inventory = 0`), el ingreso se cancela pero el costo de la mercancía permanece consumido como pérdida neta.

---

## 5. EJEMPLO PASO A PASO DE CICLO DE COSTOS Y UTILIDAD

1. **Estado Inicial:**
   - Stock: 10 unidades.
   - Costo promedio: `RD$ 100.00` (Valor total = `RD$ 1,000.00`).
2. **Nueva Compra:**
   - Entrada: 10 unidades a `RD$ 120.00` cada una (Total compra = `RD$ 1,200.00`).
   - Cálculo CPP:
     $$\frac{1,000 + 1,200}{10 + 10} = \frac{2,200}{20} = \text{RD\$ } 110.00$$
   - Nuevo `average_cost = 11000L` (RD$ 110.00).
   - `last_purchase_cost = 12000L` (RD$ 120.00).
3. **Venta de 5 Unidades:**
   - Precio de venta al público: `RD$ 160.00`.
   - `unitCostSnapshot = 11000L` (RD$ 110.00).
   - Total venta: `5 * RD$ 160.00 = RD$ 800.00`.
   - Costo total de venta: `5 * RD$ 110.00 = RD$ 550.00`.
   - Utilidad bruta obtenida: `RD$ 800.00 - RD$ 550.00 = RD$ 250.00` (Margen = 31.25%).
   - Stock restante: 15 unidades a un costo de `RD$ 110.00` (Valor inventario = `RD$ 1,650.00`).
