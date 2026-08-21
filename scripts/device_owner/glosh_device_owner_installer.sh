#!/usr/bin/env bash

set -euo pipefail

readonly EXPECTED_PACKAGE="com.contentfilter.user.dev"
readonly EXPECTED_VERSION_CODE="319"
readonly EXPECTED_APK_SHA256="ba612fe2f23c5633e7041bf6c233d1ed435db3bcc7f43e6d47dfb03d7b7cf14b"
readonly EXPECTED_COMPONENT="com.contentfilter.user.dev/com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver"

COMMAND="run"
APK_PATH=""
CHECKPOINT_DIR="${DEVICE_OWNER_CHECKPOINT_DIR:-/private/tmp/glosh-device-owner-checkpoints}"
NON_INTERACTIVE=0
ADB_BIN="${ADB_BIN:-}"
DEVICE_SERIAL=""
CHECKPOINT_FILE=""

usage() {
    cat <<'EOF'
Uso:
  glosh_device_owner_installer.sh preflight [--checkpoint-dir DIR]
  glosh_device_owner_installer.sh run --apk APK [--checkpoint-dir DIR]
  glosh_device_owner_installer.sh verify [--checkpoint-dir DIR]

Seguridad:
  - preflight y verify son read-only para el teléfono.
  - run nunca elimina cuentas, usuarios, apps ni datos.
  - run exige confirmación manual antes de instalar y ejecutar Device Owner.
  - un recibo local impide repetir set-device-owner para el mismo teléfono/APK.
EOF
}

fail() {
    local exit_code="$1"
    shift
    printf 'STOP: %s\n' "$*" >&2
    exit "$exit_code"
}

resolve_adb() {
    if [[ -n "$ADB_BIN" ]]; then
        [[ -x "$ADB_BIN" ]] || fail 2 "ADB_BIN no es ejecutable: $ADB_BIN"
        return
    fi

    if command -v adb >/dev/null 2>&1; then
        ADB_BIN="$(command -v adb)"
        return
    fi

    local candidate=""
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        candidate="${ANDROID_SDK_ROOT}/platform-tools/adb"
    elif [[ -n "${ANDROID_HOME:-}" ]]; then
        candidate="${ANDROID_HOME}/platform-tools/adb"
    else
        candidate="/Users/$(id -un)/Library/Android/sdk/platform-tools/adb"
    fi
    [[ -x "$candidate" ]] || fail 2 "No se encontró ADB. Definí ADB_BIN o instalá Android platform-tools."
    ADB_BIN="$candidate"
}

select_single_device() {
    local devices_output device_lines attached_count state
    devices_output="$($ADB_BIN devices)"
    device_lines="$(printf '%s\n' "$devices_output" | awk 'NR > 1 && NF >= 2 { print $1 " " $2 }')"
    attached_count="$(printf '%s\n' "$device_lines" | awk 'NF { count++ } END { print count + 0 }')"
    [[ "$attached_count" == "1" ]] || fail 3 "Se requiere exactamente un teléfono ADB; detectados: $attached_count"
    DEVICE_SERIAL="$(printf '%s\n' "$device_lines" | awk 'NF { print $1; exit }')"
    state="$(printf '%s\n' "$device_lines" | awk 'NF { print $2; exit }')"
    [[ "$state" == "device" ]] || fail 3 "El único dispositivo no está autorizado/listo: $state"
}

adb_device() {
    "$ADB_BIN" -s "$DEVICE_SERIAL" "$@"
}

adb_shell() {
    adb_device shell "$@"
}

sha256_file() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{ print $1 }'
    else
        openssl dgst -sha256 "$1" | awk '{ print $NF }'
    fi
}

