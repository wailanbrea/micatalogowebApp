# BSPOS — GUÍA DE DISEÑO, SISTEMA VISUAL Y UX RESPONSIVA

## 1. IDENTIDAD VISUAL Y LENGUAJE DE DISEÑO

La interfaz de usuario de **BSPOS** está inspirada en las especificaciones visuales de referencia de punto de venta moderno de alta gama:
- **Estilo:** Limpio, moderno, profesional, con alta jerarquía visual y sin saturación.
- **Colores Principales:**
  - Azul Eléctrico vibrante (`#1E60F4` / `#0066FF`) para acciones primarias, botones destacados y estados activos.
  - Azul Marino Profundo (`#0B192C` / `#102A43`) para la barra de navegación lateral en tablets y contrastes jerárquicos.
  - Fondos Claros Suaves (`#F4F6F9` / `#F8FAFC`) que hacen destacar las tarjetas.
  - Tarjetas de Superficie Blanca (`#FFFFFF`) con esquinas amplias y sombras sutiles de baja difusión.
  - Acentos Semánticos: Verde Esmeralda (`#10B981`) para existencias disponibles y cobros, Naranja Ámbar (`#F59E0B`) para alertas de stock bajo y Rojo Coral (`#EF4444`) para productos agotados y cancelaciones.

---

## 2. TOKENS DEL DESIGN SYSTEM

### 2.1 Paleta Cromática (`AppColors`)
```kotlin
object AppColors {
    // Brand & Primaries
    val Primary = Color(0xFF1E60F4)         // Azul Eléctrico Primario
    val PrimaryDark = Color(0xFF0F4AC9)     // Azul Primario Oscuro (Hover / Pressed)
    val PrimaryLight = Color(0xFFE8EFFF)    // Azul Fondo Acento / Pills
    val SecondaryNavy = Color(0xFF0B192C)   // Azul Marino Profundo (Navigation Rail)
    val SecondaryNavySurface = Color(0xFF14243B)

    // Background & Surfaces
    val Background = Color(0xFFF4F6FA)      // Fondo General de la App
    val Surface = Color(0xFFFFFFFF)         // Tarjetas y Paneles
    val SurfaceVariant = Color(0xFFF1F5F9)  // Campos de texto y separadores

    // Text & Hierarchy
    val TextPrimary = Color(0xFF0F172A)     // Títulos y Precios Principales
    val TextSecondary = Color(0xFF64748B)   // Subtítulos, Códigos y Etiquetas
    val TextTertiary = Color(0xFF94A3B8)    // Placeholders y Textos Deshabilitados
    val TextOnPrimary = Color(0xFFFFFFFF)

    // Semantic States
    val Success = Color(0xFF10B981)         // Verde Stock Disponible / Cobros
    val SuccessLight = Color(0xFFD1FAE5)
    val Warning = Color(0xFFF59E0B)         // Ámbar Stock Bajo
    val WarningLight = Color(0xFFFEF3C7)
    val Error = Color(0xFFEF4444)           // Rojo Agotado / Deuda
    val ErrorLight = Color(0xFFFEE2E2)
}
```

### 2.2 Escala de Espaciado (`AppSpacing`)
No se utilizan valores mágicos arbitrarios. Toda la interfaz respeta la escala estándar:
- `Spacing2 = 2.dp`
- `Spacing4 = 4.dp`
- `Spacing8 = 8.dp`
- `Spacing12 = 12.dp`
- `Spacing16 = 16.dp`
- `Spacing20 = 20.dp`
- `Spacing24 = 24.dp`
- `Spacing32 = 32.dp`

### 2.3 Radios de Esquinas (`AppShapes`)
- `ShapeSmall = RoundedCornerShape(8.dp)` (Botones secundarios, chips)
- `ShapeMedium = RoundedCornerShape(12.dp)` (Campos de entrada, badges)
- `ShapeLarge = RoundedCornerShape(16.dp)` (Tarjetas de producto, diálogos)
- `ShapeExtraLarge = RoundedCornerShape(20.dp)` (Tarjetas de KPIs, BottomSheets)
- `ShapePill = RoundedCornerShape(50)` (Filtros de categoría, botones de acción)

