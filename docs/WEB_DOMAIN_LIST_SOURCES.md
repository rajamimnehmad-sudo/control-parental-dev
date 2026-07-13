# Fuentes de la base Web

Este documento registra las fuentes que alimentan la base Web firmada. La base publicada es una categorizacion tecnica, no una garantia de cobertura total ni una recomendacion de bloquear cada entrada.

## Reglas de incorporacion

- Usar solamente fuentes mantenidas y con licencia documentada.
- Normalizar dominios y eliminar duplicados antes de generar estadisticas.
- Tratar `adult` y `porn` como aliases de una unica categoria `adult`.
- Resolver excepciones educativas antes de bloquear.
- Mantener confirmacion exacta despues del Bloom Filter.
- Rechazar una actualizacion incompleta o fuera de rangos esperados.
- Publicar archivo y manifiesto firmados; reemplazar el puntero solamente al final.
- Conservar la version local anterior si falla descarga, firma, checksum o parseo.

## UT1 Toulouse

- Fuente: `https://dsi.ut-capitole.fr/blacklists/`
- Descarga: `https://dsi.ut-capitole.fr/blacklists/download/`
- Categorias usadas: `adult`, `mixed_adult` y `sexual_education` como excepcion.
- Licencia: Creative Commons indicada por UT1; el archivo vigente es `LICENSE.pdf` dentro del directorio de descarga.
- Mantenimiento declarado: la categoria `adult` es la principal categoria mantenida por el proyecto.

## The Block List Project

- Proyecto: `https://github.com/blocklistproject/Lists`
- Fuente usada: `https://blocklistproject.github.io/Lists/alt-version/porn-nl.txt`
- Categoria upstream: `porn`.
- Categoria interna: alias de `adult`; no se publica una categoria ni una estadistica paralela.
- Licencia del repositorio: Unlicense / dedicacion al dominio publico, apta para uso comercial segun el texto publicado por el proyecto.
- Formato: un dominio por linea, sin direccion IP.
- Mantenimiento declarado: compilacion automatizada, validacion y actualizaciones regulares.

## Alcance inicial de Ticket 2

Se incorpora una sola fuente complementaria. En la medicion previa del 2026-07-13 aporto aproximadamente 344.000 dominios unicos que no estaban en las categorias UT1 usadas, sobre unas 928.000 entradas de origen. Las cifras exactas se calculan nuevamente en cada publicacion y quedan en el manifiesto firmado.

La construccion completa inicial produjo aproximadamente 4,94 millones de entradas unicas y un archivo binario de 44,5 MB. La descarga hacia Android sigue siendo completa cuando existe una version nueva; no hay actualizacion incremental. Antes de aumentar frecuencia o sumar otra fuente debe medirse el consumo real y disenarse un formato por fragmentos o deltas si el costo resulta excesivo.

No se incorporan en este ticket listas de publicidad, redes sociales, apuestas, malware u otras categorias. Cada categoria futura requiere una decision de producto, revision de licencia, pruebas de falsos positivos y un ticket independiente.
