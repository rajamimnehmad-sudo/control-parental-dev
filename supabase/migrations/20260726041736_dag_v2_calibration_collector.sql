create table public.dag_v2_calibration_samples (
    sample_id uuid primary key default gen_random_uuid(),
    content_sha256 text not null unique check (content_sha256 ~ '^[0-9a-f]{64}$'),
    perceptual_hash text not null check (perceptual_hash ~ '^[0-9a-f]{16}$'),
    storage_path text not null unique check (storage_path ~ '^samples/[0-9a-f]{2}/[0-9a-f]{64}\.jpg$'),
    width integer not null check (width between 1 and 768),
    height integer not null check (height between 1 and 768),
    mime_type text not null check (mime_type = 'image/jpeg'),
    size_bytes integer not null check (size_bytes between 1 and 524288),
    source_kind text not null check (source_kind in ('rasterimage', 'webview_raster', 'serviceworker_raster')),
    source_host text not null check (char_length(source_host) between 1 and 253),
    document_host text not null check (char_length(document_host) between 1 and 253),
    source_url_hash text not null check (source_url_hash ~ '^[0-9a-f]{64}$'),
    policy_version text not null check (policy_version = 'DAG_STRICT_MODESTY_V1'),
    collector_version text not null check (collector_version = 'dag-v2-calibration-collector-1'),
    created_at timestamptz not null default now(),
    status text not null default 'pending' check (status in ('pending', 'ready', 'rejected'))
);

create index dag_v2_calibration_samples_perceptual_idx
    on public.dag_v2_calibration_samples (perceptual_hash);
create index dag_v2_calibration_samples_status_created_idx
    on public.dag_v2_calibration_samples (status, created_at desc);

create table public.dag_v2_calibration_labels (
    sample_id uuid not null references public.dag_v2_calibration_samples(sample_id) on delete restrict,
    review_decision text not null check (review_decision in ('show', 'hide', 'unsure')),
    reviewer_key text not null check (reviewer_key ~ '^[0-9a-f]{64}$'),
    policy_version text not null check (policy_version = 'DAG_STRICT_MODESTY_V1'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (sample_id, reviewer_key)
);

create table public.dag_v2_calibration_audit (
    audit_id uuid primary key default gen_random_uuid(),
    sample_id uuid references public.dag_v2_calibration_samples(sample_id) on delete restrict,
    reviewer_key text check (reviewer_key is null or reviewer_key ~ '^[0-9a-f]{64}$'),
    action text not null check (
        action in (
            'submission_attempt',
            'sample_created',
            'sample_deduplicated',
            'sample_ready',
            'sample_rejected',
            'label_created',
            'label_changed',
            'submission_rejected'
        )
    ),
    details jsonb not null default '{}'::jsonb check (jsonb_typeof(details) = 'object'),
    created_at timestamptz not null default now()
);

create index dag_v2_calibration_audit_reviewer_created_idx
    on public.dag_v2_calibration_audit (reviewer_key, created_at desc);
create index dag_v2_calibration_audit_sample_created_idx
    on public.dag_v2_calibration_audit (sample_id, created_at desc);

alter table public.dag_v2_calibration_samples enable row level security;
alter table public.dag_v2_calibration_labels enable row level security;
alter table public.dag_v2_calibration_audit enable row level security;

revoke all on public.dag_v2_calibration_samples from public, anon, authenticated;
revoke all on public.dag_v2_calibration_labels from public, anon, authenticated;
revoke all on public.dag_v2_calibration_audit from public, anon, authenticated;
grant all on public.dag_v2_calibration_samples to service_role;
grant all on public.dag_v2_calibration_labels to service_role;
grant all on public.dag_v2_calibration_audit to service_role;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'dag-v2-calibration',
    'dag-v2-calibration',
    false,
    524288,
    array['image/jpeg']
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.dag_v2_calibration_submission_authorized(p_device_id uuid)
returns table (community_id uuid)
language plpgsql
security definer
stable
set search_path = ''
as $$
begin
    if not public.dag_search_authorized(p_device_id) then
        return;
    end if;

    return query
    select a.community_id
    from public.devices d
    join public.accounts a
      on a.id = d.account_id
     and a.deleted_at is null
    where d.id = p_device_id
      and d.app_role = 'user'
      and d.deleted_at is null;
end;
$$;

create or replace function public.dag_v2_calibration_authorize_and_consume(
    p_device_id uuid,
    p_reviewer_key text
)
returns table (authorization text, community_id uuid)
language plpgsql
security definer
volatile
set search_path = ''
as $$
declare
    authorized_community uuid;
    hourly_count integer;
    daily_count integer;
begin
    if p_reviewer_key !~ '^[0-9a-f]{64}$' then
        return query select 'unauthorized'::text, null::uuid;
        return;
    end if;

    select a.community_id
      into authorized_community
    from public.dag_v2_calibration_submission_authorized(p_device_id) a
    limit 1;
    if authorized_community is null then
        return query select 'unauthorized'::text, null::uuid;
        return;
    end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_reviewer_key));
    select
        count(*) filter (where created_at >= now() - interval '1 hour'),
        count(*) filter (where created_at >= now() - interval '24 hours')
      into hourly_count, daily_count
    from public.dag_v2_calibration_audit
    where reviewer_key = p_reviewer_key
      and action = 'submission_attempt'
      and created_at >= now() - interval '24 hours';

    if hourly_count >= 30 or daily_count >= 100 then
        return query select 'rate_limited'::text, authorized_community;
        return;
    end if;

    insert into public.dag_v2_calibration_audit(reviewer_key, action)
    values (p_reviewer_key, 'submission_attempt');
    return query select 'allowed'::text, authorized_community;
