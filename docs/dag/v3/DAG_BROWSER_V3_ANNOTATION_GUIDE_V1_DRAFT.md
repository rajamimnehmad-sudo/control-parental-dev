# DAG Browser V3 - guia de anotacion visual V1 (borrador)

## Estado

- `guide_version`: `glosh-visual-annotation-v1-draft.1`
- `signal_contract_version`: `glosh-visual-signals-v1`
- Estado: borrador de trabajo; no usar para cerrar el test ni aprobar un modelo.
- Aprobacion pendiente: responsable de producto y revision Ultra de bordes, ejemplos y gates.

Esta guia traduce las 21 salidas del contrato a decisiones observables. No define una norma
religiosa universal ni autoriza `allow` o `blur`. Cuando un borde no esta aprobado, la respuesta
correcta es `unknown`, no inventar una regla.

## Principios obligatorios

1. Etiquetar solamente lo visible en los pixeles.
2. No usar titulo, busqueda, pagina, autor, genero declarado ni contexto externo para decidir
   contenido.
3. No inferir identidad de genero, religion, estado civil, relacion, intencion ni conducta fuera de
   la imagen.
4. Ver la misma imagen completa, con letterbox gris y a 224 x 224 que recibira el modelo. No hacer
   zoom para convertir evidencia ilegible en una etiqueta.
5. Etiquetar cada senal por separado. Varias senales pueden ser positivas a la vez.
6. Evaluar lo que aparece dentro del encuadre. No imaginar partes del cuerpo que quedaron fuera.
7. Un caso de borde queda `unknown` y permanece oculto durante la politica.
8. Las decisiones de licencia/derechos y las decisiones visuales son revisiones separadas.
9. Los revisores no ven prelabels ni la decision del otro revisor.
10. No se incluyen fotos privadas del dispositivo ni material de menores obtenido sin derechos
    expresos.

## Estados permitidos

### `positive`

La senal se observa con evidencia suficiente en al menos una persona o region dentro de su alcance.

### `negative`

La imagen tiene evidencia suficiente para afirmar que la senal no aparece. La ausencia de una
persona o region relevante normalmente es `negative`, no `not_applicable`; esto aporta negativos
reales al modelo y evita salidas arbitrarias en imagenes sin personas.

### `unknown`

La senal podria corresponder, pero la resolucion, oclusion, iluminacion o ambiguedad visual no permite
decidir. No significa "todavia nadie la reviso".

### `not_applicable`

Uso excepcional, solamente cuando una version aprobada de la guia declare que la semantica de esa
senal queda fuera de alcance para ese tipo de representacion. Este borrador no asigna
automaticamente `not_applicable` a ninguna categoria.

### `unreviewed`

Estado administrativo anterior a la revision. Un revisor nunca entrega una anotacion completa con
este valor.

## Vista y unidad de decision

- La unidad es una imagen estatica completa, no cada persona por separado.
- En grupos, una senal es `positive` si al menos una persona dentro de su alcance la cumple.
- Fotos, imagenes generadas e ilustraciones humanas realistas usan las mismas reglas observables.
- Texto, logos y objetos sin representacion humana son negativos para las senales de persona.
- Animaciones, archivos corruptos y formatos que el preprocesador rechaza no entran al corpus.
- Si una parte relevante esta fuera del encuadre, se evalua solo lo visible. Por ejemplo, un retrato
  sin piernas es negativo para rodilla descubierta; no es desconocido.

## Contexto de personas

### `person_present`

`positive` si aparece una persona fotografiada o una representacion humana suficientemente realista
para aplicar las reglas visuales. `negative` si no aparece. Una silueta, maniqui o personaje muy
estilizado queda `unknown` durante el piloto si dos revisores no coinciden sobre su realismo.

### `female_presentation_present`

`positive` si al menos una persona presenta rasgos visuales femeninos observables. Describe
presentacion en la imagen, no identidad. `negative` si no hay presentacion femenina. Si hay una
persona visible pero la presentacion no puede decidirse, usar `unknown`.

### `female_10plus_or_age_uncertain_present`

`positive` si hay presentacion femenina claramente de 10 anos o mas, o si su edad visual no puede
ubicarse con seguridad por debajo de 10. Ante duda de edad se toma la rama conservadora de
10-o-mas/edad-incierta. `negative` si no hay presentacion femenina o todas son claramente menores de
10.

### `only_clearly_young_female_children_present`

`positive` solamente si hay presentacion femenina y todas esas personas son claramente menores de
10. `negative` si no hay presentacion femenina, si aparece alguien de 10 o mas o si cualquier edad
es incierta. No puede ser positiva al mismo tiempo que
`female_10plus_or_age_uncertain_present`.

