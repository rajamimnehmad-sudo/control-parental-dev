# Glosh Apps UI Lab 01

Laboratorio visual Vite aislado para diseñar la experiencia Android de:

- Glosh App Usuario
- Glosh App Admin

## Arranque rápido

```bash
cd tools/apps-ui-lab
pnpm install
pnpm dev
```

La app queda en:

- `http://localhost:5173/` (si está libre, si no se incrementa al siguiente puerto)

`vite` arranca con `--host true`, así que también es accesible desde red local.

## Flujo recomendado de iteración

1. Cambiás un componente en `src/`.
2. Confirmás el cambio en el frame de Vite (HMR).
3. Ajustás con la vista Android-like o iPhone-like.
4. Revisás `Normal`, `Loading`, `Empty` y `Error` desde la barra superior.

## Controles del lab

- Cambia App: `App Usuario / App Admin`
- Usuario: Inicio, Dispositivo, Protección y Licencia.
- Admin: Login, Dashboard, Mis dispositivos, Detalle, Reglas, Apps y tiempos, y Licencia.
- Estados visuales: Normal, Loading, Empty y Error.

## Build de validación

```bash
cd tools/apps-ui-lab
pnpm install --frozen-lockfile
pnpm build
pnpm preview
```

Todo el contenido es mock. El lab no usa backend, no reemplaza las apps Android y no contiene lógica productiva.
