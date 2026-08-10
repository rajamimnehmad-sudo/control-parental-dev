create table if not exists public.gloshia_r3_review_sessions (
  id uuid primary key,
  token_hash text not null unique check (length(token_hash) = 64),
  title text not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
alter table public.gloshia_r3_review_sessions enable row level security;

create table if not exists public.gloshia_r3_review_items (
  session_id uuid not null references public.gloshia_r3_review_sessions(id) on delete cascade,
  position integer not null check (position >= 0),
  sample_id text not null,
  object_path text not null,
  labels jsonb not null,
  existing_positive_signals text[] not null default '{}',
  signals_to_review text[] not null default '{}',
  primary key (session_id, sample_id),
  unique (session_id, position)
);
alter table public.gloshia_r3_review_items enable row level security;

create table if not exists public.gloshia_r3_owner_reviews (
  session_id uuid not null,
  sample_id text not null,
  labels jsonb not null,
  reviewed_at timestamptz not null default now(),
  reviewer_id text not null default 'local-owner',
  primary key (session_id, sample_id),
  foreign key (session_id, sample_id)
    references public.gloshia_r3_review_items(session_id, sample_id)
    on delete cascade
);
alter table public.gloshia_r3_owner_reviews enable row level security;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'gloshia-private-review',
  'gloshia-private-review',
  false,
  10485760,
  array['image/jpeg','image/png','image/webp']
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;
