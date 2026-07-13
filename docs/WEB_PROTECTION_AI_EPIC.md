# Epic: Proteccion Web inteligente por capas

## Proposito

Evolucionar la proteccion Web combinando reglas deterministicas, listas firmadas e inteligencia local, sin guardar historial privado ni depender de IA para los casos conocidos.

La implementacion debe conservar el funcionamiento offline, el fast path de policies y las protecciones monotónicas existentes.

## Principios obligatorios

- Resolver primero con reglas y listas locales.
- Ejecutar IA solo para casos desconocidos o dudosos.
- SafeSearch permanece obligatorio.
- No interceptar HTTPS mediante certificados instalados por la app.
- No guardar ni enviar consultas, URL completa, titulo, HTML, contenido visible o historial.
- No permitir que dispositivos o comunidades publiquen directamente una decision global.
- Toda lista global debe publicarse de forma atomica y firmada.
- Toda clasificacion debe admitir `Uncertain`.
- No declarar cobertura total: listas e IA pueden tener falsos positivos y falsos negativos.

## Arquitectura objetivo

1. Bloqueo global y reglas obligatorias.
2. Reglas explicitas del administrador.
3. Allowlist tecnica e institucional.
4. Denylist y listas categorizadas firmadas, incluida UT1.
5. Clasificador local de dominios desconocidos.
6. Clasificador local de busquedas reconocidas.
7. Cache local versionada.
8. Reportes comunitarios minimizados.
9. Revision humana en Super Admin.
10. Publicacion global firmada.
11. Analisis contextual limitado cuando Android lo permita.
12. Anti-evasion de red.

## Ticket 1: cerrar bypass de incognito y DNS

### Objetivo

Chrome normal e incognito deben obedecer la misma policy y las mismas listas.

### Revisar

- DNS normal y cacheado.
- Chrome Secure DNS y DoH.
- DoT.
- QUIC y HTTP/3.
- Sockets y conexiones reutilizadas.
- Navegacion por IP.
- Pestanas restauradas.
- Aplicacion de UT1 en modo Internet abierto.

### Aceptacion

- Un dominio presente en UT1 bloquea en normal e incognito.
- Un dominio ausente no se informa falsamente como bloqueado por UT1.
- Cambiar una lista invalida las conexiones incompatibles.
- Google y SafeSearch siguen funcionando.
- No se corta Internet de otras apps.

### Validacion

Telefono fisico obligatorio.

## Ticket 2: ampliar cobertura de listas

### Objetivo

Complementar UT1 con fuentes mantenidas y con licencia compatible.

### Trabajo

- Evaluar licencia, frecuencia, categorias y calidad de cada fuente.
- Verificar si categorias con nombres distintos son aliases antes de sumarlas.
- Normalizar y deduplicar dominios.
- Conservar confirmacion exacta despues del Bloom Filter.
- Publicar estadisticas reales por fuente y categoria.
- Mantener archivo y manifiesto firmados.
- Conservar la version anterior ante errores.

### Aceptacion

- Fuentes y licencias documentadas.
- Sin estadisticas duplicadas por aliases.
- Falsos positivos de Bloom no bloquean.
- Actualizacion atomica y reversible.

## Ticket 3: modelo comun de decisiones

### Objetivo

Definir una respuesta unica para reglas, listas e IA.

### Modelo

- `Allow`
- `Block`
- `Uncertain`
- `RequireReview`

Cada decision debe incluir categoria, confianza, fuente, version, motivo tecnico y vencimiento cuando corresponda.

### Aceptacion

- Prioridades explicitas y testeadas.
- Una capa no modifica preferencias de otra.
- Una respuesta vieja no pisa una nueva.

## Ticket 4: clasificador local de busquedas

### Objetivo

Detectar intenciones explicitas o intentos de evasion antes de ejecutar una busqueda reconocida.

### Alcance inicial

- Chrome y Google.
- Normal e incognito.
- Procesamiento local.
- Salidas `Allow`, `BlockExplicit` y `Uncertain`.

### Restricciones

- No guardar ni enviar consultas.
- No usar Home ni Atrás repetidamente.
- Evitar bloquear contextos medicos, educativos o religiosos.
- Mostrar una sola vez: `Esta busqueda esta bloqueada`.

### Rendimiento

- Modelo pequeno y cuantizado.
- Inferencia solo cuando una busqueda reconocida cambia.
- Sin proceso de inferencia permanente.

### Validacion

Telefono fisico obligatorio.

## Ticket 5: clasificador local de dominios

