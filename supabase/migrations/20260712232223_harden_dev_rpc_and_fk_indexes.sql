-- Administrative RPCs require an authenticated session. Revoking PUBLIC is
-- necessary because role-specific revocations do not override PUBLIC grants.
revoke execute on function public.revoke_device(uuid) from public, anon;
grant execute on function public.revoke_device(uuid) to authenticated;

revoke execute on function public.create_admin_pairing_code(text, text, integer) from public, anon;
grant execute on function public.create_admin_pairing_code(text, text, integer) to authenticated;

-- Cover foreign-key columns used by cascades, joins and device-scoped pulls.
create index if not exists access_requests_device_id_idx
    on public.access_requests(device_id);

create index if not exists activation_codes_community_admin_id_idx
    on public.activation_codes(community_admin_id);

create index if not exists activation_codes_consumed_device_id_idx
    on public.activation_codes(consumed_device_id);

create index if not exists app_group_apps_device_id_idx
    on public.app_group_apps(device_id);

create index if not exists app_groups_device_id_idx
    on public.app_groups(device_id);

create index if not exists daily_limits_account_id_idx
    on public.daily_limits(account_id);

create index if not exists device_activations_activated_by_user_id_idx
    on public.device_activations(activated_by_user_id);

create index if not exists device_activations_activation_code_id_idx
    on public.device_activations(activation_code_id);

create index if not exists devices_applied_policy_id_idx
    on public.devices(applied_policy_id);

create index if not exists devices_community_admin_id_idx
    on public.devices(community_admin_id);

create index if not exists extra_time_grants_request_id_idx
    on public.extra_time_grants(request_id);

create index if not exists policies_device_id_idx
    on public.policies(device_id);

create index if not exists policy_rules_account_id_idx
    on public.policy_rules(account_id);

create index if not exists protected_user_group_members_device_id_idx
    on public.protected_user_group_members(device_id);

create index if not exists protection_alert_events_device_id_idx
    on public.protection_alert_events(device_id);
