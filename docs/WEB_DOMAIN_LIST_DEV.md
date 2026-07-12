# Base dinamica Web DEV

## Fuente y licencia

- Fuente: UT1 Blacklists, Universite Toulouse Capitole.
- Categorias de bloqueo: `adult` y `mixed_adult`.
- Excepciones educativas: `sexual_education`.
- Licencia de la fuente: Creative Commons Attribution-ShareAlike 4.0.
- La fuente indica que la base se renueva aproximadamente dos veces por semana.

## Publicacion

El workflow `update-web-domain-list-dev.yml` corre diariamente y revisa solicitudes manuales cada cinco minutos. El generador:

1. descarga las categorias configuradas;
2. normaliza y deduplica dominios;
3. construye filtros Bloom categorizados y listas exactas de excepciones/canarios;
4. genera un archivo binario y un manifiesto;
5. firma ambos con ECDSA P-256/SHA-256;
6. publica objetos versionados inmutables;
7. reemplaza `current-manifest.json` solamente al final.

Ubicacion publica DEV:

```text
dev-updates/web-domain-list/dev/current-manifest.json
dev-updates/web-domain-list/dev/versions/<version>.bin
dev-updates/web-domain-list/dev/versions/<version>.manifest.json
dev-updates/web-domain-list/dev/status.json
```

La clave privada existe solo como `DOMAIN_LIST_SIGNING_PRIVATE_KEY_PKCS8_B64` en GitHub Actions Secrets. Android y Super Admin contienen unicamente la clave publica X.509.

## Canary DEV

`coca.com` se agrega al archivo dinamico solamente cuando Super Admin solicita `Publicar canario DEV`. No existe como excepcion en Android y no forma parte de los conteos oficiales UT1. Los paths Beta y Production son independientes y el generador DEV no publica en ellos.

## Cliente Android

App Usuario comprueba el manifiesto al iniciar el proceso y luego aproximadamente una vez al dia. Solo instala versiones mayores luego de verificar firma, tamano y SHA-256. El reemplazo es atomico y conserva `previous.bin`; sin red o ante un archivo invalido sigue usando la ultima base valida.
