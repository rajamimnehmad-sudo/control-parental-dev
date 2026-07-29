# DAG Browser V3 - piloto de dataset 05B

## Estado

`En progreso; gate de metadatos completo y descarga de pixeles bloqueada`.

El sondeo del 2026-07-29 valido el inventario acotado y encontro un bloqueo de derechos y diversidad
antes de descargar. No se descargo ninguna imagen, no se creo un split y no se inicio etiquetado,
entrenamiento, GPU, Supabase ni Production.

## Contrato aplicado

- Politica: `DAG_BROWSER_V3_MODEL_DATASET_CONTRACT.md`, cierre 05A.
- Consultas: 10 frases de vestimenta, presentacion y negativos dificiles.
- Catalogos: Openverse y Wikimedia Commons.
- Limite usado: una pagina de 10 resultados por consulta y catalogo.
- Red: solamente APIs de metadatos; no se solicito ninguna `asset_url`.
- Datos temporales: dos JSONL fuera del repositorio, sin cookies, credenciales ni datos Glosh.

Las consultas cubrieron manga larga, sin mangas, crop top, leggings, falda a la rodilla, cuello alto,
retrato femenino, traje masculino, ropa sin persona e interior de tienda. Son terminos de
descubrimiento, no etiquetas ni decisiones humanas.

## Resultado agregado

| Medida | Openverse | Wikimedia | Total |
| --- | ---: | ---: | ---: |
| Candidatos | 100 | 92 | 192 |
| Paginas solicitadas | 10 | 10 | 20 |
| Descartados por el inventario | 0 | 8 | 8 |
| CC BY | 69 | 34 | 103 |
| CC BY-SA | 20 | 19 | 39 |
| CC0 | 8 | 12 | 20 |
| Dominio publico | 3 | 27 | 30 |
| Creador faltante | 7 | 9 | 16 |
| URL de licencia faltante | 0 | 39 | 39 |
| Restriccion adicional informada | 0 | 10 | 10 |

No hubo `landing_url` exacta repetida entre los dos inventarios. Eso no demuestra ausencia de
duplicados visuales; SHA-256, dHash y clustering requieren primero una descarga autorizada.

La procedencia subyacente quedo concentrada:

- Wikimedia: 97 candidatos, contando cinco descubiertos tambien mediante Openverse;
- Flickr: 87;
- Rawpixel: 7;
- Brooklyn Museum: 1.

El inventario completo no puede cumplir por si solo el maximo de 40 % por fuente del piloto
elegible. Seleccionar menos filas tampoco crea cobertura dirigida suficiente desde Rawpixel o
Brooklyn Museum.

## Bloqueo antes de pixeles

1. Los 39 candidatos CC BY-SA quedan fuera de descarga y splits hasta una revision legal separada.
2. Los 192 candidatos requieren verificar pagina original, licencia y pertinencia visual.
3. Todo candidato conserva `personality_rights_must_be_reviewed`; las fotos con personas no
   acreditan por si solas autorizacion para uso comercial de su imagen.
4. Dieciseis candidatos no informan creador, 39 no informan URL de licencia y diez declaran
   restricciones adicionales.
5. La concentracion Wikimedia/Flickr supera el limite de diversidad fijado en 05A.
6. Algunos titulos del inventario mencionan menores, familias o contenido sexualizado. Se excluyen
   antes de cualquier descarga y no se enumeran ni conservan como seleccion.

Por estas razones no existe todavia un primer tramo de 20 que pueda declararse elegible. Descargarlo
solo para mirar trasladaria el riesgo a una carpeta temporal sin resolver procedencia ni derechos.

## Correccion preventiva del tooling

El inventario puede conservar BY-SA como descubrimiento para una eventual revision legal. El
descargador piloto ahora acepta exclusivamente `by`, `cc0` y `pdm`; un candidato `by-sa` falla antes
de resolver o solicitar su URL. La regresion queda cubierta por la suite local.

## Proximo desbloqueo recomendado

Prioridad:

1. conseguir material propio o encargado con permiso escrito para entrenamiento, evaluacion,
   derivados, pesos y uso comercial;
2. sumar una tercera fuente primaria cuya licencia y derechos puedan verificarse por muestra;
3. usar fuentes publicas con personas identificables solo despues de definir una revision legal de
   derechos de imagen aplicable al producto y jurisdicciones objetivo;
4. reservar Wikimedia/Openverse actuales para ropa sin persona, objetos, tiendas y negativos
   dificiles cuando la licencia individual quede completa.

Hasta resolver al menos una de estas rutas, 05B permanece en progreso y 05C no se inicia.
