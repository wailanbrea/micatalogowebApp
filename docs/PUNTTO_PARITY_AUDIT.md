# Auditoría de paridad Puntto / MiCatalogo

Fecha de evidencia: 2026-10-06  
Dispositivo: emulador Android `emulator-5554`  
Referencia: aplicación `com.puntto.app` y variante aislada `com.bsolutions.micatalogo.offlinecheck`.

Este documento registra observaciones reproducibles de la interfaz y los flujos de Puntto. No es una instrucción para sembrar datos ni para modificar producción.

## Flujo de venta verificado en Puntto

Se creó una cotización de prueba para `Spray Demo Puntto` por RD$950 y se convirtió a la venta `V-000002`. La vista de detalle mostró:

- encabezado con `VENTA`, número, fecha y origen `Terminal`;
- acciones `Recibo`, `Compartir`, `Térmico`, `Editar`, `Devolver` y `Anular`;
- resumen financiero `COBRADO`, `COSTO`, `GANANCIA` y `MARGEN`;
- cliente, estado, pago, origen y vendedor;
- líneas de productos con cantidad, precio, costo y ganancia;
- subtotal, total y pagos.

También se recorrió el tutorial contextual de cuatro pasos:

1. Qué se puede hacer con la venta: recibo, WhatsApp y devolución.
2. Lo que dejó: total, costo real y ganancia.
3. Qué se llevó: artículos, cantidades, precio y descuentos.
4. Cómo pagó: efectivo, transferencia, tarjeta o fiado.

## Resumen de Puntto

El dashboard expone `Ventas`, `Ganancia`, `Ticket promedio` y `Por cobrar`, además de `Registrar cobro`, `Nueva venta` y una guía de primeros pasos con progreso. La selección de periodo es `Hoy`, `Este mes` y `Últimos 7`.

## Menú observado

El menú lateral se organiza en `OPERACIÓN`, `COMPRAS`, `CATÁLOGO`, `COBROS`, `FINANZAS`, `ANÁLISIS` y `EQUIPO`, seguido por `AJUSTES`, `Ver tienda` y la cuenta. La navegación conserva un encabezado con menú, breadcrumb, plan y ayuda, y una barra inferior con `Terminal`, `Pedidos`, `Inventario` y `Menú`.

En `Operación / Ventas`, Puntto separa el historial del terminal: permite escoger `Hoy`, `Este mes` o `Últimos 7`, muestra total vendido, promedio por venta y porcentaje a crédito, y lista cada comprobante con cliente, fecha, método de pago, estado y total. Al seleccionar una venta abre el detalle financiero y sus acciones.

En `Operación / Cotizaciones`, la referencia muestra el contexto `VENTAS`, el título y
la explicación de que los presupuestos salen de Terminal y pueden convertirse en venta.
Después presenta los indicadores `Vigentes`, `Por convertir` y `Vencidas`, una búsqueda
por número o cliente y tarjetas con cliente, número, vigencia, artículos, estado y total.
La prueba existente conservó `COT-000001` como `CONVERTIDA` por RD$950.

En `Cobros / Crédito`, Puntto ofrece las pestañas `Por cobrar` y `Pagados`, un estado
vacío guiado cuando no hay cartera y la explicación de que las cuentas aparecen después
de vender a crédito desde Terminal. La pantalla también conserva `Exportar` como acción
del módulo.

## Inventario verificado en Puntto

La pantalla muestra acciones para añadir del catálogo, precios y costos, movimientos, importar/exportar, combos y nuevo producto. Antes de la lista presenta capital al costo, productos/unidades, nivel bajo y una alerta de productos sin foto. La lista permite seleccionar un producto para consultar sus acciones y existencias.

MiCatalogo Android ahora presenta esos indicadores con datos locales: capital calculado desde `averageCost`, unidades, nivel bajo y productos activos sin imagen. Se conservan los flujos de importar CSV/XLSX/PDF, recepción de mercancía, conteo físico, motivos, ajustes y Kardex.

## Cambios ya incorporados en MiCatalogo Android

- Navegación con breadcrumb, transiciones de entrada y expansión animada del menú.
- Dashboard con periodos, guía de primeros pasos y tarjetas de KPI en la paleta clara de MiCatalogo.
- Detalle de venta adaptable con acciones de recibo/compartir/devolución, estado, pago, cliente, origen, productos, subtotal y total.
- Costo exacto por venta leído desde `sale_items.unit_cost_snapshot`; la ganancia del periodo no se estima con el precio actual.
- KPI de ganancia y ticket promedio en el dashboard. La ganancia se oculta para el modo vendedor cuando no corresponde mostrar costos.
- La consulta de costos agregados es de solo lectura y no cambia el esquema de Room ni requiere migración.
- `Terminal` y `Ventas` ahora son accesos independientes: Terminal conserva el cobro y `Ventas` incorpora historial filtrable por periodo y forma de pago, tarjetas de resumen, estados PAGADA/PENDIENTE y acceso al mismo detalle con recibo, compartir y devolución.
- La pantalla Android de `Cotizaciones` ahora refleja esa jerarquía: contexto de ventas,
  resumen de vigentes/por convertir/vencidas, búsqueda y categorías, imágenes de producto
  en el selector y en el carrito, cantidades, cliente, teléfono, notas, guardado y conversión.
- `Cobros / Crédito` ahora puede alternar entre `Por cobrar` y `Pagados`, consultar la
  cartera global o filtrarla por cliente, mostrar un estado vacío orientativo y mantener
  el registro de abonos con método de pago y validación de monto.
- La ruta de menú `Crédito` quedó separada de la acción de Terminal a crédito: el menú
  abre la cartera de cuentas por cobrar como en Puntto, mientras que el POS a crédito
  sigue disponible desde la operación de venta.
- Los módulos móviles que llegan desde el mismo read model del backend ahora muestran
  grupo, título y descripción antes del contenido, evitando pantallas sin encabezado y
  manteniendo una jerarquía equivalente a Puntto.

## Pendientes de paridad funcional

- Editar y anular una venta requieren un flujo de dominio y contabilidad completo; no se debe simular con un botón que no revierta inventario/caja.
- Térmico requiere enlazar la pantalla de detalle con la impresora configurada.
- Completar el recorrido de cada grupo del menú y contrastar formularios, permisos, estados vacíos y mensajes de validación con las pantallas equivalentes de MiCatalogo.
- Ejecutar una prueba equivalente de cada operación en la APK oficial firmada y en la web antes de publicar.

## Verificación de esta iteración

```text
./gradlew test assembleDebug       PASS
./gradlew assembleDebug -PmiCatalogoOfflineCheck=true  PASS
adb install -r app/build/outputs/apk/debug/app-debug.apk  PASS (variante aislada)
git diff --check                   PASS
```

La variante aislada se puede instalar como `com.bsolutions.micatalogo.offlinecheck` sin desinstalar ni tocar la aplicación oficial firmada.
