# Informe final — importador adaptativo / Android 1.0.24

Corte: 2026-10-05. Implementación local terminada y probada en ambos repositorios.
No publicada en VPS; el manifiesto público continúa 1.0.23 (24), mínimo 23.
No se alteraron datos de producción ni se ejecutaron seeders/migraciones backend.

## Diagnóstico y decisiones

Se corrigió el lector que suponía encabezados en primera fila y sheet1.xml, no conservaba
formatos de ceros, elegía delimitador con una línea y redondeaba cantidades fraccionarias.
Android tenía confirmación por filas y un cálculo de cupo que contaba también updates.
Las sesiones existentes carecían de recheck de idempotencia bajo el lock y validación del creador.
Se preservó la lógica de negocio existente; el grafo disponible no cubría estas rutas recientes,
por lo que las relaciones se comprobaron en servicios, controllers, rutas, modelos y repositorios.

Se eligió PhpSpreadsheet 5.10.0 frente al ZIP/XML manual: XLS real, relaciones de hojas,
tipos/shared strings/rich text y máscaras de ceros. No Laravel Excel. Solo lectores Xlsx/Xls,
sin evaluar fórmulas externas; las fórmulas usan valores guardados y generan advertencia.
Composer audit final: sin vulnerabilidades conocidas. PHP requiere BCMath para dinero exacto.
Referencia técnica: https://phpspreadsheet.readthedocs.io/en/latest/topics/reading-files/

## Cambios entregados

- Backend: WorkbookReader y ColumnDetector; sesiones y confirmación existentes fortalecidas.
- Web: hoja/fila/mapping revisables sin segunda carga; originales/confianza/razones/ejemplos,
  ignoradas/atributos opt-in, muestra de diez filas y acceso al diagnóstico de filas inválidas.
- Android: metadatos opcionales, selección de hoja/fila, mapping/atributos, warnings/conteos,
  dinero como String y confirmación solo por session_id. Sin migración Room; sync posterior.
- Duplicados/cupos: barcode→SKU→nombre; colisión EAN bajo lock antes del cupo; updates/skips
  no consumen productos. Sesión/tienda/creador/expiración verificados, replay idempotente.
- Enriquecimiento de imágenes opcional: un fallo posterior al commit no devuelve un 500 engañoso.
- Documentación actualizada en lugar de acumular snapshots contradictorios.

## Reglas, formatos y riesgos

XLSX, XLS BIFF, CSV y TXT. Separadores coma/punto y coma/tab/pipe; UTF-8/BOM y conversión
Windows-1252 con aviso. Aliases español/inglés/abreviaturas en ColumnDetector::ALIASES.
Scan hasta fila 50, scoring de aliases/nombre/precio-costo-stock, muestras hasta 50 valores.
Código ambiguo: ≥80% EAN/UPC/GTIN (8/12/13/14 dígitos) → barcode; resto → SKU.
Confianza alta ≥0.85, media ≥0.60, baja requiere revisión, con override manual.
NFKC/puntuación/BOM normalizados; originales preservados; mojibake reparado solo en comparación
cuando el round-trip es UTF-8 válido y se advierte. Mapping guardado no pisa detección nueva.

Costo/Precio inventario y totales no son valores unitarios; desconocidas no crean atributos
sin opt-in. Una columna no alimenta dos campos. Nombre/precio obligatorios.
Moneda/separadores latinos se convierten a decimal de dos cifras con BCMath, sin float final;
stock 10/10.0/10,0 válido, 10.5 inválido. Negativos y valores fuera del DECIMAL(12,2) rechazados.
EAN/UPC/SKU son strings. Ceros numéricos se recuperan con máscara 000…; precisión/ceros ya
perdidos en Excel no son recuperables y generan advertencia. Barcodes con letras o notación
científica se rechazan, no se convierten quitando letras. Longitud no estándar se advierte.

10 MB, 5000 productos, 5050 filas físicas, 100 columnas, 20 hojas, 100000 celdas dimensionadas,
16000 caracteres/celda. ZIP bounded por entradas/tamaño/ratio; XML a cargo del lector actualizado.
Archivos temporales cacheados dos horas por creador/tienda; logs sin volcar filas completas.
ODS/PDF/imágenes/Word/HTML disfrazado de XLS no admitidos. Caché Web debe persistir entre requests.
Clientes 1.0.23 conservan contrato legacy; nuevos campos aditivos y mínimo compatible intacto.
No se afirma protección ilimitada frente a todo archivo arbitrariamente complejo: los límites
son deliberados y un inventario mayor debe dividirse. No se inventan ceros ni resultados de fórmulas.

