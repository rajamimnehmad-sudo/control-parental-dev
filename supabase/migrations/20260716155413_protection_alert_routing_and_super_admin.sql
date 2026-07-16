drop policy if exists "protection_alert_events_admin_device_token_select"
on public.protection_alert_events;

create policy "protection_alert_events_admin_device_token_select"
on public.protection_alert_events
for select
to anon
using (
    public.admin_device_token_matches(account_id)
    and alert_type <> 'tamper_attempt'
);

create or replace function public.super_admin_list_protection_alerts(max_rows integer default 100)
returns table (
    event_id uuid,
    community_id uuid,
    community_name text,
    device_id uuid,
    device_name text,
    alert_type text,
    title text,
    body text,
    created_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();

    return query
    select
        events.id,
        accounts.community_id,
        communities.name,
        events.device_id,
        devices.display_name,
        events.alert_type,
        events.title,
        events.body,
        events.created_at
    from public.protection_alert_events events
    join public.accounts accounts
      on accounts.id = events.account_id
     and accounts.deleted_at is null
    join public.communities communities
      on communities.id = accounts.community_id
     and communities.deleted_at is null
    join public.devices devices
      on devices.id = events.device_id
     and devices.deleted_at is null
    order by events.created_at desc
    limit greatest(1, least(coalesce(max_rows, 100), 500));
end;
$$;

revoke all on function public.super_admin_list_protection_alerts(integer) from public, anon, authenticated;
grant execute on function public.super_admin_list_protection_alerts(integer) to authenticated;
