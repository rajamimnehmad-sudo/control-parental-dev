# AI NEXT TICKET

## MANUAL-MODE-NO-AUTORUN

**Estado:** INACTIVO / NO EJECUTAR AUTOMÁTICAMENTE

El flujo operativo vigente de Glosh es manual:

1. ChatGPT resuelve directamente todo lo que pueda sin Codex.
2. Cuando haga falta trabajo local/Mac, ChatGPT entrega al usuario un ticket listo para copiar.
3. El usuario pega ese ticket manualmente en Codex.
4. Codex ejecuta exactamente ese ticket y deja PR/handoff en GitHub.
5. El usuario vuelve a ChatGPT y dice `ya`.
6. ChatGPT revisa el trabajo real en GitHub y decide el siguiente paso.

Este archivo **no es un trigger de ejecución** y ningún watcher/runner debe iniciar Codex a partir de su contenido.

El autorun anterior queda fuera del flujo normal. No ejecutar trabajos físicos, ADB, APK, Production, deploy, merges, borrados ni gastos desde este archivo.
