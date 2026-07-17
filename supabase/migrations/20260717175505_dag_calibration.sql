create table public.dag_calibration_reviews (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete restrict,
    device_id uuid not null references public.devices(id) on delete restrict,
    image_hash text not null check (image_hash ~ '^[0-9a-f]{64}$'),
    storage_path text not null unique,
    model_version text not null check (char_length(model_version) between 1 and 120),
    initial_decision text not null check (initial_decision in ('blocked', 'uncertain')),
    scores jsonb not null check (jsonb_typeof(scores) = 'object'),
    status text not null default 'pending' check (status in ('pending', 'reviewed', 'expired')),
    review_decision text check (review_decision in ('allow', 'block')),
    review_reason text check (
        review_reason in (
            'acceptable_clothing', 'pronounced_neckline', 'exposed_shoulders',
            'tight_or_transparent', 'swimwear', 'lingerie', 'nudity',
            'product_without_person', 'mannequin', 'false_positive', 'ambiguous', 'other'
        )
    ),
    review_note text check (review_note is null or char_length(review_note) <= 500),
    reviewed_by uuid references auth.users(id) on delete set null,
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '30 days'),
    unique (device_id, image_hash, model_version),
    check (
        (status = 'reviewed' and review_decision is not null and review_reason is not null and reviewed_at is not null)
        or (status <> 'reviewed' and review_decision is null and review_reason is null)
    )
);

create index dag_calibration_reviews_queue_idx
    on public.dag_calibration_reviews (status, created_at desc);
create index dag_calibration_reviews_community_idx
    on public.dag_calibration_reviews (community_id, status, created_at desc);

create table public.dag_calibration_versions (
    id uuid primary key default gen_random_uuid(),
    version_number bigint generated always as identity unique,
    status text not null default 'candidate' check (status in ('candidate', 'active', 'retired')),
    thresholds jsonb not null check (jsonb_typeof(thresholds) = 'object'),
    metrics jsonb not null check (jsonb_typeof(metrics) = 'object'),
    labeled_item_count integer not null check (labeled_item_count >= 0),
    model_version text not null check (char_length(model_version) between 1 and 120),
    explanation text not null check (char_length(explanation) between 1 and 1000),
    based_on_id uuid references public.dag_calibration_versions(id) on delete set null,
    created_by uuid references auth.users(id) on delete set null,
    activated_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    activated_at timestamptz
);

create unique index dag_calibration_one_active_idx
    on public.dag_calibration_versions ((status)) where status = 'active';

