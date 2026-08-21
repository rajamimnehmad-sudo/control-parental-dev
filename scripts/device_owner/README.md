# GLOSH-DEVICE-OWNER-INSTALLER-00

Asistente macOS para el gate Device Owner de laboratorio de App Usuario DEV.
No contiene operaciones para eliminar cuentas, usuarios, apps o datos, ni para
hacer factory reset.

## Preflight read-only

```bash
scripts/device_owner/glosh_device_owner_installer.sh preflight
```

Genera un checkpoint privado en `/private/tmp/glosh-device-owner-checkpoints`.
Incluye únicamente proveedores y cantidades de cuentas; nunca nombres, claves,
tokens o contraseñas.

## Ejecución controlada

```bash
scripts/device_owner/glosh_device_owner_installer.sh run \
  --apk /ruta/app-user-dev-debug.apk
```

Si hay cuentas, ofrece abrir Ajustes y espera a que el usuario las retire
manualmente. Con cuenta cero verifica el hash sellado de DEV 319, exige una
confirmación ligada al serial, actualiza in-place y realiza un único intento de
`dpm set-device-owner`. Un recibo local evita reintentos automáticos.

## Verificación posterior

```bash
scripts/device_owner/glosh_device_owner_installer.sh verify
```

Usar después de volver a agregar manualmente las cuentas. Confirma que Glosh
sigue siendo Device Owner, Device Admin y que Accessibility continúa configurado.