collect_state() {
    MODEL="$(adb_shell getprop ro.product.model | tr -d '\r')"
    ANDROID_VERSION="$(adb_shell getprop ro.build.version.release | tr -d '\r')"
    API_LEVEL="$(adb_shell getprop ro.build.version.sdk | tr -d '\r')"
    IS_EMULATOR="$(adb_shell getprop ro.kernel.qemu | tr -d '\r')"
    DEVICE_PROVISIONED="$(adb_shell settings get global device_provisioned | tr -d '\r')"
    USER_SETUP_COMPLETE="$(adb_shell settings get secure user_setup_complete | tr -d '\r')"
    OWNERS_OUTPUT="$(adb_shell dpm list-owners | tr -d '\r')"
    USERS_OUTPUT="$(adb_shell pm list users | tr -d '\r')"
    USER_COUNT="$(printf '%s\n' "$USERS_OUTPUT" | awk '/UserInfo\{/ { count++ } END { print count + 0 }')"
    USER_IDS="$(printf '%s\n' "$USERS_OUTPUT" | sed -nE 's/.*UserInfo\{([0-9]+):.*:([^}]*)\}.*/id=\1 flags=\2/p')"

    local account_dump
    account_dump="$(adb_shell dumpsys account | tr -d '\r')"
    ACCOUNT_COUNT="$(printf '%s\n' "$account_dump" | awk '/^[[:space:]]*Accounts: [0-9]+/ { print $2; exit }')"
    [[ "$ACCOUNT_COUNT" =~ ^[0-9]+$ ]] || fail 4 "No se pudo determinar el número de cuentas de forma segura."
    ACCOUNT_PROVIDERS="$(printf '%s\n' "$account_dump" |
        awk '
            /^[[:space:]]*Accounts: [0-9]+/ { in_accounts=1; next }
            in_accounts && /^[[:space:]]*Account \{/ { print; next }
            in_accounts { exit }
        ' |
        sed -nE 's/.*type=([^}]*)\}.*/\1/p' |
        sort |
        uniq -c |
        sed -E 's/^[[:space:]]+//')"

    DEVICE_POLICY_OUTPUT="$(adb_shell dumpsys device_policy | tr -d '\r')"
    if printf '%s\n' "$DEVICE_POLICY_OUTPUT" | grep -Fq "$EXPECTED_COMPONENT"; then
        GLOSH_ADMIN_ACTIVE="yes"
    else
        GLOSH_ADMIN_ACTIVE="no"
    fi

    TOTAL_PACKAGES="$(adb_shell pm list packages | awk '/^package:/ { count++ } END { print count + 0 }')"
    THIRD_PARTY_PACKAGES="$(adb_shell pm list packages -3 | awk '/^package:/ { count++ } END { print count + 0 }')"
    GLOSH_PACKAGE_OUTPUT="$(adb_shell dumpsys package "$EXPECTED_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    GLOSH_VERSION="$(printf '%s\n' "$GLOSH_PACKAGE_OUTPUT" | sed -nE 's/^[[:space:]]*versionCode=([0-9]+).*/\1/p' | head -n 1)"
    GLOSH_DATA_INODE="$(printf '%s\n' "$GLOSH_PACKAGE_OUTPUT" | sed -nE 's/.*ceDataInode=([0-9]+).*/\1/p' | head -n 1)"
}

owner_state() {
    if printf '%s\n' "$OWNERS_OUTPUT" | grep -Fqi 'no owners'; then
        printf 'none\n'
    elif printf '%s\n' "$OWNERS_OUTPUT" | grep -Fq "$EXPECTED_COMPONENT" &&
        printf '%s\n' "$OWNERS_OUTPUT" | grep -Eqi 'DeviceOwner|Device Owner|device owner'; then
        printf 'glosh\n'
    else
        printf 'other\n'
    fi
}

