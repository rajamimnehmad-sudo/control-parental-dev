-- Development-only test data for the Supabase MVP.
-- Run after supabase/schema.sql and supabase/rls.sql.
--
-- Required manual value:
-- 1. Create a user in Supabase Auth.
-- 2. Copy its User UID.
-- 3. Replace DEV_AUTH_USER_ID_HERE below.
--
-- Never paste a Service Role Key in Android, Gradle or .env.

do $$
declare
    dev_owner_user_id_text text := 'DEV_AUTH_USER_ID_HERE';
    dev_owner_user_id uuid;
    dev_account_id uuid;
    dev_policy_id uuid;
    dev_code text;
begin
    if dev_owner_user_id_text = 'DEV_AUTH_USER_ID_HERE' then
        raise exception 'Replace DEV_AUTH_USER_ID_HERE with the Supabase Auth User UID before running this file.';
    end if;

    dev_owner_user_id := dev_owner_user_id_text::uuid;

    if not exists (
        select 1
        from auth.users
        where id = dev_owner_user_id
    ) then
        raise exception 'No auth.users row exists for %. Create the Auth user first, then rerun this SQL.', dev_owner_user_id;
    end if;

    select id
    into dev_account_id
    from public.accounts
    where owner_user_id = dev_owner_user_id
      and name = 'Dev E2E Account'
      and deleted_at is null
    order by created_at
    limit 1;

    if dev_account_id is null then
        insert into public.accounts (owner_user_id, name)
        values (dev_owner_user_id, 'Dev E2E Account')
        returning id into dev_account_id;
    end if;

    select id
    into dev_policy_id
    from public.policies
    where account_id = dev_account_id
      and active = true
      and deleted_at is null
    order by created_at
    limit 1;

    if dev_policy_id is null then
        insert into public.policies (account_id, version, active)
        values (dev_account_id, 1, true)
        returning id into dev_policy_id;
    end if;

    if not exists (
        select 1
        from public.policy_rules
        where account_id = dev_account_id
          and policy_id = dev_policy_id
          and scope = 'Global'
          and target = '*'
          and action = 'RequestAuthorization'
          and deleted_at is null
    ) then
        insert into public.policy_rules (
            account_id,
            policy_id,
            scope,
            target,
            action,
            priority,
            enabled
        )
        values (
            dev_account_id,
            dev_policy_id,
            'Global',
            '*',
            'RequestAuthorization',
            0,
            true
        );
    end if;

    if not exists (
        select 1
        from public.daily_limits
        where account_id = dev_account_id
          and policy_id = dev_policy_id
          and target_type = 'Global'
          and target = '*'
          and deleted_at is null
    ) then
        insert into public.daily_limits (
            account_id,
            policy_id,
            target_type,
            target,
            limit_minutes,
            enabled
        )
        values (
            dev_account_id,
            dev_policy_id,
            'Global',
            '*',
            120,
            true
        );
    end if;

    if not exists (
        select 1
        from public.activation_codes
        where account_id = dev_account_id
          and used_at is null
          and deleted_at is null
          and expires_at > now()
          and code_hash = crypt('TEST-USER-CODE', code_hash)
    ) then
        insert into public.activation_codes (account_id, code_hash, expires_at)
        values (
            dev_account_id,
            crypt('TEST-USER-CODE', gen_salt('bf')),
            now() + interval '14 days'
        );
    end if;

    if not exists (
        select 1
        from public.activation_codes
        where account_id = dev_account_id
          and used_at is null
          and deleted_at is null
          and expires_at > now()
          and code_hash = crypt('TEST-ADMIN-CODE', code_hash)
    ) then
        insert into public.activation_codes (account_id, code_hash, expires_at)
        values (
            dev_account_id,
            crypt('TEST-ADMIN-CODE', gen_salt('bf')),
            now() + interval '14 days'
        );
    end if;

    raise notice 'Dev E2E account_id: %', dev_account_id;
    raise notice 'Activation code for App Usuario: TEST-USER-CODE';
    raise notice 'Activation code for App Admin: TEST-ADMIN-CODE';
    raise notice 'Legacy numeric activation codes 1-100 are no longer generated.';
end $$;
