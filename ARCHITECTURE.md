# MiCatalogo — arquitectura del sistema

## Integración vigente (2026-10-05)

Room 21 guarda operaciones locales y ACKs; Laravel es autoridad del catálogo compartido,
sesiones de importación y contabilidad remota. POS offline y caja local están separados
de reportes/caja remota. No se interpreta ausencia de conexión como saldo cero.

Importador: archivo → Retrofit multipart → detección Laravel → mapping revisable Compose
→ session_id → confirmación server-side → sincronización Room. DTO/dominio conservan
precios/costos importados como strings decimales y códigos como strings con ceros.
Los metadatos nuevos son opcionales; 1.0.23 sigue siendo compatible. La sesión, no Android,
contiene las filas autoritativas. Cambiar hoja/fila no necesita editar el Excel.
Confianza/razones/ejemplos y atributos opt-in se muestran antes de confirmar.
No hay migración Room ni tabla nueva para el importador. Contrato: repo backend docs/API_CONTRACT.md.

## 1. PRINCIPIOS DE DISEÑO ARQUITECTÓNICO

BSPOS está diseñado como un sistema comercial de misión crítica, offline-first, de alto rendimiento y preparado para evolucionar sin fricciones hacia la nube.

Se rige por los siguientes principios no negociables:

1. **Clean Architecture & Separación de Intereses (SoC):** Estricta división entre las capas de Presentación (UI), Dominio (Reglas de negocio puras) y Datos (Persistencia y hardware).
2. **Unidirectional Data Flow (UDF):** El estado fluye hacia abajo desde los ViewModels hacia los Composables; los eventos de usuario fluyen hacia arriba desde los Composables hacia los ViewModels.
3. **Flujo de Dependencias Inmutable:**
   ```text
   Jetpack Compose (UI)
          ↓ (Events / State)
       ViewModel
          ↓ (Invokes)
        UseCase
          ↓ (Calls)
       Repository (Domain Interface)
          ↓ (Implements)
   RepositoryImpl (Data Layer)
          ↓ (Uses)
      Room DAO / DataStore / Hardware Services
   ```
   **Prohibición absoluta:** Los Composables y ViewModels nunca acceden directamente a las entidades ni a los DAOs de Room.
4. **Integridad Transaccional de Negocio:** Toda operación que modifique más de un registro o afecte el inventario/balance financiero se ejecuta de forma atómica dentro de una transacción Room (`@Transaction`), garantizando rollback automático ante cualquier fallo.
5. **Offline-first y autoridad remota:** Room es autoridad de operaciones locales aún no sincronizadas; Laravel confirma catálogo/contabilidad remotos y sesiones de importación. UUID estables, timestamps UTC, versiones y soft-delete permiten reintentos y migraciones no destructivas. El ACK del servidor se conserva antes de marcar SENT.

---

## 2. ESTRUCTURA MODULAR DE PAQUETES