create table public.dag_calibration_models (
    id uuid primary key default gen_random_uuid(),
    model_version text not null unique check (char_length(model_version) between 1 and 120),
    status text not null default 'registered' check (status in ('registered', 'training', 'candidate', 'active', 'retired', 'failed')),
    artifact_path text,
    artifact_sha256 text check (artifact_sha256 is null or artifact_sha256 ~ '^[0-9a-f]{64}$'),
    training_example_count integer not null default 0 check (training_example_count >= 0),
    validation_metrics jsonb not null default '{}'::jsonb check (jsonb_typeof(validation_metrics) = 'object'),
    notes text check (notes is null or char_length(notes) <= 1000),
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.dag_calibration_audit (
    id uuid primary key default gen_random_uuid(),
    actor_user_id uuid references auth.users(id) on delete set null,
    action text not null check (
        action in ('review_labeled', 'calibration_created', 'calibration_activated', 'calibration_rollback', 'model_registered')
    ),
    review_id uuid references public.dag_calibration_reviews(id) on delete set null,
    calibration_id uuid references public.dag_calibration_versions(id) on delete set null,
    details jsonb not null default '{}'::jsonb check (jsonb_typeof(details) = 'object'),
    created_at timestamptz not null default now()
);

insert into public.dag_calibration_models(
    model_version, status, training_example_count, validation_metrics, notes
) values (
    'marqo-nsfw-vit-tiny-384-2', 'active', 0, '{}'::jsonb,
    'Modelo visual profesional incluido y firmado dentro de App Usuario.'
);

alter table public.dag_calibration_reviews enable row level security;
alter table public.dag_calibration_versions enable row level security;
alter table public.dag_calibration_models enable row level security;
alter table public.dag_calibration_audit enable row level security;

revoke all on public.dag_calibration_reviews from public, anon, authenticated;
revoke all on public.dag_calibration_versions from public, anon, authenticated;
revoke all on public.dag_calibration_models from public, anon, authenticated;
revoke all on public.dag_calibration_audit from public, anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('dag-calibration', 'dag-calibration', false, 131072, array['image/jpeg'])
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create policy "Super Admin reads DAG calibration images"
on storage.objects for select
to authenticated
using (bucket_id = 'dag-calibration' and (select public.is_super_admin()));

create or replace function public.dag_calibration_submission_authorized(p_device_id uuid)
returns table (community_id uuid)
language plpgsql
security definer
stable
set search_path = public, extensions
as $$
begin
    if not public.dag_search_authorized(p_device_id) then
        return;
    end if;

    return query
    select accounts.community_id
    from public.devices devices
    join public.accounts accounts on accounts.id = devices.account_id and accounts.deleted_at is null
    where devices.id = p_device_id
      and devices.app_role = 'user'
      and devices.deleted_at is null;
end;
$$;

revoke all on function public.dag_calibration_submission_authorized(uuid) from public;
grant execute on function public.dag_calibration_submission_authorized(uuid) to anon, authenticated;

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
    where id = target_review_id and expires_at > now();
    if not found then raise exception 'Review not found or expired'; end if;

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
    from public.dag_calibration_reviews where status = 'reviewed';
    if reviewed_count < 12 or allow_count < 3 or block_count < 3 then
        raise exception 'At least 12 labels with 3 allow and 3 block decisions are required';
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
    values (auth.uid(), 'calibration_created', created_id, jsonb_build_object('labeled_items', reviewed_count));
    return created_id;
end;
$$;

create or replace function public.super_admin_activate_dag_calibration(target_calibration_id uuid)
returns void
language plpgsql
security definer
volatile
set search_path = public
as $$
declare previous_id uuid;
begin
    perform public.require_super_admin();
    select id into previous_id from public.dag_calibration_versions where status = 'active' limit 1;
    update public.dag_calibration_versions set status = 'retired' where status = 'active';
    update public.dag_calibration_versions
    set status = 'active', activated_by = auth.uid(), activated_at = now()
    where id = target_calibration_id and status in ('candidate', 'retired');
    if not found then raise exception 'Calibration not found'; end if;
    insert into public.dag_calibration_audit(actor_user_id, action, calibration_id, details)
    values (
        auth.uid(),
        case when previous_id is null then 'calibration_activated' else 'calibration_rollback' end,
        target_calibration_id,
        jsonb_build_object('previous_calibration_id', previous_id)
    );
end;
$$;

create or replace function public.super_admin_list_dag_calibrations()
returns table (
    calibration_id uuid, version_number bigint, status text, thresholds jsonb, metrics jsonb,
    labeled_item_count integer, model_version text, explanation text, created_at timestamptz, activated_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();
    return query select id, dag_calibration_versions.version_number, dag_calibration_versions.status,
        dag_calibration_versions.thresholds, dag_calibration_versions.metrics,
        dag_calibration_versions.labeled_item_count, dag_calibration_versions.model_version,
        dag_calibration_versions.explanation, dag_calibration_versions.created_at,
        dag_calibration_versions.activated_at
    from public.dag_calibration_versions order by version_number desc;
end;
$$;

create or replace function public.super_admin_list_dag_calibration_audit(max_rows integer default 200)
returns table (
    audit_id uuid, action text, review_id uuid, calibration_id uuid, details jsonb, created_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();
    return query select id, dag_calibration_audit.action, dag_calibration_audit.review_id,
        dag_calibration_audit.calibration_id, dag_calibration_audit.details, dag_calibration_audit.created_at
    from public.dag_calibration_audit order by created_at desc limit greatest(1, least(max_rows, 500));
end;
$$;

create or replace function public.super_admin_list_dag_calibration_models()
returns table (
    model_id uuid, model_version text, status text, artifact_path text, artifact_sha256 text,
    training_example_count integer, validation_metrics jsonb, notes text, created_at timestamptz, updated_at timestamptz
)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
    perform public.require_super_admin();
    return query select id, dag_calibration_models.model_version, dag_calibration_models.status,
        dag_calibration_models.artifact_path, dag_calibration_models.artifact_sha256,
        dag_calibration_models.training_example_count, dag_calibration_models.validation_metrics,
        dag_calibration_models.notes, dag_calibration_models.created_at, dag_calibration_models.updated_at
    from public.dag_calibration_models order by created_at desc;
end;
$$;

revoke all on function public.super_admin_list_dag_calibration_reviews(text, integer) from public, anon;
revoke all on function public.super_admin_label_dag_calibration_review(uuid, text, text, text) from public, anon;
revoke all on function public.super_admin_create_dag_calibration(jsonb, jsonb, text, text) from public, anon;
revoke all on function public.super_admin_activate_dag_calibration(uuid) from public, anon;
revoke all on function public.super_admin_list_dag_calibrations() from public, anon;
revoke all on function public.super_admin_list_dag_calibration_audit(integer) from public, anon;
revoke all on function public.super_admin_list_dag_calibration_models() from public, anon;
grant execute on function public.super_admin_list_dag_calibration_reviews(text, integer) to authenticated;
grant execute on function public.super_admin_label_dag_calibration_review(uuid, text, text, text) to authenticated;
grant execute on function public.super_admin_create_dag_calibration(jsonb, jsonb, text, text) to authenticated;
grant execute on function public.super_admin_activate_dag_calibration(uuid) to authenticated;
grant execute on function public.super_admin_list_dag_calibrations() to authenticated;
grant execute on function public.super_admin_list_dag_calibration_audit(integer) to authenticated;
grant execute on function public.super_admin_list_dag_calibration_models() to authenticated;

grant usage, select on sequence public.dag_calibration_versions_version_number_seq to authenticated;
