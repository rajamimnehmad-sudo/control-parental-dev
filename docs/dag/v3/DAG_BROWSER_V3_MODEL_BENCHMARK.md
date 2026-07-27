# DAG Browser V3 - benchmark del filtro visual

## Objetivo

Elegir el modelo local por evidencia en el Samsung SM-A235M, no por nombre o resultados de
escritorio. Ninguna candidata puede habilitar `allow` o `blur` hasta superar seguridad, calidad y
rendimiento.

## Arquitectura a comparar

La linea principal usa:

1. preprocesador local acotado, comun a entrenamiento y telefono;
2. un clasificador multietiqueta pequeno, entrenado especificamente para las senales de Glosh;
3. calibracion separada por etiqueta;
4. politica determinista de Glosh para `allow`, `blur`, `block` o `uncertain`.

Se comparan, como minimo, MobileNetV3-Small y EfficientNet-Lite0 cuantizados a entero. Ninguno queda
elegido por reputacion: gana la combinacion con mejor seguridad, calibracion y rendimiento en el
Samsung objetivo.

Pose, segmentacion y un detector adulto generico no forman la cascada principal. Las mediciones
anteriores fueron lentas y, sobre todo, no distinguieron mangas, escote, ajuste, transparencia ni
otras reglas de tzniut. Solo se admite un segundo modelo especializado si una ablacion demuestra
que reduce falsos permisos de manera material y conserva los presupuestos de latencia y memoria.

La linea base del runtime sera LiteRT empaquetado y versionado dentro de DAG, CPU primero. Eso
mantiene funcionamiento offline y una version reproducible. Google Play Services, GPU, NNAPI u otro
runtime solo se agregan como candidatas medidas; una aceleracion no se presume.

## Etiquetas iniciales

- persona;
- apariencia femenina observable, sin afirmar identidad real;
- brazos o hombros expuestos;
- escote o pecho;
- vientre;
- muslo o rodilla;
- desnudez explicita;
- vestimenta ajustada o sugestiva;
- ropa transparente, traje de bano o ropa interior;
- segura;
- dudosa.

Las etiquetas son senales del modelo. La accion visible pertenece a la politica de Glosh y puede
calibrarse sin reentrenar.

## Conjunto de evaluacion

- Separacion por sitio y origen para evitar que fotos casi identicas aparezcan en entrenamiento y
  prueba.
- Casos de busqueda, tiendas, noticias, anuncios, dibujos, fotos pequenas y grupos de personas.
- Revision humana doble para los casos dudosos.
- Sin carga automatica de imagenes privadas desde el telefono.
- Registro por categoria de falsos negativos, falsos positivos y desacuerdo humano.

## Metricas obligatorias

- recall y precision por categoria;
- tasa de falsos negativos en categorias de bloqueo;
- latencia p50 y p95 por imagen;
- tiempo total hasta decision para una pagina con varias miniaturas;
- memoria maxima, temperatura y consumo de bateria;
- porcentaje de resultados `uncertain`;
- cero destellos visuales antes de la decision.

Ademas se registra por separado:

- tiempo de decodificacion y preprocesado;
- tiempo puro de inferencia;
- tiempo de politica;
- tamano del modelo y memoria adicional respecto de DAG sin modelo.

## Calibracion

- Umbral independiente por categoria.
- Banda `uncertain` que permanece bloqueada.
- Perfiles estricto, equilibrado y suave construidos sobre la misma salida.
- Calibracion de probabilidades sobre un conjunto separado del entrenamiento.
- Version de modelo, politica y umbrales registrada en cada benchmark.
- Rollback inmediato a la ultima combinacion aprobada.

## Gates

1. El transporte de pixeles tiene limites de tamano, tiempo y concurrencia.
2. Un error de descarga, decodificacion, runtime o modelo devuelve `block`.
3. La prueba automatica demuestra que una respuesta web no puede falsificar `allow`.
4. El benchmark fisico cumple los presupuestos acordados.
5. Una revision de razonamiento muy alto/Ultra aprueba categorias, dataset, metricas y umbrales.
6. Solo entonces se agregan `allow` y `blur` al protocolo nativo.

## Candidatas y fuentes primarias

- MobileNetV3-Small fue disenada para escenarios moviles de pocos recursos:
  <https://openaccess.thecvf.com/content_ICCV_2019/html/Howard_Searching_for_MobileNetV3_ICCV_2019_paper.html>
- EfficientNet-Lite cambia operaciones para dispositivos moviles y cuantizacion; Lite0 usa entrada
  224 x 224:
  <https://github.com/tensorflow/tpu/tree/master/models/official/efficientnet/lite>
- LiteRT 2.1 agrega Interpreter CPU en sus paquetes Maven y soporta Android desde API 23:
  <https://github.com/google-ai-edge/LiteRT/releases>
- La cuantizacion puede degradar precision y debe evaluarse; si PTQ no alcanza, se compara QAT:
  <https://ai.google.dev/edge/litert/conversion/tensorflow/quantization/post_training_quantization>
