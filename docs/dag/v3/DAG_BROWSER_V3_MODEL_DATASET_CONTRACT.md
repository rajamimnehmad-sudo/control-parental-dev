# DAG Browser V3 - contrato de modelo y dataset

## Estado

Contrato de trabajo previo al primer dataset. No autoriza descargar imagenes, contratar GPU,
incorporar pesos ni habilitar `allow` o `blur`.

## Separacion obligatoria

Se versionan por separado:

- `signal_contract_version`: orden y significado de las salidas visuales;
- `dataset_version`: muestras, procedencia, splits y etiquetas;
- `model_version`: arquitectura, pesos, entrenamiento y SHA-256;
- `calibration_version`: transformacion de logits a probabilidades;
- `policy_version`: umbrales y acciones de Glosh;
- `runtime_version`: LiteRT y configuracion de ejecucion.

El modelo predice hechos visuales acotados. No decide si una imagen se muestra, no infiere identidad,
religion, estado civil ni intencion real de una persona. Glosh transforma las probabilidades en
`allow`, `uncertain`, `blur` o `block` mediante una politica determinista y auditable.

## Contrato tensorial V1

La fuente canonica y legible por maquinas es
`docs/dag/v3/glosh-visual-signals-v1.json`.

- Entrada logica: RGB `uint8`, `1 x 224 x 224 x 3`.
- Geometria: imagen completa ajustada con letterbox gris, identica al preprocesador Android.
- Salida logica: una probabilidad por etiqueta, en el orden inmutable del JSON.
- Un artefacto cuantizado puede almacenar tensores enteros, pero el evaluador siempre compara
  probabilidades despues de aplicar escala y cero del tensor.
- Normalizacion adicional, si existe, vive dentro del grafo exportado; Android no puede inventar una
  segunda normalizacion.

`safe` no es una etiqueta: significa que ninguna senal activa supera su umbral y que la evidencia es
suficiente. `uncertain` tampoco es una clase entrenada: es abstencion por banda de confianza,
evidencia pequena/oculta, entrada fuera de distribucion o desacuerdo de politica.

Las senales de modestia se refieren a presentacion femenina observable y nunca a identidad de
genero. La edad incierta se agrupa con 10 anos o mas. En una imagen grupal cada senal es positiva si
al menos una persona dentro de su alcance la cumple.

## Estados de anotacion

Cada etiqueta usa uno de cinco valores:

- `positive`: la senal esta presente;
- `negative`: se reviso y la senal no esta presente;
- `unknown`: la evidencia visual no alcanza o los revisores no pueden decidir;
- `not_applicable`: la senal no corresponde al contenido;
- `unreviewed`: todavia no hubo juicio humano.

`unknown`, `not_applicable` y `unreviewed` enmascaran la perdida; nunca se convierten silenciosamente
en negativos. `unknown` significa duda despues de revisar y no puede usarse como sinonimo de
`unreviewed`. Las etiquetas producidas por modelos profesores son `prelabels`, conservan modelo,
version y score, y no sustituyen la verdad humana.

Las dependencias logicas entre contexto y senales viven en `annotationConsistency` dentro del
contrato JSON. El validador las aplica tanto a la etiqueta final como a cada revision independiente;
por ejemplo, una senal femenina positiva exige presentacion femenina/persona coherentes y las dos
ramas de edad no pueden ser positivas simultaneamente.

La guia de anotacion debe incluir ejemplos de borde para edad visual, grupos, escote, transparencia,
ropa ajustada, ilustraciones realistas, personas pequenas y cada tema. Dos revisores independientes
etiquetan el conjunto de evaluacion; un tercero adjudica los desacuerdos criticos.

El primer borrador versionado vive en
`docs/dag/v3/DAG_BROWSER_V3_ANNOTATION_GUIDE_V1_DRAFT.md`. Fija principios observables y protocolo,
pero enumera los bordes que aun necesitan ejemplos y aprobacion Ultra; no autoriza empezar el corpus
final.

## Politica y perfiles

Los umbrales son independientes por etiqueta. Un perfil de Glosh puede activar o desactivar temas,
pero nunca puede desactivar el cierre ante error de transporte, runtime o modelo.

- Una senal critica alta produce `block`.
- La banda media produce `uncertain` y permanece oculta.
- `allow` exige evidencia negativa suficiente para todas las senales activas.
- `blur` sera una presentacion de politica posterior; no es una salida especial del modelo.
- Pintar solamente piel requiere un modelo de segmentacion separado y no forma parte de este gate.

