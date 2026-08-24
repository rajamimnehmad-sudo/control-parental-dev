# CHROME-PROXY-WEB-SEMANTICS-11A — contrato técnico previo

Estado: PREPARED / NO EJECUTAR HASTA 10A PASS.

## Objetivo

Transformar el proxy HTTPS DEV de Chrome desde una implementación de laboratorio a un proxy semánticamente compatible con navegación web normal, sin debilitar la autoridad de imagen de GloshIA ni permitir bypass directo.

## Principio

El proxy no debe reconstruir una versión simplificada de HTTP. Debe preservar semántica end-to-end y transformar sólo aquello que necesita por seguridad.

## CONNECT

- hostname DNS normalizado/IDNA;
- puerto 443 inicialmente;
- raw IP, loopback, private, link-local, multicast y destinos reservados rechazados salvo fixture explícita;
- DNS resolution bounded y anti-rebinding;
- leaf certificate SAN exacto;
- upstream TLS con trust y hostname verification normales;
- leaf cache bounded/versioned;
- CA por sesión;
- upstream sockets protegidos antes de connect.

## Request methods

Primera versión funcional:

- GET;
- HEAD;
- POST;
- OPTIONS;
- PUT;
- PATCH;
- DELETE.

Método desconocido válido:

- forward genérico si framing y policy pueden preservarse;
- de lo contrario fail closed con error explícito, nunca downgrade a GET.

## Request framing

Soportar:

- Content-Length;
- Transfer-Encoding: chunked;
- body vacío;
- request body streaming bounded;
- Expect: 100-continue o policy explícita coherente.

Prevenir:

- request smuggling;
- CL/TE ambiguity;
- CRLF injection;
- headers oversized;
- line count/body limits sin límites globales absurdamente bajos para web normal.

## Request headers

Preservar end-to-end salvo policy explícita:

- Cookie;
- Authorization;
- Origin;
- Referer;
- User-Agent;
- Accept;
- Accept-Language;
- Accept-Encoding;
- Sec-Fetch-*;
- Sec-CH-UA-*;
- If-None-Match;
- If-Modified-Since;
- Range;
- If-Range;
- Content-Type;
- Content-Length recalculado sólo si Glosh modifica body.

Retirar hop-by-hop:

- Connection;
- Proxy-Connection;
- Keep-Alive;
- TE cuando corresponda;
- Trailer;
- Transfer-Encoding entre hops según framing;
- Upgrade si no está soportado;
- Proxy-Authenticate;
- Proxy-Authorization.

Nunca loguear:

- Cookie;
- Authorization;
- query completa si puede contener secretos;
- bodies de forms/login;
- tokens.

## Response

Preservar status y reason semantics.

Preservar end-to-end:

- Set-Cookie;
- Content-Type;
- Content-Encoding;
- Content-Language;
- Content-Disposition;
- Cache-Control;
- Expires;
- ETag;
- Last-Modified;
- Vary;
- Location;
- Access-Control-*;
- Content-Security-Policy;
- Content-Security-Policy-Report-Only;
- Cross-Origin-Resource-Policy;
- Cross-Origin-Embedder-Policy;
- Cross-Origin-Opener-Policy;
- Permissions-Policy;
- Referrer-Policy;
- Strict-Transport-Security;
- X-Content-Type-Options;
- X-Frame-Options cuando exista.

Retirar/reescribir sólo lo necesario:

- hop-by-hop;
- Content-Length si body cambia;
- Content-Encoding si Glosh decodifica/reencoda;
- validators de un body transformado;
- Alt-Svc si puede reabrir HTTP/3/QUIC fuera de la autoridad del proxy.

## Compression

No forzar `identity` globalmente.

Pipeline:

- preservar negociación normal;
- si recurso necesita inspección: decode gzip/br/zstd si soportado y bounded;
- clasificar bytes decodificados;
- SAFE: puede devolver original comprimido intacto si body no cambia;
- BLOCK/UNKNOWN: generar placeholder y recalcular Content-Encoding/Length/validators;
- unsupported encoding en recurso potencialmente imagen => UNKNOWN/fail closed, no passthrough.

## Streaming

No bufferizar HTML/CSS/JS grandes completos sin necesidad.

- non-image known content -> streaming;
- potential image -> bounded spool/decode antes de entrega;
- unknown/ambiguous -> policy conservadora;
- body size limits por tipo, no un único 12 MiB para todo.

## Image authority hook

La decisión de imagen sale de 11B. 11A sólo provee contexto:

