-- Remove the retired DAG 1/2 server search and calibration backends.
--
-- The DAG product controls intentionally remain:
--   * community_licenses.dag_entitled
--   * the __dag_enabled__ per-device policy rule
--   * the entitlement and device-management RPCs
--
-- Those controls belong to the new standalone DAG browser and are not legacy.

drop function if exists public.authorize_and_consume_dag_search(uuid);
drop function if exists public.authorize_dag_suggestions(uuid);
drop function if exists public.consume_dag_search_quota(uuid);

drop function if exists public.super_admin_get_dag_usage_summary();
drop function if exists public.super_admin_list_dag_usage_devices();
drop function if exists public.super_admin_set_dag_search_monthly_limit(uuid, integer);

drop function if exists public.dag_calibration_submission_authorized(uuid);
drop function if exists public.dag_create_automatic_calibration_candidate(jsonb, jsonb, text, text);
drop function if exists public.super_admin_activate_dag_calibration(uuid);
drop function if exists public.super_admin_create_dag_calibration(jsonb, jsonb, text, text);
drop function if exists public.super_admin_label_dag_calibration_review(uuid, text, text, text);
drop function if exists public.super_admin_list_dag_calibration_audit(integer);
drop function if exists public.super_admin_list_dag_calibration_models();
drop function if exists public.super_admin_list_dag_calibration_reviews(text, integer);
drop function if exists public.super_admin_list_dag_calibration_reviews_v2(text, integer);
drop function if exists public.super_admin_list_dag_calibrations();

drop function if exists public.dag_v2_calibration_authorize_and_consume(uuid, text);
drop function if exists public.dag_v2_calibration_mark_sample(uuid, text, text);
drop function if exists public.dag_v2_calibration_register_sample(
    text,
    text,
    text,
    integer,
    integer,
    text,
    integer,
    text,
    text,
    text,
    text,
    text,
    text,
    text
);
drop function if exists public.dag_v2_calibration_submission_authorized(uuid);
drop function if exists public.dag_v2_calibration_upsert_label(uuid, text, text, text);
drop function if exists public.dag_v2_calibration_hamming_distance(text, text);

drop function if exists public.dag_search_authorized(uuid);

drop table if exists public.dag_calibration_version_reviews;
drop table if exists public.dag_calibration_audit;
drop table if exists public.dag_calibration_models;
drop table if exists public.dag_calibration_versions;
drop table if exists public.dag_calibration_reviews;
drop table if exists public.dag_search_monthly_usage;

drop table if exists public.dag_v2_calibration_audit;
drop table if exists public.dag_v2_calibration_labels;
drop table if exists public.dag_v2_calibration_samples;

alter table public.community_licenses
    drop constraint if exists community_licenses_dag_search_monthly_limit_check,
    drop column if exists dag_search_monthly_limit;

drop policy if exists "Super Admin reads DAG calibration images" on storage.objects;

-- Storage objects and their buckets are removed separately through the Storage API.
