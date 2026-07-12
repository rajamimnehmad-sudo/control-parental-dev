import { createClient } from "@supabase/supabase-js";

const publicBase =
  `${Deno.env.get("SUPABASE_URL")}/storage/v1/object/public/dev-updates/web-domain-list/dev`;
const allowedActions = new Set(["refresh", "publish_canary", "remove_canary", "status"]);

Deno.serve(async (request) => {
  const authorization = request.headers.get("Authorization") ?? "";
  if (!(await isSuperAdmin(authorization))) return json({ error: "Forbidden" }, 403);
  const body = await request.json().catch(() => ({}));
  const action = typeof body.action === "string" ? body.action : "status";
  if (!allowedActions.has(action)) return json({ error: "Unsupported action" }, 400);
  if (action === "status") return json(await currentStatus());
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const requestBody = new TextEncoder().encode(JSON.stringify({ action, requestedAt: new Date().toISOString() }));
  const { error } = await client.storage.from("dev-updates").upload(
    "web-domain-list/dev/request.json",
    requestBody,
    { contentType: "application/json", cacheControl: "no-cache", upsert: true },
  );
  if (error) return json({ error: `Request queue failed: ${error.message}` }, 502);
  return json({ ok: true, queued: true, action }, 202);
});

async function isSuperAdmin(authorization: string): Promise<boolean> {
  if (!authorization.startsWith("Bearer ")) return false;
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authorization } },
  });
  const { data, error } = await client.rpc("is_super_admin");
  return !error && data === true;
}

async function currentStatus() {
  const [manifestResponse, statusResponse] = await Promise.all([
    fetch(`${publicBase}/current-manifest.json?ts=${Date.now()}`, { cache: "no-store" }),
    fetch(`${publicBase}/status.json?ts=${Date.now()}`, { cache: "no-store" }),
  ]);
  return {
    active: manifestResponse.ok ? await manifestResponse.json() : null,
    operational: statusResponse.ok ? await statusResponse.json() : null,
  };
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}