### Objetivo

Clasificar dominios que no aparecen en reglas, cache ni listas firmadas.

### Senales permitidas

- Dominio normalizado.
- Tokens del nombre.
- TLD.
- Patrones linguisticos y de evasion.
- Reputacion local.
- Cadena de redireccion conocida sin guardar URL completa.

### Restricciones

- No descargar una pagina solamente para clasificarla.
- No enviar historial.
- `Uncertain` debe ser una salida valida.
- Guardar resultado con version del modelo y TTL.

## Ticket 6: cache local de reputacion

### Datos

- Hash o clave normalizada del dominio.
- Categoria.
- Confianza.
- Fuente.
- Version del modelo o lista.
- Fecha y expiracion.

### Aceptacion

- Invalidacion al cambiar modelo o decision global.
- Tamano acotado.
- Limpieza automatica.
- Sin URL, consulta ni historial.

## Ticket 7: reportes comunitarios privados

### Objetivo

Agrupar evidencia sobre dominios desconocidos sin identificar navegacion individual.

### Reporte minimo

- Dominio normalizado o identificador seguro definido por el backend.
- Categoria sugerida.
- Confianza.
- Version del modelo.
- Ambiente.
- Marca temporal aproximada.

### No enviar

- Usuario o identidad directa del dispositivo.
- Consulta.
- URL completa.
- Titulo.
- HTML.
- Historial.

### Seguridad

- Rate limiting.
- Deduplicacion.
- Diversidad de dispositivos y comunidades.
- Deteccion de abuso.
- Ninguna publicacion automatica por mayoria simple.

## Ticket 8: cola de revision en Super Admin

### Ruta

`Super Admin -> Proteccion Web -> Revision de sitios`

### Vistas

- Pendientes.
- Aprobados.
- Permitidos.
- Rechazados.
- Publicados.
- Retirados.

### Mostrar

- Dominio.
- Categoria sugerida.
- Confianza.
- Cantidad agregada de reportes.
- Dispositivos y comunidades independientes.
- Primera y ultima deteccion.
- Version del modelo.
- Estado y riesgo.

### Acciones

- Aprobar categoria.
- Bloquear globalmente.
- Permitir globalmente.
- Cambiar categoria.
- Solicitar mas evidencia.
- Descartar.
- Retirar una decision.

### Seguridad

- Permiso exclusivo de Super Admin.
- Confirmacion antes de publicar.
- Auditoria de actor, fecha y cambio.
- Rollback.
- Doble aprobacion para dominios criticos.
- Proteccion especial para salud, educacion, finanzas y servicios publicos.

## Ticket 9: publicacion comunitaria firmada

### Flujo

1. Seleccionar decisiones aprobadas.
2. Normalizar y deduplicar.
3. Generar una version inmutable.
4. Firmar archivo y manifiesto.
5. Publicar primero el archivo.
6. Publicar el manifiesto al final.
7. Android verifica firma, checksum, ambiente y version.
8. Reemplazo local atomico.
9. Conservar la version anterior ante errores.

### Super Admin

Mostrar version, cantidades, altas, retiros, firma, checksum, fecha y ultimo error.

## Ticket 10: politica de casos dudosos

### Opciones por comunidad

- Permitir y reportar.
- Bloquear y permitir solicitud.
- Mostrar advertencia.
- Requerir aprobacion del administrador.

### Aceptacion

- No modifica bloqueo global ni otras preferencias Web.
- Funciona offline.
- La App Usuario muestra el estado efectivo sin controles editables.

## Ticket 11: analisis contextual limitado

### Senales posibles

- Host.
- Titulo o metadatos expuestos legitimamente.
- Categoria accesible.
- Estructura general del arbol Accessibility.

### Restricciones

- No instalar certificados.
- No interceptar HTTPS.
- No guardar HTML ni contenido visible.
- No depender de una senal que Chrome no expone de manera confiable.
- Documentar limitaciones por navegador.

## Ticket 12: anti-evasion

### Cubrir

- DoH y DoT.
- QUIC cuando evite aplicar policy.
- Proxies y VPN externas.
- Dominios espejo.
- Redirectores y acortadores.
- Navegacion por IP.
- Pestanas restauradas y conexiones previas.

### Aceptacion

- Chrome permanece abierto.
- No apagar Wi-Fi ni datos.
- No afectar apps fuera del alcance de la VPN.
- Accessibility no es la barrera principal.

### Validacion

Telefono fisico obligatorio.

## Ticket 13: observabilidad y privacidad

