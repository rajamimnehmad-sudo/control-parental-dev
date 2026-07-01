alter table public.access_requests
add column if not exists request_type text not null default 'APP_ACCESS';

alter table public.access_requests
add column if not exists target_package_name text;

alter table public.access_requests
add column if not exists target_domain text;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'access_requests_request_type_check'
          and conrelid = 'public.access_requests'::regclass
    ) then
        alter table public.access_requests
        add constraint access_requests_request_type_check
        check (request_type in ('APP_ACCESS', 'DOMAIN_ACCESS', 'EXTRA_TIME', 'OTHER'));
    end if;
end $$;

update public.access_requests
set
    request_type = case
        when requested_minutes is not null then 'EXTRA_TIME'
        when target_type = 'Domain' then 'DOMAIN_ACCESS'
        when target_type = 'App' then 'APP_ACCESS'
        else 'OTHER'
    end,
    target_package_name = case
        when target_type = 'App' then target
        else target_package_name
    end,
    target_domain = case
        when target_type = 'Domain' then target
        else target_domain
    end
where deleted_at is null
  and (
      target_package_name is null
      or target_domain is null
      or request_type = 'APP_ACCESS'
  );
