select
    'access_requests_total' as metric,
    count(*)::text as value
from public.access_requests
union all
select
    'access_requests_active',
    count(*)::text
from public.access_requests
where deleted_at is null
union all
select
    'access_requests_pending_remote',
    count(*)::text
from public.access_requests
where deleted_at is null and status = 'PendingRemote'
union all
select
    'devices_total',
    count(*)::text
from public.devices
union all
select
    'device_activations_total',
    count(*)::text
from public.device_activations;

select
    ar.id,
    ar.account_id,
    ar.device_id,
    d.app_role as device_role,
    ar.target_type,
    ar.target,
    ar.reason,
    ar.requested_minutes,
    ar.status,
    ar.created_at,
    ar.updated_at,
    ar.deleted_at
from public.access_requests ar
left join public.devices d on d.id = ar.device_id
order by ar.created_at desc
limit 25;

select
    a.id as account_id,
    a.owner_user_id,
    d.id as device_id,
    d.app_role,
    d.display_name,
    d.deleted_at
from public.accounts a
left join public.devices d on d.account_id = a.id
order by a.created_at desc, d.created_at desc
limit 25;
