alter table public.dag_calibration_reviews
    drop constraint if exists dag_calibration_reviews_initial_decision_check;

alter table public.dag_calibration_reviews
    add constraint dag_calibration_reviews_initial_decision_check
        check (initial_decision in ('allowed', 'blocked', 'uncertain')),
    add column if not exists submission_source text not null default 'automatic_uncertainty';

alter table public.dag_calibration_reviews
    drop constraint if exists dag_calibration_reviews_submission_source_check;
alter table public.dag_calibration_reviews
    add constraint dag_calibration_reviews_submission_source_check
        check (submission_source in ('automatic_uncertainty', 'manual_dag'));

alter table public.dag_calibration_audit
    drop constraint if exists dag_calibration_audit_action_check;
alter table public.dag_calibration_audit
    add constraint dag_calibration_audit_action_check check (
        action in (
            'review_labeled', 'reviews_cleared', 'manual_image_reported',
            'calibration_created', 'calibration_activated',
            'calibration_rollback', 'model_registered'
        )
    );

drop function if exists public.super_admin_list_dag_calibration_reviews(text, integer);

create function public.super_admin_list_dag_calibration_reviews(
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
    submission_source text,
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
           reviews.storage_path, reviews.model_version, reviews.initial_decision, reviews.submission_source,
           reviews.scores, reviews.status, reviews.review_decision, reviews.review_reason, reviews.review_note,
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

revoke all on function public.super_admin_list_dag_calibration_reviews(text, integer) from public, anon;
grant execute on function public.super_admin_list_dag_calibration_reviews(text, integer) to authenticated;
