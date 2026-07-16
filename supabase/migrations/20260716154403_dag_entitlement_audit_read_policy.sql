create index if not exists idx_community_license_audit_actor_user
on public.community_license_audit(actor_user_id);

drop policy if exists community_license_audit_super_admin_select
on public.community_license_audit;

create policy community_license_audit_super_admin_select
on public.community_license_audit
for select
to authenticated
using (public.is_super_admin());

grant select on public.community_license_audit to authenticated;