### 2.4 Elevación y Sombras (`AppElevation`)
- `CardElevation = 1.dp` a `2.dp` con color de sombra translúcido para mantener el look limpio y plano moderno.

---

## 3. ARQUITECTURA RESPONSIVA (TELÉFONO VS. TABLET)

### 3.1 Teléfono Móvil (WindowSizeClass = Compact)
- **Navegación Inferior (BottomNavigationBar):**
  1. `Inicio` (Dashboard rápido de KPIs).
  2. `Ventas` (POS con catálogo y buscador).
  3. `Productos` (Gestión de catálogo).
  4. `Clientes` (Directorio y cartera).
  5. `Más` (Menú de accesos a Inventario, Compras, Rutas, Reportes, Backups y Ajustes).
- **Diseño del POS Móvil:**
  - Encabezado: Saludo, avatar de usuario y campana de notificaciones.
  - Barra de búsqueda con icono de lector de código de barras a la derecha.
  - Carrusel horizontal o grid compacto de KPIs (Ventas del día, Cobros, Productos con stock bajo).
  - Fila de chips de categorías con desplazamiento horizontal ("Todos", "Bebidas", "Snacks", "Lácteos"...).
  - Catálogo de productos en **Grid de 2 Columnas**.
  - Tarjeta de producto con foto centrada, botón "+" flotante superior derecho, nombre, precio destacado (`RD$ 35.00`) y pill de stock (`En stock (48)` en verde).
  - **Barra de Carrito Inferior (Floating Bottom Cart):** Muestra cantidad total de items, miniaturas con badge, subtotal y botones de acción rápida: `Cobrar >` (Azul primario) e `Imprimir` (Botón blanco con borde). Al pulsar expande el desglose en BottomSheet.

### 3.2 Tablet / Modo Apaisado (WindowSizeClass = Expanded)
- **Navegación Lateral (NavigationRail en Azul Marino Profundo):**
  - Logotipo institucional superior: Icono de carrito + "BSPOS" / "Tu negocio, más lejos".
  - Ítems de navegación: Inicio, Ventas, Productos, Clientes, Rutas, Ajustes.
  - Pie de barra: Lema institucional "Creciendo juntos", gráfico y versión de la app.
- **Área Principal de Venta (Centro):**
  - Barra superior de estado: Wi-Fi / Conectividad, Fecha y hora local formateada, selector de vendedor activo.
  - Fila de KPIs analíticos.
  - Chips de categorías.
  - Catálogo de productos en **Grid de 4 Columnas**.
- **Panel Lateral Fijo de Carrito (Derecha):**
  - Encabezado con título "Carrito de venta" y botón de acción "Vaciar".
  - Selector de cliente vinculado con visualización de RNC/Cédula y balance.
  - Lista de productos seleccionados con miniatura, nombre, precio, stepper `[-] cantidad [+]`, botón de eliminar por fila y subtotal.
  - Resumen financiero: Subtotal, Descuento, Impuesto, Total destacado en negrita.
  - Selector de método de cobro: Pills `Contado` / `Crédito`.
  - Botones de acción fija: `Cobrar >` (Botón primario ancho con icono de tarjeta) e `Imprimir` (Botón secundario con icono de impresora térmica).

---

## 4. COMPONENTES VISUALES Y MICROINTERACCIONES

- **Empty States Profesionales:** Ninguna lista vacía debe quedar en blanco. Se presenta una ilustración o icono estilizado, un título explicativo, una descripción breve y un botón de acción sugerido (ej: "Aún no tienes productos. Agrega tu primer producto para comenzar a vender. [Crear producto]").
- **Feedback Inmediato:** Snackbars informativos no invasivos tras operaciones críticas ("Venta registrada con éxito", "Entrada guardada", "Backup generado").
- **Animaciones Discretas:** Uso de `AnimatedVisibility` para mostrar/ocultar paneles y filtros, `animateContentSize` para expansión suave del carrito, y `Crossfade` para transiciones entre categorías.
- **Rendimiento ante todo:** Prioridad estricta al framerate fluido (60-120 fps); no sobrecargar composables con layouts anidados innecesarios ni efectos blur costosos.