end;
$$;

create or replace function public.dag_v2_calibration_hamming_distance(
    first_hash text,
    second_hash text
)
returns integer
language sql
immutable
strict
set search_path = ''
as $$
    select pg_catalog.bit_count(
        (('x' || first_hash)::bit(64)) # (('x' || second_hash)::bit(64))
    )::integer
$$;

create or replace function public.dag_v2_calibration_register_sample(
    p_content_sha256 text,
    p_perceptual_hash text,
    p_storage_path text,
    p_width integer,
    p_height integer,
    p_mime_type text,
    p_size_bytes integer,
    p_source_kind text,
    p_source_host text,
    p_document_host text,
    p_source_url_hash text,
    p_policy_version text,
    p_collector_version text,
    p_reviewer_key text
)
returns table (
    sample_id uuid,
    canonical_content_sha256 text,
    canonical_storage_path text,
    created boolean,
    match_kind text
)
language plpgsql
security definer
volatile
set search_path = ''
as $$
declare
    matched public.dag_v2_calibration_samples%rowtype;
begin
    if (
        p_content_sha256 !~ '^[0-9a-f]{64}$'
        or p_perceptual_hash !~ '^[0-9a-f]{16}$'
        or p_storage_path !~ '^samples/[0-9a-f]{2}/[0-9a-f]{64}\.jpg$'
        or p_width not between 1 and 768
        or p_height not between 1 and 768
        or p_mime_type <> 'image/jpeg'
        or p_size_bytes not between 1 and 524288
        or p_source_kind not in ('rasterimage', 'webview_raster', 'serviceworker_raster')
        or p_source_host !~ '^[a-z0-9.-]{1,253}$'
        or p_document_host !~ '^[a-z0-9.-]{1,253}$'
        or p_source_url_hash !~ '^[0-9a-f]{64}$'
        or p_policy_version <> 'DAG_STRICT_MODESTY_V1'
        or p_collector_version <> 'dag-v2-calibration-collector-1'
        or p_reviewer_key !~ '^[0-9a-f]{64}$'
    ) then
        raise exception 'invalid_calibration_sample';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_content_sha256));
    select *
      into matched
    from public.dag_v2_calibration_samples s
    where s.content_sha256 = p_content_sha256
    limit 1;
    if found then
        if matched.status = 'rejected' then
            update public.dag_v2_calibration_samples
               set perceptual_hash = p_perceptual_hash,
                   width = p_width,
                   height = p_height,
                   mime_type = p_mime_type,
                   size_bytes = p_size_bytes,
                   source_kind = p_source_kind,
                   source_host = p_source_host,
                   document_host = p_document_host,
                   source_url_hash = p_source_url_hash,
                   policy_version = p_policy_version,
                   collector_version = p_collector_version,
                   status = 'pending'
             where public.dag_v2_calibration_samples.sample_id = matched.sample_id
            returning * into matched;
            insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action, details)
            values (matched.sample_id, p_reviewer_key, 'sample_created', '{"kind":"retry"}'::jsonb);
            return query
            select matched.sample_id, matched.content_sha256, matched.storage_path, true, 'retry'::text;
            return;
        end if;
        insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action, details)
        values (matched.sample_id, p_reviewer_key, 'sample_deduplicated', '{"kind":"exact"}'::jsonb);
        return query
        select matched.sample_id, matched.content_sha256, matched.storage_path, false, 'exact'::text;
        return;
    end if;

    select *
      into matched
    from public.dag_v2_calibration_samples s
    where s.status = 'ready'
      and public.dag_v2_calibration_hamming_distance(s.perceptual_hash, p_perceptual_hash) <= 5
    order by public.dag_v2_calibration_hamming_distance(s.perceptual_hash, p_perceptual_hash), s.created_at
    limit 1;
    if found then
        insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action, details)
        values (matched.sample_id, p_reviewer_key, 'sample_deduplicated', '{"kind":"perceptual"}'::jsonb);
        return query
        select matched.sample_id, matched.content_sha256, matched.storage_path, false, 'perceptual'::text;
        return;
    end if;

    insert into public.dag_v2_calibration_samples(
        content_sha256,
        perceptual_hash,
        storage_path,
        width,
        height,
        mime_type,
        size_bytes,
        source_kind,
        source_host,
        document_host,
        source_url_hash,
        policy_version,
        collector_version
    )
    values (
        p_content_sha256,
        p_perceptual_hash,
        p_storage_path,
        p_width,
        p_height,
        p_mime_type,
        p_size_bytes,
        p_source_kind,
        p_source_host,
        p_document_host,
        p_source_url_hash,
        p_policy_version,
        p_collector_version
    )
    returning * into matched;

    insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action)
    values (matched.sample_id, p_reviewer_key, 'sample_created');
    return query
    select matched.sample_id, matched.content_sha256, matched.storage_path, true, 'new'::text;
