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

La reauditoría del 6 de octubre confirmó que, debajo de esos indicadores, Puntto organiza
los bloques `Top productos`, `Encargos`, `Por cobrar`, `Dinero por método`, `Cierre`,
`Inventario bajo` y `Productos sin foto — revisar`. La cabecera conserva el breadcrumb
`Operación / Resumen`, el plan y `Ayuda`; la navegación inferior mantiene `Terminal`,
`Pedidos`, `Inventario` y `Menú`. Estos bloques sirven como referencia de contenido y no
solo de estilo para el dashboard móvil de MiCatalogo.

## Menú observado

El menú lateral se organiza en `OPERACIÓN`, `COMPRAS`, `CATÁLOGO`, `COBROS`, `FINANZAS`, `ANÁLISIS` y `EQUIPO`, seguido por `AJUSTES`, `Ver tienda` y la cuenta. La navegación conserva un encabezado con menú, breadcrumb, plan y ayuda, y una barra inferior con `Terminal`, `Pedidos`, `Inventario` y `Menú`. El pie del menú también expone `Descargar la app` y `Cerrar sesión`.

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

En `Operación / Cierre de día`, Puntto permite escoger la fecha, separa ventas cobradas,
abonos, gastos y devoluciones, calcula el efectivo esperado y solicita el efectivo contado
antes de cerrar. También advierte que después del cierre no se corrigen movimientos sin
autorización del dueño.

La inspección del emulador confirmó los bloques `VENTAS COBRADAS`, `ABONOS RECIBIDOS`,
`GASTOS`, `DEVOLUCIONES` y `EFECTIVO EN CAJA`, con el monto esperado separado de los
medios no efectivos. MiCatalogo usa ahora un cierre diario por fecha: permite consultar
el día, guardar un arqueo opcional y no exige abrir una sesión de caja para vender o
registrar gastos. Las sesiones remotas permanecen como control avanzado opcional.

En `Operación / Pedidos`, Puntto muestra el estado de la bandeja, el total recibido y
una tarjeta guiada cuando todavía no hay órdenes. Cuando existe una orden pendiente, el
flujo esperado es revisar el pedido, escoger contado, crédito o mixto y confirmarlo como
venta; esa confirmación debe descontar inventario y generar factura/caja en una sola
transacción.

En `Operación / Terminal`, la reauditoría confirmó los controles `Detalle / Mayoreo`,
ayuda contextual, opciones de terminal, búsqueda, escaneo por cámara, selector de
`Servicio`, pestañas `Productos`/`Servicios`, productos recientes y un carrito fijo en
la parte inferior. MiCatalogo ya conserva detalle/mayoreo, ayuda, opciones, carrito
responsive y cobro contado, tarjeta, transferencia, mixto o crédito; la búsqueda ahora
también compara el código de barras local para completar el flujo cuando el código se
introduce manualmente o proviene de un lector externo.
- El POS móvil ahora añade un escáner nativo con CameraX + ML Kit: solicita el permiso de
  cámara solo al usarlo, muestra una guía visual clara, agrega automáticamente el producto
  reconocido y deja el valor en la búsqueda cuando el código no pertenece al catálogo.

## Inventario verificado en Puntto

La pantalla muestra acciones para añadir del catálogo, precios y costos, movimientos, importar/exportar, combos y nuevo producto. Antes de la lista presenta capital al costo, productos/unidades, nivel bajo y una alerta de productos sin foto. La lista permite seleccionar un producto para consultar sus acciones y existencias.

La evidencia del emulador también confirma que la jerarquía de Puntto separa la cabecera
`CATÁLOGO / Inventario`, las acciones rápidas y los indicadores `Activos`, `Archivados`,
`Combos`, `Capital al costo`, `Productos`, `Nivel bajo` y `Margen prom.`. La búsqueda y
`Filtros` quedan inmediatamente antes de las tarjetas de producto.

MiCatalogo Android ahora presenta esos indicadores con datos locales: capital calculado desde `averageCost`, unidades, nivel bajo y productos activos sin imagen. Se conservan los flujos de importar CSV/XLSX/PDF, recepción de mercancía, conteo físico, motivos, ajustes y Kardex.

## Cambios ya incorporados en MiCatalogo Android

- Navegación con breadcrumb, transiciones de entrada y expansión animada del menú.
- Dashboard con periodos, guía de primeros pasos y tarjetas de KPI en la paleta clara de MiCatalogo.
- Dashboard con contexto visual `RESUMEN`, selector de periodo con icono de calendario y
  jerarquía de encabezado inspirada en Puntto, conservando los acentos claros de MiCatalogo.
