alter table public.dag_calibration_reviews
    drop constraint if exists dag_calibration_reviews_submission_source_check;

alter table public.dag_calibration_reviews
    add constraint dag_calibration_reviews_submission_source_check
        check (
            submission_source in (
                'automatic_uncertainty',
                'manual_dag',
                'manual_dag_false_positive'
            )
        );
