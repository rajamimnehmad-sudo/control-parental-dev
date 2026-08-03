# GloshIA R3 - contrato y recuperacion de etiquetas multisenal

Fecha: 2026-08-03

Ticket: `GLOSHIA-R3-MULTI-SIGNAL-DATA-CONTRACT-21`

Resultado: `GO` para completar etiquetas; `NO-GO` para entrenar todavia.

## Objetivo

Preparar un unico modelo visual local que deje de mezclar todos los motivos en
una sola probabilidad. Este ticket no entrena, no reemplaza R1 y no modifica
DAG. Convierte las revisiones historicas del propietario en etiquetas
parciales seguras y mide que senales necesitan mas ejemplos.

## Politica R3

Se conservaron exactamente los diez motivos que ya aparecian en la interfaz de
revision:

- contenido explicito o desnudez;
- ropa interior o traje de bano;
- ropa transparente;
- escote o pecho;
- abdomen visible;
- hombro o axila;
- codo descubierto;
- rodilla descubierta;
- ropa ajustada;
- pose sugerente.

La regla de edad queda fijada para este experimento desde aproximadamente seis
anos, o edad visual incierta, de acuerdo con el criterio vigente del
propietario. Personas diminutas o lejanas tipo "buscar a Wally" se permiten,
salvo que una senal critica resulte visible de forma independiente.

El contrato oficial anterior de 21 senales y umbral de diez anos no se
modifico. Si R3 supera los gates, se versionara entonces un contrato oficial
nuevo; no se cambia silenciosamente el runtime actual.

## Recuperacion de revisiones

Se procesaron las cinco exportaciones historicas disponibles:

- 177 filas unicas;
- 1 exclusion;
- 176 casos utilizables;
- 77 `allow`;
- 99 `filter` al unificar `blur` y `block`, que el propietario definio como el
  mismo criterio con distinta intensidad visual;
- 154 IDs de Wikimedia resolubles mediante su catalogo;
- 22 IDs `pilot` cuyo hash necesita resolucion local;
- 1 motivo `other`, conservado para revision y no convertido en una senal.

Las fotos estan autorizadas por el propietario para entrenamiento privado del
proyecto. Esto no declara derechos claros ni autoriza republicar las imagenes.

## Regla de etiquetas parciales

- Una decision `allow` aporta `negative` para las diez senales.
- En una decision de filtro, cada motivo marcado aporta `positive`.
- En una decision de filtro, un motivo no marcado queda `unknown`, nunca
  `negative`.

Esta ultima regla es obligatoria porque el propietario aclaro que a veces
marcaba solamente un motivo y no todos. Convertir omisiones en negativos
ensenaria datos falsos al modelo.

## Cobertura recuperada

El piso exploratorio se fijo en 25 positivos y 50 negativos por senal. No es un
gate productivo; solamente indica si existe material minimo para un piloto.

| Senal | Positivos | Negativos | Desconocidos | Piso piloto |
| --- | ---: | ---: | ---: | --- |
| Explicito o desnudez | 7 | 77 | 92 | No |
| Ropa interior o bano | 13 | 77 | 86 | No |
| Transparencia | 3 | 77 | 96 | No |
| Escote o pecho | 55 | 77 | 44 | Si |
| Abdomen | 7 | 77 | 92 | No |
| Hombro o axila | 45 | 77 | 54 | Si |
| Codo | 52 | 77 | 47 | Si |
| Rodilla | 13 | 77 | 86 | No |
| Ropa ajustada | 11 | 77 | 88 | No |
| Pose sugerente | 11 | 77 | 88 | No |

Solo escote/pecho, hombro/axila y codo alcanzan el piso. Entrenar ahora las diez
salidas reforzaria tres criterios y dejaria siete cabezas subrepresentadas.

## Decision

- El contrato parcial y el conversor quedan aprobados como base reproducible.
- No se entrena R3 todavia.
- R1 sigue siendo el unico modelo oficial.
- No se tocan Android, DAG, umbrales, APK, telefonos, Supabase ni Production.
- No se abre `final_sealed`.

## Siguiente lote recomendado

`GLOSHIA-R3-FOCUSED-RELABEL-22` debe reconstruir primero las 154 imagenes de
Wikimedia y resolver, cuando sea posible, los 22 hashes `pilot`. La revision no
necesita nuevas decisiones permitir/filtrar: sobre las 99 fotos ya filtradas
se completan solamente los diez motivos, priorizando transparencia, abdomen,
contenido explicito, ropa interior, rodilla, ropa ajustada y pose.

El objetivo minimo es llevar cada senal a 25 positivos independientes sin
convertir ninguna duda en negativa. Despues de ese gate se decide si corresponde
entrenar una candidata R3.

Artefacto privado:

- `.codex-tmp/gloshia-r3-multisignal-20260803/partial-label-audit.json`.

Fuentes reproducibles:

- `scripts/dag_v3_model/r3_multisignal_policy_v1.json`;
- `scripts/dag_v3_model/r3_multisignal_gate.py`.
