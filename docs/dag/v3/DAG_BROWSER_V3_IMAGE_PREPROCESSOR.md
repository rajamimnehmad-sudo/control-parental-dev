# DAG Browser V3 - preprocesador visual local

## Estado

Implementado y validado fisicamente en el Samsung SM-A235M. Su salida todavia no puede producir
`allow` ni `blur`: una imagen preparada correctamente termina en `block / analyzer_unavailable`.

## Objetivo

Convertir una respuesta de imagen ya acotada por el transporte en una entrada visual pequena,
determinista y privada para el futuro modelo local. El preprocesador no decide si una imagen es
apta.

## Contrato

1. Solo recibe los bytes originales capturados por Gecko, sin una segunda descarga.
2. Conserva el limite de 2 MiB comprimidos por imagen.
3. Revalida formato y dimensiones antes de decodificar pixeles:
   - AVIF, BMP, GIF estatico, HEIC/HEIF, ICO, JPEG, PNG o WebP;
   - maximo 4096 px por lado;
   - maximo 16 777 216 pixeles de origen.
4. Rechaza animaciones, imagenes parciales, formatos no admitidos y errores de decodificacion.
5. Solicita al decodificador una salida cuyo lado mayor ya esta reducido a 224 px.
6. Fuerza bitmap de software y espacio de color sRGB.
7. Ajusta la imagen completa dentro de 224 x 224, sin recortar, con relleno gris neutro.
8. Entrega exactamente 150 528 bytes RGB888.
9. Recicla los bitmaps y sobrescribe el RGB al terminar la decision; no persiste, sube ni registra
   pixeles.

La lectura de encabezado con `BitmapFactory` es una comprobacion preliminar, no
la lista real de formatos de Android. Si no reconoce el archivo, `ImageDecoder`
puede identificarlo y entrega MIME y dimensiones autoritativas antes de crear
pixeles. Ambos caminos aplican los mismos limites; si ninguno obtiene metadatos
validos, la imagen queda bloqueada.

No recortar es intencional: hombros, mangas, cintura, rodillas y contexto de vestimenta pueden
quedar fuera de un recorte central. Entrenamiento y evaluacion deben usar exactamente esta misma
geometria y relleno.

## Limites de memoria

Por analisis, los buffers propios mas grandes despues de validar el encabezado son:

- bitmap reducido: hasta 200 704 bytes;
- lienzo 224 x 224: 200 704 bytes;
- RGB888: 150 528 bytes;
- una fila temporal: 896 bytes.

Se suman los bytes comprimidos, su sobre de mensajeria y memoria interna del decodificador. La
concurrencia nativa sigue limitada a dos trabajos, con cola acotada. La memoria real, latencia y
temperatura quedaron medidas en el Samsung SM-A235M; estos calculos siguen siendo el limite de
diseno y no reemplazan los gates fisicos de cada candidato.

## Motivos de cierre

- `animated_image`: el archivo contiene animacion;
- `decode_failed`: el archivo no pudo convertirse al contrato RGB;
- `unsupported_image`: el formato no esta admitido;
- `unsafe_dimensions`: el encabezado excede los limites;
- `analyzer_unavailable`: el RGB se preparo bien, pero aun no existe un modelo aprobado.

Todas estas salidas son `block`.

## Decisiones pendientes

- ampliar el corpus de formatos con PNG transparente, WebP animado, EXIF y datos truncados;
- conectar un runtime solo cuando exista una candidata de modelo versionada y licenciada;
- mantener bloqueado cualquier resultado dudoso o error.

La prueba fisica ya cubrio JPEG, PNG, WebP, GIF estatico, GIF animado, contenido corrupto, arranque
frio y scroll rapido, con p50/p95, memoria y temperatura. Evidencia:
`docs/compatibility/results/dag-browser-v3-image-preprocessor-sm-a235m-2026-07-27.md`.

## Fuentes primarias

- Android `ImageDecoder` permite reducir durante la decodificacion con `setTargetSize`, elegir
  allocator y color, y rechaza archivos incompletos si no se acepta una imagen parcial:
  <https://developer.android.com/reference/android/graphics/ImageDecoder>
- `ImageInfo.isAnimated` expone si GIF o WebP es animado:
  <https://developer.android.com/reference/android/graphics/ImageDecoder.ImageInfo>
