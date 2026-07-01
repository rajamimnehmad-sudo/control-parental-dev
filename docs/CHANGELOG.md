# Changelog

## Unreleased

- Connected App Usuario and App Admin to the existing Supabase offline-first sync flow.
- Added immediate WorkManager sync requests after activation, user requests, admin approvals, extra-time grants and rule changes.
- Added remote outbox support for policies, policy rules and extra-time grants.
- Added Supabase device sync so App Admin can receive activated devices from the remote backend.
- Updated activation to send App Usuario as `user` and App Admin as `admin`.
- Updated Supabase SQL to support device roles, re-runnable triggers/policies and Realtime for `devices`.
- Added App Admin MVP with Login / Activation, Dashboard, Devices, Requests and Rules screens.
- Added App Admin Hilt application entry point and simple Compose bottom navigation.
- Added admin use cases for observing devices, observing requests, changing request status, granting extra time and managing policy rules.
- Added local `DeviceRepository` backed by existing Room `devices` table.
- Extended local repositories to support offline-first admin actions through the existing outbox.
- Added simple rule creation for app and domain targets.
- Added request approval, rejection and extra-time grant actions.
- Added App Admin network permissions for Supabase sync.
- Added dev APK manual update flow for App Usuario.
