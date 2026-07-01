-- Development-only activation code batch.
-- Safe to rerun: it does not duplicate existing codes and does not modify used codes.
-- Codes are intentionally simple because they are only for the linked Supabase DEV project.

create schema if not exists extensions;
create extension if not exists "pgcrypto" with schema extensions;

do $$
declare
    dev_account_id uuid;
    dev_code text;
    inserted_count integer := 0;
begin
    select id
    into dev_account_id
    from public.accounts
    where name = 'Dev E2E Account'
      and deleted_at is null
    order by created_at
    limit 1;

    if dev_account_id is null then
        raise exception 'Dev E2E Account was not found. Run supabase/dev_test_data.sql first.';
    end if;

    for dev_code in
        select generate_series(1, 100)::text
    loop
        if not exists (
            select 1
            from public.activation_codes
            where account_id = dev_account_id
              and deleted_at is null
              and code_hash = extensions.crypt(dev_code, code_hash)
        ) then
            insert into public.activation_codes (
                account_id,
                code_hash,
                expires_at,
                used_at,
                deleted_at
            )
            values (
                dev_account_id,
                extensions.crypt(dev_code, extensions.gen_salt('bf')),
                now() + interval '5 years',
                null,
                null
            );

            inserted_count := inserted_count + 1;
        end if;
    end loop;

    raise notice 'Dev account_id: %', dev_account_id;
    raise notice 'Inserted activation codes: %', inserted_count;
    raise notice 'App Usuario codes: 1-50';
    raise notice 'App Admin codes: 51-100';
end $$;
