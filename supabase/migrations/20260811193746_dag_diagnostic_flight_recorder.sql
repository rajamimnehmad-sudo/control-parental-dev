create table public.dag_diagnostic_reports (
    id uuid primary key default gen_random_uuid(),
    report_code text not null unique
        check (report_code ~ '^DAG-[A-Z0-9]{8}$'),
    report_id uuid not null unique,
    session_id uuid not null,
    received_at timestamptz not null default now(),
    created_at timestamptz not null,
    expires_at timestamptz not null default (now() + interval '14 days'),
    app_package text not null
        check (app_package ~ '^com\.contentfilter\.dagbrowser(\.dev|\.diagnostic\.dev)?$'),
    app_version_code bigint not null check (app_version_code > 0),
    app_version_name text not null check (length(app_version_name) between 1 and 80),
    android_sdk integer not null check (android_sdk between 29 and 100),
    device_manufacturer text not null check (length(device_manufacturer) between 1 and 80),
    device_model text not null check (length(device_model) between 1 and 80),
    event_count integer not null check (event_count between 0 and 4096),
    dropped_in_memory bigint not null default 0 check (dropped_in_memory between 0 and 1000000),
    payload jsonb not null check (jsonb_typeof(payload) = 'object')
);

alter table public.dag_diagnostic_reports enable row level security;

revoke all on table public.dag_diagnostic_reports from public, anon, authenticated;

create index dag_diagnostic_reports_received_at_idx
on public.dag_diagnostic_reports (received_at desc);

create index dag_diagnostic_reports_expires_at_idx
on public.dag_diagnostic_reports (expires_at);

create or replace function public.purge_expired_dag_diagnostic_reports()
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    deleted_count bigint;
begin
    delete from public.dag_diagnostic_reports
    where expires_at <= now();

    get diagnostics deleted_count = row_count;
    return deleted_count;
end;
$$;

revoke all on function public.purge_expired_dag_diagnostic_reports()
from public, anon, authenticated;

create extension if not exists pg_cron with schema pg_catalog;

do $$
declare
    existing_job bigint;
begin
    for existing_job in
        select jobid from cron.job where jobname = 'dag-diagnostic-retention-daily'
    loop
        perform cron.unschedule(existing_job);
    end loop;

    perform cron.schedule(
        'dag-diagnostic-retention-daily',
        '23 3 * * *',
        'select public.purge_expired_dag_diagnostic_reports();'
    );
end;
$$;
