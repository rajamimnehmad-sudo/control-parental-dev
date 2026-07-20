alter table public.device_protection_controls
add column if not exists recovery_kit_revision bigint not null default 0,
add column if not exists recovery_kit jsonb not null default '[]'::jsonb,
add column if not exists recovery_consumed_slots integer[] not null default '{}'::integer[];

alter table public.device_protection_controls
drop constraint if exists device_protection_controls_recovery_kit_revision_check;

alter table public.device_protection_controls
add constraint device_protection_controls_recovery_kit_revision_check
check (recovery_kit_revision >= 0);

alter table public.device_protection_controls
drop constraint if exists device_protection_controls_recovery_kit_shape_check;

alter table public.device_protection_controls
add constraint device_protection_controls_recovery_kit_shape_check
check (
    jsonb_typeof(recovery_kit) = 'array'
    and jsonb_array_length(recovery_kit) <= 5
    and cardinality(recovery_consumed_slots) <= 5
);

drop function if exists public.ack_device_protection_control(uuid, bigint, bigint);

create or replace function public.ack_device_protection_control(
    p_device_id uuid,
    p_command_revision bigint,
    p_recovery_consumed_revision bigint default null,
    p_recovery_kit_revision bigint default null,
    p_recovery_consumed_slots integer[] default '{}'::integer[]
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.device_token_matches_device(p_device_id) then
        raise exception 'invalid device authorization';
    end if;

    update public.device_protection_controls
    set applied_revision = greatest(applied_revision, least(command_revision, p_command_revision)),
        applied_at = now(),
        recovery_consumed_revision = case
            when p_recovery_consumed_revision is null then recovery_consumed_revision
            else greatest(
                recovery_consumed_revision,
                least(recovery_revision, p_recovery_consumed_revision)
            )
        end,
        recovery_consumed_slots = case
            when p_recovery_kit_revision is distinct from recovery_kit_revision then recovery_consumed_slots
            else array(
                select distinct slot
                from unnest(
                    recovery_consumed_slots || coalesce(p_recovery_consumed_slots, '{}'::integer[])
                ) as consumed(slot)
                where slot between 0 and 4
                order by slot
            )
        end,
        updated_at = now()
    where device_id = p_device_id;
end;
$$;

revoke all on function public.ack_device_protection_control(uuid, bigint, bigint, bigint, integer[])
from public, anon, authenticated;

grant execute on function public.ack_device_protection_control(uuid, bigint, bigint, bigint, integer[])
to anon, authenticated;