- Dashboard con bloques de `Top productos` y `Dinero por método`, calculados desde las
  líneas y ventas del periodo seleccionado, con diseño adaptable para móvil y tablet.
- El resumen móvil sigue el orden observado en Puntto: saludo con el nombre de la tienda,
  acciones rápidas, primeros pasos y luego el selector de período; los colores claros y
  las métricas propias de MiCatalogo se conservan.
- El icono de calendario del dashboard dejó de ser decorativo: abre un selector nativo de
  fecha y filtra ventas, cobros, ganancia, ticket, top productos y métodos para ese día.
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
- `Operación / Pedidos` ahora tiene una pantalla móvil propia, con encabezado guiado,
  KPIs, estado vacío, actualización manual, tarjetas de órdenes y diálogo de confirmación.
  Permite contado, crédito o mixto, forma de pago, cliente sincronizado y referencia.
  La API móvil usa `OrderConfirmationService`, el mismo dominio que la web, por lo que
  la confirmación es idempotente y actualiza inventario, factura, caja y crédito juntos.
- `Encargos` y `Envíos` ya no caen en una pantalla genérica sin contexto: reutilizan el
  mismo flujo de órdenes con el módulo remoto correspondiente, título, descripción y
  estados vacíos propios. Así se conserva una sola implementación de confirmación y se
  evita duplicar lógica entre pedidos, encargos y entregas.
- Las acciones de los módulos genéricos ya se resuelven dentro de la navegación Compose:
  inventario, catálogo, clientes, caja, finanzas, precios, importador, métricas, tienda,
  equipo, soporte y configuración ya no abren accidentalmente una URL web cuando existe
  una pantalla móvil equivalente.
- Inventario móvil incorpora `Exportar` junto a `Importar` y `Movimientos`. El CSV se
  genera en el dispositivo con existencias, precio de venta, costo promedio, último costo
  y mínimo, y se entrega mediante el selector de archivos del sistema.
- La navegación Compose ahora usa transiciones cortas de entrada/salida y las tarjetas
  de los módulos aparecen escalonadas; se conserva el fondo claro y los acentos de
  MiCatalogo, sin copiar el color oscuro del menú de Puntto.
- El tema de MiCatalogo queda fijado en modo claro por defecto. El modo oscuro de Puntto
  no se propaga al sistema: se reutiliza su jerarquía, sus estados y su ritmo de
  navegación, pero se mantienen los fondos claros, tarjetas blancas y acentos azules
  propios de MiCatalogo aunque el teléfono esté configurado en modo oscuro.
- Las acciones de los módulos se apilan en una columna adaptable en lugar de forzarse en
  una sola fila; esto evita cortes en teléfonos estrechos y mantiene accionables las rutas
  nativas aunque el backend no devuelva una URL externa.
- Las tablas de módulos con registros ahora incluyen búsqueda local por nombre, código,
  valor o estado, con estado vacío específico para búsquedas sin coincidencias; esto
  acerca la interacción de decants, precios, reportes y equipo a las listas de Puntto.
- La ayuda de `Terminal` ahora reproduce el recorrido guiado de Puntto en cinco pasos:
  buscar, agregar, distinguir venta/cotización, revisar el carrito y cobrar. Cada paso
  aparece individualmente con `Atrás`, `Siguiente` y una transición corta, evitando un
  bloque de texto estático y manteniendo el fondo claro de MiCatalogo.
- Las opciones de `Terminal` ahora presentan el modo `Cobrar / Cotizar` como selector,
  con una explicación explícita de que cotizar no descuenta existencias. El botón de
  continuación abre la pantalla de cotizaciones y conserva debajo `Cierre de día` y
  `Abrir o revisar caja`, como en el flujo de Puntto.
- Inventario móvil ahora replica la jerarquía de Puntto con contexto `CATÁLOGO`, título,
  descripción y accesos directos a Productos, Salud de precios, Movimientos e Importar.
  Se conservan el Kardex, recepción, conteo, ajustes, motivos e importador existente.
- `Catálogo / Fotos` ahora es una vista nativa accionable de productos sin imagen: permite
  buscar, revisar el contador de pendientes, abrir el editor del producto para cargar o
  cambiar la foto y mostrar un estado vacío cuando el catálogo ya está completo. La vista
  mantiene el mismo editor de producto y no duplica la lógica de inventario.
- El menú móvil ahora separa visualmente `Compras`, `Finanzas` y `Análisis` como Puntto y
  añade `Mi cuenta` para todos los roles, sin convertirlo en un permiso administrativo.
- El menú móvil ahora incluye `Descargar la app` dentro de `Operación`, reutilizando el
  módulo de actualizaciones ya existente para no depender de una URL de APK fija.
  También incluye `Ver tienda`, que abre el enlace público de la tienda activa, y
  `Cerrar sesión`, que limpia la conexión local mediante el mismo flujo de cuenta.
