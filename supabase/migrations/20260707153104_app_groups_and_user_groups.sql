create table if not exists public.app_groups (
    id uuid primary key,
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    name text not null,
    color text not null default 'teal',
    limit_minutes integer not null check (limit_minutes > 0),
    reset_minute_of_day integer not null default 720 check (reset_minute_of_day between 0 and 1439),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists idx_app_groups_account_device_updated
on public.app_groups(account_id, device_id, updated_at);

drop trigger if exists trg_app_groups_updated_at on public.app_groups;
create trigger trg_app_groups_updated_at before update on public.app_groups
for each row execute function public.set_updated_at();

create table if not exists public.app_group_apps (
    id uuid primary key,
    account_id uuid not null references public.accounts(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    group_id uuid not null references public.app_groups(id) on delete cascade,
    package_name text not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (group_id, package_name)
);

create index if not exists idx_app_group_apps_account_device_updated
on public.app_group_apps(account_id, device_id, updated_at);

create index if not exists idx_app_group_apps_group_package
on public.app_group_apps(group_id, package_name);

drop trigger if exists trg_app_group_apps_updated_at on public.app_group_apps;
create trigger trg_app_group_apps_updated_at before update on public.app_group_apps
for each row execute function public.set_updated_at();

-- Prepared for the next UI ticket: applying the same app group to a named set of protected users.
create table if not exists public.protected_user_groups (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists idx_protected_user_groups_account_updated
on public.protected_user_groups(account_id, updated_at);

drop trigger if exists trg_protected_user_groups_updated_at on public.protected_user_groups;
create trigger trg_protected_user_groups_updated_at before update on public.protected_user_groups
for each row execute function public.set_updated_at();

create table if not exists public.protected_user_group_members (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references public.accounts(id) on delete cascade,
    protected_user_group_id uuid not null references public.protected_user_groups(id) on delete cascade,
    device_id uuid not null references public.devices(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (protected_user_group_id, device_id)
);

create index if not exists idx_protected_user_group_members_account_updated
on public.protected_user_group_members(account_id, updated_at);

drop trigger if exists trg_protected_user_group_members_updated_at on public.protected_user_group_members;
create trigger trg_protected_user_group_members_updated_at before update on public.protected_user_group_members
for each row execute function public.set_updated_at();

alter table public.app_groups enable row level security;
alter table public.app_group_apps enable row level security;
alter table public.protected_user_groups enable row level security;
alter table public.protected_user_group_members enable row level security;

drop policy if exists "app_groups_account_owner_all" on public.app_groups;
create policy "app_groups_account_owner_all" on public.app_groups
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = app_groups.account_id
          and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = app_groups.account_id
          and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "app_groups_device_token_select" on public.app_groups;
create policy "app_groups_device_token_select" on public.app_groups
for select
using (public.device_token_matches(account_id));

drop policy if exists "app_groups_admin_device_token_all" on public.app_groups;
create policy "app_groups_admin_device_token_all" on public.app_groups
for all
using (public.admin_device_token_matches(account_id))
with check (public.admin_device_token_matches(account_id));

drop policy if exists "app_group_apps_account_owner_all" on public.app_group_apps;
create policy "app_group_apps_account_owner_all" on public.app_group_apps
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = app_group_apps.account_id
          and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = app_group_apps.account_id
          and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "app_group_apps_device_token_select" on public.app_group_apps;
create policy "app_group_apps_device_token_select" on public.app_group_apps
for select
using (public.device_token_matches(account_id));

drop policy if exists "app_group_apps_admin_device_token_all" on public.app_group_apps;
create policy "app_group_apps_admin_device_token_all" on public.app_group_apps
for all
using (public.admin_device_token_matches(account_id))
with check (public.admin_device_token_matches(account_id));

drop policy if exists "protected_user_groups_account_owner_all" on public.protected_user_groups;
create policy "protected_user_groups_account_owner_all" on public.protected_user_groups
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = protected_user_groups.account_id
          and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = protected_user_groups.account_id
          and accounts.owner_user_id = auth.uid()
    )
);

drop policy if exists "protected_user_group_members_account_owner_all" on public.protected_user_group_members;
create policy "protected_user_group_members_account_owner_all" on public.protected_user_group_members
for all
using (
    exists (
        select 1 from public.accounts
        where accounts.id = protected_user_group_members.account_id
          and accounts.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.accounts
        where accounts.id = protected_user_group_members.account_id
          and accounts.owner_user_id = auth.uid()
    )
);

grant select on public.app_groups to anon;
grant select on public.app_group_apps to anon;
grant select, insert, update on public.app_groups to anon;
grant select, insert, update on public.app_group_apps to anon;
grant select, insert, update on public.protected_user_groups to authenticated;
grant select, insert, update on public.protected_user_group_members to authenticated;

do $$
declare
    realtime_table text;
begin
    foreach realtime_table in array array[
        'app_groups',
        'app_group_apps',
        'protected_user_groups',
        'protected_user_group_members'
    ]
    loop
        if not exists (
            select 1
            from pg_publication_tables
            where pubname = 'supabase_realtime'
              and schemaname = 'public'
              and tablename = realtime_table
        ) then
            execute format('alter publication supabase_realtime add table public.%I', realtime_table);
        end if;
    end loop;
end $$;
