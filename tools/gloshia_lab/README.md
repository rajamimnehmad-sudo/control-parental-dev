# Laboratorio GloshIA

Herramienta local para evaluar el mismo modelo visual INT8 y la misma política
de DAG. No forma parte del APK, no usa una API de inferencia y no reentrena ni
reemplaza modelos.

## Privacidad y separación

- El servidor escucha únicamente en `127.0.0.1`.
- Las imágenes, predicciones y revisiones se guardan en `.codex-tmp`, fuera de
  Git.
- Sólo se adquieren miniaturas públicas de Wikimedia Commons con metadatos de
  procedencia y licencias CC BY, CC BY-SA, CC0 o dominio público. CC BY-SA se
  admite para evaluación local, pero queda marcada como no autorizada para
  entrenamiento hasta una revisión legal y de atribución independiente.
- Los perfiles privados y Supabase no participan.
- `final_sealed` no se analiza ni aparece en la interfaz sin una opción
  explícita. Abrirlo es un evento final, no una acción cotidiana.
- La API de revisión no entrega la predicción, el score, el split ni el estrato
  de una muestra pendiente. Los revela únicamente después de guardar la
  decisión humana, para que ocultarlos no dependa sólo de CSS.
- La procedencia registrada permite evaluación local. Una imagen no queda
  automáticamente autorizada para entrenamiento o redistribución: la página
  original, atribución, derechos de imagen y licencia deben revisarse antes.
- Los nombres `boundary_current`, `safe_hard`, `collage_group`,
  `children_normal` y `sensitive_control` describen estratos de búsqueda. No son
  la verdad de la imagen ni sustituyen la decisión del revisor.

## Calidad del banco

- El plan busca material de 2023 a 2026 y rechaza capturas antiguas, escaneos y
  archivos históricos aunque se hayan subido recientemente.
- Intercala los resultados de todas las consultas para que una sola búsqueda no
  domine el banco.
- Limita cada serie a 15 imágenes y cada autor a 30, elimina bytes idénticos y
  aplica dHash más pHash para reducir duplicados visuales.
- Una serie completa se asigna a un único split para evitar que ángulos vecinos
  aparezcan a ambos lados de una evaluación.
- El proceso conserva un checkpoint reanudable. No declara completo el corpus
  hasta alcanzar exactamente el objetivo de cada estrato.
- La división determinista es 60 % evaluación principal, 20 % casos difíciles y
  20 % examen final sellado.

## Preparación

Requiere Python 3.12, Node 20 o posterior y pnpm:

```bash
python3 -m venv .codex-tmp/gloshia-lab-venv
.codex-tmp/gloshia-lab-venv/bin/pip install -r tools/gloshia_lab/requirements.txt
pnpm install --dir tools/gloshia_lab
```

Las dependencias quedan fijadas por lockfile: `onnxruntime-web 1.27.0` (MIT),
`sharp 0.35.3` (Apache-2.0), `numpy 2.2.6` y `Pillow 11.3.0`.

Verificar modelo, dependencias y plan:

```bash
PYTHONPATH=. .codex-tmp/gloshia-lab-venv/bin/python \
  -m tools.gloshia_lab.cli verify
```

## Flujo

Piloto de 100:

```bash
PYTHONPATH=. python -m tools.gloshia_lab.cli build-corpus \
  .codex-tmp/gloshia-lab-pilot --target 100
PYTHONPATH=. python -m tools.gloshia_lab.cli score \
  .codex-tmp/gloshia-lab-pilot
PYTHONPATH=. python -m tools.gloshia_lab.cli report \
  .codex-tmp/gloshia-lab-pilot
PYTHONPATH=. python -m tools.gloshia_lab.cli serve \
  .codex-tmp/gloshia-lab-pilot
```

Corpus completo:

