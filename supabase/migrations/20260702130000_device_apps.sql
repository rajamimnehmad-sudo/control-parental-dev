create table if not exists public.device_apps (
    id uuid primary key,
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    app_name text not null,
    package_name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (device_id, package_name)
);

create index if not exists idx_device_apps_account_device_updated
on public.device_apps(account_id, device_id, updated_at);

drop trigger if exists trg_device_apps_updated_at on public.device_apps;
create trigger trg_device_apps_updated_at before update on public.device_apps
for each row execute function public.set_updated_at();

alter table public.device_apps enable row level security;

drop policy if exists "device_apps_account_owner_all" on public.device_apps;
create policy "device_apps_account_owner_all" on public.device_apps
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = device_apps.account_id
        and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = device_apps.account_id
        and accounts.owner_user_id = auth.uid()
    )
);

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'device_apps'
    ) then
        alter publication supabase_realtime add table public.device_apps;
    end if;
end $$;