No se incluye una senal de cabello cubierto en V1: la politica actual no define a quien aplicaria y
el sistema no debe inferir estado civil. Si se solicita, necesitara una regla observable explicita,
datos propios y una nueva version del contrato.

## Procedencia y licencia

Ninguna fuente se aprueba por el nombre del portal. Cada imagen necesita evidencia individual de
licencia, atribucion y origen.

Orden recomendado:

1. imagenes propias o encargadas con permiso escrito para entrenamiento y evaluacion;
2. Wikimedia Commons u Openverse con licencia comercial y modificable verificada en la pagina
   original;
3. subconjuntos de Open Images despues de verificar la licencia de cada archivo;
4. Fashionpedia para ontologia y preseleccion; sus imagenes solo entran si sus terminos individuales
   quedan verificados.

Se excluyen:

- resultados copiados de Google, tiendas o redes sociales sin permiso;
- capturas recolectadas automaticamente del telefono;
- fotos privadas de familias o menores;
- archivos cuya licencia permita investigacion pero no uso comercial o derivados;
- una imagen cuyo origen, autor o licencia no pueda reconstruirse.

Open Images declara CC BY 4.0 para anotaciones y lista imagenes como CC BY 2.0, pero advierte que se
verifique cada licencia. Fashionpedia publica anotaciones/ontologia bajo CC BY 4.0, pero no posee el
copyright de sus imagenes. Openverse y Wikimedia tambien dejan la verificacion final al reutilizador.

Un sondeo de metadatos del 2026-07-27 encontro entre 11 y 240 candidatos por consulta en ocho
busquedas de vestimenta, pero tambien duplicados y ruido semantico: por ejemplo, buscar ropa
transparente devolvio recursos con fondo PNG transparente. Openverse queda como descubrimiento
acotado, nunca como etiqueta ni licencia aprobada.

## Politica de fuentes cerrada para el piloto 05A

Esta politica se verifico nuevamente el 2026-07-29 contra las fuentes primarias enlazadas al final
del documento. Es una puerta de elegibilidad tecnica y de producto, no asesoramiento legal general.

### Licencias y derechos admisibles

Una muestra puede pasar a `eligible` solamente si la revision individual conserva evidencia de
origen, autor, licencia, atribucion, permiso comercial, permiso de derivados y derechos de imagen.
El orden de preferencia es:

1. `owned` o `commissioned`, con permiso escrito que cubra entrenamiento, evaluacion, derivados,
   distribucion del modelo y uso comercial;
2. CC0 o dominio publico verificado en la pagina original;
3. CC BY 2.0 o CC BY 4.0 verificada en la pagina original, con atribucion completa conservada.

CC BY-SA puede aparecer en un inventario de descubrimiento, pero queda `needs_review` y no entra a
entrenamiento, validacion ni test hasta una revision legal separada que defina las obligaciones
sobre dataset, transformaciones y pesos. Se excluyen CC BY-NC, CC BY-ND, licencias solo para
investigacion, terminos incompatibles con derivados o uso comercial y cualquier estado ambiguo.

Openverse, Wikimedia Commons y Open Images son catalogos de descubrimiento, no autoridades finales
de licencia. La pagina original de cada archivo debe seguir accesible y coincidir con los metadatos
registrados. Fashionpedia se admite para ontologia y preseleccion; una imagen suya solo puede entrar
si supera exactamente la misma revision individual.

Una licencia de copyright no resuelve automaticamente privacidad, publicidad, personalidad,
marcas ni consentimiento. Toda muestra con una persona identificable permanece `needs_review`
hasta registrar una base suficiente para su uso. Una persona posiblemente menor de edad solo se
admite desde material `owned` o `commissioned` con autorizacion verificable del responsable. Si una
imagen pudiera combinar contenido sexual con una persona menor, se rechaza antes de descargar o se
elimina del piloto aislado sin conservar copia; nunca se etiqueta ni se incorpora al dataset.

### Evidencia minima de revision

La revision de cada archivo conserva en el manifiesto:

- pagina original y URL exacta del archivo;
- autor o creador, titulo cuando exista y atribucion requerida;
- identificador, version y URL legal de la licencia;
- fecha de verificacion y fuente de los metadatos;
- permisos comercial y de derivados como decisiones explicitas;
- estado separado de derechos de imagen y restricciones no relacionadas con copyright;
- motivo estructurado si queda `needs_review`, `rejected` o `excluded`.