```text
com.example.bspos/
│
├── core/
│   ├── database/          # Configuración de Room, TypeConverters, Migrations, Transactor
│   ├── datastore/         # Preferences DataStore para configuración y banderas
│   ├── backup/            # Empaquetado ZIP, hashing SHA-256, validación y restauración
│   ├── printing/          # Conectividad Bluetooth SPP, motor ESC/POS 58mm/80mm
│   ├── image/             # Almacenamiento privado de fotos, compresión, thumbnailing y limpieza
│   ├── money/             # Modelos monetarios de alta precisión (centavos en Long, redondeo)
│   ├── ui/                # Design System: Theme, Colors, Typography, Spacing, Shapes, Components
│   ├── utils/             # Extensiones de Kotlin, formateadores de fecha, UUID helpers
│   └── common/            # Clases base Result<T>, ErrorHandler, DispatcherProvider
│
├── domain/                # Capa pura de Kotlin sin dependencias del framework de Android
│   ├── model/             # Modelos de dominio inmutables (Product, Sale, Movement, Stock...)
│   ├── repository/        # Contratos / Interfaces de repositorios
│   └── usecase/           # Casos de uso atómicos organizados por módulo:
│       ├── auth/
│       ├── catalog/       # Productos, Categorías, Unidades
│       ├── inventory/     # Movimientos, Kardex, Existencias, Ajustes, Conteos
│       ├── purchases/     # Compras, Proveedores, Costo Promedio Ponderado
│       ├── sales/         # Venta atómica, Validación de Stock, Precios
│       ├── credit/        # Cuentas por cobrar, Pagos, Balance del cliente
│       ├── routes/        # Cargas de ruta, Ventas en ruta, Liquidación
│       ├── reports/       # Agregaciones analíticas, Utilidad bruta, Exportaciones
│       └── backup/        # Generación y restauración de copias de seguridad
│
├── data/                  # Implementación de datos y hardware
│   ├── local/
│   │   ├── entity/        # Entidades Room con claves foráneas e índices
│   │   ├── dao/           # Data Access Objects con consultas optimizadas y Flow
│   │   └── AppDatabase.kt # Base de datos Room abstracta
│   ├── repository/        # Implementaciones de las interfaces de dominio
│   └── mapper/            # Mappers Entity ↔ Domain Model
│
└── presentation/          # Interfaz de usuario con Jetpack Compose y Material 3
    ├── navigation/        # NavHost, Rutas seguras, Screen destinations, Adaptive Layouts
    ├── dashboard/         # Pantalla principal con KPIs y accesos rápidos
    ├── pos/               # Punto de venta (Mobile Grid 2-col + Tablet 4-col con carrito fijo)
    ├── catalog/           # Gestión de productos, categorías y unidades
    ├── inventory/         # Existencias, Alertas, Kardex, Ajustes, Conteo Físico
    ├── purchases/         # Recepción de compras y proveedores
    ├── customers/         # Directorio de clientes y estado de cuenta
    ├── routes/            # Gestión de rutas comerciales, cargas y liquidaciones
    ├── receivables/       # Cobros rápidos y cartera de crédito
    ├── returns/           # Devoluciones con decisión de reingreso a stock
    ├── reports/           # Reportes de ventas, costos, inventario y exportaciones
    ├── backup/            # Gestión manual y automática de backups
    └── settings/          # Configuración de negocio, parámetros de stock, licencia y "Acerca de"
```

---

## 3. MANEJO DE ESTADO Y FLUJO REACTIVO

- **Coroutines & Kotlin Flow:** Toda la persistencia reactiva expone flujos fríos (`Flow<List<T>>`) desde los DAOs de Room, los cuales son mapeados en la capa de datos y consumidos por los UseCases.
- **StateFlow en ViewModels:** Los ViewModels transforman los flujos de dominio en un único `StateFlow<ScreenUiState>` inmutable mediante `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InitialState)`.
- **Eventos de UI en un solo sentido (One-off Events):** Eventos como navegación, despliegue de snackbars o confirmaciones de cobro se comunican mediante un canal (`Channel<UiEvent>`) expuesto como `Flow<UiEvent>`, evitando la repetición de eventos al recomponer.

---

## 4. GESTIÓN DE LA INTEGRIDAD TRANSACCIONAL

Las operaciones que afectan inventario, dinero o contabilidad no admiten estados intermedios. Se implementa un mecanismo seguro basado en Room `@Transaction` ejecutado dentro de la capa `data/repository/`:

```kotlin
interface AppDatabaseTransactor {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}
```

Esto garantiza que si la validación de stock, la inserción de líneas de venta, la deducción de inventario o el registro de cuentas por cobrar falla en cualquier punto:
1. Ninguna fila permanece modificada.
2. La memoria caché de Room no queda corrupta.
3. El ViewModel recibe una excepción controlada para informar al usuario de manera precisa.

---

## 5. DISEÑO RESPONSIVO Y ADAPTATIVO

Se implementa una arquitectura de UI de un solo código fuente que se adapta fluidamente según la `WindowSizeClass`:

