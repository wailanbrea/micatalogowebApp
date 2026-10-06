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

## Cambios ya incorporados en MiCatalogo Android

- Navegación con breadcrumb, transiciones de entrada y expansión animada del menú.
- Dashboard con periodos, guía de primeros pasos y tarjetas de KPI en la paleta clara de MiCatalogo.
- Detalle de venta adaptable con acciones de recibo/compartir/devolución, estado, pago, cliente, origen, productos, subtotal y total.
- Costo exacto por venta leído desde `sale_items.unit_cost_snapshot`; la ganancia del periodo no se estima con el precio actual.
- KPI de ganancia y ticket promedio en el dashboard. La ganancia se oculta para el modo vendedor cuando no corresponde mostrar costos.
- La consulta de costos agregados es de solo lectura y no cambia el esquema de Room ni requiere migración.

## Pendientes de paridad funcional

- Editar y anular una venta requieren un flujo de dominio y contabilidad completo; no se debe simular con un botón que no revierta inventario/caja.
- Térmico requiere enlazar la pantalla de detalle con la impresora configurada.
- Completar el recorrido de cada grupo del menú y contrastar formularios, permisos, estados vacíos y mensajes de validación con las pantallas equivalentes de MiCatalogo.
- Ejecutar una prueba equivalente de cada operación en la APK oficial firmada y en la web antes de publicar.

## Verificación de esta iteración

```text
./gradlew test assembleDebug       PASS
git diff --check                   PASS
```

La variante aislada se puede instalar como `com.bsolutions.micatalogo.offlinecheck` sin desinstalar ni tocar la aplicación oficial firmada.
