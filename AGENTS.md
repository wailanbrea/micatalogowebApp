# Instalación y publicación de MiCatalogo Android

Regla establecida por el usuario el 07/10/2026:

- Emulador Android: usar la variante **debug** para desarrollo y pruebas.
- Celular físico del usuario: instalar únicamente **release**, con paquete `com.bsolutions.micatalogo` y el certificado de release existente.
- Toda publicación de APK debe ser **release firmada**. Nunca publicar debug, test-only, unsigned ni paquetes de pruebas.
- No publicar sin orden expresa del usuario. Instalar localmente no autoriza publicar.
- Antes de instalar, verificar tipo de dispositivo, paquete, versión y certificado de la APK y de la app instalada.
- Si las firmas son incompatibles, detenerse: no desinstalar, limpiar datos ni rotar claves automáticamente. Proteger y verificar los datos locales y solicitar autorización explícita para el reemplazo que los retire del dispositivo.
- Una actualización compatible usa `install -r`, sin `-d`, sin `uninstall` y sin `pm clear`.
- Usar la skill `micatalogo-android-release` para preparar y verificar releases. Mantener las credenciales fuera de Git y de los logs.
- Las pruebas instrumentadas aisladas pueden usar `.offlinecheck` exclusivamente en el emulador.
