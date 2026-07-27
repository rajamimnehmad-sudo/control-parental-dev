# DAG Browser V3 - benchmark del filtro visual

## Objetivo

Elegir el modelo local por evidencia en el Samsung SM-A235M, no por nombre o resultados de
escritorio. Ninguna candidata puede habilitar `allow` o `blur` hasta superar seguridad, calidad y
rendimiento.

## Arquitectura a comparar

La prueba usa una cascada:

1. clasificador visual pequeno y cuantizado para el descarte rapido;
2. Pose Landmarker Lite cuando hay una persona;
3. clasificador multietiqueta propio para senales de tzniut;
4. politica determinista de Glosh para `allow`, `blur`, `block` o `uncertain`.

Se comparan al menos dos backbones moviles cuantizados. MobileNetV3-Small es una candidata, no una
decision cerrada. El formato y runtime definitivos se eligen despues de medir LiteRT y, si aporta
una ventaja real, ONNX Runtime Mobile.

## Etiquetas iniciales

- persona;
- persona femenina;
- brazos o hombros expuestos;
- escote o pecho;
- vientre;
- muslo o rodilla;
- desnudez explicita;
- vestimenta ajustada o sugestiva;
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
