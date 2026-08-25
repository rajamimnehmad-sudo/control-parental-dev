# DEV FLOW

Flujo operativo para validar cambios con el menor costo razonable. `AGENTS.md` y `START_HERE.md` mandan si una regla historica contradice este archivo.

## Principio central

Separar siempre tres cosas:

1. **Validacion tecnica**: codigo + tests/build + gate fisico cuando corresponda.
2. **Review/preservacion**: commits cohesivos + rama `review/*-final` para auditoria ChatGPT.
3. **Integracion/publicacion**: PR/merge/main/publicacion DEV/Production, solo como paso posterior cuando realmente se autoriza/necesita.

Un PASS tecnico NO obliga a integrar ni publicar producto.

## Flujo normal de un lote

1. Leer solo el contexto necesario (`START_HERE.md`, Central/AREAS si aplica y el ticket).
2. Confirmar owner, base, rama/worktree y rutas cuando exista trabajo paralelo.
3. Diagnosticar causa raiz.
4. Implementar un lote cohesivo; no fragmentar artificialmente ni mezclar temas no relacionados.
5. Ejecutar tests/checks proporcionales al diff y al riesgo.
6. Si el cambio necesita APK fisica, asignar `versionCode` DEV vigente (maximo real + 1), compilar e instalar desde el worktree validado.
7. Ejecutar gate fisico/lab solo cuando el riesgo o el ticket lo requiera; reutilizar evidencia anterior que el diff no invalida.
8. Dejar commits locales claros (preferentemente funcional + evidencia).
9. En PASS tecnico, publicar en el mismo ticket una rama `review/*-final` y verificar el SHA remoto.
10. ChatGPT revisa diff/codigo/tests/evidencia y actualiza Central.
11. PR/merge/main/publicacion se hacen solo si forman parte del siguiente paso autorizado; no son requisito del PASS tecnico.

## Cuando NO ejecutar Android/Gradle

Si solo cambian docs, reglas, prompts, mapas o notas:

- no compilar;
- no tests Android por costumbre;
- no incrementar `versionCode`;
- no generar/publicar APK;
- `git diff --check` es suficiente salvo necesidad especial.

Scripts/SQL/workflows se validan con checks propios cuando corresponda, no con un APK irrelevante.

## Build/test proporcional

Usar el comando mas estrecho que pruebe el cambio y sumar gates solo cuando el impacto lo exige.

| Cambio | Validacion minima orientativa |
| --- | --- |
| Solo docs | `git diff --check` opcional |
| App Admin UI/ViewModel | `:app-admin:testDevDebugUnitTest`, `:app-admin:compileDevDebugKotlin`; assemble/lint si el lote cierra o genera APK |
| App Usuario UI/ViewModel | `:app-user:testDevDebugUnitTest`, `:app-user:compileDevDebugKotlin`; assemble/lint si el lote cierra o genera APK |
| `feature-vpn` | tests/compile del modulo + app-user cuando el cambio entra al APK; gate fisico si afecta transporte/fail-close |
| `feature-accessibility` | tests/compile del modulo + app-user; gate fisico si afecta servicio/lifecycle/proteccion |
| `core-policy` | tests del core + consumidores afectados; no compilar ambas apps si una no consume el cambio relevante |
| `core-data`/`core-database` | tests/modulo + apps realmente afectadas; revisar migrations/schema si aplica |
| `core-sync`/`core-network` | tests del modulo/consumidor; validar realtime/outbox solo si el diff los toca |
| Chrome/navegador/medios | tests dirigidos + app-user build + fixture/gate fisico proporcional |
| Cambios compartidos | validar cada consumidor real; no asumir `both` si uno no entra en el camino modificado |

`ktlint`, lint y suites globales se usan cuando aportan valor de cierre. Una deuda preexistente fuera del diff se reporta y se aisla; no convierte automaticamente un cambio limpio en BLOCKED.

## APK y versionCode

Usuario y Admin tienen secuencias DEV independientes. DAG tambien mantiene su propia version cuando corresponda.