- request URL final;
- redirect chain;
- request headers / Sec-Fetch-Dest;
- response MIME;
- status;
- encoding;
- full/partial body metadata.

Contrato:

`SAFE(bytes original, metadata)` -> entregar representación aprobada.

`BLOCK` -> placeholder.

`UNKNOWN` -> placeholder/fail closed.

Nunca `UNKNOWN -> original`.

## Redirects

- no seguir redirects internamente de forma incompatible con browser semantics;
- cuando se followee upstream por implementación, preservar method rules de 301/302/303/307/308;
- host nuevo debe pasar authority general del CONNECT/full-tunnel;
- Location se conserva;
- loops bounded;
- credentials no se filtran cross-origin.

## Cookies/session

Gates obligatorios:

- Set-Cookie -> navegador;
- request Cookie vuelve al origin correcto;
- Secure/HttpOnly/SameSite no se modifican;
- redirects preservan session;
- cookies no aparecen en logs/evidence.

## CORS/CSP/cross-origin

Tests fixture:

- CORS simple/preflight;
- CSP script/style/img;
- CORP;
- COEP;
- COOP;
- iframe same/cross origin;
- `Sec-Fetch-*` preservado.

## Range/206

No permitir que Range evada GloshIA.

Para potencial imagen:

- no entregar fragmento original antes de decisión completa;
- reconstruir/obtener representación completa bounded;
- clasificar;
- SAFE -> servir rango coherente de bytes aprobados;
- BLOCK/UNKNOWN -> placeholder/policy coherente.

Para non-image media/PDF:

- preservar Range según policy del producto;
- no confundir con cierre de fotos.

## Cache

No `no-store` global.

Cache segura de decisión separada:

key incluye:

- final URL;
- ETag/Last-Modified cuando existan;
- body/content hash;
- model SHA;
- policy generation;
- preprocessing generation.

Persistencia:

- SAFE approved bytes opcional;
- placeholder BLOCK/UNKNOWN;
- nunca persistir original BLOCK/UNKNOWN.

304 sólo reutilizable si cache item sigue válido bajo misma generación.

## WebSocket/Upgrade

11A puede cerrar como una de dos opciones:

A. soporte correcto end-to-end;
B. bloqueo explícito/documentado.

No responder como HTTP normal corrupto.

## HTTP/2 y HTTP/3

Browser-facing H1 puede ser aceptable en primera iteración si semántica es correcta.

Upstream H2 permitido.

HTTP/3/QUIC directo de Chrome debe seguir bloqueado por full-tunnel; `Alt-Svc` no debe permitir escape.

## Security limits

Bounded:

- header bytes/count;
- request body spool;
- image body/decode;
- redirect count;
- concurrent connections;
- pending classification queue;
- per-host connections.

No usar límites tan bajos que rompan páginas normales sin evidencia.

## Functional fixture matrix

- GET;
- HEAD;
- POST form;
- POST JSON;
- PUT/PATCH/DELETE;
- chunked request;
- chunked response;
- gzip/br;
- cookies;
- auth fixture ficticia;
- redirect 301/302/303/307/308;
- cross-host redirect;
- Range/206;
- ETag/304;
- CORS/preflight;
- CSP/CORP/COEP/COOP;
- download Content-Disposition;
- large streaming non-image;
- MIME incorrecto image fixture;
- WebSocket expected support/block result.

## Real page matrix

Sin credenciales personales:

- example.com;
- Google home/search;
- Wikipedia;
- GitHub público;
- noticias;
- e-commerce público;
- multi-CDN/lazy-load.

PASS funcional no requiere que 13A provenance esté cerrado, pero sí debe marcar qué contenido visual no fue certificado.

## Metrics

Sanitizadas:

- CONNECT accepted/rejected reason;
- request methods counts;
- response status counts;
- bytes upstream/downstream;
- stream vs image-spool;
- image authority SAFE/BLOCK/UNKNOWN;
- compression decode;
- range decisions;
- cache hit/miss;
- queue latency;
- no secrets.

## Definition of Done 11A

- navegación general ya no depende de allowlist exacta;
- GET/POST/cookies/redirects funcionan;
- security headers preservados;
- compression funciona;
- Range no evade image authority;
- non-image streaming evita full buffering;
- proxy upstream está protegido contra VPN recursion;
- direct Chrome bypass sigue 0;
- SAFE/BLOCK/UNKNOWN sin regresión;
- crash/ANR/OOM=0 en gate.

11A no cierra `data/blob/canvas/Service Worker`; eso queda para 13A.