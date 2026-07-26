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
    previous_status text;
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
    limit 1
    for update;

    if found then
        if matched.status = 'ready' then
            insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action, details)
            values (matched.sample_id, p_reviewer_key, 'sample_deduplicated', '{"kind":"exact"}'::jsonb);
            return query
            select matched.sample_id, matched.content_sha256, matched.storage_path, false, 'exact_ready'::text;
            return;
        end if;

        previous_status := matched.status;
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

        if matched.status = 'pending' then
            if previous_status = 'rejected' then
                insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action, details)
                values (
                    matched.sample_id,
                    p_reviewer_key,
                    'sample_created',
                    '{"kind":"retry_rejected"}'::jsonb
                );
                return query
                select
                    matched.sample_id,
                    matched.content_sha256,
                    matched.storage_path,
                    true,
                    'retry_rejected'::text;
                return;
            end if;

            insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action, details)
            values (
                matched.sample_id,
                p_reviewer_key,
                'sample_deduplicated',
                '{"kind":"resume_pending"}'::jsonb
            );
            return query
            select
                matched.sample_id,
                matched.content_sha256,
                matched.storage_path,
                true,
                'resume_pending'::text;
            return;
        end if;
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
declare
    current_status text;
begin
    if p_status not in ('ready', 'rejected') or p_reviewer_key !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid_sample_status';
    end if;

    select s.status
      into current_status
    from public.dag_v2_calibration_samples s
    where s.sample_id = p_sample_id
    for update;
    if not found then
        raise exception 'calibration_sample_not_found';
    end if;

    if current_status = 'ready' and p_status = 'ready' then
        return;
    end if;
    if current_status <> 'pending' then
        raise exception 'invalid_sample_transition:%->%', current_status, p_status;
    end if;

    update public.dag_v2_calibration_samples
       set status = p_status
     where sample_id = p_sample_id;
    insert into public.dag_v2_calibration_audit(sample_id, reviewer_key, action)
    values (
        p_sample_id,
        p_reviewer_key,
        case when p_status = 'ready' then 'sample_ready' else 'sample_rejected' end
    );
end;
$$;

revoke all on function public.dag_v2_calibration_register_sample(
    text, text, text, integer, integer, text, integer, text, text, text, text, text, text, text
) from public, anon, authenticated;
revoke all on function public.dag_v2_calibration_mark_sample(uuid, text, text)
    from public, anon, authenticated;

grant execute on function public.dag_v2_calibration_register_sample(
    text, text, text, integer, integer, text, integer, text, text, text, text, text, text, text
) to service_role;
grant execute on function public.dag_v2_calibration_mark_sample(uuid, text, text)
    to service_role;
