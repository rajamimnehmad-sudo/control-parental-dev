# APP ADMIN — HANDOFF

## Mision

Administrar dispositivos, reglas, solicitudes, tiempos, estado y actualizaciones.

## Estado

- versionCode local 293; distinguir siempre local de publicado.
- `RulesViewModel.kt` supera 2.000 lineas y concentra responsabilidades.
- La fuente inmediata de UI es Room; Backend/Sync transporta cambios.

## Siguientes tickets

1. Mapear responsabilidades de `RulesViewModel` y proponer cortes sin cambiar
   comportamiento.
2. Validar flujos principales y estados vacio/error/pendiente.
3. Revisar deuda de formato solo cuando afecte el ticket activo.