write_checkpoint() {
    umask 077
    mkdir -p "$CHECKPOINT_DIR"
    local stamp provider_text glosh_version_text glosh_inode_text
    stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    CHECKPOINT_FILE="$CHECKPOINT_DIR/${stamp}-${DEVICE_SERIAL}-preflight.txt"
    provider_text="${ACCOUNT_PROVIDERS:-ninguna}"
    glosh_version_text="${GLOSH_VERSION:-no_instalado}"
    glosh_inode_text="${GLOSH_DATA_INODE:-no_disponible}"

    {
        printf 'GLOSH-DEVICE-OWNER-INSTALLER-00 — CHECKPOINT PREVIO\n'
        printf 'Fecha UTC: %s\n' "$stamp"
        printf 'Serial: %s\n' "$DEVICE_SERIAL"
        printf 'Modelo: %s\n' "$MODEL"
        printf 'Android/API: %s/%s\n' "$ANDROID_VERSION" "$API_LEVEL"
        printf 'device_provisioned: %s\n' "$DEVICE_PROVISIONED"
        printf 'user_setup_complete: %s\n' "$USER_SETUP_COMPLETE"
        printf 'Usuarios: %s\n' "$USER_COUNT"
        printf '%s\n' "$USER_IDS"
        printf 'Owners:\n%s\n' "$OWNERS_OUTPUT"
        printf 'Glosh Device Admin activo: %s\n' "$GLOSH_ADMIN_ACTIVE"
        printf 'Glosh versionCode instalada: %s\n' "$glosh_version_text"
        printf 'Glosh ceDataInode: %s\n' "$glosh_inode_text"
        printf 'Cuentas registradas: %s\n' "$ACCOUNT_COUNT"
        printf 'Tipos/proveedores (cantidad tipo):\n%s\n' "$provider_text"
        printf 'Apps instaladas: %s total; %s de terceros\n' "$TOTAL_PACKAGES" "$THIRD_PARTY_PACKAGES"
        printf '\nNo se registraron nombres, contraseñas, tokens ni secretos.\n'
        printf '\nAntes de retirar cuentas:\n'
        printf '1. Confirmá que fotos, contactos, archivos y chats importantes estén sincronizados o respaldados.\n'
        printf '2. No borres datos de WhatsApp ni de ninguna app.\n'
        printf '3. Retirá manualmente las cuentas desde Ajustes > Cuentas y respaldo > Administrar cuentas.\n'
        printf '4. Tras confirmar Device Owner, volvé a agregar manualmente Google, Samsung y los demás proveedores listados arriba.\n'
    } >"$CHECKPOINT_FILE"
    printf 'Checkpoint: %s\n' "$CHECKPOINT_FILE"
}

print_summary() {
    printf 'Teléfono: %s | Android %s / API %s | serial %s\n' "$MODEL" "$ANDROID_VERSION" "$API_LEVEL" "$DEVICE_SERIAL"
    printf 'Provisioning: device_provisioned=%s user_setup_complete=%s\n' "$DEVICE_PROVISIONED" "$USER_SETUP_COMPLETE"
    printf 'Usuarios: %s (%s)\n' "$USER_COUNT" "$(printf '%s' "$USER_IDS" | tr '\n' ';')"
    printf 'Cuentas: %s\n' "$ACCOUNT_COUNT"
    printf 'Glosh Device Admin: %s | versionCode: %s\n' "$GLOSH_ADMIN_ACTIVE" "${GLOSH_VERSION:-no_instalado}"
    printf 'Owners: %s\n' "$(printf '%s' "$OWNERS_OUTPUT" | tr '\n' ' ')"
}

check_static_preconditions() {
    [[ "$IS_EMULATOR" != "1" ]] || fail 5 "Se detectó un emulador; este ticket exige teléfono físico."
    [[ "$USER_COUNT" == "1" ]] || fail 12 "Hay $USER_COUNT usuarios/perfiles. No se eliminará ninguno automáticamente."
    printf '%s\n' "$USER_IDS" | grep -q '^id=0 ' || fail 12 "El único usuario no es el usuario principal 0."
    local current_owner
    current_owner="$(owner_state)"
    [[ "$current_owner" != "other" ]] || fail 11 "Existe otro owner. No se realizará ninguna modificación."
}

preflight() {
    resolve_adb
    select_single_device
    collect_state
    print_summary
    write_checkpoint
    check_static_preconditions
    if [[ "$ACCOUNT_COUNT" != "0" ]]; then
        printf 'BLOCKED: deben retirarse manualmente %s cuentas antes de continuar.\n' "$ACCOUNT_COUNT" >&2
        return 10
    fi
    printf 'PRECHECK PASS: usuario 0 único, cuentas=0 y sin owner incompatible.\n'
}

open_accounts_settings() {
    printf 'Abriendo Ajustes de cuentas. El script no retirará ninguna cuenta.\n'
    adb_shell am start -a android.settings.SYNC_SETTINGS >/dev/null
}

verify_apk() {
    [[ -n "$APK_PATH" ]] || fail 20 "Falta --apk con la candidata DEV 319 preservada."
    [[ -f "$APK_PATH" ]] || fail 20 "APK inexistente: $APK_PATH"
    local actual_sha
    actual_sha="$(sha256_file "$APK_PATH")"
    printf 'APK SHA-256: %s\n' "$actual_sha"
    [[ "$actual_sha" == "$EXPECTED_APK_SHA256" ]] || fail 21 "Hash APK incorrecto; no se instalará."
}

