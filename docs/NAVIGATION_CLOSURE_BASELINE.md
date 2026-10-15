# Cierre de navegación — baseline F0, 15/10/2026

- HEAD Android auditado: `4fe66cfe8e35a127b19630c48188b43f59958250`, main/origin main limpio al iniciar. Backend `f83113180aff61ef5bc184fbea6201d497731c38`.
- No se rehacen navegación canónica, Room27, ShopScope/ShopSwitchLock ni outboxes. Categorías/unidades ya tienen TenantMetadata y DAOs aislados por remote_shop_id/tenancy_status.
- Entry categories exige products en DrawerNavigation y BSPOSMainScreen; CatalogSettings tiene tabs reales pero acciones sin estado de ACK/errores/scope. Servicios-only carece de entrada y backend rechaza category/unit con403. Falta resolver taxonomía segura por tipo.
- Fuentes de sincronización: categorías Web enteras enlazadas mediante client_record_uuid/UUID determinista, unidades en ShopSyncRecord(kind=unit), relación product_unit por UUID. No se crea una tabla Web de unidades paralela.
- Build baseline actual `testDebugUnitTest assembleDebug assembleDebugAndroidTest` PASS:147 JVM,44 XML; perfil all declara201 métodos (no equivale a201 ejecutados).
- APK QA actual 1.0.113-offlinecheck/114, paquete `com.bsolutions.micatalogo.offlinecheck.syncqa`, certificado debug SHA-256 `e266fd48b028dc66dc4e530c0bc8af06b9be820545600c6c4b19c81b4b1ff775`. Emulador5554, instalación compatible `-r` tras respaldo verificado de Room y APK previo1.0.112/113.
- Capturas F0 actuales por owner/manager/physical-only/services-only/accountant/none en teléfono1344×2992/480 y tablet1920×1200/240: método NavigationBaselineTest,1/1 PASS cada formato. Roles sintéticos sales/reports reasignados temporalmente para representar physical/none y restaurados por API. Overrides de pantalla restaurados.
- SQL/Room íntegros respaldados fuera de Git. Room conserva14 productos,14 categorías/unidades,29 clientes,14 stocks,15 movimientos;3 operaciones y1 abono históricos SENT mantienen hash exacto. Cambios de metadata de caché tras login/refresh se documentaron, sin restaurar ni limpiar datos.
- Evidencia central, scripts de auditoría, matrices y todo el detalle: repo Web `docs/navigation/CLOSURE_BASELINE.md`, `CLOSURE_BUGS.md`, `CLOSURE_TODO.md`, `evidence/closure-f0-*`.
- El baseline de captura no certifica los70 NAV ni los7 journeys. El próximo gate es la decisión y fixtures de scopes (F1), seguido de implementación Web/API (F2) y Android (F3).
