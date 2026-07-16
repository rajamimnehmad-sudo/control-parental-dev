create table if not exists public.activation_code_audit (
    id uuid primary key default gen_random_uuid(),
    activation_code_id uuid not null references public.activation_codes(id),
    community_admin_id uuid references public.community_admins(id),
    actor_user_id uuid references auth.users(id),
    event_type text not null check (event_type in ('generated', 'used', 'revoked')),
    intended_app_role text not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

alter table public.activation_code_audit enable row level security;

create index if not exists idx_activation_code_audit_admin_created
on public.activation_code_audit(community_admin_id, created_at desc);

create index if not exists idx_activation_code_audit_actor
on public.activation_code_audit(actor_user_id);

drop policy if exists activation_code_audit_super_admin_select
on public.activation_code_audit;

create policy activation_code_audit_super_admin_select
on public.activation_code_audit
for select
to authenticated
using (public.is_super_admin());

revoke all on table public.activation_code_audit from public, anon, authenticated;
grant select on public.activation_code_audit to authenticated;

create or replace function public.audit_activation_code_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    audit_event text;
begin
    if tg_op = 'INSERT' then
        audit_event := 'generated';
    elsif old.used_at is null and new.used_at is not null then
        audit_event := 'used';
    elsif old.deleted_at is null and new.deleted_at is not null then
        audit_event := 'revoked';
    else
        return new;
    end if;

    insert into public.activation_code_audit (
        activation_code_id,
        community_admin_id,
        actor_user_id,
        event_type,
        intended_app_role,
        expires_at
    ) values (
        new.id,
        new.community_admin_id,
        auth.uid(),
        audit_event,
        new.intended_app_role,
        new.expires_at
    );

    return new;
end;
$$;

revoke all on function public.audit_activation_code_change() from public, anon, authenticated;

drop trigger if exists trg_activation_codes_audit on public.activation_codes;
create trigger trg_activation_codes_audit
after insert or update of used_at, deleted_at on public.activation_codes
for each row execute function public.audit_activation_code_change();

create or replace function public.super_admin_revoke_admin_pairing_codes(
    target_community_id uuid,
    target_admin_id uuid
)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    revoked_count integer;
begin
    perform public.require_super_admin();

    if not exists (
        select 1
        from public.community_admins
        where id = target_admin_id
          and community_id = target_community_id
          and deleted_at is null
    ) then
        raise exception 'Community admin not found';
    end if;

    update public.activation_codes
    set deleted_at = now(),
        updated_at = now()
    where community_admin_id = target_admin_id
      and intended_app_role = 'admin'
      and used_at is null
      and deleted_at is null;

    get diagnostics revoked_count = row_count;
    return revoked_count;
end;
$$;

revoke all on function public.super_admin_revoke_admin_pairing_codes(uuid, uuid) from public, anon, authenticated;
grant execute on function public.super_admin_revoke_admin_pairing_codes(uuid, uuid) to authenticated;
