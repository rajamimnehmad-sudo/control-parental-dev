-- Development-only Supabase Storage setup for manual app updates.
-- Creates a public DEV bucket for manifests and APKs.

insert into storage.buckets (
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values (
    'dev-updates',
    'dev-updates',
    true,
    104857600,
    array[
        'application/json',
        'application/vnd.android.package-archive',
        'application/octet-stream'
    ]
)
on conflict (id) do nothing;
