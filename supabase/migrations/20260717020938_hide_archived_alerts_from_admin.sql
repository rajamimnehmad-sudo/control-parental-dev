drop policy if exists "protection_alert_events_admin_device_token_select"
on public.protection_alert_events;

create policy "protection_alert_events_admin_device_token_select"
on public.protection_alert_events
for select
to anon
using (
    public.admin_device_token_matches(account_id)
    and alert_type <> 'tamper_attempt'
    and archived_at is null
);
