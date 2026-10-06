# MiCatalogo — TODO maestro vigente

Corte: 2026-10-05. Alcance actual: importador adaptativo, preservando funciones comerciales.
Estado: implementado y publicado, sin cambios de datos comerciales de producción.
Historial de fases anteriores en Git y especificaciones de referencia en PROJECT_PLAN.md.

## Fases de esta implementación

- [x] 1. Auditar lector Laravel, sesiones, cuotas, Web, API, DTO, repositorio y UI Android.
- [x] 2. Lectura XLSX/XLS madura y CSV/TXT seguros; hojas reales, formatos, límites y encoding.
- [x] 3. Detección adaptativa de encabezados, aliases y EAN/UPC/SKU con muestras/confianza.
- [x] 4. Normalización/validación: dinero exacto, stock entero, duplicados, categorías y tenants.
- [x] 5. UX común: originales, ejemplos, ignoradas, warnings, conteos y confirmación revisable.
- [x] 6. Web: selección hoja/fila/mapping y remapping con carga temporal privada.
- [x] 7. Android: selección hoja/fila/atributos, DTO aditivo, confirmación por sesión y sync Room.
- [x] 8. Regresiones backend, contratos Android, benchmark 1500 y E2E real en emulador.

## Invariantes conservadas

- Room 21, centavos Long, ACK persistido antes de SENT, UUID estable y dependencias de colas.
- Caja remota requiere servidor; cierre espera operaciones locales de la tienda.
- Permisos y cupos independientes; updater conserva mínimo real y bloqueo iniciado.
- Nunca seeders, purgas ni migraciones destructivas en producción.
- No se suben keystore, archivo privado ni contraseñas al repositorio.
- No se declara publicación o instalación física por el solo éxito de Gradle.

## Publicación verificada

- [x] Web/API y APK firmada 1.0.24 (25) publicadas; mínimo público conservado en 23.
- [x] Composer/Vite/cachés en destino, lectura XLSX y caché database persistente.
- [x] Manifiesto/descarga HTTP, hash/certificado/versión y conteos/IDs comerciales antes/después.
Prueba de instalación en teléfono físico no realizada; no se confunde con publicación verificada.
Estado técnico y resultados: DEVELOPMENT_STATUS.md; contrato vigente: repo backend docs/API_CONTRACT.md.
