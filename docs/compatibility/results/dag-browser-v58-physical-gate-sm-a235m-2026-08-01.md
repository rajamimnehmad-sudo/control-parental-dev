# DAG Browser 58 - gate fisico SM-A235M

Fecha: 2026-08-01

## Alcance y estado final

- dispositivo exacto: Samsung `SM-A235M`, serial ADB `R58T34V31AE`, Android 14;
- APK: `com.contentfilter.dagbrowser.dev`, versionCode `58`, versionName `0.38.0-dev`;
- SHA-256 del APK:
  `1246fb68e45ce5af422b48a417641456b22ae5291098e27737dc5411369a1444`;
- se instalo el candidato construido desde `main` local;
- se borro exclusivamente el perfil DEV de DAG, autorizado por el usuario;
- DAG quedo como titular del rol oficial `android.app.role.BROWSER`;
- el APK temporal `com.contentfilter.dagbrowser.dev.test` se retiro al terminar;
- el telefono quedo en el Inicio seguro de DAG con una pestaña;
- no se toco Chrome, App Usuario, App Admin, Supabase, DEV remoto ni Production;
- no se hizo push.

## Gates automaticos heredados

El artefacto fisico es el mismo aprobado por el gate local:

- 147 pruebas unitarias Kotlin, incluida la prohibicion de excepciones runtime
  por comercio o modelo fisico;
- 19 pruebas WebExtension aprobadas y una DOM externa opt-in omitida;
- Ktlint, Lint, `androidTest` y APK correctos;
- firma, paquete, version y hash verificados.

Detalle: `dag-browser-v58-static-gate-2026-07-31.md`.

## Matriz fisica limpia

Las muestras finales se ejecutaron en frio y sin la acumulacion artificial de
20 pestañas de una primera pasada diagnostica. Cada sitio partio con una o dos
pestañas y uso tres swipes deterministas. Los tiempos estan expresados como
`pagina / fotos visibles / estructura visible`.

| Sitio | Tiempos | Inicio Activity | Cuadros tardios | p95 de cuadro | PSS | RSS |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Mimo | `4.238 / 3.536 / 683 ms` | 795 ms | 1,77 % | 15 ms | 303.087 KiB | 407.224 KiB |
| Fravega portada | `11.774 / 16.232 / 1.099 ms` | 1.363 ms | 1,16 % | 12 ms | 294.383 KiB | 417.072 KiB |
| Cheeky | `no completo / no emitio / 2.231 ms` | 807 ms | 1,17 % | 10 ms | 327.186 KiB | 446.020 KiB |

Cheeky mantiene actividad dinamica durante el scroll y la muestra termino en su
footer, donde no habia imagenes de viewport; por eso no se interpreta la falta
de esa señal como una foto transparente. Una observacion separada, quieta y sin
scroll, emitio `page_analysis_ready=14.198 ms` y mostro correctamente el hero.

Resultados visuales:

- Mimo mostro fotos permitidas en la grilla despues del scroll;
- Fravega mostro su portada funcional y controles; su modal propio de ubicacion
  permanecio operativo;
- Cheeky mostro el hero y los controles de footer; no se observo overlay
  `Analizando` residual;
- la miniatura de pestaña se capturo a `1080x2136`, se redujo y se mostro como
  vista previa real;
- no se observaron crash, ANR, OOM ni salida inesperada. `exit-info` registro
  solamente los `force-stop` deliberados del protocolo y cierres normales de
  procesos Gecko;
- estado termico final `0`; AP 29,2 C, bateria 27,5 C y piel 30,8 C.

Los artefactos crudos viven fuera de Git en:

`/Users/yejielnehmad/Documents/Codex/2026-07-27/estuvimos-trattando-de-hacer/.codex-tmp/dag-v58-clean-sites/`

## Primera matriz diagnostica y limitaciones

Se ejecutaron 18 recorridos vivos adicionales, tres frios y tres calientes por
sitio. Cada intent externo creo una pestaña y la pasada termino con 20. Esa
acumulacion invalida una comparacion fina de caliente y no se usa como cifra
final. Sirvio para detectar el sesgo, verificar estabilidad bajo presion y
confirmar que no habia crash ni temperatura anormal. Las pestañas se cerraron
desde el organizador de DAG sin borrar otra vez el perfil.

La categoria `https://www.fravega.com/l/celulares/` devolvio la pantalla propia
`La pagina no existe` y se excluyo. La portada estable
`https://www.fravega.com/` si se uso.

El fixture local HTTPS no emitio eventos: Gecko rechazo correctamente la hoja
autofirmada y la barrera de DAG mostro su cierre seguro. No se instalo una CA,
no se relajo TLS y esa corrida no se conto. El laboratorio necesita un origen
de prueba con certificado confiable antes de poder ser gate determinista.

## Benchmark TinyCLIP en el A23

El benchmark es un smoke test sintetico, no una prueba de paridad de politica.

| Backend | Resultado |
| --- | --- |
| CPU actual | Correcto. p50 secuencial `130,57 ms`; p95 `134,45 ms`; ronda concurrente p50 `177,14 ms`. |
| XNNPACK | No apto. p50 secuencial `240,06 ms`, aproximadamente 84 % mas lento; ronda concurrente p50 `485,75 ms`; requiere fallback CPU y cambio numerico `0,0052355`, fuera de tolerancia. |
| NNAPI | No apto. El controlador Qualcomm rechazo el grafo en los dispositivos objetivo. |

Decision: conservar ORT CPU con dos hilos intra-op y un hilo inter-op. No activar
XNNPACK, NNAPI, GPU, servidor ni API.

## Conclusion

DAG 58 aprueba instalacion, rol, estabilidad, respuesta tactil, miniaturas y
backend local en el SM-A235M. La implementacion es global para Android 10+
arm64 y no contiene caminos por sitio o dispositivo; el A23 es el piso fisico
medido, no el unico destino. La estructura se vuelve visible en 0,68-2,23 s y
el p95 de cuadro queda en 10-15 ms en las tres muestras limpias. La quietud de
paginas largas sigue dependiendo de la cantidad y mutacion de imagenes del
sitio; no se publica una promesa universal ni una equivalencia con Chrome.

El lote no cambia el modelo ni sus umbrales. Cualquier reduccion de regiones o
permiso amplio para formatos no decodificables requiere un gate de precision
separado porque podria reducir seguridad a cambio de velocidad.
