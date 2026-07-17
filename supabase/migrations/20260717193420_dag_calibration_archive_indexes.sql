create index dag_calibration_reviews_archived_by_idx
    on public.dag_calibration_reviews (archived_by)
    where archived_by is not null;

create index dag_calibration_reviews_active_queue_idx
    on public.dag_calibration_reviews (status, created_at desc)
    where archived_at is null;
