create or replace function public.dag_search_authorized(p_device_id uuid)
returns boolean
language plpgsql
security definer
stable
set search_path = public, extensions
as $$
begin
    if not public.device_token_matches_device(p_device_id) then
        return false;
    end if;

    return exists (
        select 1
        from public.devices d
        join public.policies p
          on p.device_id = d.id
         and p.account_id = d.account_id
         and p.active = true
         and p.deleted_at is null
        join public.policy_rules r
          on r.policy_id = p.id
         and r.account_id = p.account_id
         and r.scope = 'Domain'
         and r.target = '__dag_enabled__'
         and r.action = 'Allow'
         and r.enabled = true
         and r.deleted_at is null
        where d.id = p_device_id
          and d.app_role = 'user'
          and d.deleted_at is null
    );
end;
$$;

revoke all on function public.dag_search_authorized(uuid) from public;
grant execute on function public.dag_search_authorized(uuid) to anon, authenticated;

create table if not exists public.dag_search_monthly_usage (
    device_id uuid not null references public.devices(id) on delete cascade,
    month_start date not null,
    request_count integer not null default 0 check (request_count between 0 and 200),
    updated_at timestamptz not null default now(),
    primary key (device_id, month_start),
    check (month_start = date_trunc('month', month_start)::date)
);

alter table public.dag_search_monthly_usage enable row level security;
revoke all on table public.dag_search_monthly_usage from public, anon, authenticated;

create or replace function public.consume_dag_search_quota(p_device_id uuid)
returns boolean
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
        return false;
    end if;

    insert into public.dag_search_monthly_usage (device_id, month_start, request_count, updated_at)
    values (p_device_id, current_month, 1, now())
    on conflict (device_id, month_start) do update
    set request_count = public.dag_search_monthly_usage.request_count + 1,
        updated_at = excluded.updated_at
    where public.dag_search_monthly_usage.request_count < 200
    returning true into consumed;

    return coalesce(consumed, false);
end;
$$;

revoke all on function public.consume_dag_search_quota(uuid) from public;
grant execute on function public.consume_dag_search_quota(uuid) to anon, authenticated;