```bash
PYTHONPATH=. python -m tools.gloshia_lab.cli build-corpus \
  .codex-tmp/gloshia-lab-current-1000
PYTHONPATH=. python -m tools.gloshia_lab.cli score \
  .codex-tmp/gloshia-lab-current-1000
PYTHONPATH=. python -m tools.gloshia_lab.cli contact-sheets \
  .codex-tmp/gloshia-lab-current-1000 \
  .codex-tmp/gloshia-lab-current-1000/contact-sheets
PYTHONPATH=. python -m tools.gloshia_lab.cli serve \
  .codex-tmp/gloshia-lab-current-1000
```

Después de una ronda humana, una lectura regional diagnóstica puede escribirse
en un archivo separado sin reemplazar las predicciones canónicas:

```bash
PYTHONPATH=. python -m tools.gloshia_lab.cli score \
  .codex-tmp/gloshia-lab-current-1000 \
  --diagnostic-regions \
  --output .codex-tmp/gloshia-lab-current-1000/diagnostic-regions.jsonl
PYTHONPATH=. python -m tools.gloshia_lab.cli calibration-report \
  .codex-tmp/gloshia-lab-current-1000 \
  .codex-tmp/gloshia-lab-current-1000/diagnostic-regions.jsonl
```

El reporte elige parámetros solamente con revisiones `main_eval` y muestra por
separado el resultado en `difficult`. No abre `final_sealed`, no modifica DAG y
no convierte un candidato de laboratorio en política aprobada.

La interfaz permite revisar la cola completa de evaluables y está preparada
para teléfono y computadora:

- deslizar a la derecha significa `Permitir`;
- deslizar a la izquierda significa `Filtrar`;
- `Dudosa` queda como botón separado;
- después de guardar muestra si la decisión coincide con GloshIA;
- `Deshacer última` elimina por completo la última revisión accidental;
- `Anterior` y `Siguiente` permiten navegar sin decidir;
- permite filtrar desacuerdos, falsos permisos, falsos filtros, dudas,
  categorías y origen;
- exporta/importa JSON y `Comenzar nuevamente` crea un respaldo local antes de
  reiniciar las decisiones;
- los botones y las flechas del teclado permiten revisar sin gestos;
- el resultado, score, split y estrato del modelo siguen ausentes de la API
  hasta que la decisión humana ya quedó guardada.

Por defecto el servidor permanece limitado a `127.0.0.1`. Para una revisión
temporal desde un teléfono conectado a la misma Wi-Fi puede iniciarse con
`--lan`: genera una clave aleatoria, acepta sólo hosts IP privados, crea una
cookie `HttpOnly` de cuatro horas y rechaza accesos sin autenticación. No
publica el banco en Internet y debe detenerse al terminar:

```bash
PYTHONPATH=. python -m tools.gloshia_lab.cli serve \
  .codex-tmp/gloshia-lab-current-1000 --lan
```

La red local usa HTTP; debe emplearse únicamente en una Wi-Fi privada y
confiable. El acceso remoto por Internet requiere HTTPS y autenticación en un
ticket separado.

La salida normal excluye las 200 muestras selladas. Sólo después de congelar
decisiones y cualquier ajuste:

```bash
PYTHONPATH=. python -m tools.gloshia_lab.cli score \
  .codex-tmp/gloshia-lab-current-1000 --include-sealed
PYTHONPATH=. python -m tools.gloshia_lab.cli report \
  .codex-tmp/gloshia-lab-current-1000 --include-sealed
```

El runner usa `onnxruntime-web` WASM porque ONNX Runtime nativo para macOS no
implementa `ConvInteger` del artefacto dinámicamente cuantizado. Los bytes y el
SHA-256 del modelo son exactamente los del APK. Sharp replica el ajuste
completo, padding gris y regiones; pequeñas diferencias de decodificación y
redimensionado entre macOS y Android se controlan con una validación física
final antes de cambiar DAG.

## Referencias primarias

- [ONNX Runtime Web](https://onnxruntime.ai/docs/get-started/with-javascript/web.html)
- [MediaWiki API: Imageinfo](https://www.mediawiki.org/wiki/API:Imageinfo)
- [Reutilización de contenido de Wikimedia Commons](https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia)
