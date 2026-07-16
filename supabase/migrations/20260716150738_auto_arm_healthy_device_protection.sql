create or replace function public.auto_arm_device_protection(p_device_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'invalid device authorization';
    end if;

    update public.device_protection_controls
    set armed = true,
        command_revision = 1,
        authorization_scope = 'none',
        authorization_expires_at = null,
        updated_at = now()
    where device_id = p_device_id
      and armed = false
      and command_revision = 0;
end;
$$;

revoke all on function public.auto_arm_device_protection(uuid) from public;
grant execute on function public.auto_arm_device_protection(uuid) to anon, authenticated;
