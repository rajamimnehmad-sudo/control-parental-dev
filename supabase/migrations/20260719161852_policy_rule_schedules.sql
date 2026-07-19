alter table public.policy_rules
    add column if not exists active_window_start_minute integer,
    add column if not exists active_window_end_minute integer,
    add column if not exists active_days_mask integer not null default 127;

alter table public.policy_rules
    drop constraint if exists policy_rules_active_window_pair_check,
    add constraint policy_rules_active_window_pair_check
        check (
            (active_window_start_minute is null and active_window_end_minute is null)
            or
            (
                active_window_start_minute between 0 and 1439
                and active_window_end_minute between 0 and 1439
            )
        ),
    drop constraint if exists policy_rules_active_days_mask_check,
    add constraint policy_rules_active_days_mask_check
        check (active_days_mask between 1 and 127);

create index if not exists policy_rules_active_schedule_idx
    on public.policy_rules (policy_id, scope, target, active_days_mask)
    where enabled = true
      and deleted_at is null
      and active_window_start_minute is not null;

comment on column public.policy_rules.active_window_start_minute is
    'Inclusive minute of day in America/Argentina/Buenos_Aires for scheduled rules.';
comment on column public.policy_rules.active_window_end_minute is
    'Exclusive minute of day in America/Argentina/Buenos_Aires; values lower than start span midnight.';
comment on column public.policy_rules.active_days_mask is
    'ISO weekday bit mask: Monday bit 0 through Sunday bit 6.';