- La pantalla de permisos de vendedores en Android ahora cubre la misma matriz de módulos
  que el panel web (operación, compras, catálogo, cobros, finanzas, análisis y equipo),
  mientras `Ajustes`, `Configuración de tienda` y `Vendedores` continúan protegidos para
  propietarios o administradores y no aparecen como permisos asignables.

## Pendientes de paridad funcional

- Editar y anular una venta requieren un flujo de dominio y contabilidad completo; no se debe simular con un botón que no revierta inventario/caja.
- Térmico ya está enlazado desde el detalle histórico y el dashboard; queda validar el
  recorrido físico con una impresora Bluetooth real, además de la prueba sin impresora.
- `Cierre de día` móvil consulta un solo día, separa efectivo de tarjetas/transferencias,
  muestra entradas/salidas y guarda un arqueo opcional. El backend conserva el cierre por
  tienda y fecha; las sesiones remotas quedan disponibles como control avanzado.
- El selector de fecha del cierre móvil no abre ni cierra sesiones de caja. Ninguna venta
  o gasto depende de una sesión abierta para aparecer en el esperado diario.
- El detalle histórico de ventas y el dashboard ahora exponen `Térmico` y reutilizan el
  motor Bluetooth configurado para enviar la factura. Sin impresora registrada se muestra
  una instrucción explícita para configurarla desde `Impresoras`.
- La pantalla `Fotos` ya no es un destino genérico: filtra los productos sin imagen,
  conserva búsqueda y edición, y permite resolver cada pendiente desde el mismo flujo de
  producto.
- Completar el recorrido de cada grupo del menú y contrastar formularios, permisos, estados vacíos y mensajes de validación con las pantallas equivalentes de MiCatalogo.
- Ejecutar una prueba equivalente de cada operación en la APK oficial firmada y en la web antes de publicar.

## Verificación de esta iteración

```text
./gradlew test assembleDebug       PASS
./gradlew assembleDebug -PmiCatalogoOfflineCheck=true  PASS
adb install -r app/build/outputs/apk/debug/app-debug.apk  PASS (variante aislada)
UI smoke launch de com.bsolutions.micatalogo.offlinecheck  PASS
git diff --check                   PASS
```

La prueba web de confirmación móvil cubre la creación de una sola factura, descuento de
una unidad y repetición idempotente de la misma solicitud:

```text
vendor/bin/pest.bat tests/Feature/BusinessRemediationTest.php  PASS (9 tests, 67 assertions)
```

La variante aislada se puede instalar como `com.bsolutions.micatalogo.offlinecheck` sin desinstalar ni tocar la aplicación oficial firmada.

### Continuación solicitada desde Resumen

En Puntto se continuó el recorrido hacia las pantallas siguientes de Operación:

- Terminal: se verificaron Detalle/Mayoreo, lector, Servicio, pestañas de productos,
  carrito fijo, controles de cantidad, opciones Cobrar/Cotizar y la hoja de cobro
  con cliente, fecha, métodos, importes recibidos/cambio, montos rápidos, mixto,
  crédito y nota. La venta de demostración se dejó sin confirmar.
- Ventas: períodos, exportación, resumen, búsqueda, filtros, filas y detalle.
  El filtro avanzado ofrece rango de total, pago y origen. El detalle mantiene
  recibo, compartir, térmico, editar, devolver y anular.
- Cotizaciones: resumen de vigentes/por convertir/vencidas, lista, detalle de
  artículos, PDF y acceso a venta.
- Pedidos: bandeja vacía guiada y enlace para compartir la tienda.
- Encargos: contadores de hoy/mañana/atrasados y pestañas abiertos/entregados/cancelados.
- Envíos: totales por modalidad y estados todos/preparando/en camino/entregado/problema.
- Cierre de día: fecha, cobros, abonos, gastos, devoluciones, efectivo entrado/salido,
  esperado y conteo; no se cerró ni alteró la caja de demostración.

En MiCatalogo se verificó que Terminal, Cotizaciones, Pedidos, Encargos, Envíos y
Cierre de día tienen rutas nativas y conservan funciones previamente implementadas.
Se corrigió Ventas para evitar el resumen de 3 columnas que se cortaba en el móvil:
ahora usa cuatro métricas en 2×2, con importe pendiente dividido entre ventas para
el porcentaje a crédito. El filtro avanzado ofrece rango de importes, estado de pago
y origen. La exportación ofrece CSV de la selección filtrada o ventas locales
completadas; no se presenta como el Excel completo de Puntto (no inventa pagos ni
devoluciones que no estén disponibles localmente).

