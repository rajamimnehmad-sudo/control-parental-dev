create table if not exists public.support_reports (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    community_id uuid references public.communities(id) on delete set null,
    app_role text not null check (app_role in ('user', 'admin')),
    category text not null check (
        category in (
            'dag-images',
            'dag-navigation',
            'web-protection',
            'app-protection',
            'accessibility',
            'updates',
            'activation',
            'uninstall-protection',
            'sync',
            'unclassified'
        )
    ),
    safe_summary text not null check (char_length(safe_summary) between 1 and 240),
    app_version_code integer not null check (app_version_code > 0),
    manufacturer text,
    model text,
    android_version text,
    diagnostic_codes text[] not null default '{}',
    status text not null default 'open' check (status in ('open', 'reviewing', 'resolved')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint support_reports_device_fields check (
        (manufacturer is null or char_length(manufacturer) between 1 and 100)
        and (model is null or char_length(model) between 1 and 100)
        and (android_version is null or char_length(android_version) between 1 and 100)
    ),
    constraint support_reports_diagnostic_codes check (
        cardinality(diagnostic_codes) <= 12
        and array_position(diagnostic_codes, null) is null
    )
);

drop trigger if exists trg_support_reports_updated_at on public.support_reports;
create trigger trg_support_reports_updated_at
before update on public.support_reports
for each row execute function public.set_updated_at();

create index if not exists idx_support_reports_status_created
on public.support_reports(status, created_at desc);

create index if not exists idx_support_reports_community_created
on public.support_reports(community_id, created_at desc);

alter table public.support_reports enable row level security;
revoke all on table public.support_reports from public, anon, authenticated;

create or replace function public.submit_own_support_report(
    p_device_id uuid,
    p_category text,
    p_safe_summary text,
    p_app_version_code integer,
    p_manufacturer text,
    p_model text,
    p_android_version text,
    p_diagnostic_codes text[]
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    current_device record;
    normalized_summary text;
    normalized_codes text[];
    report_id uuid;
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'Device not authorized';
    end if;
    if p_category not in (
        'dag-images',
        'dag-navigation',
        'web-protection',
        'app-protection',
        'accessibility',
        'updates',
        'activation',
        'uninstall-protection',
        'sync',
        'unclassified'
    ) then
        raise exception 'Unsupported report category';
    end if;
    normalized_summary := nullif(trim(p_safe_summary), '');
    if normalized_summary is null or char_length(normalized_summary) > 240 then
        raise exception 'Invalid safe summary';
    end if;
    if p_app_version_code <= 0 then
        raise exception 'Invalid app version';
    end if;

    select
        device.account_id,
        device.app_role,
        account.community_id
    into current_device
    from public.devices device
    join public.accounts account on account.id = device.account_id
    where device.id = p_device_id
      and device.deleted_at is null;

    if current_device.account_id is null then
        raise exception 'Device not found';
    end if;

    select coalesce(array_agg(code order by code), '{}')
    into normalized_codes
    from (
        select distinct trim(raw_code) as code
        from unnest(coalesce(p_diagnostic_codes, '{}')) raw_code
        where trim(raw_code) ~ '^[a-z0-9-]{1,48}$'
        limit 12
    ) safe_codes;

    insert into public.support_reports (
        account_id,
        device_id,
        community_id,
        app_role,
        category,
        safe_summary,
        app_version_code,
        manufacturer,
        model,
        android_version,
        diagnostic_codes
    )
    values (
        current_device.account_id,
        p_device_id,
        current_device.community_id,
        current_device.app_role,
        p_category,
        normalized_summary,
        p_app_version_code,
        nullif(left(trim(p_manufacturer), 100), ''),
        nullif(left(trim(p_model), 100), ''),
        nullif(left(trim(p_android_version), 100), ''),
        normalized_codes
    )
    returning id into report_id;

    return report_id;
end;
$$;

create or replace function public.super_admin_list_support_reports(max_rows integer default 500)
returns table (
    report_id uuid,
    community_id uuid,
    community_name text,
    device_id uuid,
    device_name text,
    app_role text,
    category text,
    safe_summary text,
    app_version_code integer,
    manufacturer text,
    model text,
    android_version text,
    diagnostic_codes text[],
    status text,
    created_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_super_admin() then
        raise exception 'Not authorized';
    end if;
    return query
    select
        report.id,
        report.community_id,
        community.name,
        report.device_id,
        device.display_name,
        report.app_role,
        report.category,
        report.safe_summary,
        report.app_version_code,
        report.manufacturer,
        report.model,
        report.android_version,
        report.diagnostic_codes,
        report.status,
        report.created_at
    from public.support_reports report
    join public.devices device on device.id = report.device_id
    left join public.communities community on community.id = report.community_id
    where device.deleted_at is null
    order by report.created_at desc
    limit greatest(1, least(coalesce(max_rows, 500), 1000));
end;
$$;

revoke all on function public.submit_own_support_report(
    uuid,
    text,
    text,
    integer,
    text,
    text,
    text,
    text[]
) from public, anon, authenticated;
revoke all on function public.super_admin_list_support_reports(integer)
from public, anon, authenticated;

grant execute on function public.submit_own_support_report(
    uuid,
    text,
    text,
    integer,
    text,
    text,
    text,
    text[]
) to anon, authenticated;
grant execute on function public.super_admin_list_support_reports(integer) to authenticated;
