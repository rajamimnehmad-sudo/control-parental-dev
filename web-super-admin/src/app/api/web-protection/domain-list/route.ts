import { NextResponse } from "next/server";
import { lookupDomain } from "@/lib/domain-list";
import { createClient } from "@/lib/supabase/server";

const allowedActions = new Set(["refresh", "status", "publish_canary", "remove_canary", "add_domain"]);

async function authorize() {
  const supabase = await createClient();
  const { data: claims } = await supabase.auth.getClaims();
  if (!claims?.claims) return { error: NextResponse.json({ error: "No autenticado" }, { status: 401 }) };
  const { data: allowed } = await supabase.rpc("is_super_admin");
  if (allowed !== true) return { error: NextResponse.json({ error: "Sin permisos de Super Admin" }, { status: 403 }) };
  return { supabase };
}

export async function GET(request: Request) {
  const authorization = await authorize();
  if ("error" in authorization) return authorization.error;
  const domain = new URL(request.url).searchParams.get("domain") ?? "";
  try {
    return NextResponse.json(await lookupDomain(domain));
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "No se pudo consultar el dominio." }, { status: 400 });
  }
}

export async function POST(request: Request) {
  const authorization = await authorize();
  if ("error" in authorization) return authorization.error;
  const { supabase } = authorization;
  const payload = await request.json().catch(() => ({}));
  if (!allowedActions.has(payload.action)) return NextResponse.json({ error: "Accion invalida" }, { status: 400 });
  const { data, error } = await supabase.functions.invoke("update-web-domain-list", {
    body: { action: payload.action, domain: payload.domain, category: payload.category },
  });
  if (error) return NextResponse.json({ error: error.message }, { status: 502 });
  return NextResponse.json(data ?? { ok: true });
}