La edad se estima solo para esta regla conservadora. No se registra una edad exacta ni se intenta
identificar a la persona.

## Contenido sexual y prendas criticas

### `adult_or_explicit`

`positive` ante genitales visibles, desnudez sexual explicita, acto sexual o contenido graficamente
sexual. Puede coexistir con exposicion de pecho, ropa interior o encuadre sexualizado. Desnudez
medica, lactancia o arte no se convierten automaticamente en "sexualizados"; se etiquetan por los
pixeles visibles y los otros hechos que correspondan.

Toda imagen que pudiera combinar contenido sexual con una persona menor de 18 anos queda excluida,
no se almacena para el piloto y se deriva al procedimiento legal/seguridad.

### `female_underwear_or_swimwear`

`positive` si una persona de presentacion femenina usa ropa interior, lenceria, bikini, traje de
bano o una prenda equivalente por corte y contexto visual. La ropa deportiva comun no se marca
solo por ser ajustada; un top visualmente equivalente a corpiño necesita ejemplo aprobado en Ultra.

### `female_transparent_clothing`

`positive` si una prenda transparente permite ver claramente piel o anatomia que la prenda aparenta
cubrir. Un PNG con fondo transparente, reflejo, tul decorativo aislado o una manga apenas translucida
no bastan. El punto exacto en el que una tela deja de contar como cobertura queda pendiente de
ejemplos Ultra.

### `sexualized_pose_or_framing`

`positive` cuando pose, recorte o foco visual enfatizan de manera clara zonas sexuales o presentan
una escena sexualizada. No se infiere intencion de la persona. Modelaje comun, deporte, danza,
salud, lactancia o retrato no son positivos por si solos. Los casos cuya lectura depende de gusto o
contexto externo son `unknown`.

## Senales de vestimenta y exposicion

Estas seis senales se aplican a presentacion femenina de 10 anos o mas, o edad visual incierta. Si
no aparece ninguna persona dentro de ese alcance, son `negative`. En un grupo basta una persona
positiva.

### `female_10plus_or_uncertain_deep_neckline`

`positive` si el escote desciende de forma visible hacia el pecho o muestra division/contorno de
pecho. Un cuello abierto que solo muestra cuello o parte alta del esternon no basta. La linea exacta
de borde necesita ejemplos positivos, negativos y `unknown` aprobados en Ultra.

### `female_10plus_or_uncertain_chest_exposed`

`positive` si una zona del pecho que normalmente cubriria la parte frontal superior de una prenda
queda visiblemente descubierta. Puede coexistir con escote profundo y con contenido explicito. No se
marca piel de cuello aislada. El limite anatomico exacto queda pendiente de ejemplos Ultra.

### `female_10plus_or_uncertain_abdomen_exposed`

`positive` si hay piel visible del abdomen entre pecho/costillas y cintura. Un pequeno recorte de
prenda cuenta si deja abdomen visible. Espalda descubierta por si sola no activa esta senal.

### `female_10plus_or_uncertain_shoulder_or_armpit_exposed`

`positive` si se ve la cabeza del hombro o la axila sin cobertura opaca. Incluye prendas sin mangas,
con breteles, strapless u off-shoulder. Una manga opaca que cubre hombro y axila es `negative`.

### `female_10plus_or_uncertain_elbow_uncovered`

`positive` si la articulacion del codo queda visible sin cobertura opaca. Una manga que cubre la
articulacion es `negative`, aunque el antebrazo sea visible. Tela claramente transparente no cuenta
como cobertura opaca y tambien puede activar transparencia.

### `female_10plus_or_uncertain_knee_uncovered`

`positive` si la articulacion de la rodilla queda visible sin cobertura opaca. Un ruedo que cubre la
rodilla es `negative`. Si la rodilla esta fuera del encuadre, es `negative`; si esta dentro pero los
pixeles no permiten ver si existe cobertura, es `unknown`.

### `female_10plus_or_uncertain_tight_clothing`

`positive` si una prenda marca de forma clara y continua el contorno de torso, cadera o muslos. No
se decide por talla corporal, peso ni tipo de cuerpo. Pliegues, tela estructurada o un punto de
contacto aislado no bastan. Este es uno de los bordes mas subjetivos y requiere una biblioteca de
ejemplos Ultra antes de usarlo en un corpus final.

## Temas configurables

### `graphic_violence_or_gore`

`positive` ante heridas severas visibles, sangre grafica, mutilacion, organos, cadaveres graficos o
gore. Armas, peleas o sangre minima sin dano grafico no bastan. Una escena medica puede ser positiva
si los pixeles son graficos, aunque su contexto no sea violento.

### `horror_or_disturbing`

