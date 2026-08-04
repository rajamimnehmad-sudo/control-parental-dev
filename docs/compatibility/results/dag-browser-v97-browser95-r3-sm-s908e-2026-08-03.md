# DAG Browser 97 — navegador DAG 95 con GloshIA R3

Fecha: 2026-08-03  
Dispositivo: Samsung SM-S908E  
Android: 16  
Paquete: `com.contentfilter.dagbrowser.dev`  
Version: `0.69.1-dev` (`versionCode 97`)

## Alcance

DAG 97 restaura exactamente el navegador de DAG 95 y conserva como unico
cambio funcional la carga de GloshIA R3 con R1 como fallback de apertura. Se
retiraron el cliente de autoactualizacion agregado en DAG 96, su permiso de
instalacion, recursos, menu y pruebas. No cambiaron la extension `1.50.0`, la
politica visual, los umbrales, la carga, las pestañas, los iconos, la
navegacion ni la presentacion.

La numeracion 97 es necesaria para actualizar Android sobre DAG 96; no implica
una nueva arquitectura de navegador.

## Validacion automatica

- Unit tests DEV: OK.
- Ktlint: OK.
- Lint DEV: OK.
- Build DEV: OK.
- R3 SHA-256:
  `0aaa1700182623173c41d233bd0e072cce2b2880aca14430d9f9af43fa2c44a8`.
- R1 fallback SHA-256:
  `2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee`.
- APK: `129942417` bytes.
- APK SHA-256:
  `e2425cb87769ebffec29e3ed90c430cbbf08566c0015657fc4023eae113b1a60`.

## Matriz fisica

- Mimo: `page_visible 78 ms`, `viewport_images_ready 464 ms` y
  `page_analysis_ready 915 ms`; hero, controles e iconos visibles.
- Cheeky: hero, menu, buscador, favoritos y controles visibles; imagenes del
  viewport listas en `7409 ms` durante una carga dinamica con modal propio.
- Fravega: fotos iniciales listas en `344 ms`, estructura visible en `1793 ms`
  y pagina finalizada en `10918 ms`; se recuperaron las miniaturas de categorias
  que en la corrida DAG 96 aparecieron negras.
- Google, control `hombres con traje`: 19 decisiones `allow`, 0 `filter`; fotos
  visibles y trabajo inicial listo en `1124 ms`.
- Google, control `ropa de verano mujer`: 14 decisiones `allow`, 20 `filter`;
  interfaz visible en `110 ms`, trabajo inicial en `334 ms` y pagina terminada
  en `2360 ms`.
- Sin crash ni ANR.

## Resultado

GO como candidato local para revision del propietario. DAG 97 esta instalado
en el SM-S908E. No hubo push, publicacion DEV, Supabase ni Production. DAG 96
continua siendo la version remota publicada hasta una autorizacion separada.

