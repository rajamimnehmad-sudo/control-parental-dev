# DAG Browser V3 - puente controlado con Glosh

## Alcance

Este corte agrega un puente unidireccional y opt-in desde App Usuario DEV hacia la APK aislada:

- destino fijo: `com.contentfilter.dagbrowser.dev`;
- actividad fija: `com.contentfilter.dagbrowser.DagBrowserActivity`;
- sin dependencia Gradle entre aplicaciones;
- sin intercambio de tokens, sesiones, historial, reglas ni datos;
- sin cambios en Admin, Supabase, VPN, Accessibility o Production.

## Interruptor

El boton `Probar navegador DAG nuevo` solo se compila cuando
`DAG_BROWSER_V3_BRIDGE_AVAILABLE=true`.

- DEV: apagado por defecto y activable solo al construir explicitamente el candidato.
- Beta: forzado a apagado.
- Production: forzado a apagado.

Si la APK nueva no esta instalada, el intento queda contenido y Glosh no falla. El DAG vigente
permanece disponible y sin cambios.

## Identidad y firma

La APK aislada ya reutiliza el mismo contrato de firma DEV que App Usuario y App Admin:

- `ANDROID_DEV_KEYSTORE_PATH`;
- `ANDROID_DEV_KEYSTORE_PASSWORD`;
- `ANDROID_DEV_KEY_ALIAS`;
- `ANDROID_DEV_KEY_PASSWORD`.

No se guarda keystore, clave ni contraseña en Git. En una maquina sin esas variables el build local
usa la firma debug de Android y sirve solamente para pruebas locales.

## Gate previo a distribucion

1. construir Browser V3 `versionCode 2` con la firma DEV historica;
2. construir App Usuario `versionCode 281` con el puente explicitamente encendido;
3. verificar paquete, version, certificado, SHA-256 y tamaño de ambas APK;
4. instalar primero Browser V3 y luego actualizar App Usuario en el Samsung;
5. comprobar que el boton abre Browser V3 y que volver regresa a Glosh;
6. repetir Google, Fravega, Mimo, Cheeky y `target=_blank`;
7. conservar Device Admin, Accessibility y VPN;
8. no publicar ni habilitar nada en Production.

El flujo cloud actual no contempla un tercer artefacto y no debe reutilizarse de forma improvisada.
Se requiere agregar un target DEV separado y verificar el limite de tamaño antes de subir la APK de
aproximadamente 98 MB.
