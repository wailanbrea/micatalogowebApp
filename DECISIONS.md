# BSPOS — ARCHITECTURAL DECISION RECORDS (ADR)

Este registro documenta las decisiones técnicas, de arquitectura y de diseño adoptadas para garantizar la longevidad, rendimiento y auditabilidad de **BSPOS**.

---

### ADR-001: Persistencia Offline-First con Room como Fuente Única de Verdad
- **Contexto:** La aplicación se comercializa inicialmente bajo un modelo de pago único offline por dispositivo. Los comercios operan en entornos con conectividad inestable o nula (reparto en ruta, colmados, almacenes).
- **Decisión:** Utilizar SQLite administrado mediante Android Jetpack Room. Toda lectura y escritura se realiza contra la base local. No se requiere servidor ni Firebase.
- **Consecuencias:**
  - Máxima velocidad y disponibilidad ininterrumpida.
  - El dispositivo es responsable de la integridad y persistencia física de los datos.

---

### ADR-002: Identificadores Universales UUID (v4) y Preparación para la Nube
- **Contexto:** Aunque la versión inicial es 100% offline, el producto evolucionará a sincronización multidispositivo con `BSPOS Cloud`. Los IDs autoincrementales numéricos (`1, 2, 3...`) generan colisiones insalvables al fusionar bases de datos independientes.
- **Decisión:** Todas las entidades clave utilizan `UUID.randomUUID().toString()` como clave primaria (`TEXT` en Room). Se incluyen campos de auditoría estándar: `created_at`, `updated_at` y `deleted_at` (soft delete).
- **Consecuencias:**
  - Los registros pueden crearse en cualquier dispositivo sin riesgo de colisión al sincronizar.
  - Se incrementa ligeramente el espacio de almacenamiento por índice en SQLite (despreciable frente a los beneficios arquitectónicos).

---

### ADR-003: Modelado Monetario en Centavos Enteros (`Long`)
- **Contexto:** El uso de tipos en coma flotante (`Float` / `Double`) genera errores de aproximación binaria inaceptables en sistemas de facturación y contabilidad (ej. `0.1 + 0.2 = 0.30000000000000004`).
- **Decisión:** Todos los montos de dinero (precios de venta, costos, subtotales, descuentos, impuestos, pagos, balances) se modelan como enteros de 64 bits (`Long`) que representan centavos. Para cálculos intermedios de porcentajes y prorrateos se utiliza `BigDecimal` con escala a 4 decimales y redondeo bancario (`HALF_EVEN`).
- **Consecuencias:**
  - Precisión contable absoluta al centavo en todas las operaciones.
  - Imposibilidad matemática de discrepancias en cuadres de caja o estados de cuenta.

---

### ADR-004: Inmutabilidad de Movimientos de Inventario y Kardex
- **Contexto:** La pérdida de trazabilidad de stock en sistemas tradicionales ocurre cuando el stock se modifica directamente (`stock = 10`) sin registrar el motivo del cambio.
- **Decisión:** Ningún registro de existencia se modifica sin insertar de manera atómica una fila en `inventory_movements`. Las filas de la tabla de movimientos son inmutables: nunca se editan ni se eliminan con `DELETE`. Las correcciones se realizan exclusivamente mediante nuevos movimientos compensatorios (`ADJUSTMENT_IN`, `ADJUSTMENT_OUT`).
- **Consecuencias:**
  - Auditoría total: cada unidad de producto puede rastrearse hasta su entrada original.
  - La tabla de movimientos actúa como el libro mayor (Kardex) oficial del negocio.

---

### ADR-005: Costo Promedio Ponderado Móvil y `unitCostSnapshot` en Ventas
- **Contexto:** Los costos de adquisición varían constantemente. Calcular la utilidad histórica usando el costo actual distorsiona los reportes financieros pasados.
- **Decisión:**
  1. Cada compra formal o entrada de inventario recalcula el Costo Promedio Ponderado (CPP) del producto.
  2. Cada línea de venta (`SaleItem`) captura obligatoriamente una copia estática del CPP vigente en el milisegundo de la venta (`unitCostSnapshot`).
- **Consecuencias:**
  - Los reportes de utilidad bruta histórica son inmutables y exactos, reflejando fielmente el margen comercial de cada momento en el tiempo.

---

