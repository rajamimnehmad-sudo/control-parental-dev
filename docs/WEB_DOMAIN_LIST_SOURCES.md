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
- Categorias usadas: `adult`, `mixed_adult`, `gambling`, `drogue` y `warez`; `sexual_education` se usa como excepcion.
- Licencia: Creative Commons indicada por UT1; el archivo vigente es `LICENSE.pdf` dentro del directorio de descarga.
- Mantenimiento declarado: la categoria `adult` es la principal categoria mantenida por el proyecto.

## The Block List Project

- Proyecto: `https://github.com/blocklistproject/Lists`
- Fuentes usadas: listas `porn`, `gambling`, `drugs`, `piracy` y `torrent` en formato de un dominio por linea.
- Mapeo interno: `porn` es alias de `adult`; `piracy` y `torrent` forman una unica categoria `piracy_torrents`.
- Licencia del repositorio: Unlicense / dedicacion al dominio publico, apta para uso comercial segun el texto publicado por el proyecto.
- Formato: un dominio por linea, sin direccion IP.
- Mantenimiento declarado: compilacion automatizada, validacion y actualizaciones regulares.

## Grupo obligatorio Temas sensibles

La politica global contiene cuatro opciones de producto: adulto, apuestas, drogas y pirateria/torrents. Internamente `adult` y `mixed_adult` permanecen separados para preservar las fuentes existentes, pero pertenecen a la misma opcion adulto. Los aliases no generan categorias ni estadisticas duplicadas.

La medicion local previa a publicar el 2026-07-13 produjo aproximadamente 5,26 millones de entradas unicas: 4,94 millones adulto, 294 mil apuestas, 19,7 mil drogas y 5 mil pirateria/torrents. El binario fue de aproximadamente 47,4 MB. Las cifras oficiales se recalculan en cada publicacion y quedan en el manifiesto firmado.

## Alcance inicial de Ticket 2

Se incorpora una sola fuente complementaria. En la medicion previa del 2026-07-13 aporto aproximadamente 344.000 dominios unicos que no estaban en las categorias UT1 usadas, sobre unas 928.000 entradas de origen. Las cifras exactas se calculan nuevamente en cada publicacion y quedan en el manifiesto firmado.

La construccion completa inicial produjo aproximadamente 4,94 millones de entradas unicas y un archivo binario de 44,5 MB. La descarga hacia Android sigue siendo completa cuando existe una version nueva; no hay actualizacion incremental. Antes de aumentar frecuencia o sumar otra fuente debe medirse el consumo real y disenarse un formato por fragmentos o deltas si el costo resulta excesivo.

No se incorporan listas de publicidad, redes sociales, malware ni categorias adicionales. Cada categoria futura requiere una decision de producto, revision de licencia y pruebas de falsos positivos.
