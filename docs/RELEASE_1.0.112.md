# MiCatalogo 1.0.112 (versionCode113)

Integra navegación canónica del servidor, whitelist/fallback legacy y permisos efectivos por tienda. Catálogo/Fotos, Compras, Cobros, POS crédito y Finanzas comprueban sus permisos independientes antes de leer o encolar operaciones y descartan respuestas tardías al cambiar contexto.

Encargos y Envíos no sincronizan clientes de crédito innecesarios. Pedidos conserva actualización de clientes y listas visibles al refrescar. Dos pruebas nuevas en OrdersEntryTest verifican que no se ejecuta aquella descarga.

Validación de entrega:

- 147 pruebas JVM aprobadas.
- Build debug QA y androidTest compiladas, instaladas con `-r` en emulator-5554.
- Build debug normal 1.0.112 instalada sobre 1.0.27 en el emulador; certificados iguales. No se desinstaló ni se borraron datos.
- Release firmada, paquete `com.bsolutions.micatalogo`, minSDK26, no debuggable, 41.245.004 bytes.
- Certificado SHA256: `5a5670decdac3ee1e2fc95503ae65343c3a1f075f62dec835d26125a578d490f`.
- APK SHA256: `e043645ed2ee02a5697a2001d3b5465ef101978ef4fcb35b6ce1a3b1f2d442c9`.
- Batería instrumentada amplia:183 PASS iniciales. Tres tests de navegación requieren `-e navigationQa true` y ocho casos requieren fixtures/argumentos especiales; no se presentan como aprobados por omisión. Evidencia y repetición correcta en el reporte Web `docs/RELEASE_1.0.112_2026-10-15.md` y `docs/navigation/evidence/`.
- Cierre: **194 identidades instrumentadas únicas PASS**, sin fallos/assumptions pendientes entre las configuraciones QA18768 y QA18767. Se conservan las salidas iniciales y reruns en el reporte combinado del repositorio Web. El arnés de journeys se adaptó al filtro que se vacía al navegar y a Vender/Cobros y abonos; conserva las verificaciones originales de stock, clientes, crédito, caja y aislamiento.

URL release: https://micatalogo.bsolutions.dev/downloads/bspos-1.0.112-release.apk

Regla de entrega conservada: debug exclusivamente para emulador, release firmada para publicación/dispositivo físico. Samsung no conectado en esta sesión. No cambiar la keystore ni desinstalar para forzar una actualización.
