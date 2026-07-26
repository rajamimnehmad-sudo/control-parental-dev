begin;

do $$
declare
    reviewer text := repeat('f', 64);
    first_hash text := encode(extensions.digest(gen_random_uuid()::text, 'sha256'), 'hex');
    second_hash text := encode(extensions.digest(gen_random_uuid()::text, 'sha256'), 'hex');
    url_hash text := encode(extensions.digest('dag-v2-reliability-test', 'sha256'), 'hex');
    test_perceptual text;
    second_perceptual text;
    registered record;
    resumed record;
    retried record;
    ready_audit_count integer;
    v1_reviews_before bigint;
    v1_models_before bigint;
    v1_versions_before bigint;
begin
    select substr(md5('dag-v2-reliability-' || candidate::text), 1, 16)
      into test_perceptual
    from generate_series(1, 1000) candidate
    where not exists (
        select 1
        from public.dag_v2_calibration_samples sample
        where sample.status = 'ready'
          and public.dag_v2_calibration_hamming_distance(
              sample.perceptual_hash,
              substr(md5('dag-v2-reliability-' || candidate::text), 1, 16)
          ) <= 5
    )
    limit 1;
    if test_perceptual is null then
        raise exception 'no_test_perceptual_hash_available';
    end if;

    select count(*) into v1_reviews_before from public.dag_calibration_reviews;
    select count(*) into v1_models_before from public.dag_calibration_models;
    select count(*) into v1_versions_before from public.dag_calibration_versions;

    select *
      into registered
    from public.dag_v2_calibration_register_sample(
        p_content_sha256 => first_hash,
        p_perceptual_hash => test_perceptual,
        p_storage_path => 'samples/' || substr(first_hash, 1, 2) || '/' || first_hash || '.jpg',
        p_width => 2,
        p_height => 2,
        p_mime_type => 'image/jpeg',
        p_size_bytes => 123,
        p_source_kind => 'rasterimage',
        p_source_host => 'images.example',
        p_document_host => 'shop.example',
        p_source_url_hash => url_hash,
        p_policy_version => 'DAG_STRICT_MODESTY_V1',
        p_collector_version => 'dag-v2-calibration-collector-1',
        p_reviewer_key => reviewer
    );
    if registered.created is not true or registered.match_kind <> 'new' then
        raise exception 'new_registration_failed';
    end if;

    select *
      into resumed
    from public.dag_v2_calibration_register_sample(
        p_content_sha256 => first_hash,
        p_perceptual_hash => test_perceptual,
        p_storage_path => registered.canonical_storage_path,
        p_width => 2,
        p_height => 2,
        p_mime_type => 'image/jpeg',
        p_size_bytes => 123,
        p_source_kind => 'rasterimage',
        p_source_host => 'images.example',
        p_document_host => 'shop.example',
        p_source_url_hash => url_hash,
        p_policy_version => 'DAG_STRICT_MODESTY_V1',
        p_collector_version => 'dag-v2-calibration-collector-1',
        p_reviewer_key => reviewer
    );
    if resumed.sample_id <> registered.sample_id or resumed.match_kind <> 'resume_pending' then
        raise exception 'pending_resume_failed';
    end if;

    perform public.dag_v2_calibration_mark_sample(registered.sample_id, 'ready', reviewer);
    perform public.dag_v2_calibration_mark_sample(registered.sample_id, 'ready', reviewer);
    select count(*)
      into ready_audit_count
    from public.dag_v2_calibration_audit
    where sample_id = registered.sample_id
      and action = 'sample_ready';
    if ready_audit_count <> 1 then
        raise exception 'ready_audit_not_idempotent';
    end if;

    select *
      into resumed
    from public.dag_v2_calibration_register_sample(
        p_content_sha256 => first_hash,
        p_perceptual_hash => test_perceptual,
        p_storage_path => registered.canonical_storage_path,
        p_width => 2,
        p_height => 2,
        p_mime_type => 'image/jpeg',
        p_size_bytes => 123,
        p_source_kind => 'rasterimage',
        p_source_host => 'images.example',
        p_document_host => 'shop.example',
        p_source_url_hash => url_hash,
        p_policy_version => 'DAG_STRICT_MODESTY_V1',
        p_collector_version => 'dag-v2-calibration-collector-1',
        p_reviewer_key => reviewer
    );
    if resumed.created is not false or resumed.match_kind <> 'exact_ready' then
        raise exception 'ready_exact_dedup_failed';
    end if;

    perform public.dag_v2_calibration_upsert_label(
        registered.sample_id,
        'unsure',
        reviewer,
        'DAG_STRICT_MODESTY_V1'
    );
    perform public.dag_v2_calibration_upsert_label(
        registered.sample_id,
        'hide',
        reviewer,
        'DAG_STRICT_MODESTY_V1'
    );
    if not exists (
        select 1
        from public.dag_v2_calibration_labels
        where sample_id = registered.sample_id
          and reviewer_key = reviewer
          and review_decision = 'hide'
    ) then
        raise exception 'relabel_failed';
    end if;

    select substr(md5('dag-v2-reliability-second-' || candidate::text), 1, 16)
      into second_perceptual
    from generate_series(1, 1000) candidate
    where not exists (
        select 1
        from public.dag_v2_calibration_samples sample
        where sample.status = 'ready'
          and public.dag_v2_calibration_hamming_distance(
              sample.perceptual_hash,
              substr(md5('dag-v2-reliability-second-' || candidate::text), 1, 16)
          ) <= 5
    )
    limit 1;
    if second_perceptual is null then
        raise exception 'no_second_test_perceptual_hash_available';
    end if;

    select *
      into retried
    from public.dag_v2_calibration_register_sample(
        p_content_sha256 => second_hash,
        p_perceptual_hash => second_perceptual,
        p_storage_path => 'samples/' || substr(second_hash, 1, 2) || '/' || second_hash || '.jpg',
        p_width => 2,
        p_height => 2,
        p_mime_type => 'image/jpeg',
        p_size_bytes => 123,
        p_source_kind => 'rasterimage',
        p_source_host => 'images.example',
        p_document_host => 'shop.example',
        p_source_url_hash => url_hash,
        p_policy_version => 'DAG_STRICT_MODESTY_V1',
        p_collector_version => 'dag-v2-calibration-collector-1',
        p_reviewer_key => reviewer
    );
    perform public.dag_v2_calibration_mark_sample(retried.sample_id, 'rejected', reviewer);

    select *
      into resumed
    from public.dag_v2_calibration_register_sample(
        p_content_sha256 => second_hash,
        p_perceptual_hash => second_perceptual,
        p_storage_path => retried.canonical_storage_path,
        p_width => 2,
        p_height => 2,
        p_mime_type => 'image/jpeg',
        p_size_bytes => 123,
        p_source_kind => 'rasterimage',
        p_source_host => 'images.example',
        p_document_host => 'shop.example',
        p_source_url_hash => url_hash,
        p_policy_version => 'DAG_STRICT_MODESTY_V1',
        p_collector_version => 'dag-v2-calibration-collector-1',
        p_reviewer_key => reviewer
    );
    if resumed.sample_id <> retried.sample_id or resumed.match_kind <> 'retry_rejected' then
        raise exception 'rejected_retry_failed';
    end if;

    if (select count(*) from public.dag_calibration_reviews) <> v1_reviews_before
        or (select count(*) from public.dag_calibration_models) <> v1_models_before
        or (select count(*) from public.dag_calibration_versions) <> v1_versions_before
    then
        raise exception 'dag_v1_changed';
    end if;
end;
$$;

rollback;
