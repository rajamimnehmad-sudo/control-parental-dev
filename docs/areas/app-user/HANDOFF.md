# APP USUARIO — HANDOFF

## Mision

Experiencia del telefono protegido: activacion, estado, apps, Internet,
solicitudes, ayuda y actualizaciones.

## Estado

- versionCode local 311; confirmar manifiesto remoto antes de llamarla publicada.
- Integracion DAG reconoce produccion, DEV y Diagnostic. El antiguo paquete
  LAB fue retirado de la confianza, runners y documentacion operativa.
- Si DEV no esta instalado, el acceso a DAG queda indisponible sin abrir otra
  app ni asumir un paquete alternativo.
- La UI local se apoya en Room; sync remoto no debe bloquear la experiencia.

## Limites

- Motor Accessibility/VPN pertenece a Proteccion Android.
- Licencias, auth y contratos remotos pertenecen a Backend.

## Siguientes tickets

1. Inventario UX de estados rotos, mensajes tecnicos y pantallas incompletas.
2. Dividir componentes que superen el umbral de tamaño cuando sean tocados.
