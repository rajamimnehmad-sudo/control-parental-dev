export function getDeploymentCommit(): string {
  return process.env.VERCEL_GIT_COMMIT_SHA ?? process.env.CF_PAGES_COMMIT_SHA ?? "local";
}

export function getDeploymentBuildLabel(): string {
  const commit = getDeploymentCommit();
  return commit === "local" ? "Superweb DEV · build local" : `Superweb DEV · build ${commit.slice(0, 7)}`;
}
