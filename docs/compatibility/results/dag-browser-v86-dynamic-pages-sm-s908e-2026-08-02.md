# DAG Browser 86 - paginas dinamicas en SM-S908E

Fecha: 2026-08-02. Dispositivo: Samsung SM-S908E `R5CT717BZTZ`, Android 16.
APK DEV local, sin push ni publicacion. El perfil, la cache y las pestañas se
conservaron; al cierre habia 18 pestañas y DAG mantenia un maximo de tres
sesiones Gecko abiertas. La evidencia es deliberadamente conservadora y no
representa una instalacion limpia.

## Causa raiz

El script de anuncios instalaba un `MutationObserver` global con `subtree` y
recorria cada subarbol agregado. En tiendas dinamicas ese trabajo coincidia con
la construccion inicial del menu y los carruseles. En Mimo, una interaccion ya
asentada no genero inferencias GloshIA. Un candidato que solo bajo la prioridad
de los hilos del modelo empeoro el menu temprano y fue retirado.

DAG 86 elimina ese observador. Conserva el bloqueo de red, revisa una vez los
selectores explicitos y limita la busqueda textual exacta de anuncios a URLs
con ruta o parametros genericos de buscador. No contiene dominios de tiendas ni
modelos de telefono. Tambien habilita el marcado paralelo ofrecido por
GeckoRuntime como ajuste de rendimiento sin cambiar la politica visual.

## Interaccion Mimo

La accion medida fue abrir el menu hamburguesa aproximadamente 1,5 segundos
despues de iniciar la pagina. Base: 26,7 fps. Un candidato descartado con
prioridad de hilos alterada: 21,8 fps. DAG 86, dos repeticiones: 47,8 y 48,6
fps, con 12,8 % y 8,5 % de cuadros tardios; quedaron maximos aislados de 133 y
150 ms. Chrome en el mismo telefono y accion dio 62,5 fps, por lo que la brecha
inicial no esta totalmente cerrada.

Con la pagina asentada, el carrusel midio 55,2 fps hacia adelante y 56,2 fps al
volver. El menu y una expansion de categoria midieron aproximadamente 50,2 y
54,5 fps. No hubo inferencias GloshIA en estas interacciones asentadas.

## Matriz viva

Las celdas siguen el orden pagina / fotos iniciales / visible:

- Mimo: `583-617 / 441-444 / 51-61 ms` en las dos repeticiones tempranas.
- Fravega: `6.949 / 7.722 / 1.657 ms`, PSS 344.950 KiB y RSS 523.284 KiB.
- Cheeky: `4.577 / no valida / 2.024 ms`, PSS 322.066 KiB y RSS 494.920 KiB.
  El evento de fotos llego antes de visible y se descarto como quietud real.

Frente a la muestra DAG 78 del mismo telefono, Fravega tuvo visibilidad
equivalente (`+0,8 %`), fin de pagina 13,5 % mas rapido y fotos 21,2 % mas
rapidas. Cheeky fue 10,5 % mas lento hasta visible y 11 % mas rapido hasta fin
de pagina. Son comparaciones observadas de una muestra, no una promesa global.

Google busqueda se recorrio despues del cambio: no aparecieron rotulos
`Patrocinado` en pantalla ni en accesibilidad. Los resultados comerciales sin
ese rotulo se conservaron porque la apariencia por si sola no demuestra que
sean anuncios. No hubo crash, ANR ni OOM en la matriz. El fixture HTTPS
autofirmado fue rechazado por TLS y no se conto como prueba aprobada.

## Verificacion

- 13 pruebas WebExtension.
- 154 pruebas unitarias Kotlin.
- Ktlint, Lint y APK DEV correctos.
- APK: `121372369` bytes.
- SHA-256: `ea3003d434d2effcb63c9a77c28b7065249c3574bb7376e117ab07f4770b3a10`.
- Android confirma DAG 86 (`0.66.0-dev`) instalado y como navegador
  predeterminado.
