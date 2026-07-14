create or replace function public.authorize_and_consume_dag_search(p_device_id uuid)
returns text
language plpgsql
security definer
volatile
set search_path = public, extensions
as $$
declare
    current_month date := date_trunc('month', timezone('UTC', now()))::date;
    consumed boolean := false;
begin
    if not public.dag_search_authorized(p_device_id) then
        return 'unauthorized';
    end if;

    insert into public.dag_search_monthly_usage (device_id, month_start, request_count, updated_at)
    values (p_device_id, current_month, 1, now())
    on conflict (device_id, month_start) do update
    set request_count = public.dag_search_monthly_usage.request_count + 1,
        updated_at = excluded.updated_at
    where public.dag_search_monthly_usage.request_count < 200
    returning true into consumed;

    return case when coalesce(consumed, false) then 'allowed' else 'quota' end;
end;
$$;

revoke all on function public.authorize_and_consume_dag_search(uuid) from public;
grant execute on function public.authorize_and_consume_dag_search(uuid) to anon, authenticated;

revoke execute on function public.dag_search_authorized(uuid) from anon, authenticated;
revoke execute on function public.consume_dag_search_quota(uuid) from anon, authenticated;