end;
$$;

create or replace function public.dag_v2_calibration_mark_sample(
    p_sample_id uuid,
    p_status text,
    p_reviewer_key text
)
returns void
language plpgsql
security definer
volatile
set search_path = ''
as $$
begin
    if p_status not in ('ready', 'rejected') or p_reviewer_key !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid_sample_status';
    end if;
    update public.dag_v2_calibration_samples
       set status = p_status
     where sample_id = p_sample_id
       and status = 'pending';
    insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action)
    values (
        p_sample_id,
        p_reviewer_key,
        case when p_status = 'ready' then 'sample_ready' else 'sample_rejected' end
    );
end;
$$;

create or replace function public.dag_v2_calibration_upsert_label(
    p_sample_id uuid,
    p_review_decision text,
    p_reviewer_key text,
    p_policy_version text
)
returns table (audit_recorded boolean, relabeled boolean)
language plpgsql
security definer
volatile
set search_path = ''
as $$
declare
    previous_decision text;
begin
    if (
        p_review_decision not in ('show', 'hide', 'unsure')
        or p_reviewer_key !~ '^[0-9a-f]{64}$'
        or p_policy_version <> 'DAG_STRICT_MODESTY_V1'
        or not exists (
            select 1
            from public.dag_v2_calibration_samples s
            where s.sample_id = p_sample_id
              and s.status = 'ready'
        )
    ) then
        raise exception 'invalid_calibration_label';
    end if;

    select l.review_decision
      into previous_decision
    from public.dag_v2_calibration_labels l
    where l.sample_id = p_sample_id
      and l.reviewer_key = p_reviewer_key
    for update;

    insert into public.dag_v2_calibration_labels(
        sample_id,
        review_decision,
        reviewer_key,
        policy_version
    )
    values (p_sample_id, p_review_decision, p_reviewer_key, p_policy_version)
    on conflict (sample_id, reviewer_key) do update
    set review_decision = excluded.review_decision,
        policy_version = excluded.policy_version,
        updated_at = now();

    insert into public.dag_v2_calibration_audit(
        sample_id,
        reviewer_key,
        action,
        details
    )
    values (
        p_sample_id,
        p_reviewer_key,
        case when previous_decision is null then 'label_created' else 'label_changed' end,
        jsonb_build_object(
            'previous_decision', previous_decision,
            'review_decision', p_review_decision,
            'training_example', p_review_decision in ('show', 'hide')
        )
    );
    return query select true, previous_decision is not null;
end;
$$;

revoke all on function public.dag_v2_calibration_submission_authorized(uuid) from public, anon, authenticated;
revoke all on function public.dag_v2_calibration_authorize_and_consume(uuid, text) from public, anon, authenticated;
revoke all on function public.dag_v2_calibration_hamming_distance(text, text) from public, anon, authenticated;
revoke all on function public.dag_v2_calibration_register_sample(
    text, text, text, integer, integer, text, integer, text, text, text, text, text, text, text
) from public, anon, authenticated;
revoke all on function public.dag_v2_calibration_mark_sample(uuid, text, text) from public, anon, authenticated;
revoke all on function public.dag_v2_calibration_upsert_label(uuid, text, text, text)
    from public, anon, authenticated;

grant execute on function public.dag_v2_calibration_submission_authorized(uuid) to service_role;
grant execute on function public.dag_v2_calibration_authorize_and_consume(uuid, text) to service_role;
grant execute on function public.dag_v2_calibration_hamming_distance(text, text) to service_role;
grant execute on function public.dag_v2_calibration_register_sample(
    text, text, text, integer, integer, text, integer, text, text, text, text, text, text, text
) to service_role;
grant execute on function public.dag_v2_calibration_mark_sample(uuid, text, text) to service_role;
grant execute on function public.dag_v2_calibration_upsert_label(uuid, text, text, text) to service_role;
