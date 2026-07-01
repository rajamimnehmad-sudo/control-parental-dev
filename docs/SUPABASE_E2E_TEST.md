# Supabase E2E Test

Checklist simple para probar App Usuario y App Admin con Supabase real.

## Lo Que Vas A Necesitar

- Un proyecto creado en Supabase.
- `SUPABASE_URL`.
- `SUPABASE_ANON_KEY`.
- Un usuario creado en Supabase Auth.
- Dos APKs dev compilados: Usuario y Admin.
- Uno o dos dispositivos Android.

No uses Service Role Key en Android, `.env` ni Gradle.

## 1. Configurar `.env`

1. Copia `.env.example` como `.env`.
2. Completa:

```text
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=tu-anon-key-publica
UPDATE_MANIFEST_URL=
```

3. Ejecuta:

```bash
scripts/setup_supabase_dev.sh
```

4. Si el script marca un error, corrige `.env` y vuelve a ejecutarlo.

## 2. Crear Usuario Auth

1. Abre Supabase.
2. Ve a Authentication.
3. Crea un usuario con email y password.
4. Copia el `User UID`.

Este paso es manual porque Supabase Auth no se debe crear desde Android ni con anon key.

## 3. Ejecutar SQL

Opción más corta:

1. Abre `supabase/dev_setup_all.sql`.
2. Reemplaza `DEV_AUTH_USER_ID_HERE` por el `User UID`.
3. Copia todo el archivo.
4. En Supabase, abre SQL Editor.
5. Pega y ejecuta.

Si prefieres hacerlo separado:

1. Ejecuta `supabase/schema.sql`.
2. Ejecuta `supabase/rls.sql`.
3. Edita `supabase/dev_test_data.sql`.
4. Reemplaza `DEV_AUTH_USER_ID_HERE` por el `User UID`.
5. Ejecuta `supabase/dev_test_data.sql`.

Al terminar deberías tener:

- Account: `Dev E2E Account`.
- Código Usuario: `TEST-USER-CODE`.
- Código Admin: `TEST-ADMIN-CODE`.
- Política mínima activa.
- Regla global mínima.
- Límite global mínimo.

## 4. Compilar APKs

```bash
./gradlew :app-user:assembleDevDebug :app-admin:assembleDevDebug
```

APKs esperados:

```text
app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
app-admin/build/outputs/apk/dev/debug/app-admin-dev-debug.apk
```

## 5. Instalar APKs

```bash
adb install -r app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk
adb install -r app-admin/build/outputs/apk/dev/debug/app-admin-dev-debug.apk
```

Puedes usar el mismo dispositivo o dos dispositivos.

## 6. Activar Apps

En App Usuario:

1. Abre la app.
2. Usa el email/password del usuario Auth.
3. Usa el código `TEST-USER-CODE`.
4. Confirma que queda activada como usuario.

En App Admin:

1. Abre la app.
2. Usa el mismo email/password.
3. Usa el código `TEST-ADMIN-CODE`.
4. Confirma que queda activada como admin.

Cada código se usa una sola vez. Si reinstalas desde cero y necesitas reactivar, vuelve a ejecutar `supabase/dev_test_data.sql` para crear códigos nuevos.

## 7. Probar Usuario A Admin

1. En App Usuario, crea una solicitud de acceso o tiempo extra.
2. Abre App Admin.
3. Espera unos segundos.
4. Confirma que aparece la solicitud.
5. Aprueba, rechaza o concede tiempo extra desde App Admin.
6. Vuelve a App Usuario.
7. Confirma que el estado de la solicitud cambia.

Si no cambia al instante, espera a que WorkManager sincronice o cierra y vuelve a abrir las apps.

## 8. Probar Reglas Admin A Usuario

1. En App Admin, crea o modifica una regla simple.
2. Espera unos segundos.
3. En App Usuario, verifica que la app siga funcionando y sincronice.
4. Si la regla no aparece inmediatamente, espera el sync de fondo o reabre App Usuario.

## 9. Señales De Éxito

- Usuario y Admin activan con la misma cuenta Auth y códigos distintos.
- La solicitud creada en Usuario aparece en Admin.
- La aprobación o rechazo hecho en Admin vuelve a Usuario.
- El tiempo extra aprobado aparece en Usuario.
- Las reglas creadas en Admin llegan a Usuario.
- La app sigue mostrando datos locales si se corta internet.

## 10. Si Algo Falla

- Activación falla: verifica email/password, `User UID`, account y códigos.
- Solicitudes no aparecen: verifica que ambos APKs usen la misma `.env`.
- Nada sincroniza: revisa `SUPABASE_URL` y `SUPABASE_ANON_KEY`.
- Realtime tarda: espera WorkManager o reabre la app.
- SQL falla por `DEV_AUTH_USER_ID_HERE`: reemplaza el placeholder por el `User UID` real.
- SQL falla por Auth user inexistente: crea el usuario en Authentication primero.
