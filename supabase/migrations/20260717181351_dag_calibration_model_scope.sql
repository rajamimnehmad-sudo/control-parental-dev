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
    where status = 'reviewed' and model_version = calibration_model_version;
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

revoke all on function public.super_admin_create_dag_calibration(jsonb, jsonb, text, text) from public, anon;
grant execute on function public.super_admin_create_dag_calibration(jsonb, jsonb, text, text) to authenticated;
