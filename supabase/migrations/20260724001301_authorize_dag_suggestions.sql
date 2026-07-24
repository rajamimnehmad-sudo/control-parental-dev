create or replace function public.authorize_dag_suggestions(p_device_id uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
    select public.dag_search_authorized(p_device_id);
$$;

comment on function public.authorize_dag_suggestions(uuid) is
'Authorizes DAG autosuggest without consuming monthly web-search quota; device token and entitlement are checked by dag_search_authorized.';

revoke all on function public.authorize_dag_suggestions(uuid) from public, anon, authenticated;
grant execute on function public.authorize_dag_suggestions(uuid) to anon, authenticated;
