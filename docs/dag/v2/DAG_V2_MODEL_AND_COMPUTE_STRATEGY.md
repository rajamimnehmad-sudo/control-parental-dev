# Estrategia de modelo y computo DAG v2

## Objetivo

Construir un modelo visual propio de Glosh, rapido y confiable en Android, sin exigir que una persona etiquete manualmente todo el dataset y sin depender de una GPU local.

El resultado final debe ejecutar inferencia dentro del telefono. Las imagenes de navegacion no se envian a un servidor para decidir si se muestran.

## Estrategia de aprendizaje

La ruta predeterminada es:

1. partir de uno o mas backbones visuales preentrenados con licencia y procedencia verificadas;
2. usar modelos profesores y reglas auxiliares para preetiquetado automatico;
3. deduplicar y agrupar imagenes iguales o visualmente cercanas;
4. medir acuerdo y confianza entre profesores;
5. enviar a revision humana solamente desacuerdos, incertidumbre, casos nuevos y errores criticos;
6. ajustar y, cuando convenga, destilar un modelo movil especifico de Glosh;
7. comparar varias arquitecturas compactas en los telefonos fisicos;
8. activar solamente un artefacto que supere las puertas de precision, seguridad, latencia, memoria y temperatura.

Entrenar desde pesos aleatorios deja de ser una obligacion. Solo se autoriza si una prueba reproducible demuestra una ventaja material y existe un presupuesto explicito de datos, GPU y tiempo.

## Rol humano

La revision humana define el criterio de producto y valida casos ambiguos. No funciona como mano de obra para etiquetar masivamente ejemplos evidentes.

La interfaz sigue siendo:

- `Mostrar`;
- `Ocultar`;
- `No estoy seguro`.

El sistema debe priorizar para revision:

- desacuerdos entre modelos;
- baja confianza;
- ropa ajustada o transparente ambigua;
- edad dudosa;
- limites de codo, rodilla, hombro, abdomen y escote;
- grupos;
- dibujos, renders y medios sinteticos;
- errores encontrados en modo sombra o pruebas fisicas.

## Donde vive cada componente

### Mac M2 de 8 GB

Se utiliza para:

- programacion y revision;
- preparacion de manifiestos y metadatos;
- procesamiento por lotes acotados y streaming;
- deduplicacion incremental;
- pruebas pequenas;
- exportacion, cuantizacion y validacion de artefactos compactos cuando entren en memoria;
- pruebas Android y coordinacion de trabajos remotos.

No se utiliza para entrenamiento visual grande. Los procesos deben limitar memoria y concurrencia, evitar cargar el dataset completo y conservar espacio libre suficiente para Android, Gradle y artefactos temporales.

### Codex local y cloud

Codex:

- escribe y mantiene el pipeline;
- prepara configuraciones reproducibles;
- ejecuta tests y pruebas pequenas;
- lanza trabajos remotos mediante scripts o APIs autorizadas;
- observa logs y checkpoints;
- compara metricas;
- genera informes y propuestas de mejora.

Codex no se considera una GPU ni se presupone aceleracion de hardware no documentada. El consumo de Codex se reduce con tickets pequenos, archivos de configuracion versionados, scripts reutilizables, resultados resumidos y ausencia de reanalisis global innecesario.

### Supabase

Supabase se utiliza para:

- muestras normalizadas privadas y autorizadas;
- metadatos, etiquetas y auditoria;
- versiones de dataset;
- registro de experimentos y modelos;
- almacenamiento privado de artefactos aprobados;
- manifiestos de distribucion y rollback;
- coordinacion liviana y estados de trabajos.

Supabase Edge Functions no ejecutan entrenamiento pesado. Pueden validar, autorizar, registrar y orquestar llamadas a un servicio externo.

### GitHub

GitHub se utiliza para:

- codigo y configuraciones;
- CI y pruebas reproducibles;
- revisiones y trazabilidad;
- manifiestos de datasets y experimentos;
- automatizacion para lanzar o verificar trabajos.

Los runners estandar no se consideran GPU. Un runner GPU pago solo puede adoptarse tras verificar disponibilidad, plan y costo en un ticket separado.

### GPU externa efimera

El entrenamiento pesado se ejecuta en una GPU alquilada por tiempo. El proveedor no queda fijado todavia.

Antes de elegirlo se realiza un benchmark pequeno que compare:

- disponibilidad real;
- tipo y memoria de GPU;
- costo total por corrida;
- tiempo de subida y descarga;
- almacenamiento persistente;
- soporte para checkpoints y reanudacion;
- API o CLI automatizable;
- seguridad y ubicacion de datos;
- apagado automatico;
- facilidad para exportar todos los artefactos sin dependencia del proveedor.

El trabajo remoto debe ser reproducible desde el repositorio y no depender de una sesion manual irrepetible.

## Controles de costo

Cada trabajo de GPU debe declarar antes de comenzar:

- presupuesto maximo;
- duracion maxima;
- tipo y cantidad de GPU;
- dataset y configuracion exactos;
- frecuencia de checkpoints;
- criterio de parada temprana;
- apagado automatico al terminar o fallar;
- ubicacion de resultados;
- procedimiento de reanudacion.

La primera corrida siempre es pequena. No se autoriza escalar por intuicion.

## Modelo final Android

El modelo final sera un artefacto movil propio de Glosh, probablemente exportado desde PyTorch a ONNX o al formato movil que gane las pruebas.

La arquitectura concreta no se fija por nombre antes del benchmark. Se comparan candidatas compactas por:

- falsos negativos criticos;
- falsos positivos;
- metricas por señal;
- latencia por imagen;
- memoria PSS;
- CPU y aceleradores disponibles;
- temperatura y bateria;
- tamaño de descarga;
- estabilidad por marca y version Android.

La distribucion recomendada es hibrida:

- un modelo de respaldo pequeno incluido en la APK;
- el modelo principal aprobado descargado desde almacenamiento privado;
- firma, SHA-256, version, compatibilidad y rollback verificados antes de activarlo;
- inferencia completamente local durante la navegacion.

## Automatizacion del dataset

El pipeline de dataset debe automatizar:

- importacion autorizada;
- validacion de licencia y procedencia;
- normalizacion;
- deteccion de corrupcion;
- SHA-256 y hash perceptual;
- deduplicacion exacta y cercana;
- embeddings y clustering;
- preetiquetado por ensamble;
- estimacion de confianza;
- seleccion activa para revision;
- separacion sin fuga entre entrenamiento, validacion y prueba;
- versionado y auditoria.

Las etiquetas automaticas no se consideran verdad humana. Deben conservar procedencia, profesor, version, confianza y reglas que las produjeron.

## Puertas antes de producción

El modelo v2 no controla imagenes visibles hasta completar:

1. dataset aprobado;
2. entrenamiento reproducible;
3. evaluacion independiente;
4. optimizacion Android;
5. modo sombra;
6. pruebas fisicas en telefonos objetivo;
7. canary reversible;
8. aprobacion manual.

DAG v1 continua disponible como rollback durante toda la transicion.