Un dato de Openverse, una etiqueta de categoria, un filtro `mature=false` o una licencia informada
por un catalogo nunca se transforma automaticamente en aprobacion.

### Limites del primer piloto

El primer tramo de 05B valida el pipeline; no pretende entrenar ni demostrar cobertura final:

- hasta 12 consultas unicas por inventario, 20 resultados por pagina y tres paginas;
- primera descarga de 20 candidatos y maximo absoluto de 100;
- maximo de 8 MiB por archivo y 100 MiB descargados en total;
- maximo de 250 MiB de almacenamiento temporal total para originales, normalizados y manifiestos;
- cero GPU, cero API paga, cero Supabase, cero Production y cero imagenes en Git;
- procesos seriales o acotados, con deduplicacion contra lotes anteriores;
- todo pixel descargado permanece fuera del repositorio y sigue
  `needs_license_and_visual_review`.

La seleccion busca cobertura de las 21 senales, negativos dificiles y diversidad de fuente,
iluminacion, presentacion y tipo de medio. Ninguna fuente puede aportar mas del 40 % del piloto
elegible. Imagenes del mismo creador, sesion, producto, campana o cluster permanecen en un unico
grupo. No se fuerza una cuota inventando positivos: la ausencia o escasez por senal se informa y
define el siguiente tramo.

Estos limites no son el tamaño del dataset final. Para categorias criticas se conserva la referencia
estadistica de al menos 300 positivos independientes para aproximar, con cero errores, un limite
superior de falsos negativos cercano al 1 % con 95 % de confianza.

## Manifiesto minimo por muestra

El dataset se almacena como objetos inmutables y manifiestos JSONL con
`manifest_schema_version: dag-v3-dataset-manifest-v1`. Cada fila conserva:

- `sample_id`, SHA-256 de contenido, hash perceptual, dimensiones y MIME;
- URL de pagina original, URL del archivo y fecha de recuperacion;
- fuente, autor, creador y grupo de sesion/producto/campana;
- licencia, version, URL legal, atribucion y fecha/evidencia de verificacion;
- permiso comercial, permiso de derivados y estado de revision para ML;
- estado conocido de derechos de imagen o consentimiento cuando corresponda;
- origen `owned`, `commissioned`, `commons`, `openverse`, `openimages` u otro aprobado;
- prelabels con profesor/version/score separados de las etiquetas humanas;
- etiqueta final y las 21 decisiones originales de cada revisor seudonimo;
- timestamps, guia usada y decision de un tercer adjudicador para cada desacuerdo;
- `split_group_id`, version de dataset y motivo de exclusion si no se usa.

El manifiesto nunca contiene cookies, credenciales, historial privado ni identificadores de un
dispositivo Glosh.

El validador local de este contrato vive en `scripts/dag_v3_model/manifest_validator.py`. Rechaza
antes del entrenamiento licencias incompletas, fuentes no aprobadas, hashes duplicados, etiquetas
fuera del contrato, resumenes de revision sin decisiones independientes, adjudicaciones
inconsistentes y grupos repartidos entre splits. Validacion y test exigen exactamente dos revisores
independientes; un desacuerdo exige un tercer adjudicador distinto y la etiqueta final debe coincidir
con esa resolucion.

`scripts/dag_v3_model/annotation_agreement.py` mide acuerdo exacto, kappa, desacuerdos binarios,
casos con incertidumbre y arbitrajes por senal, split y version de guia. El reporte conserva los
casos discrepantes mediante IDs seudonimos y no abre las imagenes.

`scripts/dag_v3_model/openverse_inventory.py` puede reunir solamente metadatos candidatos con
limites de consultas/paginas y licencias potencialmente compatibles. No descarga imagenes y marca
todo como `needs_review`.

## Deduplicacion y splits

Primero se eliminan duplicados exactos por SHA-256 y cercanos por hash perceptual/embedding. Despues
se agrupan por creador, sitio, sesion fotografica, producto/campana y cluster visual.

El split se asigna al grupo completo, no a cada archivo. Una foto recortada, redimensionada o
publicada por otra URL no puede quedar en entrenamiento y prueba. El test permanece congelado y no
se usa para elegir arquitectura, augmentation, cuantizacion ni umbrales.

