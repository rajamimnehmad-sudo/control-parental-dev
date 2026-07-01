# Supabase Setup

This project uses Supabase as the remote replica for offline-first sync. Room remains the operational local database.

## 1. Create A Supabase Project

1. Go to https://supabase.com.
2. Create a free account or sign in.
3. Create a new project.
4. Choose a project name, region and database password.
5. Wait until the project dashboard is ready.

## 2. Run The SQL Files

Open the Supabase dashboard for the project.

1. Go to SQL Editor.
2. Create a new query.
3. Paste and run `supabase/schema.sql`.
4. Create another query.
5. Paste and run `supabase/rls.sql`.
6. Optionally inspect `supabase/seed.sql`.

Do not run the commented seed values in production. They are only examples for local manual testing.

## 3. Verify Realtime

The schema script attempts to enable Realtime automatically for the MVP tables.

In the Supabase dashboard, verify:

1. Go to Database.
2. Open Replication or Realtime settings.
3. Confirm Realtime is enabled for these tables only:
   - `devices`
   - `policies`
   - `policy_rules`
   - `daily_limits`
   - `access_requests`
   - `extra_time_grants`

Do not enable Realtime for technical logs or high-volume event tables.

## 4. Get URL And Anon Key

1. Go to Project Settings.
2. Open API.
3. Copy Project URL.
4. Copy the public anon key.

Never use the service role key in Android.

## 5. Configure The App

Create a local `.env` file from `.env.example`:

```text
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anon-key
```

The `.env` file must stay local and must not be committed.

Build-time environment variables with the same names override `.env` values, which is useful for CI.

## 6. Verify The Project

1. Run `supabase/schema.sql` successfully.
2. Run `supabase/rls.sql` successfully.
3. Confirm the tables exist in Table Editor.
4. Confirm RLS is enabled for every public table.
5. Confirm Realtime is enabled only for the six MVP sync tables.
6. Confirm `.env` contains only URL and anon key.

## 7. Create Test Activations

For a manual test without a backend panel:

1. Go to Authentication.
2. Create a user with email and password.
3. Copy the created user id.
4. Open SQL Editor.
5. Insert an account for that user:

```sql
insert into public.accounts (owner_user_id, name)
values ('AUTH_USER_ID_HERE', 'Test account')
returning id;
```

6. Copy the returned account id.
7. Insert a one-time activation code:

```sql
insert into public.activation_codes (account_id, code_hash, expires_at)
values (
    'ACCOUNT_ID_HERE',
    crypt('TEST-USER-CODE', gen_salt('bf')),
    now() + interval '7 days'
);
```

8. Insert a second one-time activation code for App Admin:

```sql
insert into public.activation_codes (account_id, code_hash, expires_at)
values (
    'ACCOUNT_ID_HERE',
    crypt('TEST-ADMIN-CODE', gen_salt('bf')),
    now() + interval '7 days'
);
```

9. In App Usuario, enter:
   - email: the Auth user email;
   - password: the Auth user password;
   - activation code: `TEST-USER-CODE`.

10. In App Admin, enter:
   - email: the same Auth user email;
   - password: the same Auth user password;
   - activation code: `TEST-ADMIN-CODE`.

The app signs in with Supabase Auth, stores the user token locally, calls `activate_device`, and saves the returned device activation locally.

This is intentionally minimal:

- email and password create the Supabase Auth session;
- activation code binds the authenticated account to a device;
- App Usuario activates with role `user`;
- App Admin activates with role `admin`;
- no Service Role Key is used on Android;
- no payment, license or community role logic is implemented yet.

## 8. Test The Full MVP Flow

1. Build both dev APKs.
2. Install App Usuario and App Admin on one or two Android devices.
3. Activate App Usuario with `TEST-USER-CODE`.
4. Activate App Admin with `TEST-ADMIN-CODE`.
5. In App Usuario, create an access request or extra-time request.
6. Keep network enabled and open App Admin.
7. App Admin should receive the request after Realtime triggers sync. If Realtime is delayed, WorkManager retries in the background.
8. Approve, reject or grant extra time from App Admin.
9. App Usuario should receive the updated request/grant through Realtime or the next WorkManager sync.
10. Create a simple app/domain rule in App Admin.
11. App Usuario should pull the policy/rule into Room and the PolicyEngine-backed services should use the updated snapshot.

## 9. Security Rules

- Android clients use only the anon key.
- The service role key is never placed in Android, Gradle or `.env`.
- RLS restricts records to the authenticated account owner.
- The anon key identifies the public client; user data still requires a Supabase Auth user token.
- The activation RPC is executable only by authenticated users.
- Offline behavior must not depend on Supabase availability.

## 10. Troubleshooting

- If reads return no rows, verify the user is authenticated and owns the account.
- If inserts fail, verify RLS policies and required foreign keys.
- If Realtime does not fire, verify the table is enabled for Realtime in Supabase.
- If the app works offline but not online, check `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
- If activation fails with an invalid code, remember each activation code is one-time use.
- If changes do not appear immediately, wait for WorkManager or reopen the app to restart Realtime with the stored session.