### Metricas permitidas

- Etapa y duracion.
- Categoria y confianza.
- Fuente y version.
- Decision y error tecnico.
- Cantidades agregadas.

### Prohibido registrar

- Consultas.
- URL completa.
- Titulo.
- HTML.
- Historial.
- Contenido visible.
- Datos personales.

## Orden recomendado

1. Ticket 1: bypass de incognito y DNS.
2. Ticket 3: modelo comun de decisiones.
3. Ticket 2: fuentes complementarias.
4. Ticket 6: cache local.
5. Ticket 5: IA local de dominios.
6. Ticket 4: IA local de busquedas.
7. Ticket 7: reportes comunitarios.
8. Ticket 8: revision Super Admin.
9. Ticket 9: publicacion firmada.
10. Ticket 10: politica de dudosos.
11. Ticket 12: anti-evasion avanzado.
12. Ticket 11: analisis contextual.
13. Ticket 13: observabilidad final.

No implementar varios tickets grandes en un mismo cambio. Completar, probar y cerrar cada ticket antes de iniciar el siguiente. Publicar APK solamente cuando el ticket cambie Android y pase su validacion correspondiente.

## Uso del telefono fisico

No hace falta mantenerlo conectado durante todo el epic.

Conectarlo para validar los tickets 1, 4, 5, 9, 11 y 12. Es especialmente importante para incognito, DNS cifrado, QUIC, rendimiento, consumo y aplicacion efectiva.

## Prompt para iniciar otro chat de Codex

```text
Toma docs/HANDOFF_ACTUAL.md como contexto oficial.
Lee AGENTS.md, START_HERE.md, docs/AREAS.md y docs/WEB_PROTECTION_AI_EPIC.md antes de trabajar.

Usa exclusivamente /Users/yejielnehmad/Developer/content-filter.
No uses copias del repositorio ubicadas en Documents.
Trabaja solamente contra Supabase DEV. No tocar Production.
No usar Service Role Key en Android.
No borrar datos sin confirmacion explicita.
No revisar todo el repositorio ni reanalizar la arquitectura Android completa.

Objetivo inicial:
Ejecutar unicamente el Ticket 1 de docs/WEB_PROTECTION_AI_EPIC.md: cerrar el bypass de incognito y DNS.

Contexto confirmado:
- App Usuario DEV 186.
- Google funciona en modo Internet abierto con SafeSearch obligatorio.
- La lista UT1 firmada esta instalada y el canario DEV se bloquea.
- Un dominio presente en UT1 puede abrir en Chrome incognito.
- Para un dominio ausente de UT1, la VPN registra Allow / no-blocking-rule; no confundir falta de cobertura con bypass.
- UT1 adult y porn son aliases en el servidor configurado; no duplicar categorias ni estadisticas.

Diagnosticar de punta a punta:
Chrome normal/incognito -> DNS/DoH/DoT -> cache -> QUIC/HTTP3 -> VPN -> PolicyEngine -> lista dinamica.

No implementar IA, fuentes nuevas, reportes comunitarios ni cambios de Super Admin en este ticket.
No ampliar el alcance salvo una dependencia directa demostrada.

Privacidad:
- No registrar ni entregar consultas, URL completa, titulo, HTML, contenido visible o historial.
- Redactar dominios en salidas de diagnostico salvo un dominio canario o de prueba explicitamente autorizado.

Al empezar:
1. pwd
2. git status --short
3. verificar que no existan .gradle, .gradle-home ni app-user/build

Trabajo esperado:
- inspeccionar la implementacion actual;
- reproducir con telefono fisico si esta conectado;
- encontrar la causa exacta;
- implementar el cambio minimo y robusto;
- agregar tests;
- ejecutar solamente los tests y builds del area afectada;
- no publicar hasta validar normal e incognito en dispositivo real.

Si cambia Android:
- incrementar versionCode;
- compilar y probar App Usuario DEV;
- commit y push;
- publicar solamente App Usuario DEV;
- verificar manifiesto, URL y SHA-256 descargado.

Al cerrar:
- actualizar docs/HANDOFF_ACTUAL.md;
- verificar CI;
- dejar el worktree limpio;
- verificar que no existan .gradle, .gradle-home ni app-user/build.

Entregar:
- causa raiz exacta;
- diferencia entre falta de cobertura UT1 y bypass de incognito;
- archivos modificados;
- tests y prueba fisica;
- versionCode;
- commit;
- publicacion y SHA-256;
- limitaciones restantes.
```

