# Vercel deployment filter

`web-super-admin` is isolated from unrelated monorepo commits through `web-super-admin/vercel.json`.

Vercel's `ignoreCommand` compares `HEAD^` with `HEAD` from the Super Admin project root. If nothing changed inside `web-super-admin`, the command exits 0 and Vercel skips the build. If Super Admin files changed, it exits non-zero and the deployment may continue normally.

This prevents Glosh Central / Ruta Técnica, Chrome, Android and unrelated documentation commits from consuming Super Admin preview builds or generating deployment-failure notifications.