`positive` ante imagenes observablemente grotescas o de terror: cadaveres no graficos pero
perturbadores, rostros/figuras deformadas con recursos visuales claros de susto, monstruos amenazantes o
escenas equivalentes. No se etiqueta como perturbadora a una persona por discapacidad, cicatriz,
enfermedad o apariencia. El borde necesita ejemplos Ultra y un perfil que permita desactivar el
tema.

### `drug_use_or_paraphernalia`

`positive` ante consumo visible de drogas o parafernalia presentada en contexto de uso. Medicacion
prescripta, farmacia, tabaco o alcohol no entran automaticamente en esta senal; ampliar esos temas
requeriria nuevas senales/version.

### `gambling`

`positive` ante apuestas, casino, tragamonedas, ruleta, apuestas deportivas o juego de azar en
contexto de uso/promocion. Cartas, dados o fichas aislados sin contexto de apuesta son `negative`.

### `weapon_threat`

`positive` si un arma se apunta, blande o presenta como amenaza/violencia. Un cuchillo de cocina,
herramienta, pieza historica o producto aislado sin contexto amenazante es `negative`. La violencia
grafica se etiqueta ademas en su propia senal cuando corresponde.

## Evidencia insuficiente

### `relevant_subject_too_small_or_obscured`

`positive` si existe una persona relevante pero, en la vista 224 x 224, su tamano, desenfoque,
oclusion o iluminacion impiden evaluar de forma confiable las senales activas. Las senales afectadas
quedan `unknown`.

No se usa para partes del cuerpo simplemente fuera del encuadre: solo se filtra lo visible. Tampoco
se activa si no hay una persona relevante. El piloto determinara si hace falta un limite objetivo de
pixeles; este borrador no inventa uno.

## Dependencias que debe respetar una anotacion

| Si esta senal es `positive` | Tambien debe ser `positive` |
| --- | --- |
| Cualquier senal femenina | `female_presentation_present` y `person_present` |
| Cualquier senal `female_10plus_or_uncertain_*` | `female_10plus_or_age_uncertain_present` |
| `only_clearly_young_female_children_present` | `female_presentation_present` |
| `adult_or_explicit` sobre una persona/representacion | `person_present` |
| `relevant_subject_too_small_or_obscured` | `person_present` |

Ademas:

- `only_clearly_young_female_children_present` y
  `female_10plus_or_age_uncertain_present` no pueden ser positivas juntas.
- Una senal puede quedar `unknown` aunque su contexto sea positivo.
- Una senal de tema no depende de presentacion femenina.
- Las dependencias se validaran automaticamente antes de entrenar; no sustituyen la revision humana.

## Protocolo de revision

1. La muestra supera primero procedencia, licencia, derechos y seguridad.
2. Se genera y fija el mismo bitmap 224 x 224 que usa Android.
3. Revisor A etiqueta las 21 senales sin ver prelabels, metadata ni otras decisiones.
4. Revisor B repite el proceso de forma independiente.
5. Si coinciden, la etiqueta final conserva esa decision.
6. Si difieren, un tercer revisor distinto ve la imagen, la guia y ambas decisiones y arbitra solo
   las senales discrepantes.
7. El manifiesto conserva las dos revisiones, timestamps, arbitraje y version de guia.
8. Se ejecutan el validador y el informe de acuerdo antes de asignar splits o entrenar.

Cambiar una definicion o ejemplo crea una nueva version de guia. Las muestras afectadas deben
revisarse nuevamente; no se cambia su etiqueta final en silencio.

## Proteccion de revisores y personas

- Revisores de contenido sexual/violento deben ser adultos, recibir advertencia y poder retirarse.
- El piloto no usa material sexual que pueda involucrar menores, contenido ilegal ni fotos privadas.
- No se guardan nombres, emails ni datos del revisor: solo claves seudonimas.
- No se infiere ni registra etnia, religion, identidad, salud, estado civil o nombre de las personas
  fotografiadas.
- Una apariencia, discapacidad o cicatriz nunca es por si misma una senal de terror.

## Decisiones que requieren Ultra antes de anotar el corpus final

1. Ejemplos exactos de borde para escote y pecho.
2. Ejemplos de prenda ajustada que no dependan del tipo de cuerpo.
3. Cuando una tela transparente deja de contar como cobertura.
4. Ejemplos positivos/negativos de pose o encuadre sexualizado.
5. Limites de `horror_or_disturbing` y perfiles que activan cada tema.
6. Tratamiento de presentacion femenina visualmente ambigua.
7. Si se fija un tamano minimo objetivo en la vista 224 x 224.
8. Gates de acuerdo entre revisores por senal.

No se agrega cabello cubierto en V1. Hacerlo requeriria definir una regla observable que no infiera
estado civil ni pertenencia religiosa, nuevas muestras, un nuevo contrato y otra revision Ultra.
