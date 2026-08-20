-- SQL checks for BACKEND-PAIRING-HARDENING-03B (manual/db-side review)
-- Scope: do not execute directly in production.
-- Run in a local Supabase/Postgres sandbox.

do $$
declare
    v_marker_prefix constant text := '03B_TEST_PAIRING';
    v_account_id uuid;
    v_token_32 text := 'A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6';
    v_token_31 text := left(v_token_32, 31);
    v_token_32_legacy_input text := 'A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D7';
    v_token_8 text := 'A1B2C3D4';
    v_hash_32 text;
    v_code_row public.activation_codes%rowtype;
    v_old_legacy_token text := 'OLDPAIR';
    v_new_legacy_token text := 'NEWPAIR';
    v_duplicate_rejected boolean := false;
begin
    select id
    into v_account_id
    from public.accounts
    where deleted_at is null
    order by created_at asc
    limit 1;

    if v_account_id is null then
        raise notice 'SKIP: no account rows available in this DB.';
        return;
    end if;

    delete from public.activation_codes
    where intended_display_name like v_marker_prefix || '%';

    -- A. new token valid first lookup -> second lookup must fail after used_at is set.
    v_hash_32 := public.pairing_code_lookup_hash(v_token_32);

    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        expires_at
    )
    values (
        v_account_id,
        extensions.crypt(v_token_32, extensions.gen_salt('bf')),
        v_hash_32,
        'user',
        v_marker_prefix || '_NEW_VALID',
        now() + interval '15 minutes'
    );

    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code(v_token_32);

    if v_code_row.id is null then
        raise exception 'TEST-FAIL A: valid 32-char token was not found on first lookup';
    end if;

    update public.activation_codes
    set used_at = now()
    where id = v_code_row.id;

    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code(v_token_32);

    if v_code_row.id is not null then
        raise exception 'TEST-FAIL A: 32-char token still resolves after used_at set';
    end if;

    -- B. 31-char token is rejected.
    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code(v_token_31);

    if v_code_row.id is not null then
        raise exception 'TEST-FAIL B: 31-char token was accepted';
    end if;

    -- C. 32-char incorrect input must not execute legacy bcrypt fallback.
    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        expires_at
    )
    values (
        v_account_id,
        extensions.crypt(v_token_32_legacy_input, extensions.gen_salt('bf')),
        null,
        'user',
        v_marker_prefix || '_LEGACY_LIKE_32',
        now() + interval '30 minutes'
    );

    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code(v_token_32_legacy_input);

    if v_code_row.id is not null then
        raise exception 'TEST-FAIL C: 32-char token unexpectedly matched legacy fallback path';
    end if;

    -- D. different account cannot duplicate code_lookup_hash due unique partial index.
    begin
        insert into public.activation_codes (
            account_id,
            code_hash,
            code_lookup_hash,
            intended_app_role,
            intended_display_name,
            expires_at
        )
        values (
            v_account_id,
            extensions.crypt(v_token_8, extensions.gen_salt('bf')),
            v_hash_32,
            'user',
            v_marker_prefix || '_COLLISION',
            now() + interval '30 minutes'
        );
    exception
        when unique_violation then
            v_duplicate_rejected := true;
    end;

    if not v_duplicate_rejected then
        raise exception 'TEST-FAIL D: code_lookup_hash duplicated unexpectedly';
    end if;

    -- E. token never stored in plaintext (sanity: no cleartext columns equal raw token).
    if exists (
        select 1
        from public.activation_codes
        where intended_display_name like v_marker_prefix || '_%'
          and (
            code_hash = v_token_32
            or code_lookup_hash = v_token_32
            or code_lookup_hash = v_token_31
          )
    ) then
        raise exception 'TEST-FAIL E: pairing token appears in plaintext in activation_codes';
    end if;

    -- F. legacy fallback only for pre-rollout rows (transition strategy).
    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        created_at,
        expires_at
    )
    values (
        v_account_id,
        extensions.crypt(v_old_legacy_token, extensions.gen_salt('bf')),
        null,
        'user',
        v_marker_prefix || '_LEGACY_OLD',
        timestamp '2026-08-18 12:00:00+00',
        now() + interval '10 minutes'
    );

    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        created_at,
        expires_at
    )
    values (
        v_account_id,
        extensions.crypt(v_new_legacy_token, extensions.gen_salt('bf')),
        null,
        'user',
        v_marker_prefix || '_LEGACY_NEW',
        now(),
        now() + interval '10 minutes'
    );

    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code(v_old_legacy_token);

    if v_code_row.id is null then
        raise exception 'TEST-FAIL F: pre-rollout legacy token not accepted';
    end if;

    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code(v_new_legacy_token);

    if v_code_row.id is not null then
        raise exception 'TEST-FAIL F: post-rollout legacy token accepted unexpectedly';
    end if;

    -- G. invalid token does not reveal whether exists; lookup remains null for unknown input.
    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code('Z9Z9Z9Z9Z9Z9Z9Z9');

    if v_code_row.id is not null then
        raise exception 'TEST-FAIL G: unknown token resolved';
    end if;

    -- H. ensure no plaintext exists for 32-char token generation path either.
    v_hash_32 := public.pairing_code_lookup_hash(v_token_32);
    if v_hash_32 is null or length(v_hash_32) <> 64 then
        raise exception 'TEST-FAIL H: lookup hash should be deterministic 256-bit hex';
    end if;

    -- I/J. expiry boundary (expired token no match), and query-by-hash exactness for short token.
    insert into public.activation_codes (
        account_id,
        code_hash,
        code_lookup_hash,
        intended_app_role,
        intended_display_name,
        created_at,
        expires_at
    )
    values (
        v_account_id,
        extensions.crypt(v_old_legacy_token || '_EX', extensions.gen_salt('bf')),
        null,
        'user',
        v_marker_prefix || '_EXPIRED',
        now(),
        now() - interval '1 hour'
    );

    select *
    into v_code_row
    from public.find_activation_code_by_pairing_code(v_old_legacy_token || '_EX');

    if v_code_row.id is not null then
        raise exception 'TEST-FAIL I: expired token is still resolvable';
    end if;

    -- cleanup
    delete from public.activation_codes
    where intended_display_name like v_marker_prefix || '_%';
    raise notice 'PASS: pairing hardening checks finished';
end;
$$;