| Categoría de Pantalla | Ancho Típico | Dispositivo | Disposición de Navegación | Disposición de POS |
| :--- | :--- | :--- | :--- | :--- |
| **Compact** | < 600dp | Teléfonos Portrait | Bottom Navigation Bar (5 tabs principales: Inicio, Ventas, Productos, Clientes, Más) | Grid 2 columnas + Barra de búsqueda superior + Drawer/BottomSheet para carrito |
| **Medium** | 600dp - 839dp | Foldables / Tablets pequeñas | Navigation Rail vertical a la izquierda | Grid 2-3 columnas con carrito desplegable lateral |
| **Expanded** | >= 840dp | Tablets grandes / Modo apaisado | Navigation Rail vertical fijo con branding y usuario | Vista dividida de 3 columnas: Rail + Grid de 4 columnas de productos + Panel de Carrito fijo a la derecha |

---

## 6. PREPARACIÓN ESTRATÉGICA PARA EL FUTURO (BSPOS CLOUD)

Las funciones locales de MiCatalogo permiten trabajar desconectado; importaciones, reportes y caja remota requieren conexión. La sincronización sigue estas normas:

1. **IDs Universales (UUID):** Ninguna tabla usa autoincrementales como identificador de dominio; todos los registros se crean con `UUID.randomUUID().toString()`, eliminando colisiones al sincronizar dispositivos independientes.
2. **Marcas de Tiempo UTC:** Todas las marcas temporales se almacenan en milisegundos desde la época Unix (UTC) (`Instant.toEpochMilli()`).
3. **Soft-Delete (`deletedAt`):** Los registros dados de baja no se eliminan físicamente con `DELETE` de SQL durante la operativa normal; se marca su fecha de borrado, permitiendo propagar eliminaciones a otros nodos al sincronizar.
4. **Separación de DataSource:** Los repositorios se programan recibiendo un `LocalDataSource`. Cuando se integre la nube, se agregará un `RemoteDataSource` y un `SyncWorker` sin modificar la capa de dominio ni la interfaz de usuario.
## Alineación financiera Web/API/Android — 2026-10-05

- Contrato oficial y fixtures: docs/API_CONTRACT.md y docs/api-contracts, replicados y probados por serialización en Android.
- Caja conserva aliases web y Android <=1.0.22; nombres canónicos plurales, deuda, egresos, conteo y cierre esperado reales. Campos monetarios críticos no inventan ceros.
- Impuestos informativos fuera de utilidad; descuentos, devoluciones y cobertura por ingresos. Costos y utilidades masked permanecen nullable.
- Gastos permiten pago inicial parcial y abonos; ACK refresca saldo. Cobros conservan allocations, saldo autoritativo y efecto en caja.
- Permisos finance/cash/expenses independientes en API y Android. Finance requiere delegación explícita para vendedores; null heredado no concede acceso nuevo.
- Android Room 21: recibos nullable en payment_sync_outbox y operation_outbox; migración no destructiva. Payload/UUID estable, estados PENDING/SENT/BLOCKED y orden existente respecto a ventas.
- Apertura/cierre remoto requieren conexión; cierre espera colas de la tienda vacías. Caja local POS permanece separada y explícita, no representa una sesión del servidor.
- Updater conserva el mínimo real: opcional antes de iniciar; obligatorio por mínimo o proceso ya iniciado persistente. Mínimo publicado se conserva en 23.
- Pruebas backend: 320 casos / 1602 assertions en SQLite :memory:. Matriz nueva: caja 5000+10000+2000-1500-500=15000; gasto 10000/pagado4000 y abono3000; deuda3000/cobro1500/FIFO/saldo1500; permisos e idempotencia. Wholesale continúa validando snapshot y price_conflict.

Release pública vigente: 1.0.24 (25), mínimo admitido 23. Android: 70 pruebas unitarias y 96 instrumentadas en emulador. Publicación, integridad y límites de evidencia: docs/INVENTORY_IMPORT_RELEASE_1.0.24.md. La matriz financiera previa se conserva en docs/FINANCIAL_ALIGNMENT_RELEASE_1.0.23.md.
- No hay migraciones backend ni cambios de datos de producción en esta entrega.
