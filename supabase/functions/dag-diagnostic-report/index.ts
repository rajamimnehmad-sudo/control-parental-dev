import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "@supabase/supabase-js";

const MAX_COMPRESSED_BYTES = 256 * 1024;
const MAX_JSON_BYTES = 1024 * 1024;
const MAX_EVENTS = 4096;
const MAX_REPORTS_PER_HOUR = 120;
const RETENTION_DAYS = 14;
const UUID_PATTERN = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/iu;
const REPORT_CODE_PATTERN = /^DAG-[A-Z0-9]{8}$/u;
const SAFE_EVENT_VALUE = /^[a-z0-9_]{1,48}$/u;
const SAFE_METADATA_VALUE = /^[A-Za-z0-9 ._()+-]{1,80}$/u;
const PACKAGE_PATTERN = /^com\.contentfilter\.dagbrowser(?:\.dev|\.diagnostic\.dev)?$/u;
const EVENT_KEYS = new Set([
  "sequence", "wall_ms", "elapsed_ms", "type", "tab", "candidate", "carrier", "priority",
  "action", "reason", "basis", "bytes", "width", "height", "score", "full_score",
  "bridge_ms", "queue_ms", "native_ms", "inference_ms", "inferences", "count",
]);
const SAFE_EVENT_TYPES = new Set([
  "app_started", "navigation_started", "barrier_ready", "document_sanitized", "viewport_ready",
  "page_visible", "barrier_timeout", "media_decision", "media_drop",
]);

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

async function authorized(req: Request, header: string, secretName: string): Promise<boolean> {
  const provided = req.headers.get(header) ?? "";
  const expectedHash = Deno.env.get(secretName) ?? "";
  if (provided.length < 32 || !/^[a-f0-9]{64}$/u.test(expectedHash)) return false;
  return constantTimeEqual(await sha256(provided), expectedHash);
}

async function readBody(req: Request): Promise<unknown> {
  const declaredLength = Number(req.headers.get("content-length") ?? "0");
  if (!Number.isFinite(declaredLength) || declaredLength < 1 || declaredLength > MAX_COMPRESSED_BYTES) {
    throw new Error("invalid_size");
  }
  const compressed = new Uint8Array(await req.arrayBuffer());
  if (compressed.byteLength > MAX_COMPRESSED_BYTES) throw new Error("invalid_size");
  let decoded: Uint8Array;
  if (req.headers.get("content-encoding")?.toLowerCase() === "gzip") {
    const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream("gzip"));
    decoded = new Uint8Array(await new Response(stream).arrayBuffer());
  } else {
    decoded = compressed;
  }
  if (decoded.byteLength < 2 || decoded.byteLength > MAX_JSON_BYTES) throw new Error("invalid_size");
  return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(decoded));
}

function finiteNumber(value: unknown, minimum: number, maximum: number): boolean {
  return typeof value === "number" && Number.isFinite(value) && value >= minimum && value <= maximum;
}

function validateEvent(value: unknown): boolean {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const event = value as Record<string, unknown>;
  if (Object.keys(event).some((key) => !EVENT_KEYS.has(key))) return false;
  if (!SAFE_EVENT_TYPES.has(String(event.type ?? ""))) return false;
  if (!finiteNumber(event.sequence, 1, Number.MAX_SAFE_INTEGER)) return false;
  if (!finiteNumber(event.wall_ms, 0, Number.MAX_SAFE_INTEGER)) return false;
  if (!finiteNumber(event.elapsed_ms, 0, Number.MAX_SAFE_INTEGER)) return false;
  for (const key of ["carrier", "priority", "action", "reason", "basis"] as const) {
    if (event[key] !== undefined && (typeof event[key] !== "string" || !SAFE_EVENT_VALUE.test(event[key]))) return false;
  }
  if (event.candidate !== undefined && (typeof event.candidate !== "string" || !/^[a-f0-9]{16}$/u.test(event.candidate))) return false;
  for (const key of ["tab", "bytes", "width", "height", "inferences", "count"] as const) {
    if (event[key] !== undefined && !finiteNumber(event[key], 0, 10_000_000)) return false;
  }
  for (const key of ["score", "full_score"] as const) {
    if (event[key] !== undefined && !finiteNumber(event[key], 0, 1)) return false;
  }
  for (const key of ["bridge_ms", "queue_ms", "native_ms", "inference_ms"] as const) {
    if (event[key] !== undefined && !finiteNumber(event[key], -1, 60_000)) return false;
  }
  return true;
}

type ValidReport = {
  report_id: string;
  session_id: string;
  created_at_ms: number;
  event_count: number;
  dropped_in_memory: number;
  app: { package: string; version_code: number; version_name: string };
  device: { sdk: number; manufacturer: string; model: string };
  events: unknown[];
};