La comprobación del shell/navegación entre Resumen e Inventario confirmó un único
encabezado y barra inferior en ambas rutas. Ventas ahora muestra la cuadrícula móvil
2×2, filtros por total/pago/origen y exportación CSV declarada con el alcance de datos
locales. `compileDebugKotlin`, `testDebugUnitTest` y las pruebas UI aisladas de Resumen
y Ventas pasan. La release firmada 1.0.72 (código 73) quedó instalada y su APK extraída
del móvil coincide por SHA-256 con el artefacto compilado. Tras actualizar, MiCatalogo
presenta el bloqueo normal de sesión y requiere que su usuario vuelva a autenticarse
antes de verificar rutas autenticadas en el teléfono.

También se inspeccionó sin guardar ni confirmar operaciones el grupo `Compras`:

- `Contenedores` presenta órdenes por suplidor, borrador y recepción; la captura de
  factura (imagen/PDF/Excel) solo prepara un borrador revisable. El formulario incluye
  moneda del suplidor, tasa FX, courier/naviera, guía/BL, llegada estimada, libras y notas.
- `Cargas` describe agrupación de contenedores del exterior y asignación de libraje,
  flete y aduana al costo real; el formulario captura moneda, tasa, courier, guía/BL,
  fecha estimada, peso y notas.
- `Suplidores` muestra el estado vacío y formulario de nombre, contacto, teléfono y
  moneda de factura.
- `Facturas de suplidor` muestra saldo pendiente y permite registrar deuda histórica.

Los formularios de Compras no se enviaron. MiCatalogo ya dispone de módulos nativos de
compra/recepción y suplidores; queda una comparación UI individual y autenticada del
recorrido completo después de volver a iniciar sesión en el teléfono.

`Catálogo / Fotos` también se recorrió hasta las acciones por foto: búsqueda en la
biblioteca, estados `Seguras`, `Revisar` y `Sin foto`, revisión de coincidencia por
producto, aceptar la foto, buscar otra o saltar. No se aceptó ninguna foto ni se
alteró el catálogo Puntto durante la revisión. En MiCatalogo la ruta Fotos es nativa;
verificar en una sesión autenticada el resultado del buscador de imágenes y los flujos
de subir/cambiar foto en el editor.

### Ventas — corrección e instalación 1.0.72

- `SalesHistoryScreen` presenta el resumen de ventas en dos filas por dos columnas y
  usa importe pendiente/ventas como proporción a crédito.
- Se añadió filtro avanzado de mínimo/máximo, estado de pago (incluidas parciales y
  anuladas) y origen terminal/ruta, sin alterar la consulta base local.
- Exportar permite CSV con columnas de factura, fecha, cliente, método, estado, total,
  producto, cantidad, precio unitario y subtotal por línea; admite el filtro visible o
  ventas completadas locales. No inventa pagos o devoluciones exportables no presentes
  en la base local.
- Prueba unitaria CSV: pasa con comillas, líneas e importes en centavos. Pruebas UI
  aisladas de Resumen y KPIs de Ventas: pasan en el emulador. `compileDebugKotlin` y
  `testDebugUnitTest`: PASS.
- Release `1.0.72` / `versionCode 73`: firma, certificado y paquete verificados; SHA-256
  `503e31108e16be213c7d9b5a68dd1284bf6a066897204cd568dee98c0c2d5c47`. APK instalada
  con `adb install -r` y confirmada por versión y hash extraído del teléfono. No se
  publicó el manifiesto OTA ni la APK en el VPS.
- Al volver a abrir en el teléfono, la aplicación exige iniciar sesión. La inspección
  visual de rutas autenticadas en la versión nueva queda pendiente del desbloqueo de
  sesión por el usuario; no se intentó introducir ni restablecer credenciales.

### Terminal — corrección solicitada

- Eliminado el encabezado interno duplicado `Nueva venta` / `Venta a crédito` para que
  el shell `Operación / Terminal` sea el único título y permanezca una sola etiqueta.
- La cuadrícula móvil conserva dos columnas y obtiene una altura mínima desplazable
  incluso si los controles del encabezado ocupan más espacio.
- Nueva prueba instrumental con 20 productos: verifica que se puede desplazar hasta
  `Terminal product 19`; pasa en el emulador aislado.
- Se conserva el comportamiento existente de detalle/mayoreo, categorías, productos
  recientes, lector, servicio, carrito fijo y cobro.
- Release firmada 1.0.73 / código 74 instalada en el móvil; la app abre su pantalla
  de inicio de sesión. La verificación visual de la Terminal en el móvil debe
  completarse después de que el usuario autentique la sesión.
