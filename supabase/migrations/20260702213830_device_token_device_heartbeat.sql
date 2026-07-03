create or replace function public.device_token_matches_device(target_device_id uuid)
returns boolean
language plpgsql
security definer
stable
set search_path = public, extensions
as $$
declare
    token text;
begin
    token := public.request_device_token();
    if token is null then
        return false;
    end if;

    return exists (
        select 1
        from public.devices
        where id = target_device_id
          and deleted_at is null
          and device_token_hash is not null
          and device_token_hash = crypt(token, device_token_hash)
    );
end;
$$;

revoke all on function public.device_token_matches_device(uuid) from public;
grant execute on function public.device_token_matches_device(uuid) to anon, authenticated;

drop policy if exists "devices_device_token_update_seen" on public.devices;
create policy "devices_device_token_update_seen" on public.devices
for update
to anon
using (
    public.device_token_matches_device(id)
)
with check (
    public.device_token_matches_device(id)
);

grant update (last_seen_at, updated_at) on public.devices to anon;
