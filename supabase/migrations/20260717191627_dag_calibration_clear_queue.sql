alter table public.dag_calibration_reviews
    add column if not exists archived_at timestamptz,
    add column if not exists archived_by uuid references auth.users(id) on delete set null;

alter table public.dag_calibration_audit
    drop constraint if exists dag_calibration_audit_action_check;
alter table public.dag_calibration_audit
    add constraint dag_calibration_audit_action_check check (
        action in (
            'review_labeled', 'reviews_cleared', 'calibration_created',
            'calibration_activated', 'calibration_rollback', 'model_registered'
        )
    );

create or replace function public.super_admin_list_dag_calibration_reviews(
    requested_status text default 'pending',
    max_rows integer default 100
)
returns table (
    review_id uuid,
    community_id uuid,
    community_name text,
    device_id uuid,
    device_name text,
    storage_path text,
    model_version text,
    initial_decision text,
    scores jsonb,
    status text,
    review_decision text,
    review_reason text,
    review_note text,
    created_at timestamptz,
    expires_at timestamptz,
    reviewed_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();
    if requested_status not in ('pending', 'reviewed', 'all') then
        raise exception 'Invalid review status';
    end if;

    return query
    select reviews.id, reviews.community_id, communities.name, reviews.device_id, devices.display_name,
           reviews.storage_path, reviews.model_version, reviews.initial_decision, reviews.scores,
           reviews.status, reviews.review_decision, reviews.review_reason, reviews.review_note,
           reviews.created_at, reviews.expires_at, reviews.reviewed_at
    from public.dag_calibration_reviews reviews
    join public.communities communities on communities.id = reviews.community_id
    join public.devices devices on devices.id = reviews.device_id
    where (requested_status = 'all' or reviews.status = requested_status)
      and reviews.archived_at is null
      and reviews.expires_at > now()
    order by reviews.created_at desc
    limit greatest(1, least(max_rows, 500));
end;
$$;

create or replace function public.super_admin_label_dag_calibration_review(
    target_review_id uuid,
    new_decision text,
    new_reason text,
    new_note text default null
)
returns void
language plpgsql
security definer
volatile
set search_path = public
as $$
begin
    perform public.require_super_admin();
    if new_decision not in ('allow', 'block') then raise exception 'Invalid review decision'; end if;
    if new_reason not in (
        'acceptable_clothing', 'pronounced_neckline', 'exposed_shoulders', 'tight_or_transparent',
        'swimwear', 'lingerie', 'nudity', 'product_without_person', 'mannequin',
        'false_positive', 'ambiguous', 'other'
    ) then raise exception 'Invalid review reason'; end if;

    update public.dag_calibration_reviews
    set status = 'reviewed', review_decision = new_decision, review_reason = new_reason,
        review_note = nullif(left(trim(coalesce(new_note, '')), 500), ''),
        reviewed_by = auth.uid(), reviewed_at = now()
    where id = target_review_id and archived_at is null and expires_at > now();
    if not found then raise exception 'Review not found, archived or expired'; end if;

    insert into public.dag_calibration_audit(actor_user_id, action, review_id, details)
    values (auth.uid(), 'review_labeled', target_review_id, jsonb_build_object('decision', new_decision, 'reason', new_reason));
end;
$$;

create or replace function public.super_admin_create_dag_calibration(
    new_thresholds jsonb,
    new_metrics jsonb,
    calibration_model_version text,
    calibration_explanation text
)
returns uuid
language plpgsql
security definer
volatile
set search_path = public
as $$
declare
    created_id uuid;
    reviewed_count integer;
    allow_count integer;
    block_count integer;
    active_id uuid;
begin
    perform public.require_super_admin();
    select count(*), count(*) filter (where review_decision = 'allow'), count(*) filter (where review_decision = 'block')
      into reviewed_count, allow_count, block_count
    from public.dag_calibration_reviews
    where status = 'reviewed'
      and archived_at is null
      and model_version = calibration_model_version;
    if reviewed_count < 12 or allow_count < 3 or block_count < 3 then
        raise exception 'At least 12 labels for this model with 3 allow and 3 block decisions are required';
    end if;
    if jsonb_typeof(new_thresholds) <> 'object' or jsonb_typeof(new_metrics) <> 'object' then
        raise exception 'Invalid calibration payload';
    end if;
    if exists (
        select 1 from jsonb_each_text(new_thresholds)
        where value::numeric < 0 or value::numeric > 1
    ) then raise exception 'Thresholds must be between zero and one'; end if;

    select id into active_id from public.dag_calibration_versions where status = 'active' limit 1;
    insert into public.dag_calibration_versions(
        thresholds, metrics, labeled_item_count, model_version, explanation, based_on_id, created_by
    ) values (
        new_thresholds, new_metrics, reviewed_count, left(calibration_model_version, 120),
        left(calibration_explanation, 1000), active_id, auth.uid()
    ) returning id into created_id;

    insert into public.dag_calibration_audit(actor_user_id, action, calibration_id, details)
    values (auth.uid(), 'calibration_created', created_id, jsonb_build_object('labeled_items', reviewed_count, 'model_version', calibration_model_version));
    return created_id;
end;
$$;

revoke all on function public.super_admin_list_dag_calibration_reviews(text, integer) from public, anon;
revoke all on function public.super_admin_label_dag_calibration_review(uuid, text, text, text) from public, anon;
revoke all on function public.super_admin_create_dag_calibration(jsonb, jsonb, text, text) from public, anon;
grant execute on function public.super_admin_list_dag_calibration_reviews(text, integer) to authenticated;
grant execute on function public.super_admin_label_dag_calibration_review(uuid, text, text, text) to authenticated;
grant execute on function public.super_admin_create_dag_calibration(jsonb, jsonb, text, text) to authenticated;
