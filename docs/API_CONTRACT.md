# Contrato Web/API/Android — auditoría 2026-10-05

Repositorios: micatalogo y micatalogowebApp. API existente `/api/v1`, Android base 1.0.22 (23).

## Comparación terminada antes de modificar código

| Backend / JSON | Kotlin actual | Coincide | Riesgo / corrección |
| --- | --- | --- | --- |
| cash.collections_cash | debt_collections_cash | No | Cobros mostrados como cero. Añadir alias canónico debt_collections_cash, conservar alias web. |
| cash.expected_amount | expected_closing_amount | No | Arqueo cero. Alias canónico y campo requerido en DTO. |
| owner_contributions / owner_withdrawals | owner_contribution / owner_withdrawal | No | Usar plurales; conservar entradas manuales singulares. |
| expenses_cash, supplier_payments, adjustments, total_in/out | Ausentes | No | Añadir y mostrar valores reales. |
| movements_count | No se emite, default Kotlin 0 | No | Emitir conteo real. |
| period.tax_collected, line/general_discounts, revenue_cost_coverage | Ausentes | No | Modelar, mostrar impuestos informativos y cobertura por ingresos. |
| income_statement.gross_sales, discounts, returns, tax_collected | Ausentes | No | Desglose P&L completo; sin sumar impuestos a ganancia. |
| expense.amount_paid, unpaid_amount | Ausentes | No | Modelar saldo; formulario paid_amount, abonos. |
| expense.category en creación / category_name en listado | category_name | No | Normalizar respuestas manteniendo alias temporal. |
| payments.allocations, cash_register_affected, customer_balance, UUID | Se descartan | No | Persistir ACK y presentar facturas / saldo / caja. |
| finance permiso | Android consulta; backend no define | No | Delegación explícita, excluida de permisos heredados por defecto. |
| expenses permiso | Backend solo valida pertenencia | No | Verificar permiso en endpoints y navegación independiente. |
| wholesale_price, sale_mode, 409 price_conflict | Coinciden | Sí | Preservar snapshot e idempotencia, añadir regresiones. |
| catálogo y POS IDs / precios string | Coinciden | Sí | Fixtures del contrato existente. |
| minimum_supported_version_code | Se reemplaza por latest al persistir | No | Persistir mínimo real y mantener required/optional. |
| operaciones financieras | UUID en memoria; llamadas directas | Parcial | Reutilizar OperationOutbox para gastos/abonos/movimientos. Apertura/cierre requieren servidor. |

## Reglas del contrato

Dinero en requests: cadenas decimales de dos cifras; cálculo y snapshots locales en centavos Long. JSON financiero de lectura conserva números compatibles; campos críticos obligatorios fallan explícitamente si faltan, no fabrican cero. Valores masked permanecen null.

Caja: opening_amount + total_in - total_out + adjustments = expected_closing_amount. Egresos del resumen positivos; movimientos individuales firmados. counted_amount/difference nullable. El contado es introducido por la persona, nunca sobrescrito por refrescos.

POST gastos/abonos/movimientos usa client_operation_uuid estable y payload inmutable. POST cobros usa client_transaction_uuid. 409/422/403 => BLOCKED; 401/426/429/5xx o conexión fallida => PENDING y backoff WorkManager. ACK se guarda antes de SENT.

Abrir/cerrar caja remota requiere servidor: no inferir una sesión local equivalente. Los reportes remotos sin conexión deben identificarse como no disponibles; una operación en cola no es un resultado contable confirmado.

Permisos independientes: finance, cash, expenses. Propietario/admin/miembro gestor conservan acceso; vendedor requiere delegación. No conceder finance automáticamente a asignaciones antiguas con permisos null. Aplicar límites del plan además de autorización.

Updater: required si installed < minimum; optional si minimum <= installed < latest; sin actualización si installed >= latest. Un opcional puede posponerse antes de iniciar; tras iniciar, mantener proceso pendiente hasta instalar. Fallo de comprobación sin requerida pendiente permite continuar.

## TODO MASTER

- [x] Fase 1: comparación de rutas, servicios, controllers, DTO, repositorios, UI, outbox y updater.
- [x] Fase 2: caja y contract tests.
- [x] Fase 3: finanzas/P&L/cobertura/masking.
- [x] Fase 4: gastos parciales y abonos.
- [x] Fase 5: cobros y ACK/allocations.
- [x] Fase 6: permisos backend y Android.
- [x] Fase 7: outbox financiero, conflictos y documentación offline.
- [x] Fase 8: política y persistencia updater.
- [x] Fase 9: fixtures compartidos, tests backend/Android/E2E.
- [x] Release final firmada, manifiesto y verificación pública: 1.0.23 (24), mínimo 23. Evidencia en FINANCIAL_ALIGNMENT_RELEASE_1.0.23.md.

## Endpoints y compatibilidad

GET /api/v1/shops/{shop}/cash-sessions/current: permiso cash; aliases collections_cash/expected_amount y owner_contribution/owner_withdrawal se conservan temporalmente para clientes 1.0.22. Nuevos clientes consumen debt_collections_cash/expected_closing_amount y plurales.

GET finance/summary y reports/income-statement/cash-flow: permiso finance explícito para vendedores. GET/POST expenses y POST expenses/{expense}/payments: permiso expenses y límite del plan. Los POST financieros devuelven client_operation_uuid; el ACK de abono incluye amount_paid/unpaid_amount/payment_status actualizados.

POST customers/{customer}/payments: client_transaction_uuid obligatorio; payment_id numérico, amount/customer_balance cadenas, allocations por factura con allocated_cents Long; cash_register_affected y server_timestamp confirmados. Android conserva el JSON del recibo en Room antes de SENT.

Offline: gastos/abonos/movimientos comparten OperationOutbox y secuencia existente con ventas. Cobros conservan PaymentSyncOutbox y dependencias. Reintento de conflicto desde Configuración requiere revisión y no cambia UUID/payload. Apertura/cierre remoto no se encola ni se simula; cierre se bloquea si quedan operaciones locales de la tienda. Caja local POS se identifica por separado.

No se modifica el esquema de la base remota. Room 20→21 añade server_response nullable a ambas colas, conservando filas antiguas. No se usan migraciones destructivas ni seeders en producción.

QA: caja real 15,000 y cierre diferencia cero; gasto 10,000 reconoce 10,000 en P&L y 4,000 efectivo; abono 3,000 deja 3,000 pendiente; deuda 3,000/cobro 1,500 con FIFO y replay sin duplicados; wholesale 1,200 aceptado/1,199 price_conflict 409. Emulador: 74 pruebas de datos y 7 de UI/persistencia/update; teléfono físico no conectado.