El conjunto debe cubrir busquedas, e-commerce, noticias, anuncios, miniaturas, grupos, dibujos
realistas, imagenes generadas, pieles/iluminaciones diversas y negativos dificiles. Los datos
sinteticos pueden ampliar entrenamiento, pero nunca reemplazan el test real independiente.

## Tamano y evidencia estadistica

La primera corrida es un piloto para validar el pipeline y comparar candidatas; no habilita fotos.
Antes de fijar un numero total se mide la frecuencia real por etiqueta y se calcula el poder
estadistico.

Como referencia, con cero errores la regla aproximada de tres necesita:

- al menos 300 positivos independientes de una categoria para sostener un limite superior cercano
  a 1 % de falsos negativos con 95 % de confianza;
- al menos 600 para acercarse a 0,5 %.

Cuando existan errores se informan intervalos Wilson exactos. No se mezclan miles de negativos
faciles para esconder una categoria critica pequena.

## Entrenamiento y exportacion

Se comparan MobileNetV3-Small y EfficientNet-Lite0 con:

- el mismo split, augmentations, cabeza multietiqueta y presupuesto de ajuste;
- inicializacion exacta, URL, licencia y SHA-256 registrados;
- perdida enmascarada para estados no concluyentes y tratamiento documentado del desbalance;
- float como referencia;
- INT8 post-training con conjunto representativo separado;
- QAT solamente si PTQ pierde calidad critica de forma material.

La linea Android sera LiteRT empaquetado y fijado, CPU primero. Al iniciar la implementacion se
verifica nuevamente la version estable; la investigacion del 2026-07-27 encontro `2.1.5` como
release oficial mas reciente. No se agrega el runtime antes de tener un artefacto candidato
versionado y revisado.

## Evaluacion y gates

Por etiqueta y por politica se registran:

- precision, recall, PR-AUC, matriz de errores y soporte;
- falsos permisos criticos con intervalo de confianza;
- Brier score, error de calibracion y curva precision/recall;
- tasa `uncertain` sobre casos seguros y riesgosos;
- cortes por fuente, tipo de medio, grupo, tamano de persona y dificultad;
- float contra INT8 y PTQ contra QAT si corresponde;
- decodificacion, inferencia y politica por separado;
- p50/p95/maximo, PSS adicional, bateria y temperatura en SM-A235M;
- matriz Fravega, Mimo y Cheeky sin destello.

`scripts/dag_v3_model/evaluation_harness.py` fija el primer formato reproducible para estas
mediciones. Enmascara estados no concluyentes, calcula intervalos Wilson, conserva la banda
`uncertain`, admite cortes acotados y compara una referencia float contra una candidata INT8. Sus
resultados son evidencia para calibrar: no aprueban umbrales ni habilitan acciones del navegador.

Los objetivos numericos finales y umbrales necesitan una revision Ultra separada cuando exista la
distribucion del piloto. Hasta entonces, ningun resultado puede generar `allow` o `blur`.

## Fuentes primarias

- MobileNetV3: <https://openaccess.thecvf.com/content_ICCV_2019/html/Howard_Searching_for_MobileNetV3_ICCV_2019_paper.html>
- EfficientNet-Lite: <https://github.com/tensorflow/tpu/tree/master/models/official/efficientnet/lite>
- LiteRT: <https://github.com/google-ai-edge/LiteRT/releases>
- Cuantizacion: <https://ai.google.dev/edge/litert/conversion/tensorflow/quantization/post_training_quantization>
- Open Images V7: <https://storage.googleapis.com/openimages/web/factsfigures_v7.html>
- Metadata de Open Images: <https://storage.googleapis.com/openimages/web/download_v7.html>
- Licencia Fashionpedia: <https://fashionpedia.github.io/home/data_license.html>
- Openverse API: <https://api.openverse.org/>
- Terminos de Openverse:
  <https://docs.openverse.org/terms_of_service.html>
- Politica de licencias de Wikimedia Commons: <https://commons.wikimedia.org/wiki/Commons:Licensing>
- Metadata de licencia mediante Wikimedia API:
  <https://commons.wikimedia.org/wiki/Commons:Credit_line>
- Restricciones no relacionadas con copyright en Wikimedia Commons:
  <https://commons.wikimedia.org/wiki/Commons:Non-copyright_restrictions>
- Derechos de privacidad, publicidad y personalidad en Creative Commons:
  <https://creativecommons.org/faq/#what-are-publicity-personality-and-privacy-rights>