### ADR-006: Segregación de Inventario Físico por Ubicación (`locationType` / `locationId`)
- **Contexto:** BSPOS gestiona tanto ventas en mostrador (almacén central) como ventas móviles en rutas de reparto. Despachar un vehículo no es una venta, es una transferencia temporal de custodia.
- **Decisión:** El inventario se almacena en `inventory_stock` indexado por `(product_id, location_type, location_id)`. Las cargas de ruta descuentan del almacén principal (`TRANSFER_OUT`) e incrementan el inventario de la ruta (`TRANSFER_IN`). Las ventas en calle descuentan únicamente del stock de la ruta activa. Al finalizar el día, se realiza la liquidación y retorno al almacén.
- **Consecuencias:**
  - Se previenen ventas accidentales de mercancía que ya se encuentra en tránsito en un vehículo.
  - Permite liquidar rutas y detectar faltantes o mermas con exactitud.

---

### ADR-007: Almacenamiento de Fotografías de Productos en Disco Privado
- **Contexto:** Guardar imágenes de productos como `BLOB` dentro de SQLite infla el tamaño del archivo de base de datos, ralentiza las consultas de Room y agota la memoria del CursorWindow de Android.
- **Decisión:** Las imágenes capturadas con la cámara o seleccionadas con PhotoPicker se procesan localmente: se corrige la orientación EXIF, se comprimen en JPEG/WebP (máximo 1024px) y se genera una miniatura (256px). Se almacenan en el directorio privado de la aplicación (`context.filesDir/products/`). En la base de datos únicamente se guardan las rutas relativas (`image_path` y `thumbnail_path`).
- **Consecuencias:**
  - Rendimiento óptimo de Room y uso eficiente de memoria.
  - Carga ultrarrápida en listas y grids con Coil.

---

### ADR-008: Interfaz Adaptativa Unificada con WindowSizeClass
- **Contexto:** Desarrollar dos aplicaciones separadas para teléfono y tablet duplica el mantenimiento y aumenta la tasa de bugs.
- **Decisión:** Un solo código base de Jetpack Compose que inspecciona dinámicamente la `WindowSizeClass` (`Compact`, `Medium`, `Expanded`). En teléfonos se despliega `NavigationBar` inferior y catálogo en grid de 2 columnas con drawer de carrito. En tablets se despliega `NavigationRail` lateral en azul marino, catálogo en grid de 4 columnas y panel lateral de carrito persistente a la derecha.
- **Consecuencias:**
  - Experiencia nativa optimizada en cualquier factor de forma con cero duplicación de lógica de negocio o ViewModels.

---

### ADR-009: Formato de Respaldo Autónomo en Archivo ZIP
- **Contexto:** El usuario debe poder respaldar toda su información comercial y transferirla a un nuevo dispositivo sin intervención técnica ni servidores externos.
- **Decisión:** El sistema de copias de seguridad genera un único archivo comprimido `.zip` (`BSPOS_BACKUP_<timestamp>.zip`) que contiene:
  1. Base de datos SQLite (`database/bspos.db`), garantizando previamente el vaciado del log WAL (`PRAGMA wal_checkpoint(FULL)`).
  2. Carpeta completa de imágenes de productos (`images/products/`).
  3. Parámetros de configuración (`config/settings.json`).
  4. Manifiesto de integridad (`metadata.json`) con versión del esquema, fecha y hash SHA-256.
  - WorkManager programa respaldos automáticos en almacenamiento local con política de retención (7 diarios, 4 semanales, 3 mensuales).
- **Consecuencias:**
  - Portabilidad total y restauración integral garantizada en caso de pérdida o cambio de equipo.

---

### ADR-010: Impresión Térmica Bluetooth Directa (ESC/POS)
- **Contexto:** Los pequeños y medianos comerciantes utilizan impresoras térmicas portátiles de 58 mm y 80 mm conectadas por Bluetooth (SPP).
- **Decisión:** Implementar un servicio ligero de generación de bytes ESC/POS estándar, configurable para 32 columnas (58 mm) y 48 columnas (80 mm). Sin dependencias de servicios pesados en la nube ni librerías propietarias que requieran licencias adicionales.
- **Consecuencias:**
  - Compatibilidad universal con el 99% de las impresoras térmicas del mercado.

---

### ADR-011: Licenciamiento por Dispositivo y Resguardo Criptográfico
- **Contexto:** BSPOS se comercializa bajo un esquema de pago único por dispositivo.
- **Decisión:** La activación de la licencia se valida mediante firma digital criptográfica basada en clave asimétrica (algoritmo Ed25519 o RSA con clave pública embebida en la app y clave privada resguardada exclusivamente por BSolutions.Dev).
- **Consecuencias:**
  - Imposibilidad de generar licencias falsas por ingeniería inversa, protegiendo el modelo comercial del desarrollador sin requerir conexión a un servidor para verificar la activación.