function validateReport(value: unknown): ValidReport | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const report = value as Record<string, unknown>;
  const allowedRoot = new Set(["schema_version", "report_id", "session_id", "created_at_ms", "event_count", "dropped_in_memory", "app", "device", "events"]);
  if (Object.keys(report).some((key) => !allowedRoot.has(key)) || report.schema_version !== 1) return null;
  if (!UUID_PATTERN.test(String(report.report_id ?? "")) || !UUID_PATTERN.test(String(report.session_id ?? ""))) return null;
  if (!Array.isArray(report.events) || report.events.length > MAX_EVENTS || !report.events.every(validateEvent)) return null;
  if (report.event_count !== report.events.length || !finiteNumber(report.dropped_in_memory, 0, 1_000_000)) return null;
  if (!finiteNumber(report.created_at_ms, Date.now() - 30 * 86400_000, Date.now() + 86400_000)) return null;
  const app = report.app as Record<string, unknown> | null;
  const device = report.device as Record<string, unknown> | null;
  if (!app || !device || Array.isArray(app) || Array.isArray(device)) return null;
  if (Object.keys(app).some((key) => !["package", "version_code", "version_name"].includes(key))) return null;
  if (Object.keys(device).some((key) => !["sdk", "manufacturer", "model"].includes(key))) return null;
  if (!PACKAGE_PATTERN.test(String(app.package ?? "")) || !finiteNumber(app.version_code, 1, Number.MAX_SAFE_INTEGER)) return null;
  if (typeof app.version_name !== "string" || !SAFE_METADATA_VALUE.test(app.version_name)) return null;
  if (!finiteNumber(device.sdk, 29, 100)) return null;
  for (const key of ["manufacturer", "model"] as const) {
    if (typeof device[key] !== "string" || !SAFE_METADATA_VALUE.test(device[key])) return null;
  }
  return report as unknown as ValidReport;
}

function reportCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  return `DAG-${[...bytes].map((byte) => alphabet[byte % alphabet.length]).join("")}`;
}

Deno.serve(async (req: Request) => {
  try {
    const client = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      { auth: { persistSession: false, autoRefreshToken: false } },
    );

    if (req.method === "POST") {
      if (!await authorized(req, "x-dag-diagnostic-token", "DAG_DIAGNOSTIC_UPLOAD_TOKEN_SHA256")) {
        return json({ error: "unauthorized" }, 401);
      }
      const since = new Date(Date.now() - 3600_000).toISOString();
      const { count, error: countError } = await client.from("dag_diagnostic_reports")
        .select("id", { count: "exact", head: true }).gte("received_at", since);
      if (countError) return json({ error: "unavailable" }, 503);
      if ((count ?? 0) >= MAX_REPORTS_PER_HOUR) return json({ error: "rate_limited" }, 429);

      const report = validateReport(await readBody(req));
      if (!report) return json({ error: "invalid_report" }, 400);
      const expiresAt = new Date(Date.now() + RETENTION_DAYS * 86400_000).toISOString();
      let code = "";
      let insertError: unknown = null;
      for (let attempt = 0; attempt < 3; attempt += 1) {
        code = reportCode();
        const { error } = await client.from("dag_diagnostic_reports").insert({
          report_code: code,
          report_id: report.report_id,
          session_id: report.session_id,
          created_at: new Date(report.created_at_ms).toISOString(),
          expires_at: expiresAt,
          app_package: report.app.package,
          app_version_code: report.app.version_code,
          app_version_name: report.app.version_name,
          android_sdk: report.device.sdk,
          device_manufacturer: report.device.manufacturer,
          device_model: report.device.model,
          event_count: report.event_count,
          dropped_in_memory: report.dropped_in_memory,
          payload: report,
        });
        if (!error) {
          insertError = null;
          break;
        }
        insertError = error;
      }
      if (insertError) return json({ error: "store_failed" }, 500);
      return json({ report_code: code, expires_at: expiresAt }, 201);
    }

    if (req.method === "GET") {
      if (!await authorized(req, "x-dag-reader-token", "DAG_DIAGNOSTIC_READER_TOKEN_SHA256")) {
        return json({ error: "unauthorized" }, 401);
      }
      const code = new URL(req.url).searchParams.get("code");
      if (code !== null) {
        if (!REPORT_CODE_PATTERN.test(code)) return json({ error: "invalid_code" }, 400);
        const { data, error } = await client.from("dag_diagnostic_reports")
          .select("report_code,received_at,expires_at,payload").eq("report_code", code).maybeSingle();
        if (error) return json({ error: "unavailable" }, 503);
        if (!data) return json({ error: "not_found" }, 404);
        return json(data);
      }
      const { data, error } = await client.from("dag_diagnostic_reports")
        .select("report_code,received_at,expires_at,app_package,app_version_code,app_version_name,android_sdk,device_manufacturer,device_model,event_count,dropped_in_memory")
        .order("received_at", { ascending: false }).limit(20);
      if (error) return json({ error: "unavailable" }, 503);
      return json({ reports: data ?? [] });
    }

    return json({ error: "method_not_allowed" }, 405);
  } catch (error) {
    console.error(error instanceof Error ? error.message : "diagnostic_error");
    return json({ error: "invalid_request" }, 400);
  }
});