## QA ejecutado

| Validación | Resultado |
| --- | --- |
| Backend completo, SQLite :memory: | 343 pruebas / 1745 aserciones, todas pasando |
| Adaptativo XLSX/XLS y casos nuevos | 23 pruebas / 143 aserciones |
| Regresiones importador previas | 16 pruebas / 127 aserciones |
| Android unit tests | 70, sin fallos |
| Android instrumentación completa | 96, sin fallos en las dos últimas ejecuciones |
| Vite producción | build correcto |
| Pint / diff whitespace | correcto |
| Composer validate / audit | válido, sin avisos de vulnerabilidad |
| APK firma/package/versión/debuggable | verificado y certificado comparado con APK pública anterior |

Caso obligatorio: libro anónimo con hoja de instrucciones y Existencias, encabezados en fila 8,
EAN 0850050062035, UPC 812256024194 y SKU 000045. Mapping detecta Cant., Costo unitario y
Precio unitario; ignora Creado y totales. Prueba Retrofit multipart real desde emulador
Pixel_10_Pro_XL_2 → Laravel local aislado → diálogo Compose → confirmación/replay.
Ni archivo editado manualmente ni consultas a producción para crear fixtures.

Otros casos: títulos/filas vacías, inglés/abreviaturas, multihoja, comillas/multilínea y cuatro
delimitadores, BOM/Windows-1252/mojibake/NFKC, quince columnas desconocidas, duplicación de
encabezados, mapping manual/guardado, atributos opt-in, precisión/dinero/stock, ZIP bomb,
XLS falso, caducidad/creador/tenant, replays de objetos obsoletos y colisiones EAN/cupo.

Una ejecución temprana amplia tuvo un fallo de foco Espresso en una prueba del updater;
pasó aislada y luego dos suites completas de 96 pasaron. No se atribuyó ese fallo transitorio
al importador ni se cambió el updater para ocultarlo. Los avisos SDK duplicado/JDK target
Kotlin son del entorno, no fallos de la build.

## Rendimiento

1500 productos XLSX, 12 columnas, segunda hoja/fila 8: cuatro consultas en preview.
Lectura+detección+validación+sesión: normalmente 0.37–0.55 s, hasta 1.21 s bajo builds
simultáneas. Memoria retenida después de la carga: delta 2 MB; pico del proceso de suite
100–112 MB. Incluye infraestructura/test fixtures, no RSS aislado ni confirmación de 1500 altas.
No se hicieron afirmaciones de rendimiento de producción sin medir allí.

## Artefacto Android final

- Versión: 1.0.24 / versionCode 25; paquete com.bsolutions.micatalogo; minSdk 26.
- Archivo: C:/Users/waila/AndroidStudioProjects/micatalogowebApp/app/build/outputs/apk/release/app-release.apk
- Tamaño: 16492372 bytes.
- SHA-256 APK: c979c168311563637b50149678f93461da3eb349951a77a9436262cbd63e9649.
- Certificado SHA-256: 5a5670decdac3ee1e2fc95503ae65343c3a1f075f62dec835d26125a578d490f.
- Firma verificada, no debuggable; misma identidad/certificado que 1.0.23 pública.
- Las 70 pruebas unitarias pasaron sobre el código final antes del ensamblado firmado;
  en el último ensamblado se omitió únicamente repetir esas mismas pruebas.
- No publicado ni instalado en teléfono físico. El teléfono no apareció en adb.

## TODO y alcance cerrado

Las ocho fases de implementación/QA están completas en docs/09_TODO.md y TODO Android.
No quedan fixes conocidos pendientes del importador en esta entrega local.
Publicación, despliegue Composer y prueba de actualización en teléfono son un paso operativo
separado que no se ejecutó en esta tarea. No se cambió el manifiesto público ni el mínimo 23.
Reglas completas: INVENTORY_IMPORT_ARCHITECTURE.md y API_CONTRACT.md.
