alter table public.device_apps
add column if not exists version_name text,
add column if not exists is_system_app boolean not null default false;
