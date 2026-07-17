revoke all on function public.dag_calibration_submission_authorized(uuid) from public, anon, authenticated;
grant execute on function public.dag_calibration_submission_authorized(uuid) to service_role;
