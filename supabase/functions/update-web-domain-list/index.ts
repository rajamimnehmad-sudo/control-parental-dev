import { createClient } from "@supabase/supabase-js";

const publicBase =
  `${Deno.env.get("SUPABASE_URL")}/storage/v1/object/public/dev-updates/web-domain-list/dev`;
const allowedActions = new Set(["refresh", "publish_canary", "remove_canary", "status", "add_domain"]);
const allowedCategories = new Set(["adult", "gambling", "drugs", "piracy_torrents"]);

Deno.serve(async (request) => {
  if (request.headers.get("x-domain-list-publish-secret") === Deno.env.get("DOMAIN_LIST_PUBLISH_SECRET")) {
    return publishPointer(request);
  }
  const authorization = request.headers.get("Authorization") ?? "";
  const superAdminId = await superAdminUserId(authorization);
  if (!superAdminId) return json({ error: "Forbidden" }, 403);
  const body = await request.json().catch(() => ({}));
  const action = typeof body.action === "string" ? body.action : "status";
  if (!allowedActions.has(action)) return json({ error: "Unsupported action" }, 400);
  if (action === "status") return json(await currentStatus());
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  if (action === "add_domain") {
    const domain = normalizeDomain(body.domain);
    const category = typeof body.category === "string" ? body.category : "";
    if (!domain || !allowedCategories.has(category)) return json({ error: "Invalid domain or category" }, 400);
    const path = "web-domain-list/dev/manual-domains.json";
    const { data: existing } = await client.storage.from("dev-updates").download(path);
    const entries = existing ? await existing.json().catch(() => []) : [];
    const current = Array.isArray(entries) ? entries : [];
    if (!current.some((entry) => entry?.domain === domain)) {
      current.push({ domain, category, addedAt: new Date().toISOString(), addedBy: superAdminId });
      current.sort((left, right) => left.domain.localeCompare(right.domain));
      const { error: uploadError } = await client.storage.from("dev-updates").upload(
        path,
        new TextEncoder().encode(JSON.stringify(current)),
        { contentType: "application/json", cacheControl: "no-cache", upsert: true },
      );
      if (uploadError) return json({ error: `Manual domain write failed: ${uploadError.message}` }, 502);
    }
    const queued = new TextEncoder().encode(JSON.stringify({ action: "refresh", requestedAt: new Date().toISOString() }));
    const { error: queueError } = await client.storage.from("dev-updates").upload(
      "web-domain-list/dev/request.json",
      queued,
      { contentType: "application/json", cacheControl: "no-cache", upsert: true },
    );
    if (queueError) return json({ error: `Request queue failed: ${queueError.message}` }, 502);
    return json({ ok: true, queued: true, action, domain, category }, 202);
  }
  const requestBody = new TextEncoder().encode(JSON.stringify({ action, requestedAt: new Date().toISOString() }));
  const { error } = await client.storage.from("dev-updates").upload(
    "web-domain-list/dev/request.json",
    requestBody,
    { contentType: "application/json", cacheControl: "no-cache", upsert: true },
  );
  if (error) return json({ error: `Request queue failed: ${error.message}` }, 502);
  return json({ ok: true, queued: true, action }, 202);
});

async function publishPointer(request: Request): Promise<Response> {
  const body = await request.json().catch(() => ({}));
  const path = typeof body.path === "string" ? body.path : "";
  if (!new Set(["current-manifest.json", "status.json"]).has(path)) {
    return json({ error: "Unsupported publication path" }, 400);
  }
  const content = typeof body.content === "string" ? body.content : "";
  if (!content || content.length > 128_000) return json({ error: "Invalid publication content" }, 400);
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { error } = await client.storage.from("dev-updates").upload(
    `web-domain-list/dev/${path}`,
    new TextEncoder().encode(content),
    { contentType: "application/json", cacheControl: "max-age=60", upsert: true },
  );
  if (error) return json({ error: `Publication failed: ${error.message}` }, 502);
  return json({ ok: true, path });
}

async function superAdminUserId(authorization: string): Promise<string | null> {
  if (!authorization.startsWith("Bearer ")) return null;
  const client = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authorization } },
  });
  const { data: userData, error: userError } = await client.auth.getUser(authorization.slice("Bearer ".length));
  if (userError || !userData.user) return null;
  const { data, error } = await client.rpc("is_super_admin");
  return !error && data === true ? userData.user.id : null;
}

function normalizeDomain(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const host = raw.trim().toLowerCase().replace(/^https?:\/\//, "").split("/")[0].split(":")[0].replace(/\.$/, "");
  const value = host.startsWith("www.") ? host.slice(4) : host;
  return /^(?=.{3,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z0-9-]{2,}$/.test(value) ? value : null;
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
