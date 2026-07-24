alter table public.dag_calibration_reviews
    drop constraint if exists dag_calibration_reviews_submission_source_check;

alter table public.dag_calibration_reviews
    add constraint dag_calibration_reviews_submission_source_check
    check (
        submission_source in (
            'automatic_uncertainty',
            'manual_dag',
            'manual_dag_false_positive',
            'manual_dag_binary'
        )
    );

do $$
declare
    constraint_name text;
begin
    select constraints.conname
      into constraint_name
    from pg_constraint constraints
    where constraints.conrelid = 'public.dag_calibration_reviews'::regclass
      and constraints.contype = 'c'
      and lower(pg_get_constraintdef(constraints.oid)) like '%status = ''reviewed''%'
      and lower(pg_get_constraintdef(constraints.oid)) like '%review_reason is not null%'
    limit 1;

    if constraint_name is not null then
        execute format(
            'alter table public.dag_calibration_reviews drop constraint %I',
            constraint_name
        );
    end if;
end;
$$;

alter table public.dag_calibration_reviews
    add constraint dag_calibration_reviews_review_state_check
    check (
        (
            status = 'reviewed'
            and review_decision is not null
            and reviewed_at is not null
        )
        or (
            status <> 'reviewed'
            and review_decision is null
            and review_reason is null
        )
    );

create or replace function public.super_admin_label_dag_calibration_review(
    target_review_id uuid,
    new_decision text,
    new_reason text default null,
    new_note text default null
)
returns void
language plpgsql
security definer
volatile
set search_path = ''
as $$
begin
    perform public.require_super_admin();
    if new_decision not in ('allow', 'block') then
        raise exception 'Invalid review decision';
    end if;

    update public.dag_calibration_reviews
    set status = 'reviewed',
        review_decision = new_decision,
        review_reason = null,
        review_note = nullif(left(trim(coalesce(new_note, '')), 500), ''),
        reviewed_by = (select auth.uid()),
        reviewed_at = now()
    where id = target_review_id
      and archived_at is null
      and expires_at > now();
    if not found then
        raise exception 'Review not found, archived or expired';
    end if;

    insert into public.dag_calibration_audit(actor_user_id, action, review_id, details)
    values (
        (select auth.uid()),
        'review_labeled',
        target_review_id,
        jsonb_build_object('decision', new_decision, 'binary', true)
    );
end;
$$;

revoke all on function public.super_admin_label_dag_calibration_review(uuid, text, text, text)
    from public, anon;
grant execute on function public.super_admin_label_dag_calibration_review(uuid, text, text, text)
    to authenticated;

create or replace function public.dag_create_automatic_calibration_candidate(
    new_thresholds jsonb,
    new_metrics jsonb,
    calibration_model_version text,
    calibration_explanation text
)
returns uuid
language plpgsql
security definer
volatile
set search_path = ''
as $$
declare
    created_id uuid;
    reviewed_count integer;
    allow_count integer;
    block_count integer;
    previous_labeled_count integer;
    active_id uuid;
begin
    select count(*),
           count(*) filter (where review_decision = 'allow'),
           count(*) filter (where review_decision = 'block')
      into reviewed_count, allow_count, block_count
    from public.dag_calibration_reviews
    where status = 'reviewed'
      and archived_at is null
      and model_version = calibration_model_version;

    if reviewed_count < 40 or allow_count < 10 or block_count < 10 then
        return null;
    end if;

    select coalesce(max(labeled_item_count), 0)
      into previous_labeled_count
    from public.dag_calibration_versions
    where model_version = calibration_model_version;

    if previous_labeled_count > 0 and reviewed_count - previous_labeled_count < 10 then
        return null;
    end if;

    if jsonb_typeof(new_thresholds) <> 'object' or jsonb_typeof(new_metrics) <> 'object' then
        raise exception 'Invalid calibration payload';
    end if;

    if not coalesce((
        (new_thresholds->>'professional_safe')::numeric between 0.05 and 0.75 and
        (new_thresholds->>'professional_block')::numeric between 0.35 and 0.90 and
        (new_thresholds->>'professional_safe')::numeric < (new_thresholds->>'professional_block')::numeric and
        (new_thresholds->>'female_face')::numeric between 0.12 and 0.65 and
        (new_thresholds->>'male_face')::numeric between 0.12 and 0.65 and
        (new_thresholds->>'male_breast_exposed')::numeric between 0.25 and 0.90 and
        (new_thresholds->>'female_breast_covered')::numeric between 0.08 and 0.65 and
        (new_thresholds->>'female_genitalia_covered')::numeric between 0.08 and 0.65 and
        (new_thresholds->>'buttocks_covered')::numeric between 0.08 and 0.65 and
        (new_thresholds->>'armpits_exposed')::numeric between 0.08 and 0.65 and
        (new_thresholds->>'belly_exposed')::numeric between 0.08 and 0.65 and
        (new_thresholds->>'explicit_region')::numeric between 0.08 and 0.65 and
        (new_thresholds->>'sleeves_above_elbow')::numeric between 0.45 and 0.95 and
        (new_thresholds->>'hem_above_knee')::numeric between 0.45 and 0.95
    ), false) then
        raise exception 'Thresholds exceed safe calibration bounds';
    end if;

    select id
      into active_id
    from public.dag_calibration_versions
    where status = 'active'
    limit 1;

    insert into public.dag_calibration_versions(
        thresholds,
        metrics,
        labeled_item_count,
        model_version,
        explanation,
        based_on_id,
        created_by
    )
    values (
        new_thresholds,
        new_metrics,
        reviewed_count,
        left(calibration_model_version, 120),
        left(calibration_explanation, 1000),
        active_id,
        null
    )
    returning id into created_id;

    insert into public.dag_calibration_version_reviews(calibration_id, review_id)
    select created_id, reviews.id
    from public.dag_calibration_reviews reviews
    where reviews.status = 'reviewed'
      and reviews.archived_at is null
      and reviews.model_version = calibration_model_version
    on conflict do nothing;

    insert into public.dag_calibration_audit(action, calibration_id, details)
    values (
        'calibration_created',
        created_id,
        jsonb_build_object(
            'labeled_items', reviewed_count,
            'model_version', calibration_model_version,
            'automatic_evaluation', true,
            'manual_activation_required', true
        )
    );

    return created_id;
end;
$$;

revoke all on function public.dag_create_automatic_calibration_candidate(jsonb, jsonb, text, text)
    from public, anon, authenticated;
grant execute on function public.dag_create_automatic_calibration_candidate(jsonb, jsonb, text, text)
    to service_role;
