# Contrato de producto DAG v2

## Propósito y alcance

Este documento fija el comportamiento de producto que deberá cumplir DAG v2. No selecciona arquitectura de entrenamiento, no implementa navegador, calibración ni modelo, y no modifica DAG v1.

DAG v2 se construirá separado de DAG v1. DAG v1 permanecerá disponible como respaldo hasta que v2 esté validado, estable y habilitado mediante una decisión manual.

## Resultado de producción

La salida final visible del filtro de imágenes tendrá sólo dos acciones:

- `Mostrar`
- `Ocultar`

Internamente puede existir `No estoy seguro`, pero una imagen incierta no se muestra automáticamente. La incertidumbre suficiente para impedir una aprobación se resuelve como `Ocultar`.

## Política canónica de sitios

La precedencia no negociable es:

1. Búsquedas adultas: bloquear.
2. Resultados adultos: no mostrar.
3. Dominios adultos: bloquear completamente.
4. Página con contenido adulto inequívoco: bloquear.
5. Sitio normal: permitir la página y filtrar cada imagen de forma individual.
6. Fallo o incertidumbre crítica: fallar de forma segura.
7. Una regla común de permitir dominio nunca puede reemplazar una prohibición adulta no negociable.

Las reglas administrativas de permitir dominio pueden operar únicamente fuera de las prohibiciones adultas no negociables.

## Política visual canónica

### Mujeres de apariencia de 10 años o más

Ocultar la imagen completa ante cualquiera de estas condiciones:

- desnudez o poca ropa;
- ropa interior o lencería;
- traje de baño;
- transparencia;
- escote pronunciado;
- pecho parcialmente visible;
- abdomen descubierto;
- hombros o axilas descubiertos;
- codos descubiertos;
- rodillas descubiertas;
- calzas o leggings;
- biker shorts;
- ropa deportiva muy ceñida;
- vestidos o prendas bodycon;
- ropa que marque claramente pecho, cintura, caderas, glúteos, entrepierna o silueta corporal;
- incertidumbre que no permita aprobar la imagen con suficiente confianza.

### Niñas claramente menores de 10 años

- Permitir ropa infantil ordinaria aunque deje visibles brazos, codos o rodillas.
- Seguir ocultando desnudez, ropa interior, traje de baño, transparencia, sexualización y casos dudosos.

### Edad incierta

Cuando la edad no pueda determinarse con suficiente confianza, aplicar la política estricta de 10 años o más.

### Fotografías grupales

Si al menos una mujer incumple la política, ocultar la imagen completa.

### Medios cubiertos

Aplicar las mismas reglas a:

- fotografías;
- anuncios;
- miniaturas;
- ilustraciones realistas;
- imágenes generadas por IA.

La política no depende de que el contenido se describa con una categoría concreta en la interfaz. Los casos sexualizados o explícitos, incluso ilustrados, quedan dentro de la prohibición visual correspondiente.

## Funcionamiento de páginas

- Un documento principal se analiza completamente una sola vez.
- Abrir menús, botones, categorías, filtros, acordeones, modales, pestañas internas o carruseles no vuelve a analizar toda la página.
- Las nuevas imágenes y recursos visuales sí se filtran individualmente.
- Una imagen no aprobada permanece como placeholder neutro.
- Nunca se muestran temporalmente los píxeles de una imagen prohibida.
- No se modifica globalmente `src`, `srcset`, clases o estilos del sitio.
- No se interfiere con React, Next.js ni Service Workers.
- HTML, CSS, JavaScript, JSON, XHR, `fetch`, fuentes y datos funcionales no pasan por el clasificador de imágenes.
- Menús, inputs, botones y scripts funcionales no quedan bloqueados mientras las imágenes se deciden.
- La página no se congela por una imagen lenta; cada recurso visual conserva su propio estado seguro.

## Modelo visual DAG v2

DAG v2 utilizará un único modelo visual propio:

- entrenado desde cero;
- con pesos iniciales aleatorios;
- diseñado específicamente para esta política;
- sin depender de pesos preentrenados de DAG v1 ni de modelos visuales externos;
- separado de la implementación y de los estados internos de decisión visual de DAG v1.

Este ticket no define todavía la arquitectura de red, el pipeline de entrenamiento, el formato final, los hiperparámetros ni el código del modelo.

### Contrato mínimo de señales

El modelo deberá contemplar como mínimo:

- `adult_or_explicit`
- `female_present`
- `clearly_young_child`
- `underwear_or_swimwear`
- `deep_neckline`
- `chest_exposed`
- `abdomen_exposed`
- `shoulder_or_armpit_exposed`
- `elbow_uncovered`
- `knee_uncovered`
- `transparent_clothing`
- `tight_clothing`
- `confidence`

Estas señales son el contrato semántico mínimo. No obligan todavía a una forma específica de salida tensorial ni a una arquitectura concreta.

## Calibración DEV v2

### Acciones

- `✓ Mostrar`
- `× Ocultar`
- `? No estoy seguro`

### Reglas

- Disponible sólo en DEV.
- Requiere activación explícita.
- No recarga la página.
- Una etiqueta no cambia inmediatamente el filtro.
- `No estoy seguro` no se utiliza como etiqueta positiva o negativa.
- Las etiquetas son globales, no personales.
- Deduplicación por SHA-256 y hash perceptual.
- Ninguna muestra se activa automáticamente.
- Todo candidato se prueba primero en modo sombra.
- La activación es manual.
- Siempre existe rollback.
- La capacidad de calibración visual se elimina del producto final cuando termine la etapa de entrenamiento.

### Separación entre etiquetado y activación

Etiquetar una muestra sólo agrega evidencia al dataset candidato. No altera la imagen actual, no crea una excepción exacta para ese recurso y no cambia automáticamente el modelo ni los thresholds de los dispositivos.

`No estoy seguro` sirve para separar evidencia insuficiente y priorizar revisión; no participa como ejemplo positivo ni negativo hasta que exista una etiqueta concluyente.

## Criterios de aceptación del contrato

DAG v2 sólo puede considerarse conforme cuando:

- las prohibiciones adultas prevalecen sobre permisos comunes;
- una página normal permanece funcional mientras cada imagen se decide;
- ningún píxel prohibido aparece antes de la decisión;
- la política de edad, grupos y medios sintéticos se aplica de forma consistente;
- la incertidumbre falla de forma segura;
- producción presenta sólo `Mostrar` u `Ocultar`;
- la calibración DEV no activa muestras, thresholds ni modelos automáticamente;
- existe rollback verificable a DAG v1 durante toda la transición.

## Fuera de alcance de este ticket

- crear el navegador base de v2;
- crear el módulo DAG v2;
- diseñar o implementar el entrenamiento;
- reunir o modificar el dataset;
- entrenar o convertir modelos;
- modificar thresholds de DAG v1;
- cambiar Calibración DEV actual;
- publicar APK o cambiar `versionCode`;
- desplegar cambios en Supabase;
- iniciar la siguiente fase del roadmap.
