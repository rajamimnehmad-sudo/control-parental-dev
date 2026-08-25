# Vercel deployment filter

`web-super-admin` is isolated from unrelated monorepo commits through `web-super-admin/vercel.json`.

For the Glosh Control Center branch `build/glosh-control-center-v2`, automatic Vercel deployments are disabled explicitly with `git.deploymentEnabled=false`. This stops Ruta Técnica / tracker commits before they create a Super Admin deployment.

For other branches where deployments remain enabled, `ignoreCommand` compares `HEAD^` with `HEAD` from the Super Admin project root. If nothing changed inside `web-super-admin`, Vercel can skip the build; if Super Admin files changed, the deployment may continue normally.

This prevents Glosh Central / Ruta Técnica, Chrome, Android and unrelated documentation commits on the Control Center branch from consuming Super Admin preview builds or generating deployment-failure notifications.