verify_device_owner() {
    collect_state
    local current_owner
    current_owner="$(owner_state)"
    [[ "$current_owner" == "glosh" ]] || fail 31 "Glosh no figura como Device Owner."
    [[ "$GLOSH_ADMIN_ACTIVE" == "yes" ]] || fail 31 "Glosh no figura como Device Admin activo."
    local accessibility_enabled accessibility_bound
    accessibility_enabled="$(adb_shell settings get secure enabled_accessibility_services | tr -d '\r')"
    accessibility_bound="$(adb_shell dumpsys accessibility | grep -c "$EXPECTED_PACKAGE" || true)"
    printf 'Device Owner confirmado: %s\n' "$EXPECTED_COMPONENT"
    printf 'Device Admin: %s\n' "$GLOSH_ADMIN_ACTIVE"
    printf 'Accessibility configurado para Glosh: %s\n' "$(printf '%s' "$accessibility_enabled" | grep -q "$EXPECTED_PACKAGE" && printf yes || printf no)"
    printf 'Accessibility referencias bound/configuradas: %s\n' "$accessibility_bound"
    printf 'Cuentas actuales: %s\n' "$ACCOUNT_COUNT"
    printf 'Apps actuales: %s total; %s de terceros\n' "$TOTAL_PACKAGES" "$THIRD_PARTY_PACKAGES"
    printf 'dpm list-owners:\n%s\n' "$OWNERS_OUTPUT"
}

