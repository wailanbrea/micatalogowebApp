# Contrato vigente Web/API/Android

Corte: 2026-10-05. API /api/v1. Público Android 1.0.23 (24), mínimo 23.
Nueva implementación local 1.0.24 (25). Cambios aditivos compatibles con 1.0.23.

## Importación: vista previa

POST /api/v1/shops/{shop}/inventory-import/preview, multipart.
Archivo file: XLSX/XLS/CSV/TXT, máximo 10 MB. Alternativamente upload_token UUID de una
carga anterior de la misma tienda y usuario, dos horas de vigencia.
Opcionales: sheet_index (0-based), header_row (1-based), mapping[field]=source_column,
attribute_columns[]=source_column. Nombre y precio obligatorios; una columna por campo.
XLS es BIFF genuino; no HTML con extensión XLS.

Campos anteriores conservados: session_id, quota, headers, mapping, fields, rows,
valid_rows, invalid_rows, new_rows_count, existing_rows_count, duplicate_rows_count,
missing_categories, mojibake_warning.

Campos aditivos:

| Campo | Significado |
| --- | --- |
| file.type | xlsx/xls/csv/txt |
| sheet | name, index (0-based), confidence |
| sheets | name, index, confidence, header_row y data_rows de cada hoja |
| header_row | Número real de fila de encabezados |
| original_headers | Encabezados originales en el orden de headers |
| header_candidates | Hasta diez candidatos: row/confidence/values |
| needs_header_selection | Confianza insuficiente; requiere fila manual, sin sesión confirmable |
| mapping_confidence | Mapa por campo: field/source_column/original_header/confidence/reason/examples |
| ignored_columns | source_column/original_header/reason/examples |
| warnings | Diagnósticos globales; no sustituyen errors de cada fila |
| sample_rows | Primeras diez filas normalizadas, mismas reglas que rows |
| counts | total/valid/invalid/new/existing/duplicates/missing_categories |
| upload_token | Identificador temporal privado para remapping/hoja/fila |

headers son claves normalizadas únicas. Encabezados repetidos/vacíos reciben column N
para permitir una selección por columna sin colisión. mapping usa esas claves.
Los clientes antiguos pueden seguir mostrando headers; nuevos muestran original_headers.
price/cost_price son strings decimales exactos o null; SKU/barcode strings, nunca números.
stock es int o null; errores no se redondean. rows[].line conserva la fila Excel original.

## Importación: confirmación

POST /api/v1/shops/{shop}/inventory-import con session_id, duplicate_strategy
(skip/update/create), create_missing_categories boolean; también está disponible
POST /api/v1/shops/{shop}/inventory-import/{session}/confirm.
row_actions[line] opcional existente (create/update/skip), validado por el servicio.

Android 1.0.24 envía exclusivamente opciones y session_id; no reenvía rows.
El servidor usa rows_payload almacenado; ignorará filas manipuladas anexadas a una
confirmación por sesión. Mantiene el POST legacy con rows para 1.0.23 y anteriores,
sujeto a validación/cupo/autorización existentes, sin elevar el mínimo compatible.

Respuesta: message, imported (=created+updated), summary
(created/updated/skipped/errors/categories_created), quota actualizada.
Misma tienda y usuario creador; expiración/status; lock de Shop e InventoryImportSession.
Replays retornan el mismo summary. Cupo solo de altas efectivas, nunca updates/skips;
colisión EAN bajo lock se omite antes del cálculo de cupo.
422: formato/mapping/cupo/expiración inválido. 403: permisos, otro usuario o tenant.
Los permisos de gestor y feature bulk_import se verifican en todas las rutas.

## Contrato financiero vigente

Dinero en requests: strings decimales de dos cifras; snapshots/cálculo local en centavos Long.
Lecturas financieras mantienen números compatibles; campos críticos requeridos, masked null.
Caja: opening_amount + total_in - total_out + adjustments = expected_closing_amount.
Nombres canónicos debt_collections_cash/expected_closing_amount/owner_contributions/
owner_withdrawals/movements_count; aliases collections_cash/expected_amount y owner
singular preservados para clientes antiguos. Egresos positivos; movimientos firmados.

P&L separa venta bruta, descuentos, impuestos informativos, ingresos netos, devoluciones,
COGS, comisiones y gastos devengados. revenue_cost_coverage y is_cost_coverage_partial
no se inventan. Flujo de efectivo usa dinero realmente cobrado/pagado.

Gastos: paid_amount inicial, amount_paid/unpaid_amount/payment_status autoritativos,
POST expenses/{expense}/payments para abonos. category y category_name compatibles.
Gastos/abonos/movimientos: client_operation_uuid estable. Cobros:
client_transaction_uuid, payment_id, allocations (allocated_cents), customer_balance,
cash_register_affected y server_timestamp. Android persiste ACK antes de SENT.

409/422/403 → BLOCKED; 401/426/429/5xx o conexión fallida → PENDING/backoff.
OperationOutbox y PaymentSyncOutbox conservan dependencias con ventas; apertura/cierre
remoto no se encola ni se simula y cierre espera colas de tienda vacías.
Caja local POS se presenta aparte de caja remota; reporte sin conexión no inventa cero.

Permisos finance/cash/expenses independientes y límites de plan; propietario/admin/
gestor con acceso, vendedor con delegación. Null antiguo no concede finance.

## Actualizador

X-MiCatalogo-Version-Code obligatorio en rutas móviles protegidas cuando mínimo >0.
installed < minimum → 426/required; minimum <= installed < latest → optional;
installed >= latest → sin actualización. Tras iniciar descarga, proceso persistente
bloqueado hasta instalar; cancelación del instalador se recupera al volver a la app.
Esta entrega no cambia el mínimo 23 ni publica el manifiesto.

## Evidencia

Fixtures financieros en docs/api-contracts; pruebas backend y Android de serialización,
permisos, cuotas, idempotencia y colas. Libro anónimo y E2E de importación descritos en
INVENTORY_IMPORT_ARCHITECTURE.md. Estado y resultados actuales en 10_PROJECT_STATE.md.
