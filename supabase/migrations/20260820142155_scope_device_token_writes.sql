drop policy if exists "access_requests_device_token_all" on public.access_requests;
create policy "access_requests_device_token_all" on public.access_requests
for all
to anon, authenticated
using (
    access_requests.device_id is not null
    and public.device_token_matches_device(access_requests.device_id)
    and exists (
        select 1
        from public.devices device
        where device.id = access_requests.device_id
          and device.account_id = access_requests.account_id
          and device.deleted_at is null
    )
)
with check (
    access_requests.device_id is not null
    and public.device_token_matches_device(access_requests.device_id)
    and exists (
        select 1
        from public.devices device
        where device.id = access_requests.device_id
          and device.account_id = access_requests.account_id
          and device.deleted_at is null
    )
);

drop policy if exists "device_apps_device_token_all" on public.device_apps;
create policy "device_apps_device_token_all" on public.device_apps
for all
to anon, authenticated
using (
    device_apps.device_id is not null
    and public.device_token_matches_device(device_apps.device_id)
    and exists (
        select 1
        from public.devices device
        where device.id = device_apps.device_id
          and device.account_id = device_apps.account_id
          and device.deleted_at is null
    )
)
with check (
    device_apps.device_id is not null
    and public.device_token_matches_device(device_apps.device_id)
    and exists (
        select 1
        from public.devices device
        where device.id = device_apps.device_id
          and device.account_id = device_apps.account_id
          and device.deleted_at is null
    )
);

grant select, insert, update, delete on public.access_requests to anon, authenticated;
grant select, insert, update, delete on public.device_apps to anon, authenticated;
