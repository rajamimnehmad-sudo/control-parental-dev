#!/usr/bin/env bash
set -euo pipefail

user_changed=false
admin_changed=false
shared_changed=false

while IFS= read -r path; do
    [[ -z "$path" ]] && continue
    case "$path" in
        app-user/*)
            user_changed=true
            ;;
        app-admin/*)
            admin_changed=true
            ;;
        .github/*|docs/*|release-notes/*|scripts/*|*.md)
            ;;
        *)
            shared_changed=true
            ;;
    esac
done

if [[ "$shared_changed" == "true" ]] || { [[ "$user_changed" == "true" ]] && [[ "$admin_changed" == "true" ]]; }; then
    printf 'both\n'
elif [[ "$user_changed" == "true" ]]; then
    printf 'user\n'
elif [[ "$admin_changed" == "true" ]]; then
    printf 'admin\n'
else
    printf 'none\n'
fi
