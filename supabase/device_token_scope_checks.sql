do $$
declare
    policy_record record;
    role_oids oid[];
begin
    select array_agg(oid order by rolname)
    into role_oids
    from pg_roles
    where rolname in ('anon', 'authenticated');

    if coalesce(cardinality(role_oids), 0) <> 2 then
        raise exception 'Expected anon and authenticated roles';
    end if;

    for policy_record in
        select
            policy.tablename,
            policy.policyname,
            policy.cmd,
            policy.roles,
            policy.qual,
            policy.with_check
        from pg_policies policy
        where policy.schemaname = 'public'
          and (
              (policy.tablename = 'access_requests'
               and policy.policyname = 'access_requests_device_token_all')
              or
              (policy.tablename = 'device_apps'
               and policy.policyname = 'device_apps_device_token_all')
          )
    loop
        if policy_record.cmd <> 'ALL' then
            raise exception 'Policy %.% must cover ALL commands',
                policy_record.tablename,
                policy_record.policyname;
        end if;

        if policy_record.roles <> array['anon', 'authenticated']::name[]
           and policy_record.roles <> array['authenticated', 'anon']::name[] then
            raise exception 'Policy %.% must target only anon and authenticated',
                policy_record.tablename,
                policy_record.policyname;
        end if;

        if policy_record.qual not like '%device_token_matches_device%'
           or policy_record.qual not like '%device_id IS NOT NULL%'
           or policy_record.qual not like '%account_id%'
           or policy_record.with_check not like '%device_token_matches_device%'
           or policy_record.with_check not like '%device_id IS NOT NULL%'
           or policy_record.with_check not like '%account_id%' then
            raise exception 'Policy %.% lacks device scope or account coherence',
                policy_record.tablename,
                policy_record.policyname;
        end if;
    end loop;

    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'access_requests'
          and policyname = 'access_requests_device_token_all'
    ) or not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'device_apps'
          and policyname = 'device_apps_device_token_all'
    ) then
        raise exception 'Expected device-token write policies were not created';
    end if;

    if exists (
        select 1
        from pg_policies policy
        where policy.schemaname = 'public'
          and policy.tablename in ('access_requests', 'device_apps')
          and policy.cmd in ('ALL', 'INSERT', 'UPDATE', 'DELETE')
          and (
              coalesce(policy.qual, '') like '%device_token_matches(account_id)%'
              or coalesce(policy.with_check, '') like '%device_token_matches(account_id)%'
          )
    ) then
        raise exception 'Account-scoped device-token write policy still exists';
    end if;
end;
$$;