- Incrementar `versionCode` solo de la app cuyo APK cambia y cuando se vaya a generar una nueva APK identificable para gate/distribucion.
- Determinar el maximo DEV real vigente antes de elegir el numero; no asumir que `N+1` sigue libre por memoria.
- No reutilizar un `versionCode` ya usado/publicado para esa app.
- Mantener `versionName` salvo necesidad explicita.
- Una instalacion de gate fisico puede hacerse desde el worktree validado (`adb install -r`) sin fusionar primero a `main`.
- Preservar datos/Device Owner/Accessibility y otras precondiciones cuando el ticket lo requiera; no usar uninstall/clear/reset salvo autorizacion especifica.

## Gate fisico

Es obligatorio/proporcional cuando el cambio depende de comportamiento que JVM/build no puede acreditar razonablemente, por ejemplo:

- navegador/render/captura/medios;
- VPN/HEV/DNS/UID/fail-close;
- Accessibility/Device Owner/lifecycle/process death;
- compatibilidad OEM/dispositivo;
- rendimiento o memoria que dependa de hardware.

No repetir una matriz completa si un follow-up toca un area aislada y existe evidencia previa valida. Ejecutar el smoke minimo que demuestre la correccion y ausencia de regresion relevante.

Al terminar, limpiar solo recursos creados por el gate y acreditar rollback cuando corresponda. No limpiar trabajo ajeno.

## Review branch

Para un PASS tecnico que ChatGPT deba revisar:

- commit funcional cohesivo;
- commit de evidencia/docs separado cuando ayude;
- push automatico/preautorizado de `review/<task>-final` al SHA exacto validado;
- verificar origin;
- reportar base SHA, functional SHA, final/evidence SHA, version/APK/hash (si existe), gates, fisico y residuales.

No pedir una segunda interaccion Codex solo para hacer este push.

Una rama review es una superficie de auditoria/preservacion; **no autoriza merge ni publicacion**.

## Publicacion DEV de producto

Publicar una APK a usuarios/Supabase es distinto de construirla o instalarla en laboratorio.

Solo publicar cuando el ticket/paso de entrega lo autorice. Entonces:

1. determinar app(s) afectadas;
2. confirmar codigo exacto revisado/integrado que se va a distribuir;
3. confirmar `versionCode`, firma y notas;
4. ejecutar el workflow/script de publicacion correspondiente;
5. verificar manifests publicos, package, version, hash y firma;
6. actualizar Central/entrega con la evidencia real.

No publicar automaticamente por el solo hecho de que cambie un archivo Android o que un build pase.

Documentacion especifica de publicacion:

- `docs/DEV_APK_UPDATES.md`
- `docs/PUBLICAR_DEV_REMOTO.md`
- scripts `scripts/publicar_dev.sh` / `scripts/publicar_dev_app.sh` cuando el paso este autorizado.

## CI / GitHub Actions

- CI es evidencia complementaria; usar el workflow que corresponda al alcance.
- No disparar/esperar suites globales por costumbre si el ticket no las necesita.
- Si una publicacion/integracion depende de CI, verificar el run antes de cerrar ese paso.
- Un warning heredado no bloquea si el check relevante pasa y no es causado por el diff.

## Cierre eficiente

Un lote se considera tecnicamente listo para revision cuando informa:

- causa/objetivo;
- base/rama/worktree cuando aplica;
- archivos modificados;
- tests/checks ejecutados;
- APK/version/hash si hubo gate Android;
- evidencia fisica/lab si corresponde;
- rollback/estado final cuando corresponde;
- residuales reales;
- rama review y SHAs remotos.

ChatGPT decide despues PASS FINAL, follow-up o cambio de ruta y sincroniza Central.

No actualizar por rutina `HANDOFF_ACTUAL.md` y `BACKLOG_PRODUCTO.md` en cada microcambio. Actualizarlos solo cuando el cambio de contexto/producto tenga valor persistente; Central es el tracker estructurado principal.

## NO TOCAR sin necesidad del ticket

- Production/deploy/secrets.
- Supabase Auth/config sensible.
- Room migrations/schemas ajenos.
- scripts/workflows de publicacion si el ticket no es de entrega.
- areas/hotspots no relacionados.
- trabajo de otro owner.
