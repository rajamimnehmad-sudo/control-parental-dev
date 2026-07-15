import { createClient } from "@supabase/supabase-js";

type DagSearchPayload = {
  device_id?: string;
  query?: string;
  language?: string;
};

type BraveWebResult = {
  title?: string;
  url?: string;
  description?: string;
};

const rateWindows = new Map<string, { minute: number; count: number }>();

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Método no permitido." }, 405);

  const payload = await request.json().catch(() => ({})) as DagSearchPayload;
  const deviceId = payload.device_id?.trim() ?? "";
  const query = payload.query?.trim() ?? "";
  const language = supportedLanguage(payload.language);
  if (!isUuid(deviceId) || query.length < 2 || query.length > 400 || wordCount(query) > 50) {
    return json({ error: "La búsqueda no es válida." }, 400);
  }

  const supabaseUrl = requiredEnv("SUPABASE_URL");
  const anonKey = requiredEnv("SUPABASE_ANON_KEY");
  const deviceToken = request.headers.get("x-device-token") ?? "";
  const authHeader = request.headers.get("authorization") ?? `Bearer ${anonKey}`;
  const client = createClient(supabaseUrl, anonKey, {
    global: { headers: { authorization: authHeader, "x-device-token": deviceToken } },
  });
  if (!consumeRate(deviceId)) return json({ error: "Esperá un minuto antes de volver a buscar." }, 429);

  const braveKey = Deno.env.get("BRAVE_SEARCH_API_KEY")?.trim() ?? "";
  if (!braveKey) return json({ error: "El buscador todavía no está configurado." }, 503);
  const { data: authorization, error: authorizationError } = await client.rpc(
    "authorize_and_consume_dag_search",
    {
      p_device_id: deviceId,
    },
  );
  if (authorizationError) return json({ error: "No se pudo comprobar el acceso a DAG." }, 503);
  if (authorization === "unauthorized") {
    return json({ error: "DAG no está habilitado para este dispositivo." }, 403);
  }
  if (authorization !== "allowed") {
    return json({ error: "Alcanzaste el límite de búsquedas disponible para este mes." }, 429);
  }

  const endpoint = new URL("https://api.search.brave.com/res/v1/web/search");
  endpoint.searchParams.set("q", query);
  endpoint.searchParams.set("count", "10");
  endpoint.searchParams.set("country", "AR");
  endpoint.searchParams.set("search_lang", language);
  endpoint.searchParams.set("ui_lang", language === "he" ? "he-IL" : language === "en" ? "en-US" : "es-AR");
  endpoint.searchParams.set("safesearch", "strict");
  endpoint.searchParams.set("spellcheck", "1");
  endpoint.searchParams.set("text_decorations", "0");

  const response = await fetch(endpoint, {
    method: "GET",
    headers: {
      accept: "application/json",
      "accept-encoding": "gzip",
      "x-subscription-token": braveKey,
    },
  });
  if (!response.ok) {
    return json({ error: "El buscador no está disponible en este momento." }, response.status === 429 ? 429 : 502);
  }

  const body = await response.json().catch(() => ({}));
  const remoteResults = Array.isArray(body?.web?.results) ? body.web.results as BraveWebResult[] : [];
  const results = remoteResults
    .map((result) => ({
      title: cleanText(result.title, 240),
      url: cleanHttpsUrl(result.url),
      description: cleanText(result.description, 600),
    }))
    .filter((result) => result.title.length > 0 && result.url.length > 0)
    .slice(0, 10);
  return json({ results }, 200);
});

function consumeRate(deviceId: string): boolean {
  const minute = Math.floor(Date.now() / 60_000);
  const current = rateWindows.get(deviceId);
  if (!current || current.minute !== minute) {
    rateWindows.set(deviceId, { minute, count: 1 });
    return true;
  }
  if (current.count >= 20) return false;
  current.count += 1;
  return true;
}

function supportedLanguage(value?: string): "es" | "en" | "he" {
  return value === "en" || value === "he" ? value : "es";
}

function cleanText(value: unknown, maxLength: number): string {
  if (typeof value !== "string") return "";
  return value.replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim().slice(0, maxLength);
}

function cleanHttpsUrl(value: unknown): string {
  if (typeof value !== "string" || value.length > 2048) return "";
  try {
    const url = new URL(value);
    return url.protocol === "https:" ? url.toString() : "";
  } catch {
    return "";
  }
}

function wordCount(value: string): number {
  return value.split(/\s+/).filter(Boolean).length;
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store, max-age=0",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
    },
  });
}