run_install() {
    resolve_adb
    select_single_device
    collect_state
    print_summary
    write_checkpoint
    check_static_preconditions

    while [[ "$ACCOUNT_COUNT" != "0" ]]; do
        printf '\nHay %s cuentas. Revisá el checkpoint antes de retirarlas manualmente.\n' "$ACCOUNT_COUNT"
        if [[ "$NON_INTERACTIVE" == "1" ]]; then
            fail 10 "Preflight bloqueado por cuentas; no se modificó el teléfono."
        fi
        local answer
        printf '¿Abrir Ajustes > Cuentas ahora? [s/N]: '
        read -r answer
        if [[ "$answer" == "s" || "$answer" == "S" ]]; then
            open_accounts_settings
        fi
        printf 'Retirá manualmente las cuentas. Presioná Enter para reverificar (Ctrl-C para salir): '
        read -r _
        collect_state
        print_summary
        check_static_preconditions
    done

    verify_apk
    local current_owner
    current_owner="$(owner_state)"
    if [[ "$current_owner" == "glosh" ]]; then
        printf 'Glosh ya es Device Owner; no se repetirá set-device-owner.\n'
        verify_device_owner
        return
    fi

    printf '\nLa siguiente fase actualizará Glosh in-place a DEV 319 y ejecutará set-device-owner UNA vez.\n'
    printf 'No se borrarán datos de Glosh, cuentas, usuarios ni otras apps.\n'
    printf 'Para continuar escribí exactamente: DEVICE OWNER %s\n> ' "$DEVICE_SERIAL"
    local confirmation
    read -r confirmation
    [[ "$confirmation" == "DEVICE OWNER $DEVICE_SERIAL" ]] || fail 22 "Confirmación cancelada; no se instaló nada."

    umask 077
    mkdir -p "$CHECKPOINT_DIR"
    local safe_serial attempt_receipt result_file stamp pre_total pre_third pre_inode install_output install_status
    safe_serial="$(printf '%s' "$DEVICE_SERIAL" | tr -cd 'A-Za-z0-9._-')"
    attempt_receipt="$CHECKPOINT_DIR/${safe_serial}-${EXPECTED_APK_SHA256}.set-device-owner-attempted"
    [[ ! -e "$attempt_receipt" ]] || fail 23 "Ya existe un recibo de intento: $attempt_receipt. No se repetirá automáticamente."
    stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    result_file="$CHECKPOINT_DIR/${stamp}-${safe_serial}-result.txt"
    pre_total="$TOTAL_PACKAGES"
    pre_third="$THIRD_PARTY_PACKAGES"
    pre_inode="${GLOSH_DATA_INODE:-no_disponible}"

    set +e
    install_output="$(adb_device install -r "$APK_PATH" 2>&1)"
    install_status=$?
    set -e
    printf 'adb install -r respuesta exacta:\n%s\n' "$install_output"
    {
        printf 'APK SHA-256: %s\n' "$EXPECTED_APK_SHA256"
        printf 'adb install -r exit: %s\n' "$install_status"
        printf 'adb install -r respuesta exacta:\n%s\n' "$install_output"
    } >"$result_file"
    [[ "$install_status" == "0" ]] || fail 24 "Falló adb install -r. No se intentó Device Owner. Evidencia: $result_file"

    collect_state
    [[ "$GLOSH_VERSION" == "$EXPECTED_VERSION_CODE" ]] || fail 25 "La versión instalada no es DEV 319. No se intentó Device Owner."
    printf '%s\n' "$GLOSH_PACKAGE_OUTPUT" | grep -Fq 'ProtectionDeviceAdminReceiver' ||
        fail 25 "El receiver Device Admin esperado no está registrado."
    check_static_preconditions
    [[ "$ACCOUNT_COUNT" == "0" ]] || fail 25 "Aparecieron cuentas antes de Device Owner; STOP."
    [[ "$(owner_state)" == "none" ]] || fail 25 "Cambió el estado de owners antes del intento; STOP."

    printf 'timestamp=%s serial=%s apk_sha256=%s state=started\n' "$stamp" "$safe_serial" "$EXPECTED_APK_SHA256" >"$attempt_receipt"
    local dpm_output dpm_status owners_after
    set +e
    dpm_output="$(adb_shell dpm set-device-owner --user 0 "$EXPECTED_COMPONENT" 2>&1)"
    dpm_status=$?
    set -e
    owners_after="$(adb_shell dpm list-owners | tr -d '\r')"
    printf 'state=finished exit=%s\n' "$dpm_status" >>"$attempt_receipt"
    {
        printf 'set-device-owner exit: %s\n' "$dpm_status"
        printf 'set-device-owner respuesta exacta:\n%s\n' "$dpm_output"
        printf 'dpm list-owners inmediato:\n%s\n' "$owners_after"
    } >>"$result_file"
    printf 'set-device-owner respuesta exacta:\n%s\n' "$dpm_output"
    printf 'dpm list-owners inmediato:\n%s\n' "$owners_after"
    [[ "$dpm_status" == "0" ]] || fail 30 "Android rechazó Device Owner. No se reintentará. Evidencia: $result_file"

    OWNERS_OUTPUT="$owners_after"
    [[ "$(owner_state)" == "glosh" ]] || fail 31 "Android no confirmó Glosh como Device Owner. No se reintentará."
    verify_device_owner
    printf 'Apps antes/después: total %s/%s; terceros %s/%s\n' "$pre_total" "$TOTAL_PACKAGES" "$pre_third" "$THIRD_PARTY_PACKAGES"
    printf 'Glosh ceDataInode antes/después: %s/%s\n' "$pre_inode" "${GLOSH_DATA_INODE:-no_disponible}"
    printf 'PASS. Evidencia: %s\n' "$result_file"
    printf '\nYa podés volver a agregar manualmente tus cuentas desde Ajustes > Cuentas.\n'
    printf 'Después ejecutá este script con `verify` para confirmar que Device Owner permanece activo.\n'
}

parse_args() {
    if [[ $# -gt 0 && "$1" != --* ]]; then
        COMMAND="$1"
        shift
    fi
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --apk)
                [[ $# -ge 2 ]] || fail 1 "Falta valor para --apk"
                APK_PATH="$2"
                shift 2
                ;;
            --checkpoint-dir)
                [[ $# -ge 2 ]] || fail 1 "Falta valor para --checkpoint-dir"
                CHECKPOINT_DIR="$2"
                shift 2
                ;;
            --non-interactive)
                NON_INTERACTIVE=1
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                fail 1 "Argumento desconocido: $1"
                ;;
        esac
    done
}

main() {
    parse_args "$@"
    case "$COMMAND" in
        preflight)
            preflight
            ;;
        run)
            run_install
            ;;
        verify)
            resolve_adb
            select_single_device
            verify_device_owner
            ;;
        *)
            usage
            fail 1 "Comando desconocido: $COMMAND"
            ;;
    esac
}

main "$@"
