import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";

const allowedActions = new Set(["refresh", "status", "publish_canary", "remove_canary"]);

export async function POST(request: Request) {
  const supabase = await createClient();
  const { data: claims } = await supabase.auth.getClaims();
  if (!claims?.claims) return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  const { data: allowed } = await supabase.rpc("is_super_admin");
  if (allowed !== true) return NextResponse.json({ error: "Sin permisos de Super Admin" }, { status: 403 });
  const payload = await request.json().catch(() => ({}));
  if (!allowedActions.has(payload.action)) return NextResponse.json({ error: "Accion invalida" }, { status: 400 });
  const { data, error } = await supabase.functions.invoke("update-web-domain-list", { body: { action: payload.action } });
  if (error) return NextResponse.json({ error: error.message }, { status: 502 });
  return NextResponse.json(data ?? { ok: true });
}
